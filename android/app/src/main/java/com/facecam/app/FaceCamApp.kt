package com.facecam.app

import android.app.Application
import com.facecam.app.data.SettingsStore
import com.facecam.app.film.FilmRepository

/**
 * Application entry point. Holds the few process-wide singletons FaceCam needs.
 *
 * FaceCam is 100% offline and completely free: no network calls, no ads, no
 * billing and no purchase gating anywhere in the app.
 */
class FaceCamApp : Application() {

    /** Loaded camera presets and repositories, lazily initialised. */
    lateinit var filmRepository: FilmRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        filmRepository = FilmRepository(this)
        filmRepository.load()

        settingsStore = SettingsStore(this)
    }

    companion object {
        lateinit var instance: FaceCamApp
            private set
    }
}
