package com.facecam.app.film

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * The full on-device film pipeline. Applied to a captured still with
 * android.graphics only (no RenderScript, no native code, no network).
 *
 * Pipeline order:
 *   colour curve (ColorMatrix)
 *     -> film tone curve (per-channel lift/gamma/gain LUT)
 *     -> halation / bloom (warm highlight bleed)
 *     -> sharpen / soften
 *     -> grain (luminance + chroma noise)
 *     -> vignette
 *     -> light leak
 *     -> dust / scratches
 *     -> frame (drawn by [OverlayRenderer])
 *     -> optional burned-in date stamp (drawn by [DateStampRenderer])
 *     -> FaceCam branding band (drawn by [BrandingRenderer])
 *
 * Every stage is randomised per shot so no two frames are identical.
 */
object AnalogEffects {

    /** Options controlling the pipeline for a single shot. */
    data class Options(
        val preset: FilmPreset,
        val border: Boolean = true,
        val dateStamp: Boolean = true,
        val branding: Boolean = true,
        val seed: Long = System.nanoTime()
    )

    /**
     * Run the complete pipeline and return a NEW bitmap. The input is never
     * modified. [applyFrame] and [dateStampText] are optional callbacks that let
     * the caller draw the frame and the date using the shared renderers.
     */
    fun develop(
        source: Bitmap,
        options: Options,
        applyFrame: (Bitmap) -> Bitmap = { it },
        dateStampText: String? = null
    ): Bitmap {
        val rnd = Random(options.seed)
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true)

        bmp = applyColorCurve(bmp, options.preset, rnd)
        bmp = applyToneCurve(bmp, rnd)
        bmp = applyHalation(bmp, rnd)
        bmp = if (options.preset.grain > 0.34f) soften(bmp, rnd) else sharpen(bmp, rnd)
        bmp = applyGrain(bmp, options.preset.grain, rnd)
        bmp = applyVignette(bmp, options.preset.vignette)
        bmp = applyLightLeak(bmp, options.preset.leak, rnd)
        bmp = applyDust(bmp, options.preset.grain, rnd)

