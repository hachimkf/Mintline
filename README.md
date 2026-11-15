# Mintline

Live football scores, line-ups and stats for Android, powered by ESPN's public soccer data.
Liquid-glass navigation, Material 3 Expressive styling, light / dark (true black) / system themes.

## Install
Download the latest `Mintline-x.y.apk` from [Releases](https://github.com/hachimkf/Mintline/releases/latest).
Installed apps check this page on launch and offer new versions in-app (Settings → About to check manually).

## Publishing an update
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew :app:assembleRelease` (needs the private `keystore.properties` + `keystore/`, never committed).
3. Create a GitHub release tagged `v<versionName>` and attach the APK.
   Updates must be signed with the same key as the installed app.
