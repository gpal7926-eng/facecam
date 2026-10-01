package com.facecam.app.film

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads the 13 camera presets from `assets/cameras/*.json`.
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

    fun get(id: String): FilmPreset? = presets[id]

    /** First free camera, used as the default selection. */
    fun defaultCamera(): FilmPreset =
        all().firstOrNull { it.free } ?: all().firstOrNull() ?: fallback().first()

    private fun parse(text: String): FilmPreset {
        val o = JSONObject(text)
        val arr: JSONArray = o.getJSONArray("colorMatrix")
        val matrix = FloatArray(20) { i -> arr.getDouble(i).toFloat() }
        return FilmPreset(
            id = o.getString("id"),
            name = o.getString("name"),
            description = o.optString("description", ""),
            tag = if (o.isNull("tag")) null else o.optString("tag", null),
            matrix = matrix,
            grain = o.optDouble("grain", 0.3).toFloat(),
            leak = o.optDouble("leak", 0.1).toFloat(),
            vignette = o.optDouble("vignette", 0.3).toFloat(),
            frame = o.optString("frame", "35mm"),
            dateStamp = o.optBoolean("dateStamp", true),
            free = o.optBoolean("free", false),
            instant = o.optBoolean("instant", false),
            overlay = if (o.isNull("overlay")) null else o.optString("overlay", null)
        )
    }

    private fun fallback(): List<FilmPreset> = listOf(
        FilmPreset(
            id = "nomo_135_b",
            name = "135 B",
            description = "Classic 35mm black-and-white film.",
            matrix = ColorMatrixFactory.identity(),
            grain = 0.35f,
            leak = 0.10f,
            vignette = 0.30f,
            frame = "35mm",
            dateStamp = true,
            free = true,
            instant = false
        )
    )

    companion object {
        private const val TAG = "FilmRepository"
    }
}
