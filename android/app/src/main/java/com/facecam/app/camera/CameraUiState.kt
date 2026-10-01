package com.facecam.app.camera

/** Which physical camera is active. */
enum class LensFacing { BACK, FRONT }

/** Flash mode exposed in the viewfinder. */
enum class FlashMode { OFF, ON, AUTO }

/** Self-timer options, in seconds (0 = immediate). */
enum class SelfTimer(val seconds: Int) {
    OFF(0),
    THREE(3),
    FIVE(5),
    TEN(10)
}

/**
 * Immutable snapshot of the viewfinder controls. Held by the ViewModel and read
 * by [com.facecam.app.ui.screens.ViewfinderScreen].
 */
data class CameraUiState(
    val lensFacing: LensFacing = LensFacing.BACK,
    val flash: FlashMode = FlashMode.OFF,
    val selfTimer: SelfTimer = SelfTimer.OFF,
    val isCapturing: Boolean = false,
    val isDeveloping: Boolean = false,
    val developProgress: Float = 0f,
    val cameraReady: Boolean = false,
    val error: String? = null
)
