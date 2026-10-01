package com.facecam.app.camera.pro

import android.graphics.Bitmap
import android.graphics.Color

/**
 * False-colour exposure mode.
 *
 * Maps scene luminance onto a colour ramp so exposure is read at a glance (the
 * same idea as the false-colour monitoring on Blackmagic / broadcast cameras):
 *
 *   deep blue  -> crushed blacks
 *   blue/cyan  -> shadows
 *   green      -> middle grey (correct exposure)
 *   yellow     -> bright
 *   orange     -> near clipping
 *   red        -> blown highlights
 */
object FalseColorRenderer {

    /** Ramp control points: [luma 0..1, ARGB colour]. */
    private val STOPS = arrayOf(
        0.00f to Color.parseColor("#2020A0"),
        0.10f to Color.parseColor("#2060FF"),
        0.22f to Color.parseColor("#20C0E0"),
        0.40f to Color.parseColor("#20C040"),
        0.60f to Color.parseColor("#E0E020"),
        0.80f to Color.parseColor("#FF8000"),
        1.00f to Color.parseColor("#FF2020")
    )

    /** Map a normalised luminance (0..1) to a false-colour ARGB value. */
    fun ramp(luma: Float): Int {
        val l = luma.coerceIn(0f, 1f)
        for (i in 0 until STOPS.size - 1) {
            val (a, ca) = STOPS[i]
            val (b, cb) = STOPS[i + 1]
            if (l in a..b) {
                val t = if (b - a <= 0f) 0f else (l - a) / (b - a)
                return lerpColor(ca, cb, t)
            }
        }
        return STOPS.last().second
    }

    /** Apply the false-colour ramp to every pixel of [src]. */
    fun apply(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (w == 0 || h == 0) return out

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val l = HistogramAnalyzer.luma((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
            result[i] = ramp(l / 255f)
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt()
        return Color.rgb(r, g, b)
    }
}
