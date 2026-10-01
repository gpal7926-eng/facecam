package com.facecam.app.camera.pro

import kotlin.math.roundToInt

/**
 * Immutable snapshot of the manual (Blackmagic-Camera-style) shooting HUD.
 *
 * Manual mode is strictly opt-in: when [enabled] is false the viewfinder behaves
 * exactly
 * as the simple mode always has. Every manual control carries its own boolean so
 * that an unsupported control (as reported by [ProCapabilities]) can simply be
 * left off.
 */
data class ProState(
    val enabled: Boolean = false,

    // Overlays
    val showGrid: Boolean = true,
    val showLevel: Boolean = true,
    val showHistogram: Boolean = true,
    val focusPeaking: Boolean = false,
    val zebra: Boolean = false,
    val falseColor: Boolean = false,

    // Manual controls (each opt-in)
    val manualIso: Boolean = false,
    val manualShutter: Boolean = false,
    val manualWhiteBalance: Boolean = false,
    val manualFocus: Boolean = false,

    // Manual values
    val iso: Int = 400,
    /** Exposure time in nanoseconds (default ~1/125 s). */
    val exposureNanos: Long = 8_000_000L,
    /** White balance in Kelvin. */
    val whiteBalanceK: Int = 5600,
    /** Manual focus distance in dioptres (0 = infinity). */
    val focusDistance: Float = 0f
) {
    /** Shutter angle in degrees for the given frame rate (default 24 fps). */
    fun shutterAngle(fps: Int = 24): Int =
        ((exposureNanos / 1_000_000_000.0) * fps * 360.0).roundToInt().coerceIn(1, 360)

    /** Human-readable exposure time, e.g. "1/125". */
    fun exposureLabel(): String {
        val seconds = exposureNanos / 1_000_000_000.0
        if (seconds <= 0.0) return "auto"
        val denom = (1.0 / seconds).roundToInt().coerceAtLeast(1)
        return "1/$denom"
    }

    /** Human-readable focus distance, e.g. "3.2 m" or "inf". */
    fun focusLabel(): String {
        if (focusDistance <= 0.0001f) return "inf"
        val metres = 1f / focusDistance
        return String.format("%.1f m", metres)
    }

    companion object {
        /** Frame rate assumed for shutter-angle maths. */
        const val DEFAULT_FPS = 24
    }
}
