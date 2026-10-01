package com.facecam.app.camera.pro

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Focus peaking: highlights the high-contrast edges of a frame so the operator
 * can see exactly where the lens is in focus.
 *
 * A cheap Sobel operator is run over the luminance channel; pixels whose edge
 * magnitude exceeds [threshold] are painted with [PEAK_COLOR] and the rest are
 * left transparent, so the result can be composited straight over the preview.
 */
object FocusPeaking {

    /** Peaking colour - the familiar bright green used by cinema cameras. */
    val PEAK_COLOR: Int = Color.parseColor("#39FF6A")

    /**
     * Produce a transparent overlay of highlighted edges for [src].
     *
     * @param threshold 0..1 sensitivity (higher = fewer edges shown).
     */
    fun render(src: Bitmap, threshold: Float = 0.35f): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (w < 3 || h < 3) return out

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Luminance plane.
        val luma = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            luma[i] = HistogramAnalyzer.luma((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }

        val limit = (threshold.coerceIn(0f, 1f) * 255f)
        val result = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val tl = luma[i - w - 1]
                val t = luma[i - w]
                val tr = luma[i - w + 1]
                val l = luma[i - 1]
                val r = luma[i + 1]
                val bl = luma[i + w - 1]
                val b = luma[i + w]
                val br = luma[i + w + 1]

                val gx = (tr + 2 * r + br) - (tl + 2 * l + bl)
                val gy = (bl + 2 * b + br) - (tl + 2 * t + tr)
                val mag = Math.hypot(gx.toDouble(), gy.toDouble()).toFloat()

                result[i] = if (mag >= limit) PEAK_COLOR else Color.TRANSPARENT
            }
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }
}
