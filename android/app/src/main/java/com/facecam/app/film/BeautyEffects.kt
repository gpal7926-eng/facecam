package com.facecam.app.film

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The Beauty camera pipeline: an iPhone-like, clean, well-exposed enhance.
 *
 * Unlike [AnalogEffects] this deliberately adds NO film character - no grain, no
 * light leaks, no vignette, no frame, no date stamp and no branding band. The
 * result is meant to look like a modern phone photo, not a vintage one.
 *
 * Pipeline order (all on-device, android.graphics only - no network, no native
 * code):
 *
 *   colour matrix (the preset's base grade)
 *     -> subtle warm-neutral white balance
 *     -> exposure lift
 *     -> gentle S-curve contrast + natural saturation
 *     -> highlight rolloff (soft shoulder so highlights never clip harshly)
 *     -> edge-aware skin smoothing
 *     -> soft highlight glow (bloom)
 *     -> unsharp-mask sharpening
 *
 * The skin smoothing is the interesting stage: a heavily blurred copy of the
 * image is blended back per pixel, weighted by a local high-frequency detail
 * map, so flat areas (skin) are smoothed while edges and fine texture are left
 * sharp.
 */
object BeautyEffects {

    /** Options controlling the pipeline for a single shot. */
    data class Options(
        val preset: FilmPreset,
        val beauty: BeautyParams = preset.beauty ?: BeautyParams(),
        /**
         * Optional feathered alpha mask (same size as the source, or scaled
         * internally) from [com.facecam.app.ml.FaceMaskProvider]. Where the mask
         * is opaque the skin smoothing runs at full strength; outside it a
         * reduced amount is used. When null the pipeline falls back to its
         * original global behaviour.
         */
        val faceMask: Bitmap? = null,
        val seed: Long = System.nanoTime()
    )

    /** Run the complete pipeline and return a NEW bitmap. [source] is untouched. */
    fun develop(source: Bitmap, options: Options): Bitmap {
        val b = options.beauty
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true)

        bmp = applyColorMatrix(bmp, options.preset)
        bmp = applyWhiteBalance(bmp, b.warmth)
        bmp = applyExposure(bmp, b.exposure)
        bmp = applyToneCurve(bmp, b.contrast, b.saturation)
        bmp = applyHighlightRolloff(bmp)
        bmp = applySkinSmoothing(bmp, b.smooth, options.faceMask)
        bmp = applyGlow(bmp, b.glow)
        bmp = applySharpen(bmp, b.sharpen)

        return bmp
    }

    // ------------------------------------------------------------------
    // Stage 0: base colour grade (the preset's colour matrix)
    // ------------------------------------------------------------------
    private fun applyColorMatrix(src: Bitmap, preset: FilmPreset): Bitmap {
        val m = FloatArray(20)
        System.arraycopy(preset.matrix, 0, m, 0, minOf(20, preset.matrix.size))
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(m))
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }

    // ------------------------------------------------------------------
    // Stage 1: subtle warm-neutral white balance
    // ------------------------------------------------------------------
    private fun applyWhiteBalance(src: Bitmap, warmth: Float): Bitmap {
        val w = warmth.coerceIn(0f, 1f)
        if (w <= 0.001f) return src
        val rGain = 1f + w * 0.10f
        val gGain = 1f + w * 0.015f
        val bGain = 1f - w * 0.10f
        val px = pixelsOf(src)
        for (i in px.indices) {
            val c = px[i]
            val r = clamp((comp(c, 16) * rGain).toInt())
            val g = clamp((comp(c, 8) * gGain).toInt())
            val b = clamp((comp(c, 0) * bGain).toInt())
            px[i] = Color.argb(comp(c, 24), r, g, b)
        }
        return bitmapOf(px, src.width, src.height, src)
    }

    // ------------------------------------------------------------------
    // Stage 2: exposure lift
    // ------------------------------------------------------------------
    private fun applyExposure(src: Bitmap, exposure: Float): Bitmap {
        if (abs(exposure) <= 0.001f) return src
        val gain = 2.0.pow(exposure.toDouble()).toFloat()
        val px = pixelsOf(src)
        for (i in px.indices) {
            val c = px[i]
            val r = clamp((comp(c, 16) * gain).toInt())
            val g = clamp((comp(c, 8) * gain).toInt())
            val b = clamp((comp(c, 0) * gain).toInt())
            px[i] = Color.argb(comp(c, 24), r, g, b)
        }
        return bitmapOf(px, src.width, src.height, src)
    }

    // ------------------------------------------------------------------
    // Stage 3: gentle S-curve contrast + natural saturation
    // ------------------------------------------------------------------
    private fun applyToneCurve(src: Bitmap, contrast: Float, saturation: Float): Bitmap {
        val c = contrast.coerceIn(0f, 1f)
        val s = saturation.coerceIn(-1f, 1f)

        // Gentle S-curve: blend the identity with a smoothstep.
        val lut = IntArray(256) { i ->
            val x = i / 255f
            val smooth = x * x * (3f - 2f * x)          // smoothstep S
            val y = (x + c * (smooth - x)).coerceIn(0f, 1f)
            (y * 255f + 0.5f).toInt().coerceIn(0, 255)
        }

        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f

        val px = pixelsOf(src)
        for (i in px.indices) {
            val col = px[i]
            var r = lut[comp(col, 16)]
            var g = lut[comp(col, 8)]
            var b = lut[comp(col, 0)]
            if (s != 0f) {
                val luma = lr * r + lg * g + lb * b
                r = clamp((luma + (r - luma) * (1f + s)).toInt())
                g = clamp((luma + (g - luma) * (1f + s)).toInt())
                b = clamp((luma + (b - luma) * (1f + s)).toInt())
            }
            px[i] = Color.argb(comp(col, 24), r, g, b)
        }
        return bitmapOf(px, src.width, src.height, src)
    }

    // ------------------------------------------------------------------
    // Stage 4: highlight rolloff (soft shoulder)
    // ------------------------------------------------------------------
    private fun applyHighlightRolloff(src: Bitmap): Bitmap {
        val knee = 0.72f
        val strength = 1.6f
        val lut = IntArray(256) { i ->
            val x = i / 255f
            val y = if (x <= knee) {
                x
            } else {
                val over = x - knee
                knee + over / (1f + over * strength)
            }
            (y.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        val px = pixelsOf(src)
        for (i in px.indices) {
            val c = px[i]
            val r = lut[comp(c, 16)]
            val g = lut[comp(c, 8)]
            val b = lut[comp(c, 0)]
            px[i] = Color.argb(comp(c, 24), r, g, b)
        }
        return bitmapOf(px, src.width, src.height, src)
    }

    // ------------------------------------------------------------------
    // Stage 5: EDGE-AWARE skin smoothing
    // ------------------------------------------------------------------
    /**
     * Blur a copy of the image, then blend it back per pixel weighted by the
     * local high-frequency detail. Flat regions (skin) get the blurred value,
     * while edges (where detail is high) keep the original sharp pixel.
     *
     * When [mask] is supplied (a feathered face mask) the smoothing runs at full
     * strength inside the detected face and a reduced amount elsewhere, so the
     * strongest effect lands on skin while eyes, brows and lips stay sharp (the
     * edge term above already protects them). With no mask the original global
     * behaviour is kept.
     */
    private fun applySkinSmoothing(src: Bitmap, smooth: Float, mask: Bitmap?): Bitmap {
        val strength = smooth.coerceIn(0f, 1f)
        val w = src.width
        val h = src.height
        if (strength <= 0.001f || w < 3 || h < 3) return src

        val base = pixelsOf(src)

        // A heavily blurred copy for the "smooth" target.
        val bigBlur = boxBlurScaled(src, divisor = 5)
        val blurPx = pixelsOf(bigBlur)

        // A small blur used to measure local high-frequency detail.
        val detailPx = blur3x3(base, w, h)

        // Optional face mask, sampled as an alpha weight per pixel.
        val maskPx: IntArray? = mask?.let {
            val scaled = if (it.width == w && it.height == h) it
            else Bitmap.createScaledBitmap(it, w, h, true)
            val px = pixelsOf(scaled)
            if (scaled !== it) scaled.recycle()
            px
        }

        val edgeThreshold = 16f
        val out = IntArray(w * h)
        for (i in out.indices) {
            val c = base[i]
            val srcLuma = luma(comp(c, 16), comp(c, 8), comp(c, 0))
            val blurLuma = luma(comp(detailPx[i], 16), comp(detailPx[i], 8), comp(detailPx[i], 0))
            val detail = abs(srcLuma - blurLuma).toFloat()

            // weight = strength on flat areas, 0 on strong edges.
            val edge = (detail / edgeThreshold).coerceIn(0f, 1f)
            var weight = strength * (1f - edge)
            if (maskPx != null) {
                val face = (comp(maskPx[i], 24) / 255f).coerceIn(0f, 1f)
                weight *= (OUTSIDE_FACE_WEIGHT + (1f - OUTSIDE_FACE_WEIGHT) * face)
            }

            val br = comp(blurPx[i], 16)
            val bg = comp(blurPx[i], 8)
            val bb = comp(blurPx[i], 0)
            val r = clamp((comp(c, 16) + (br - comp(c, 16)) * weight).toInt())
            val g = clamp((comp(c, 8) + (bg - comp(c, 8)) * weight).toInt())
            val b = clamp((comp(c, 0) + (bb - comp(c, 0)) * weight).toInt())
            out[i] = Color.argb(comp(c, 24), r, g, b)
        }

        bigBlur.recycle()
        return bitmapOf(out, w, h, src)
    }

    // ------------------------------------------------------------------
    // Stage 6: soft highlight glow (bloom)
    // ------------------------------------------------------------------
    private fun applyGlow(src: Bitmap, glow: Float): Bitmap {
        val strength = glow.coerceIn(0f, 1f)
        val w = src.width
        val h = src.height
        if (strength <= 0.01f || w < 4 || h < 4) return src

        val px = pixelsOf(src)
        val bright = IntArray(w * h)
        val threshold = 168f
        for (i in px.indices) {
            val c = px[i]
            val l = luma(comp(c, 16), comp(c, 8), comp(c, 0))
            if (l > threshold) {
                val excess = ((l - threshold) / (255f - threshold)).coerceIn(0f, 1f)
                val v = (excess * 255f).toInt()
                bright[i] = Color.argb(255, v, v, v)
            } else {
                bright[i] = Color.TRANSPARENT
            }
        }

        val brightBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        brightBmp.setPixels(bright, 0, w, 0, 0, w, h)

        val small = Bitmap.createScaledBitmap(brightBmp, max(1, w / 8), max(1, h / 8), true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        brightBmp.recycle()

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        paint.alpha = (strength * 110f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(blurred, 0f, 0f, paint)
        paint.xfermode = null
        blurred.recycle()
        src.recycle()
        return out
    }

    // ------------------------------------------------------------------
    // Stage 7: unsharp-mask sharpening
    // ------------------------------------------------------------------
    private fun applySharpen(src: Bitmap, sharpen: Float): Bitmap {
        val amount = sharpen.coerceIn(0f, 1f) * 1.6f
        val w = src.width
        val h = src.height
        if (amount <= 0.001f || w < 3 || h < 3) return src

        val px = pixelsOf(src)
        val blurred = blur3x3(px, w, h)
        val out = IntArray(w * h)
        for (i in out.indices) {
            val c = px[i]
            val r = clamp((comp(c, 16) + (comp(c, 16) - comp(blurred[i], 16)) * amount).toInt())
            val g = clamp((comp(c, 8) + (comp(c, 8) - comp(blurred[i], 8)) * amount).toInt())
            val b = clamp((comp(c, 0) + (comp(c, 0) - comp(blurred[i], 0)) * amount).toInt())
            out[i] = Color.argb(comp(c, 24), r, g, b)
        }
        return bitmapOf(out, w, h, src)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private fun pixelsOf(src: Bitmap): IntArray {
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        return px
    }

    private fun bitmapOf(px: IntArray, w: Int, h: Int, recycle: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        recycle?.recycle()
        return out
    }

    /** Cheap large-radius blur: downscale then upscale. */
    private fun boxBlurScaled(src: Bitmap, divisor: Int): Bitmap {
        val w = src.width
        val h = src.height
        val small = Bitmap.createScaledBitmap(src, max(1, w / divisor), max(1, h / divisor), true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        return blurred
    }

    /** 3x3 box blur over an ARGB pixel array (used for detail + unsharp mask). */
    private fun blur3x3(px: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    out[i] = px[i]
                    continue
                }
                var r = 0
                var g = 0
                var b = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val c = px[(y + dy) * w + (x + dx)]
                        r += comp(c, 16)
                        g += comp(c, 8)
                        b += comp(c, 0)
                    }
                }
                out[i] = Color.argb(comp(px[i], 24), r / 9, g / 9, b / 9)
            }
        }
        return out
    }

    private fun luma(r: Int, g: Int, b: Int): Int =
        (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

    private fun comp(color: Int, shift: Int): Int = (color shr shift) and 0xFF

    private fun clamp(v: Int): Int = min(255, max(0, v))

    companion object {
        /** Smoothing strength kept OUTSIDE the detected face (relative to full). */
        private const val OUTSIDE_FACE_WEIGHT = 0.35f
    }
}
