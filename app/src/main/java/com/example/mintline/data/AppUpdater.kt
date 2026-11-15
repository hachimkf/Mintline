package com.example.mintline.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * In-app updates from GitHub Releases.
 *
 * Publish a release on github.com/hachimkf/Mintline with a tag like "v1.2"
 * and attach the signed APK. Installed apps compare that tag with their own
 * versionName and offer the download, then hand it to Android's installer.
 * The update must be signed with the same key as the installed app.
 */
class AppUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0"

    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Mintline-Android")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@runCatching null // no release published yet
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val json = JSONObject(resp.body!!.string())
                val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
                if (!isNewer(tag, currentVersion)) return@runCatching null
                val assets = json.optJSONArray("assets") ?: return@runCatching null
                var apk: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a; break }
                }
                apk ?: return@runCatching null
                UpdateInfo(
                    version = tag,
                    notes = json.optString("body").trim(),
                    apkUrl = apk.optString("browser_download_url"),
                    sizeBytes = apk.optLong("size"),
                )
            }
        }
    }

    /** Downloads the APK, reporting progress 0..1, and returns the file when complete. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("No storage")
            val file = File(dir, "Mintline-${info.version}.apk")
            if (file.exists()) file.delete()
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(
                DownloadManager.Request(Uri.parse(info.apkUrl))
                    .setTitle("Mintline ${info.version}")
                    .setDescription("Downloading update")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(file))
            )
            while (true) {
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) error("Download cancelled")
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@runCatching file
                        DownloadManager.STATUS_FAILED -> error("Download failed")
                    }
                }
                delay(400)
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }

    /** Opens Android's installer (it asks the user to allow installs from Mintline the first time). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val REPO = "hachimkf/Mintline"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val APK_MIME = "application/vnd.android.package-archive"

        /** "1.10" > "1.9", "1.2" > "1.1.5" */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
