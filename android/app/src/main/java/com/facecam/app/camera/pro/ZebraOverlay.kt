package com.facecam.app.camera.pro

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Zebra stripes over blown highlights.
 *
 * Any pixel whose luminance is at or above [threshold] (default ~95% of full
 * scale) is replaced by a diagonal black/white stripe pattern, exactly like the
 * zebras on a broadcast/cinema camera. The rest of the overlay is transparent.
 */
object ZebraOverlay {

    private const val STRIPE_PERIOD = 8

    /**
     * Render a transparent zebra overlay for [src].
     *
     * @param threshold 0..1 luma above which stripes are drawn (default 0.94).
     */
    fun render(src: Bitmap, threshold: Float = 0.94f): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (w == 0 || h == 0) return out

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val cut = (threshold.coerceIn(0f, 1f) * 255f)
        val result = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = pixels[i]
                val l = HistogramAnalyzer.luma((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
                result[i] = if (l >= cut) {
                    val on = ((x + y) / (STRIPE_PERIOD / 2)) % 2 == 0
                    if (on) Color.argb(210, 255, 255, 255) else Color.argb(210, 0, 0, 0)
                } else {
                    Color.TRANSPARENT
                }
            }
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }
}
