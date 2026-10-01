package com.facecam.app.camera

import com.facecam.app.video.VideoSpeed

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

/** Photo vs Video mode in the viewfinder. */
enum class CaptureMode { PHOTO, VIDEO }

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
    val error: String? = null,

    // ---- Video mode ----
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val videoSpeed: VideoSpeed = VideoSpeed.X1,
    /** True while a recorded clip is being post-processed. */
    val processingVideo: Boolean = false,
    val videoProgress: Float = 0f,
    /** Highest target fps the device advertises (for the slow-motion hint). */
    val maxFps: Int = 30,

    // ---- Subtitles ----
    val typedCaption: String = "",
    val liveCaptions: Boolean = false,
    val liveCaptionText: String = "",

    // ---- On-device ML framing guide ----
    val faceGuide: Boolean = false
) {
    /** True when the device can capture at 60 fps or more. */
    val highFpsAvailable: Boolean get() = maxFps >= 60
}
