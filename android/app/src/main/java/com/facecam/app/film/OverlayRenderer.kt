package com.facecam.app.film

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min

/**
 * Draws the physical "frame" of the camera - the white instant border, the
 * rounded 35mm surround, the square medium-format mask, etc. - directly onto the
 * bitmap with Canvas. No texture assets are required.
 */
object OverlayRenderer {

    /** Draw a frame of the given [style] around [src], returning a new bitmap. */
    fun drawFrame(src: Bitmap, style: String): Bitmap {
        return when (style.lowercase()) {
            "instant" -> instantFrame(src)
            "square" -> squareFrame(src)
            "toy" -> toyFrame(src)
            "rounded" -> roundedFrame(src)
            "medium" -> mediumFrame(src)
            "cinema" -> cinemaFrame(src)
            else -> classic35mm(src)
        }
    }

    private fun instantFrame(src: Bitmap): Bitmap {
        // Wide white border, thicker at the bottom (Polaroid style).
        val side = (min(src.width, src.height) * 0.08f).toInt().coerceAtLeast(12)
        val bottom = (side * 2.4f).toInt()
        val outW = src.width + side * 2
        val outH = src.height + side + bottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#F5F2EA"))
        canvas.drawBitmap(src, side.toFloat(), side.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun classic35mm(src: Bitmap): Bitmap {
        val pad = (min(src.width, src.height) * 0.025f).toInt().coerceAtLeast(4)
        val outW = src.width + pad * 2
        val outH = src.height + pad * 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun roundedFrame(src: Bitmap): Bitmap {
        val pad = (min(src.width, src.height) * 0.03f).toInt().coerceAtLeast(6)
        val outW = src.width + pad * 2
        val outH = src.height + pad * 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#111111"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val r = pad * 1.5f
        val rect = RectF(pad.toFloat(), pad.toFloat(), (pad + src.width).toFloat(), (pad + src.height).toFloat())
        val path = android.graphics.Path()
        path.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), paint)
        canvas.restore()
        src.recycle()
        return out
    }

    private fun squareFrame(src: Bitmap): Bitmap {
        val size = min(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#0A0A0A"))
        val pad = (size * 0.02f).toInt()
        val left = (size - (size - pad * 2)) / 2
        val dst = Rect(left, pad, left + (size - pad * 2), size - pad)
        val srcRect = Rect(0, 0, src.width, src.height)
        canvas.drawBitmap(src, srcRect, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun toyFrame(src: Bitmap): Bitmap {
        val pad = (min(src.width, src.height) * 0.05f).toInt().coerceAtLeast(10)
        val outW = src.width + pad * 2
        val outH = src.height + pad * 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#1B1B1B"))
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun mediumFrame(src: Bitmap): Bitmap {
        val pad = (min(src.width, src.height) * 0.035f).toInt().coerceAtLeast(6)
        val outW = src.width + pad * 2
        val outH = src.height + pad * 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#FAFAF5"))
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private fun cinemaFrame(src: Bitmap): Bitmap {
        val bar = (src.height * 0.06f).toInt().coerceAtLeast(8)
        val outH = src.height + bar * 2
        val out = Bitmap.createBitmap(src.width, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(src, 0f, bar.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }
}
