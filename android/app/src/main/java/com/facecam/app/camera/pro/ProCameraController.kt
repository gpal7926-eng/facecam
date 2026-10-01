package com.facecam.app.camera.pro

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import kotlin.math.ln
import kotlin.math.pow

/**
 * Applies REAL manual camera controls through the Camera2 interop layer
 * (Camera2Interop / Camera2CameraControl). Everything is driven by the camera2
 * [CaptureRequest] keys the task asks for:
 *
 *  - ISO          -> [CaptureRequest.SENSOR_SENSITIVITY]
 *  - shutter      -> [CaptureRequest.SENSOR_EXPOSURE_TIME] (shown as shutter angle)
 *  - white balance-> [CaptureRequest.COLOR_CORRECTION_GAINS] with AWB off
 *  - focus        -> [CaptureRequest.LENS_FOCUS_DISTANCE]
 *
 * Capabilities are read from [CameraCharacteristics] so the UI can grey out any
 * control the device does not expose. All of this is strictly local; there is no
 * network access anywhere in this class.
 */
class ProCameraController {

    private var camera: Camera? = null
    private var cameraInfo: CameraInfo? = null

    /** Latest queried capabilities; [ProCapabilities.UNSUPPORTED] until attached. */
    var capabilities: ProCapabilities = ProCapabilities.UNSUPPORTED
        private set

    /** Called whenever the device is bound, so the UI can refresh its sliders. */
    fun attach(camera: Camera, cameraInfo: CameraInfo) {
        this.camera = camera
        this.cameraInfo = cameraInfo
        capabilities = queryCapabilities(cameraInfo)
    }

    fun detach() {
        camera = null
        cameraInfo = null
        capabilities = ProCapabilities.UNSUPPORTED
    }

    // ------------------------------------------------------------------
    // Capability discovery
    // ------------------------------------------------------------------
    private fun queryCapabilities(info: CameraInfo): ProCapabilities {
        return try {
            val chars = Camera2CameraInfo.from(info)

            val isoRange: Range<Int>? =
                chars.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val expRange: Range<Long>? =
                chars.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minFocus: Float? =
                chars.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            val maxFocus: Float? =
                chars.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)
            val awbModes: IntArray? =
                chars.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            val hwLevel: Int? =
                chars.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

            val isoSupported = isoRange != null && isoRange.upper > isoRange.lower
            val expSupported = expRange != null && expRange.upper > expRange.lower
            val focusSupported = minFocus != null && minFocus > 0f
            val awbSupported = awbModes != null &&
                awbModes.contains(CaptureRequest.CONTROL_AWB_MODE_OFF)

            ProCapabilities(
                supportsManualIso = isoSupported,
                supportsManualShutter = expSupported,
                supportsManualWhiteBalance = awbSupported,
                supportsManualFocus = focusSupported,
                isoMin = isoRange?.lower ?: 100,
                isoMax = isoRange?.upper ?: 3200,
                exposureMinNanos = expRange?.lower ?: 1_000_000L,
                exposureMaxNanos = expRange?.upper ?: 100_000_000L,
                minFocusDistanceDiopters = minFocus ?: 0f,
                maxFocusDistanceDiopters = maxFocus?.takeIf { it > 0f } ?: 10f,
                hardwareLevel = hardwareLevelName(hwLevel)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Could not query camera characteristics", t)
            ProCapabilities.UNSUPPORTED
        }
    }

    private fun hardwareLevelName(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "LIMITED"
    }

    // ------------------------------------------------------------------
    // Applying manual controls
    // ------------------------------------------------------------------
    /** Push the current [state] to the camera. Safe to call on every slider tick. */
    fun applyState(state: ProState) {
        val cam = camera ?: return
        if (!state.enabled) {
            reset()
            return
        }

        val builder = CaptureRequestOptions.Builder()

        val wantIso = state.manualIso && capabilities.supportsManualIso
        val wantShutter = state.manualShutter && capabilities.supportsManualShutter
        if (wantIso || wantShutter) {
            // Turn AE off once, then set whichever manual values are enabled.
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
        }
        if (wantIso) {
            val iso = state.iso.coerceIn(capabilities.isoMin, capabilities.isoMax)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        }
        if (wantShutter) {
            val exposure = state.exposureNanos
                .coerceIn(capabilities.exposureMinNanos, capabilities.exposureMaxNanos)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
        }
        if (state.manualWhiteBalance && capabilities.supportsManualWhiteBalance) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_OFF
            )
            builder.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_GAINS,
                gainsFromKelvin(state.whiteBalanceK)
            )
        }
        if (state.manualFocus && capabilities.supportsManualFocus) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
            val diopters = state.focusDistance
                .coerceIn(0f, capabilities.minFocusDistanceDiopters)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
        }

        try {
            Camera2CameraControl.from(cam.cameraControl)
                .setCaptureRequestOptions(builder.build())
        } catch (t: Throwable) {
            Log.w(TAG, "Could not apply manual capture options", t)
        }
    }

    /** Restore fully automatic exposure / white balance / focus. */
    fun reset() {
        val cam = camera ?: return
        val builder = CaptureRequestOptions.Builder()
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        )
        if (capabilities.supportsManualFocus) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }
        try {
            Camera2CameraControl.from(cam.cameraControl)
                .setCaptureRequestOptions(builder.build())
        } catch (t: Throwable) {
            Log.w(TAG, "Could not reset capture options", t)
        }
    }

    companion object {
        private const val TAG = "ProCameraController"

        /**
         * Approximate R/B channel gains (green normalised to 1) for a colour
         * temperature in Kelvin. Based on the well-known Tanner Helland
         * black-body approximation; good enough for a manual WB slider.
         */
        fun gainsFromKelvin(kelvin: Int): RggbChannelVector {
            val t = (kelvin.coerceIn(1500, 15000) / 100.0).toFloat()
            val red: Float
            val green: Float
            val blue: Float

            if (t <= 66f) {
                red = 255f
                green = 99.4708025861f * ln(t.toDouble()).toFloat() - 161.1195681661f
            } else {
                red = 329.698727446f * (t - 60f).pow(-0.1332047592f)
                green = 288.1221695283f * (t - 60f).pow(-0.0755148492f)
            }
            blue = when {
                t >= 66f -> 255f
                t <= 19f -> 0f
                else -> 138.5177312231f * ln((t - 10f).toDouble()).toFloat() - 305.0447927307f
            }

            val r = (red / 255f).coerceIn(0.05f, 4f)
            val g = (green / 255f).coerceIn(0.05f, 4f)
            val b = (blue / 255f).coerceIn(0.05f, 4f)
            // Invert the illuminant to white-balance: normalise so green = 1.
            val gInv = 1f / g
            return RggbChannelVector(r * gInv, 1f, 1f, b * gInv)
        }
    }
}
