package com.facecam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin CameraX wrapper used by the viewfinder. Handles binding the preview and
 * capturing a still to an in-memory [Bitmap] for the film pipeline.
 *
 * CameraX gives us the raw still; the vintage look is applied afterwards by
 * [com.facecam.app.film.AnalogEffects].
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraInfo: CameraInfo? = null
    private var lensFacing: LensFacing = LensFacing.BACK

    /** The bound [Camera], or null before [bind] completes. Used by PRO mode. */
    fun currentCamera(): Camera? = camera

    /** The bound [CameraInfo], or null before [bind] completes. Used by PRO mode. */
    fun currentCameraInfo(): CameraInfo? = cameraInfo

    /**
     * Attach (or clear) a frame analyzer on the ImageAnalysis use case. PRO mode
     * uses this for the histogram and the peaking / zebra / false-colour
     * overlays. Passing null installs a no-op analyzer so simple mode stays
     * completely idle.
     */
    fun setFrameAnalyzer(analyzer: ImageAnalysis.Analyzer?) {
        val analysis = imageAnalysis ?: return
        val effective = analyzer ?: ImageAnalysis.Analyzer { it.close() }
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context), effective)
    }

    /** Bind the preview to the given [previewView] and prepare the capture use case. */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: LensFacing,
        onReady: (Boolean) -> Unit
    ) {
        this.lensFacing = lensFacing
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy -> proxy.close() } }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(
                        if (lensFacing == LensFacing.FRONT) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    )
                    .build()

                provider.unbindAll()
                val bound = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                camera = bound
                cameraInfo = bound.cameraInfo
                onReady(true)
            } catch (t: Throwable) {
                Log.e(TAG, "Camera bind failed", t)
                onReady(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setFlash(mode: FlashMode) {
        val m = when (mode) {
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = m
    }

    /** Capture a still and return it as an upright [Bitmap]. */
    suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resumeWithException(IllegalStateException("Camera not ready"))
            return@suspendCancellableCoroutine
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bmp = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        val rotated = rotate(bmp, rotation)
                        cont.resume(rotated)
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer: ByteBuffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Could not decode capture")
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix()
        m.postRotate(degrees.toFloat())
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        camera = null
        cameraInfo = null
    }

    /** Persist a bitmap to a temp file (used when a FileProvider Uri is needed). */
    fun writeTemp(file: File, bitmap: Bitmap): Boolean = try {
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        true
    } catch (t: Throwable) {
        Log.e(TAG, "writeTemp failed", t)
        false
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
