package com.facecam.app.ml

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * On-device face detection (ML Kit, bundled model - no network, no download).
 *
 * Builds a feathered alpha mask over the detected face region so
 * [com.facecam.app.film.BeautyEffects] can apply its strongest skin smoothing
 * inside the face while leaving eyes, brows and lips sharp (those areas are
 * carved back out of the mask). It also exposes the union face bounds for a
 * "centre on face" framing guide in the viewfinder.
 *
 * If no face is found the caller simply gets null and the Beauty pipeline falls
 * back to its original global behaviour.
 */
object FaceMaskProvider {

    /** A feathered face mask plus the union bounds of the detected faces. */
    data class FaceMask(
        val mask: Bitmap,
        val bounds: RectF,
        val faceCount: Int
    )

    /** Create a detector. Reuse one per analyzer; close it when done. */
    fun newDetector(): FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.12f)
            .build()
    )

    /**
     * Detect faces in [bitmap] and build a feathered alpha mask over them.
     * Returns null when nothing is found (or detection fails).
     */
    suspend fun detect(bitmap: Bitmap, detector: FaceDetector = newDetector()): FaceMask? {
        val faces = process(detector, InputImage.fromBitmap(bitmap, 0)) ?: return null
        if (faces.isEmpty()) return null
        val bounds = unionBounds(faces) ?: return null
        val mask = buildMask(bitmap.width, bitmap.height, faces)
        return FaceMask(mask, bounds, faces.size)
    }

    /** Just the union face bounds (for the framing guide), or null. */
    suspend fun detectBounds(bitmap: Bitmap, detector: FaceDetector = newDetector()): RectF? {
        val faces = process(detector, InputImage.fromBitmap(bitmap, 0)) ?: return null
        return unionBounds(faces)
    }

    /**
     * Grab a representative frame from [video] and build a face mask from it.
     * Used to steer the Beauty skin smoothing over a recorded clip.
     */
    suspend fun detectFromVideo(video: java.io.File, atMs: Long = -1L): FaceMask? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(video.absolutePath)
        val frame = if (atMs >= 0L) {
            retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } else {
            retriever.getFrameAtTime()
        }
        retriever.release()
        if (frame == null) {
            null
        } else {
            val mask = detect(frame)
            frame.recycle()
            mask
        }
    } catch (t: Throwable) {
        Log.w(TAG, "detectFromVideo failed", t)
        null
    }

    /** Union of the detected faces' bounding boxes, or null when empty. */
    fun unionBounds(faces: List<Face>): RectF? {
        if (faces.isEmpty()) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (face in faces) {
            val b = face.boundingBox
            left = min(left, b.left.toFloat())
            top = min(top, b.top.toFloat())
            right = max(right, b.right.toFloat())
            bottom = max(bottom, b.bottom.toFloat())
        }
        return RectF(left, top, right, bottom)
    }

    private suspend fun process(detector: FaceDetector, image: InputImage): List<Face>? =
        suspendCancellableCoroutine<List<Face>?> { cont ->
            detector.process(image)
                .addOnSuccessListener { faces -> if (cont.isActive) cont.resume(faces) }
                .addOnFailureListener { t ->
                    Log.w(TAG, "Face detection failed", t)
                    if (cont.isActive) cont.resume(null)
                }
        }

    /**
     * Paint a soft white oval over each face onto a transparent mask, then carve
     * out the eye and mouth regions (PorterDuff.CLEAR) so those details are not
     * over-smoothed.
     */
    private fun buildMask(w: Int, h: Int, faces: List<Face>): Bitmap {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)

        val feather = max(10f, w * 0.035f)
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
        }
        val carvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            maskFilter = BlurMaskFilter(feather * 0.5f, BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        for (face in faces) {
            val b = face.boundingBox
            val fw = b.width().toFloat()
            val fh = b.height().toFloat()
            val oval = RectF(b)
            // Grow slightly so the whole face (chin / jaw) is covered.
            oval.inset(-fw * 0.06f, -fh * 0.10f)
            oval.top -= fh * 0.06f
            canvas.drawOval(oval, facePaint)

            // Carve out eyes and mouth so they stay sharp.
            val eyeR = fw * 0.13f
            val mouthR = fw * 0.17f
            face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let {
                canvas.drawCircle(it.x, it.y, eyeR, carvePaint)
            }
            face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let {
                canvas.drawCircle(it.x, it.y, eyeR, carvePaint)
            }
            val mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
                ?: face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
            mouth?.let { canvas.drawCircle(it.x, it.y, mouthR, carvePaint) }
        }
        return mask
    }

    private const val TAG = "FaceMaskProvider"
}
