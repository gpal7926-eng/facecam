package com.facecam.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * User preferences persisted in SharedPreferences. Everything stays on-device.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var dateStamp: Boolean
        get() = prefs.getBoolean(KEY_DATE_STAMP, true)
        set(value) = prefs.edit { putBoolean(KEY_DATE_STAMP, value) }

    var border: Boolean
        get() = prefs.getBoolean(KEY_BORDER, true)
        set(value) = prefs.edit { putBoolean(KEY_BORDER, value) }

    /** Append the "FaceCam" branding band under every processed photo. */
    var branding: Boolean
        get() = prefs.getBoolean(KEY_BRANDING, true)
        set(value) = prefs.edit { putBoolean(KEY_BRANDING, value) }

    var shutterSound: Boolean
        get() = prefs.getBoolean(KEY_SHUTTER_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SHUTTER_SOUND, value) }

    var defaultCameraId: String?
        get() = prefs.getString(KEY_DEFAULT_CAMERA, null)
        set(value) = prefs.edit { putString(KEY_DEFAULT_CAMERA, value) }

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    var saveCount: Int
        get() = prefs.getInt(KEY_SAVE_COUNT, 0)
        set(value) = prefs.edit { putInt(KEY_SAVE_COUNT, value) }

    companion object {
        private const val PREFS = "facecam_settings"
        private const val KEY_DATE_STAMP = "date_stamp"
        private const val KEY_BORDER = "border"
        private const val KEY_BRANDING = "branding"
        private const val KEY_SHUTTER_SOUND = "shutter_sound"
        private const val KEY_DEFAULT_CAMERA = "default_camera"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_SAVE_COUNT = "save_count"
    }
}
