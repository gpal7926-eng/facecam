package com.facecam.app.film

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads the camera presets from `assets/cameras/*.json` - 20 vintage film
 * cameras (including the 1950s-1990s decade looks and the B&W stocks) plus 9
 * Beauty cameras.
 *
 * Parsing is defensive: a malformed preset is skipped rather than crashing the
 * app, and a built-in fallback list is used if the assets folder is missing.
 */
class FilmRepository(private val context: Context) {

    private val presets = linkedMapOf<String, FilmPreset>()

    /** Camera ids in a stable display order. */
    val order: List<String>
        get() = presets.keys.toList()

    fun load() {
        presets.clear()
        val assets = try {
            context.assets.list("cameras")?.toList().orEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not list camera assets", t)
            emptyList()
        }

        for (fileName in assets.sorted()) {
            if (!fileName.endsWith(".json")) continue
            val preset = try {
                val text = context.assets.open("cameras/$fileName")
                    .bufferedReader().use { it.readText() }
                parse(text)
            } catch (t: Throwable) {
                Log.w(TAG, "Skipping preset $fileName", t)
                null
            }
            if (preset != null) presets[preset.id] = preset
        }

        if (presets.isEmpty()) {
            Log.w(TAG, "No presets loaded; using built-in fallback")
            for (p in fallback()) presets[p.id] = p
        }
    }

    fun all(): List<FilmPreset> = order.mapNotNull { presets[it] }

    /** Only the vintage film cameras, in display order. */
    fun vintage(): List<FilmPreset> = all().filter { it.isVintage }

    /** Only the Beauty cameras, in display order. */
    fun beauty(): List<FilmPreset> = all().filter { it.isBeauty }

    /** Cameras of a given group ("vintage" or "beauty"). */
    fun ofGroup(group: String): List<FilmPreset> = all().filter { it.group == group }

    fun get(id: String): FilmPreset? = presets[id]

    /** First camera, used as the default selection. */
    fun defaultCamera(): FilmPreset =
        all().firstOrNull() ?: fallback().first()

    private fun parse(text: String): FilmPreset {
        val o = JSONObject(text)
        val arr: JSONArray = o.getJSONArray("colorMatrix")
        val matrix = FloatArray(20) { i -> arr.getDouble(i).toFloat() }
        return FilmPreset(
            id = o.getString("id"),
            name = o.getString("name"),
            description = o.optString("description", ""),
            tag = if (o.isNull("tag")) null else o.optString("tag", null),
            group = o.optString("group", CameraGroup.VINTAGE),
            matrix = matrix,
            grain = o.optDouble("grain", 0.3).toFloat(),
            leak = o.optDouble("leak", 0.1).toFloat(),
            vignette = o.optDouble("vignette", 0.3).toFloat(),
            frame = o.optString("frame", "35mm"),
            dateStamp = o.optBoolean("dateStamp", true),
            instant = o.optBoolean("instant", false),
            beauty = parseBeauty(o.optJSONObject("beauty")),
            overlay = if (o.isNull("overlay")) null else o.optString("overlay", null)
        )
    }

    private fun parseBeauty(o: JSONObject?): BeautyParams? {
        if (o == null) return null
        return BeautyParams(
            exposure = o.optDouble("exposure", 0.08).toFloat(),
            contrast = o.optDouble("contrast", 0.18).toFloat(),
            saturation = o.optDouble("saturation", 0.06).toFloat(),
            warmth = o.optDouble("warmth", 0.04).toFloat(),
            smooth = o.optDouble("smooth", 0.35).toFloat(),
            sharpen = o.optDouble("sharpen", 0.30).toFloat(),
            glow = o.optDouble("glow", 0.10).toFloat()
        )
    }

    private fun fallback(): List<FilmPreset> = listOf(
        FilmPreset(
            id = "nomo_135_b",
            name = "135 B",
            description = "Classic 35mm black-and-white film.",
            group = CameraGroup.VINTAGE,
            matrix = ColorMatrixFactory.identity(),
            grain = 0.35f,
            leak = 0.10f,
            vignette = 0.30f,
            frame = "35mm",
            dateStamp = true,
            instant = false
        ),
        FilmPreset(
            id = "beauty_natural",
            name = "NATURAL",
            description = "Clean, true-to-life skin tones with a soft natural glow.",
            group = CameraGroup.BEAUTY,
            matrix = ColorMatrixFactory.identity(),
            grain = 0f,
            leak = 0f,
            vignette = 0f,
            frame = "none",
            dateStamp = false,
            instant = false,
            beauty = BeautyParams()
        )
    )

    companion object {
        private const val TAG = "FilmRepository"
    }
}
