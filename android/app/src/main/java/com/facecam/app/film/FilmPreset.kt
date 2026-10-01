package com.facecam.app.film

/**
 * The extra tuning a Beauty camera applies on top of its colour matrix.
 *
 * All values are small, phone-photo-friendly amounts - this is a clean,
 * well-exposed "iPhone-like" look, not a vintage one. See [BeautyEffects] for
 * the pipeline that consumes these.
 *
 *  - [exposure]   stops of exposure lift (e.g. 0.08 = +0.08 EV).
 *  - [contrast]   0..1 strength of the gentle S-curve.
 *  - [saturation] 0..1 boost of colour, relative to natural.
 *  - [warmth]     0..1 subtle warm-neutral white balance shift.
 *  - [smooth]     0..1 edge-aware skin-smoothing strength.
 *  - [sharpen]    0..1 unsharp-mask sharpening strength.
 *  - [glow]       0..1 strength of the soft highlight bloom.
 */
data class BeautyParams(
    val exposure: Float = 0.08f,
    val contrast: Float = 0.18f,
    val saturation: Float = 0.06f,
    val warmth: Float = 0.04f,
    val smooth: Float = 0.35f,
    val sharpen: Float = 0.30f,
    val glow: Float = 0.10f
)

/** The three camera families shown in the picker. */
object CameraGroup {
    const val VINTAGE = "vintage"
    const val BW = "bw"
    const val BEAUTY = "beauty"

    /** Display order of the families in the picker tabs and the browser. */
    val ordered: List<String> = listOf(VINTAGE, BW, BEAUTY)

    /** Human-readable family label for tabs and section headers. */
    fun label(group: String): String = when (group) {
        VINTAGE -> "Vintage"
        BW -> "B&W"
        BEAUTY -> "Beauty"
        else -> "Vintage"
    }
}

/**
 * A single "camera" simulation.
 *
 * The [matrix] is a 4x5 colour matrix in the same row-major order Android's
 * [android.graphics.ColorMatrix] expects (20 floats):
 *
 *     [ r0 r1 r2 r3 r4
 *       g0 g1 g2 g3 g4
 *       b0 b1 b2 b3 b4
 *       a0 a1 a2 a3 a4 ]
 *
 * A camera belongs to one of three families, given by [group]:
 *
 *  - [CameraGroup.VINTAGE] runs the procedural analog pipeline in
 *    [AnalogEffects] (grain, light leaks, vignette, dust, frame, date stamp and
 *    the FaceCam branding band).
 *  - [CameraGroup.BW] is the black-and-white family. It runs the very same
 *    analog pipeline as the vintage cameras (grain, vignette, frame, date
 *    stamp, branding band), but its colour matrix genuinely desaturates the
 *    image to monochrome.
 *  - [CameraGroup.BEAUTY] runs the clean [BeautyEffects] pipeline instead - no
 *    grain, leaks, vignette, frame, date stamp or branding, just an
 *    iPhone-like enhance. Beauty cameras carry a [beauty] parameter block.
 *
 * Everything is applied on-device with android.graphics only - no network, no
 * native libs. All cameras are free.
 */
data class FilmPreset(
    val id: String,
    val name: String,
    val description: String,
    /** Short tag burned into the FaceCam branding band, e.g. "35mm Classic". */
    val tag: String? = null,
    /** "vintage", "bw" or "beauty" - which family this camera belongs to. */
    val group: String = CameraGroup.VINTAGE,
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
    /** Instant cameras show the "developing" wait animation. */
    val instant: Boolean,
    /** Beauty tuning; non-null only for beauty cameras. */
    val beauty: BeautyParams? = null,
    /** Optional texture overlay asset file name, or null for procedural only. */
    val overlay: String? = null
) {
    /** Tag used in the branding band; falls back to the display name. */
    fun brandingTag(): String = tag?.takeIf { it.isNotBlank() } ?: name

    /** True when this camera belongs to the Beauty family. */
    val isBeauty: Boolean get() = group == CameraGroup.BEAUTY

    /** True when this camera belongs to the Vintage film family. */
    val isVintage: Boolean get() = group == CameraGroup.VINTAGE

    /** True when this camera belongs to the black-and-white family. */
    val isBw: Boolean get() = group == CameraGroup.BW

    /**
     * True when this camera runs the analog (film) pipeline - i.e. the Vintage
     * and B&W families. Beauty cameras are the only ones that do not.
     */
    val isAnalog: Boolean get() = !isBeauty

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilmPreset) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
