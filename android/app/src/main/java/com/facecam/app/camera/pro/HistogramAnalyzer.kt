package com.facecam.app.camera.pro

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Small, allocation-light luminance histogram used by the PRO HUD.
 *
 * Frames are read from the preview [androidx.camera.core.ImageAnalysis] stream
 * (via [ProFrameProcessor]) and reduced to a 64-bin luma histogram. The HUD
 * draws it top-right, Blackmagic-style.
 */
object HistogramAnalyzer {

    /** Number of bins in the histogram. */
    const val BINS = 64

    /** Rec.709 luma for an ARGB colour. */
    fun luma(r: Int, g: Int, b: Int): Int =
        (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)

    /**
     * Build a luminance histogram from an ARGB [Bitmap], down-sampled to keep it
     * cheap on every frame.
     */
    fun fromBitmap(bitmap: Bitmap, bins: Int = BINS): IntArray {
        val hist = IntArray(bins)
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return hist
        val stepX = (w / 160).coerceAtLeast(1)
        val stepY = (h / 160).coerceAtLeast(1)
        val scale = bins / 256f
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val l = luma((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
                val bin = (l * scale).toInt().coerceIn(0, bins - 1)
                hist[bin]++
                x += stepX
            }
            y += stepY
        }
        return hist
    }

    /** Normalise a histogram so the tallest bin becomes 1.0 (for drawing). */
    fun normalized(hist: IntArray): FloatArray {
        val max = hist.maxOrNull() ?: 0
        if (max <= 0) return FloatArray(hist.size)
        return FloatArray(hist.size) { hist[it].toFloat() / max.toFloat() }
    }

    /** Fraction of sampled pixels at or above [threshold] (blown highlights). */
    fun blownFraction(hist: IntArray, thresholdBin: Int = BINS - 2): Float {
        val total = hist.sum()
        if (total <= 0) return 0f
        var blown = 0
        for (i in thresholdBin.coerceIn(0, BINS - 1) until BINS) blown += hist[i]
        return blown.toFloat() / total.toFloat()
    }
}
