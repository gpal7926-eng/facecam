package com.facecam.app.camera.pro

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which live overlay the PRO preview should draw. */
enum class OverlayMode { NONE, PEAKING, ZEBRA, FALSE_COLOR }

/**
 * [ImageAnalysis.Analyzer] that powers the PRO preview overlays.
 *
 * Each preview frame is reduced to a small grayscale bitmap (from the Y plane of
 * the YUV_420_888 stream). That bitmap feeds the luminance histogram and, when an
 * overlay mode is active, the focus-peaking / zebra / false-colour renderers. The
 * resulting overlay is exposed as a [Bitmap] the HUD draws on top of the preview.
 *
 * This is entirely on-device; no frame ever leaves the phone.
 */
class ProFrameProcessor : ImageAnalysis.Analyzer {

    private val _histogram = MutableStateFlow(IntArray(HistogramAnalyzer.BINS))
    val histogram: StateFlow<IntArray> = _histogram.asStateFlow()

    private val _overlay = MutableStateFlow<Bitmap?>(null)
    val overlay: StateFlow<Bitmap?> = _overlay.asStateFlow()

    /** Whether overlays are produced at all (PRO on and an overlay selected). */
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var mode: OverlayMode = OverlayMode.NONE

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val w = image.width
            val h = image.height
            if (w <= 0 || h <= 0) return

            // Down-sample for speed: overlays and histograms don't need full res.
            val step = 4
            val ow = (w / step).coerceAtLeast(1)
            val oh = (h / step).coerceAtLeast(1)
            val pixels = IntArray(ow * oh)
            for (yy in 0 until oh) {
                val sy = yy * step
                for (xx in 0 until ow) {
                    val sx = xx * step
                    val idx = sy * rowStride + sx * pixelStride
                    val y = if (idx in 0 until buffer.limit()) buffer.get(idx).toInt() and 0xFF else 0
                    pixels[yy * ow + xx] = Color.rgb(y, y, y)
                }
            }
            val lumaBmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            lumaBmp.setPixels(pixels, 0, ow, 0, 0, ow, oh)

            _histogram.value = HistogramAnalyzer.fromBitmap(lumaBmp)

            if (enabled && mode != OverlayMode.NONE) {
                val overlay = when (mode) {
                    OverlayMode.PEAKING -> FocusPeaking.render(lumaBmp)
                    OverlayMode.ZEBRA -> ZebraOverlay.render(lumaBmp)
                    OverlayMode.FALSE_COLOR -> FalseColorRenderer.apply(lumaBmp)
                    OverlayMode.NONE -> null
                }
                val previous = _overlay.value
                _overlay.value = overlay
                if (previous != null && previous !== overlay && !previous.isRecycled) {
                    previous.recycle()
                }
            } else if (_overlay.value != null) {
                _overlay.value = null
            }

            lumaBmp.recycle()
        } catch (t: Throwable) {
            // Never let an analysis failure crash the preview.
        } finally {
            image.close()
        }
    }

    /** Release the current overlay bitmap. */
    fun clear() {
        _overlay.value?.let { if (!it.isRecycled) it.recycle() }
        _overlay.value = null
    }
}
