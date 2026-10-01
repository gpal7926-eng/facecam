package com.facecam.app.film

/**
 * A single film "camera" simulation.
 *
 * The [matrix] is a 4x5 colour matrix in the same row-major order Android's
 * [android.graphics.ColorMatrix] expects (20 floats):
 *
 *     [ r0 r1 r2 r3 r4
 *       g0 g1 g2 g3 g4
 *       b0 b1 b2 b3 b4
 *       a0 a1 a2 a3 a4 ]
 *
 * The remaining fields tune the procedural analog effects. Everything is applied
 * on-device with android.graphics only - no network, no native libs.
 */
data class FilmPreset(
    val id: String,
    val name: String,
    val description: String,
    /** Short tag burned into the FaceCam branding band, e.g. "35mm Classic". */
    val tag: String? = null,
    val matrix: FloatArray,
    /** Grain density, 0..1 (higher = more visible film grain). */
    val grain: Float,
    /** Light-leak strength, 0..1. */
    val leak: Float,
    /** Vignette strength, 0..1. */
    val vignette: Float,
    /** Frame style key, e.g. "35mm", "instant", "square", "toy". */
    val frame: String,
    /** Whether the date stamp is enabled by default for this camera. */
    val dateStamp: Boolean,
    /** Free cameras are available without purchase. */
    val free: Boolean,
    /** Instant cameras show the "developing" wait animation. */
    val instant: Boolean,
    /** Optional texture overlay asset file name, or null for procedural only. */
    val overlay: String? = null
) {
    /** Tag used in the branding band; falls back to the display name. */
    fun brandingTag(): String = tag?.takeIf { it.isNotBlank() } ?: name

    /** True when the camera must be bought (not free) and not covered by PRO. */
    fun isPaid(): Boolean = !free

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilmPreset) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
