package com.facecam.app.film

import android.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Helpers for building and combining [ColorMatrix] instances.
 *
 * The 20-float matrices in the preset JSONs are already in ColorMatrix order, so
 * [fromPreset] simply wraps them. The extra builders are used for the live
 * viewfinder tint and for the randomised per-shot jitter.
 */
object ColorMatrixFactory {

    /** Neutral identity matrix. */
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    /** Wrap a preset's 20-float matrix, padding/truncating to exactly 20. */
    fun fromPreset(preset: FilmPreset): ColorMatrix {
        val m = FloatArray(20)
        System.arraycopy(preset.matrix, 0, m, 0, minOf(20, preset.matrix.size))
        return ColorMatrix(m)
    }

    /**
     * Build a simple saturation + warmth matrix used by the lightweight live
     * preview tint (cheap enough to run every frame).
     */
    fun previewTint(saturation: Float, warmth: Float, brightness: Float = 0f): ColorMatrix {
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f
        val s = saturation.coerceIn(0f, 3f)
        val sr = (1 - s) * lr
        val sg = (1 - s) * lg
        val sb = (1 - s) * lb
        val cm = ColorMatrix(
            floatArrayOf(
                sr + s, sg, sb, 0f, warmth * 16f,
                sr, sg + s, sb, 0f, brightness,
                sr, sg, sb + s, 0f, -warmth * 14f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return cm
    }

    /** Slight per-shot hue/brightness jitter so no two frames look identical. */
    fun jitter(base: ColorMatrix, hueDeg: Float, brightness: Float): ColorMatrix {
        val out = ColorMatrix(base)
        val rad = Math.toRadians(hueDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // Approximate hue rotation on the R/B axis (cheap, visually fine).
        val hue = ColorMatrix(
            floatArrayOf(
                c, 0f, s, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                -s, 0f, c, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        out.postConcat(hue)
        val bright = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        out.postConcat(bright)
        return out
    }

    /** Multiply two 20-float matrices (a then b), returning a new array. */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[row * 5 + k] * b[k * 5 + col]
                }
                result[row * 5 + col] = sum
            }
            // Constant (translation) column.
            var sum = a[row * 5 + 4]
            for (k in 0 until 4) {
                sum += a[row * 5 + k] * b[k * 5 + 4]
            }
            result[row * 5 + 4] = sum
        }
        return result
    }
}