        if (options.border) {
            bmp = applyFrame(bmp)
        }
        if (options.dateStamp && dateStampText != null) {
            bmp = DateStampRenderer.draw(bmp, dateStampText, rnd)
        }
        if (options.branding) {
            bmp = BrandingRenderer.draw(
                src = bmp,
                tag = options.preset.brandingTag(),
                bandColor = BrandingRenderer.bandColorForFrame(options.preset.frame)
            )
        }
        return bmp
    }

    /** Merge two shots using a blend mode (double-exposure mode). */
    fun doubleExpose(base: Bitmap, overlay: Bitmap, blend: PorterDuff.Mode): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val scaled = if (overlay.width != out.width || overlay.height != out.height) {
            Bitmap.createScaledBitmap(overlay, out.width, out.height, true)
        } else {
            overlay
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(blend)
        paint.alpha = 190
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        paint.xfermode = null
        if (scaled !== overlay) scaled.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 1: colour curve
    // ---------------------------------------------------------------------
    private fun applyColorCurve(src: Bitmap, preset: FilmPreset, rnd: Random): Bitmap {
        val hue = (rnd.nextFloat() - 0.5f) * 8f
        val bright = (rnd.nextFloat() - 0.5f) * 10f
        val base = ColorMatrixFactory.fromPreset(preset)
        val cm = ColorMatrixFactory.jitter(base, hue, bright)

        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 1b: film tone curve (per-channel lift / gamma / gain LUT)
    // ---------------------------------------------------------------------
    /**
     * Builds three 256-entry lookup tables (R, G, B) from a randomised
     * lift/gamma/gain model - the classic film printer controls - and applies
     * them per pixel. This replaces the flat, digital look of a pure colour
     * matrix with a proper film response curve.
     */
    private fun applyToneCurve(src: Bitmap, rnd: Random): Bitmap {
        val lift = FloatArray(3) { (rnd.nextFloat() - 0.5f) * 0.055f }
        val gamma = FloatArray(3) { 0.90f + rnd.nextFloat() * 0.22f }   // 0.90..1.12
        val gain = FloatArray(3) { 0.96f + rnd.nextFloat() * 0.09f }    // 0.96..1.05

        val lut = Array(3) { ch ->
            IntArray(256) { i ->
                val x = i / 255f
                // Film-style S response: gamma-shaped with a soft shoulder.
                val shaped = x.pow(1f / gamma[ch])
                val y = ((shaped + lift[ch]) * gain[ch]).coerceIn(0f, 1f)
                (y * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
        }

        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = lut[0][comp(c, 16)]
            val g = lut[1][comp(c, 8)]
            val b = lut[2][comp(c, 0)]
            pixels[i] = Color.argb(comp(c, 24), r, g, b)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 1c: halation / bloom around highlights
    // ---------------------------------------------------------------------
    /**
     * Extracts a bright pass, blurs it heavily and screen-blends it back with a
     * warm amber tint - the soft reddish glow film gives around specular
     * highlights (halation).
     */
    private fun applyHalation(src: Bitmap, rnd: Random): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 4 || h < 4) return src

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val bright = IntArray(w * h)
        val threshold = 188f
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = comp(c, 16)
            val g = comp(c, 8)
            val b = comp(c, 0)
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (lum > threshold) {
                // Keep only the excess above the threshold, scaled up.
                val excess = ((lum - threshold) / (255f - threshold)).coerceIn(0f, 1f)
                val s = (excess * 255f).toInt()
                bright[i] = Color.argb(255, s, s, s)
            } else {
                bright[i] = Color.argb(0, 0, 0, 0)
            }
        }
        val brightBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        brightBmp.setPixels(bright, 0, w, 0, 0, w, h)

        // Heavy blur via downscale / upscale (cheap, GPU-free).
        val sw = max(1, w / 10)
        val sh = max(1, h / 10)
        val small = Bitmap.createScaledBitmap(brightBmp, sw, sh, true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        brightBmp.recycle()

        // Warm tint: lift red, drop blue slightly.
        val warm = ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 12f,
                0f, 1.00f, 0f, 0f, 4f,
                0f, 0f, 0.82f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val halo = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val haloCanvas = Canvas(halo)
        val tintPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        tintPaint.colorFilter = ColorMatrixColorFilter(warm)
        haloCanvas.drawBitmap(blurred, 0f, 0f, tintPaint)
        blurred.recycle()

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val blend = Paint(Paint.FILTER_BITMAP_FLAG)
        blend.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        blend.alpha = (70 + rnd.nextInt(60)).coerceAtMost(150)
        canvas.drawBitmap(halo, 0f, 0f, blend)
        blend.xfermode = null
        halo.recycle()
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 2: sharpen / soften
    // ---------------------------------------------------------------------
    private fun sharpen(src: Bitmap, rnd: Random): Bitmap {
        // Light sharpen via a 3x3 convolution kernel.
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)
        val amount = 0.15f + rnd.nextFloat() * 0.15f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val c = pixels[i]
                val n = pixels[i - w]
                val s = pixels[i + w]
                val e = pixels[i + 1]
                val wst = pixels[i - 1]
                val rc = comp(c, 16)
                val gc = comp(c, 8)
                val bc = comp(c, 0)
                val rn = (comp(n, 16) + comp(s, 16) + comp(e, 16) + comp(wst, 16)) / 4
                val gn = (comp(n, 8) + comp(s, 8) + comp(e, 8) + comp(wst, 8)) / 4
                val bn = (comp(n, 0) + comp(s, 0) + comp(e, 0) + comp(wst, 0)) / 4
                val r = clamp(rc + (rc - rn) * amount)
                val g = clamp(gc + (gc - gn) * amount)
                val b = clamp(bc + (bc - bn) * amount)
                result[i] = Color.argb(comp(c, 24), r, g, b)
            }
        }
        // Copy the border rows/cols unchanged.
        for (x in 0 until w) {
            result[x] = pixels[x]
            result[(h - 1) * w + x] = pixels[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            result[y * w] = pixels[y * w]
            result[y * w + (w - 1)] = pixels[y * w + (w - 1)]
        }
        out.setPixels(result, 0, w, 0, 0, w, h)
        src.recycle()
        return out
    }

    private fun soften(src: Bitmap, rnd: Random): Bitmap {
        // Cheap box blur via downscale/upscale, plus a translucent white veil.
        val w = src.width
        val h = src.height
        val small = Bitmap.createScaledBitmap(src, max(1, w / 4), max(1, h / 4), true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
        val veil = Paint(Paint.ANTI_ALIAS_FLAG)
        veil.color = Color.WHITE
        veil.alpha = (18 + rnd.nextInt(20))
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 3: grain
    // ---------------------------------------------------------------------
    private fun applyGrain(src: Bitmap, grain: Float, rnd: Random): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val strength = (grain.coerceIn(0f, 1f) * 60f).toInt()
        // Chroma noise is independent per channel; luminance grain is shared.
        val chroma = (grain.coerceIn(0f, 1f) * 22f).toInt()
        for (i in pixels.indices) {
            val c = pixels[i]
            val luma = rnd.nextInt(-strength, strength + 1)
            val nr = luma + rnd.nextInt(-chroma, chroma + 1)
            val ng = luma + rnd.nextInt(-chroma, chroma + 1)
            val nb = luma + rnd.nextInt(-chroma, chroma + 1)
            val r = clamp(comp(c, 16) + nr)
            val g = clamp(comp(c, 8) + ng)
            val b = clamp(comp(c, 0) + nb)
            pixels[i] = Color.argb(comp(c, 24), r, g, b)
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 4: vignette
    // ---------------------------------------------------------------------
    private fun applyVignette(src: Bitmap, vignette: Float): Bitmap {
        if (vignette <= 0f) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = max(w, h) * 0.75f
        val alpha = (vignette.coerceIn(0f, 1f) * 235f).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 5: light leak
    // ---------------------------------------------------------------------
    private fun applyLightLeak(src: Bitmap, leak: Float, rnd: Random): Bitmap {
        if (leak <= 0.01f) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val alpha = (leak.coerceIn(0f, 1f) * 150f).toInt().coerceAtLeast(20)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val leakColors = intArrayOf(
            Color.argb(alpha, 255, 180, 90),
            Color.argb(alpha, 255, 90, 120),
            Color.argb(alpha, 255, 230, 150)
        )
        val color = leakColors[rnd.nextInt(leakColors.size)]
        when (rnd.nextInt(3)) {
            0 -> paint.shader = LinearGradient(
                0f, 0f, w * 0.6f, 0f,
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            1 -> paint.shader = LinearGradient(
                w, h, w * 0.4f, h * 0.4f,
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            else -> paint.shader = RadialGradient(
                w * 0.15f, h * 0.1f, w * 0.7f,
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.xfermode = null
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 6: dust / scratches
    // ---------------------------------------------------------------------
    private fun applyDust(src: Bitmap, density: Float, rnd: Random): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width
        val h = out.height

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val specks = (density.coerceIn(0f, 1f) * 90).toInt()
        for (i in 0 until specks) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = 0.5f + rnd.nextFloat() * 1.8f
            val dark = rnd.nextBoolean()
            paint.color = if (dark) {
                Color.argb(120 + rnd.nextInt(90), 0, 0, 0)
            } else {
                Color.argb(120 + rnd.nextInt(90), 255, 255, 255)
            }
            canvas.drawCircle(x, y, r, paint)
        }

        // A couple of hairline scratches.
        val scratches = 1 + rnd.nextInt(3)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        linePaint.color = Color.argb(60, 255, 255, 255)
        linePaint.strokeWidth = 0.8f + rnd.nextFloat()
        for (i in 0 until scratches) {
            val x = rnd.nextFloat() * w
            linePaint.color = Color.argb(40 + rnd.nextInt(50), 255, 255, 255)
            canvas.drawLine(x, 0f, x + (rnd.nextFloat() - 0.5f) * 20f, h.toFloat(), linePaint)
        }
        src.recycle()
        return out
    }

    // ---------------------------------------------------------------------
    // Stage 7: frame
    // ---------------------------------------------------------------------
    private fun applyFrame(src: Bitmap): Bitmap = OverlayRenderer.drawFrame(src, "35mm")

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    private fun comp(color: Int, shift: Int): Int = (color shr shift) and 0xFF

    private fun clamp(v: Int): Int = min(255, max(0, v))
}
