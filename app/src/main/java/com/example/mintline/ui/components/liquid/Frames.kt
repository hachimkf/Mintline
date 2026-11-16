/*
 * Adapted from Kyant0/AndroidLiquidGlass (catalog sample), Apache License 2.0.
 * https://github.com/Kyant0/AndroidLiquidGlass
 */
package com.example.mintline.ui.components.liquid

/** Suspends until the next frame (Android actual of the sample's expect fun). */
suspend fun awaitFrame() {
    kotlinx.coroutines.android.awaitFrame()
}
