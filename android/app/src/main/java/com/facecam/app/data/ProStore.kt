package com.facecam.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PRO membership state. A single non-consumable "pro" product unlocks:
 *  - all cameras,
 *  - importing photos from the gallery,
 *  - skipping the developing wait,
 *  - removing ads.
 */
class ProStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_PRO, false))
    val isProFlow: StateFlow<Boolean> = _isPro.asStateFlow()

    var isPro: Boolean
        get() = prefs.getBoolean(KEY_PRO, false)
        set(value) {
            prefs.edit { putBoolean(KEY_PRO, value) }
            _isPro.value = value
        }

    fun setProFromBilling(active: Boolean) {
        isPro = active
    }

    companion object {
        private const val PREFS = "facecam_pro"
        private const val KEY_PRO = "is_pro"
    }
}
