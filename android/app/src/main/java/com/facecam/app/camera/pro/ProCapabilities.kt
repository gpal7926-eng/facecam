package com.facecam.app.camera.pro

/**
 * What the current device/camera actually supports for manual control.
 *
 * Populated by querying [android.hardware.camera2.CameraCharacteristics] through
 * [androidx.camera.camera2.interop.Camera2CameraInfo]. Any control whose
 * [supports...] flag is false is greyed out in the PRO HUD and left to the
 * camera's automatic behaviour.
 */
data class ProCapabilities(
    val supportsManualIso: Boolean = false,
    val supportsManualShutter: Boolean = false,
    val supportsManualWhiteBalance: Boolean = false,
    val supportsManualFocus: Boolean = false,

    val isoMin: Int = 100,
    val isoMax: Int = 3200,

    val exposureMinNanos: Long = 1_000_000L,
    val exposureMaxNanos: Long = 100_000_000L,

    /** 0 means the lens cannot focus manually (fixed focus). */
    val minFocusDistanceDiopters: Float = 0f,
    val maxFocusDistanceDiopters: Float = 10f,

    /** Coarse hardware level name, shown in the readout strip. */
    val hardwareLevel: String = "LIMITED"
) {
    companion object {
        /** Everything disabled - the safe default before a camera is attached. */
        val UNSUPPORTED = ProCapabilities()
    }
}
