package com.facecam.app.film

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.random.Random

/**
 * Burns an amber "date stamp" into the lower-right corner of a photo, mimicking
 * the quartz-date backs of old film cameras. Drawn with Canvas.drawText.
 */
object DateStampRenderer {

    /**
     * Draw [text] (e.g. "2026 10 01") onto a copy of [src]. The stamp is placed
     * just inside the frame with a small random jitter so it looks analogue.
     */
    fun draw(src: Bitmap, text: String, rnd: Random = Random.Default): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val base = minOf(out.width, out.height)
        val textSize = (base * 0.045f).coerceAtLeast(18f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = textSize
        paint.color = Color.parseColor("#FFB347") // classic amber
        paint.alpha = 235

        // Soft glow behind the digits for the classic light-bleed look.
        val glow = Paint(paint)
        glow.color = Color.parseColor("#FFD27F")
        glow.alpha = 90
        glow.maskFilter = android.graphics.BlurMaskFilter(textSize * 0.35f, android.graphics.BlurMaskFilter.Blur.NORMAL)

        val margin = (base * 0.06f)
        val jitterX = (rnd.nextFloat() - 0.5f) * (base * 0.01f)
        val x = out.width - margin - paint.measureText(text) + jitterX
        val y = out.height - margin

        canvas.drawText(text, x, y, glow)
        canvas.drawText(text, x, y, paint)

        return out
    }

    /** Format a timestamp as the classic dot/space separated date stamp. */
    fun formatDate(year: Int, month: Int, day: Int): String {
        val mm = month.toString().padStart(2, '0')
        val dd = day.toString().padStart(2, '0')
        return "$year $mm $dd"
    }
}
