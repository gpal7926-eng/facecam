package com.facecam.app.ml

import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A throttled [ImageAnalysis.Analyzer] that runs on-device face detection over
 * the live preview and publishes the union face bounds (normalised 0..1) for the
 * viewfinder's "centre on face" framing guide.
 *
 * Throttled to a couple of frames a second so the preview stays smooth. The
 * model is bundled with ML Kit - no network.
 */
class FaceGuideAnalyzer : ImageAnalysis.Analyzer {

    private val detector: FaceDetector = FaceMaskProvider.newDetector()

    private val _bounds = MutableStateFlow<RectF?>(null)

    /** Union face bounds in 0..1 preview space, or null when no face is seen. */
    val bounds: StateFlow<RectF?> = _bounds.asStateFlow()

    private var lastRunMs = 0L

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRunMs < INTERVAL_MS) {
            image.close()
            return
        }
        lastRunMs = now

        val media = image.image
        if (media == null) {
            image.close()
            return
        }

        val rotation = image.imageInfo.rotationDegrees
        val rotatedWidth = if (rotation == 90 || rotation == 270) image.height else image.width
        val rotatedHeight = if (rotation == 90 || rotation == 270) image.width else image.height

        val input = InputImage.fromMediaImage(media, rotation)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val union = FaceMaskProvider.unionBounds(faces)
                _bounds.value = union?.let {
                    RectF(
                        (it.left / rotatedWidth).coerceIn(0f, 1f),
                        (it.top / rotatedHeight).coerceIn(0f, 1f),
                        (it.right / rotatedWidth).coerceIn(0f, 1f),
                        (it.bottom / rotatedHeight).coerceIn(0f, 1f)
                    )
                }
            }
            .addOnFailureListener { _bounds.value = null }
            .addOnCompleteListener { image.close() }
    }

    /** Clear the current hint. */
    fun reset() {
        _bounds.value = null
    }

    /** Release the detector. */
    fun close() {
        runCatching { detector.close() }
    }

    companion object {
        private const val INTERVAL_MS = 500L
    }
}
