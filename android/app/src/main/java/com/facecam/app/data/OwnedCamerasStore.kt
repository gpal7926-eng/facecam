package com.facecam.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks which cameras the user has bought. Stored as a set of camera ids in
 * SharedPreferences. Entitlements are re-validated against Google Play Billing
 * on "Restore purchases".
 */
class OwnedCamerasStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ownedIds(): Set<String> = prefs.getStringSet(KEY_OWNED, emptySet()) ?: emptySet()

    fun isOwned(cameraId: String): Boolean = ownedIds().contains(cameraId)

    fun add(cameraId: String) {
        val current = ownedIds().toMutableSet()
        current.add(cameraId)
        prefs.edit { putStringSet(KEY_OWNED, current) }
    }

    fun addAll(ids: Collection<String>) {
        val current = ownedIds().toMutableSet()
        current.addAll(ids)
        prefs.edit { putStringSet(KEY_OWNED, current) }
    }

    fun clear() = prefs.edit { remove(KEY_OWNED) }

    companion object {
        private const val PREFS = "facecam_owned"
        private const val KEY_OWNED = "owned_camera_ids"
    }
}
