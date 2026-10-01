package com.facecam.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdMob integration. All ad unit ids below are PLACEHOLDERS - replace them with
 * real ids before publishing. When the user unlocks PRO, ads are disabled
 * entirely and no ad requests are made.
 *
 * Placement policy:
 *  - Banner: Gallery screen + result preview.
 *  - Interstitial: capped, shown after every 3rd save only.
 *  - Viewfinder: always ad-free.
 */
object AdManager {

    // ---- Placeholder ad unit ids (replace before publishing) -------------
    const val BANNER_UNIT_ID = "ca-app-pub-0000000000000000/0000000000"
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-0000000000000000/1111111111"

    private const val SAVE_INTERVAL = 3

    @Volatile
    private var initialised = false

    @Volatile
    private var adsAllowed = true

    private var interstitial: InterstitialAd? = null

    fun init(context: Context, adsAllowed: Boolean) {
        this.adsAllowed = adsAllowed
        if (!adsAllowed || initialised) return
        try {
            MobileAds.initialize(context) {
                initialised = true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds init failed", t)
        }
    }

    fun setAdsAllowed(allowed: Boolean) {
        adsAllowed = allowed
        if (!allowed) interstitial = null
    }

    /** Load an interstitial ahead of time. Safe to call repeatedly. */
    fun preloadInterstitial(context: Context) {
        if (!adsAllowed) return
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed: ${error.message}")
                    interstitial = null
                }
            }
        )
    }

    /**
     * Show an interstitial only if this is the 3rd, 6th, 9th ... save. Returns
     * true if an ad was shown.
     */
    fun showInterstitialIfDue(activity: Activity, totalSaves: Int): Boolean {
        if (!adsAllowed) return false
        if (totalSaves <= 0 || totalSaves % SAVE_INTERVAL != 0) return false
        val ad = interstitial ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
            }
        }
        ad.show(activity)
        return true
    }

    private const val TAG = "AdManager"
}
