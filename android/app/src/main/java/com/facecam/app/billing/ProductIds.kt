package com.facecam.app.billing

/**
 * Google Play Billing product identifiers.
 *
 * IMPORTANT: these are PLACEHOLDERS. Replace them with the exact product ids you
 * create in the Play Console before publishing. The camera ids used here must
 * match the ids in `assets/cameras/*.json`.
 */
object ProductIds {

    /** One-time, non-consumable PRO unlock. */
    const val PRO = "facecam_pro_unlock"

    /** Base prefix for per-camera one-time products, e.g. "cam_roma". */
    const val CAMERA_PREFIX = "cam_"

    /** All in-app product ids (PRO + every paid camera). */
    val ALL: List<String> by lazy {
        listOf(PRO) + PAID_CAMERAS.map { CAMERA_PREFIX + it }
    }

    /** Paid (non-free) camera ids. Keep in sync with the preset JSONs. */
    val PAID_CAMERAS: List<String> = listOf(
        "nomo_135_b",
        "nomo_135_m",
        "nomo_135_p",
        "toy_f",
        "toy_k",
        "roma",
        "fr2",
        "film_2007",
        "eats",
        "ins_2",
        "swirly_2",
        "range_67",
        "wide_17"
    )

    /** Map a Play product id back to a camera id, or null if it is the PRO sku. */
    fun cameraIdFor(productId: String): String? =
        if (productId.startsWith(CAMERA_PREFIX)) productId.removePrefix(CAMERA_PREFIX) else null

    /** The Play product id for a given camera. */
    fun productFor(cameraId: String): String = CAMERA_PREFIX + cameraId
}
