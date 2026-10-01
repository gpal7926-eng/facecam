package com.facecam.app

import android.app.Application
import com.facecam.app.ads.AdManager
import com.facecam.app.data.OwnedCamerasStore
import com.facecam.app.data.ProStore
import com.facecam.app.data.SettingsStore
import com.facecam.app.film.FilmRepository

/**
 * Application entry point. Holds the few process-wide singletons FaceCam needs.
 *
 * FaceCam is 100% offline: no network calls are made by app logic. The only
 * outbound traffic comes from the AdMob SDK, which is fully optional and can be
 * disabled by the user by unlocking PRO.
 */
class FaceCamApp : Application() {

    /** Loaded film presets and repositories, lazily initialised. */
    lateinit var filmRepository: FilmRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var ownedCamerasStore: OwnedCamerasStore
        private set
    lateinit var proStore: ProStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        filmRepository = FilmRepository(this)
        filmRepository.load()

        settingsStore = SettingsStore(this)
        ownedCamerasStore = OwnedCamerasStore(this)
        proStore = ProStore(this)

        // Initialise the ads SDK only if the user has not unlocked PRO.
        AdManager.init(this, adsAllowed = !proStore.isPro)
    }

    companion object {
        lateinit var instance: FaceCamApp
            private set
    }
}
