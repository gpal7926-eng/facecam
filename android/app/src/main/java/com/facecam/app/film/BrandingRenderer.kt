package com.facecam.app.film

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Appends the "FaceCam" branding band BELOW a processed photo.
 *
 * The band never covers the photograph: the output canvas simply grows taller by
 * roughly 7-8% of the photo height and the extra strip is painted with the
 * camera's frame colour (cream / off-white by default). A hairline divider runs
 * along the top edge of the band, the wordmark "FaceCam" sits bold and
 * letter-spaced on the left, and the camera's tag (e.g. "35mm Classic") is
 * right-aligned in a smaller grey face.
 *
 * Implemented with android.graphics only - no network, no native code.
 */
object BrandingRenderer {

    /** Fraction of the photo height used for the band (7.5%). */
    private const val BAND_FRACTION = 0.075f

    /** Default band colour: FaceCam cream. */
    const val DEFAULT_BAND = "#F5F2EA"

    /** The wordmark burned into every band. */
    const val WORDMARK = "FaceCam"

    /**
     * Map a preset frame style to the band colour. Light frame styles keep the
     * warm cream / off-white used across the app; everything else falls back to
     * the same cream so the dark lettering stays legible.
     */
    fun bandColorForFrame(frameStyle: String?): Int = when (frameStyle?.lowercase()) {
        "instant" -> Color.parseColor("#F5F2EA")
        "medium" -> Color.parseColor("#FAFAF5")
        "square" -> Color.parseColor("#F3EFE6")
        else -> Color.parseColor(DEFAULT_BAND)
    }

    /**
     * Draw the branding band under [src] and return a NEW (taller) bitmap.
     * [src] is recycled, matching the other pipeline stages.
     *
     * @param tag the camera's tag / name shown on the right (e.g. "35mm Classic").
     * @param bandColor the fill colour of the band; defaults to FaceCam cream.
     */
    fun draw(
        src: Bitmap,
        tag: String?,
        bandColor: Int = Color.parseColor(DEFAULT_BAND)
    ): Bitmap {
        val w = src.width
        val h = src.height
        val bandH = max(28, (h * BAND_FRACTION).roundToInt())
        val outH = h + bandH

        val out = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1) The untouched photograph at the top.
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        // 2) The band itself, below the photo.
        val bandTop = h.toFloat()
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bandPaint.color = bandColor
        canvas.drawRect(0f, bandTop, w.toFloat(), outH.toFloat(), bandPaint)

        // 3) Hairline divider along the top edge of the band.
        val divider = Paint(Paint.ANTI_ALIAS_FLAG)
        divider.color = Color.argb(46, 0, 0, 0)
        divider.strokeWidth = max(1f, bandH * 0.018f)
        canvas.drawLine(0f, bandTop + divider.strokeWidth / 2f, w.toFloat(), bandTop + divider.strokeWidth / 2f, divider)

        val margin = max(12f, w * 0.035f)
        val centreY = bandTop + bandH / 2f

        // 4) Wordmark - bold, letter-spaced, left aligned.
        val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        wordPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        wordPaint.textSize = bandH * 0.40f
        wordPaint.letterSpacing = 0.14f
        wordPaint.color = Color.parseColor("#1B1B1B")
        val wordFm = wordPaint.fontMetrics
        val wordBaseline = centreY - (wordFm.ascent + wordFm.descent) / 2f
        canvas.drawText(WORDMARK, margin, wordBaseline, wordPaint)

        // 5) Camera tag - smaller, grey, right aligned.
        if (!tag.isNullOrBlank()) {
            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            tagPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            tagPaint.textSize = bandH * 0.28f
            tagPaint.letterSpacing = 0.05f
            tagPaint.color = Color.parseColor("#8A8578")
            val tagFm = tagPaint.fontMetrics
            val tagBaseline = centreY - (tagFm.ascent + tagFm.descent) / 2f
            val tagWidth = tagPaint.measureText(tag)
            canvas.drawText(tag, w - margin - tagWidth, tagBaseline, tagPaint)
        }

        src.recycle()
        return out
    }
}
