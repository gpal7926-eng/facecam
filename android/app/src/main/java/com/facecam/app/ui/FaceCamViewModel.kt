package com.facecam.app.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facecam.app.FaceCamApp
import com.facecam.app.ads.AdManager
import com.facecam.app.billing.BillingManager
import com.facecam.app.billing.ProductIds
import com.facecam.app.camera.CameraUiState
import com.facecam.app.camera.FlashMode
import com.facecam.app.camera.LensFacing
import com.facecam.app.camera.SelfTimer
import com.facecam.app.camera.pro.ProState
import com.facecam.app.film.AnalogEffects
import com.facecam.app.film.DateStampRenderer
import com.facecam.app.film.DevelopingController
import com.facecam.app.film.FilmPreset
import com.facecam.app.film.FilmRepository
import com.facecam.app.gallery.GalleryPhoto
import com.facecam.app.gallery.GalleryRepository
import com.facecam.app.gallery.MediaStoreSaver
import com.facecam.app.gallery.ShareHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Single source of truth for FaceCam's UI state. Owns the selected camera, the
 * viewfinder controls, the in-app gallery and the purchase/entitlement state.
 */
class FaceCamViewModel(app: Application) : AndroidViewModel(app) {

    private val faceCamApp = app as FaceCamApp

    val filmRepository: FilmRepository = faceCamApp.filmRepository
    val settingsStore = faceCamApp.settingsStore
    val ownedStore = faceCamApp.ownedCamerasStore
    val proStore = faceCamApp.proStore
    val galleryRepository = GalleryRepository(app)
    val developingController = DevelopingController()

    private val billingManager = BillingManager(
        context = app,
        ownedStore = ownedStore,
        proStore = proStore,
        onStateChanged = { refreshEntitlements() }
    )

    private val _cameraState = MutableStateFlow(CameraUiState())
    val cameraState: StateFlow<CameraUiState> = _cameraState.asStateFlow()

    private val _selectedCamera = MutableStateFlow<FilmPreset?>(null)
    val selectedCamera: StateFlow<FilmPreset?> = _selectedCamera.asStateFlow()

    private val _gallery = MutableStateFlow<List<GalleryPhoto>>(emptyList())
    val gallery: StateFlow<List<GalleryPhoto>> = _gallery.asStateFlow()

    private val _lastDeveloped = MutableStateFlow<GalleryPhoto?>(null)
    val lastDeveloped: StateFlow<GalleryPhoto?> = _lastDeveloped.asStateFlow()

    private val _isPro = MutableStateFlow(proStore.isPro)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _ownedIds = MutableStateFlow(ownedStore.ownedIds())
    val ownedIds: StateFlow<Set<String>> = _ownedIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---- PRO camera mode (opt-in; simple mode is untouched) ----
    private val _proState = MutableStateFlow(ProState())
    val proState: StateFlow<ProState> = _proState.asStateFlow()

    /** Turn the Blackmagic-style PRO HUD on or off. */
    fun toggleProMode() {
        _proState.value = _proState.value.copy(enabled = !_proState.value.enabled)
    }

    /** Generic mutator used by every PRO control in the HUD. */
    fun updateProState(transform: (ProState) -> ProState) {
        _proState.value = transform(_proState.value)
    }

    /** For the double-exposure flow. */
    private var pendingDoubleExposureBase: Bitmap? = null

    init {
        _selectedCamera.value = settingsStore.defaultCameraId
            ?.let { filmRepository.get(it) }
            ?: filmRepository.defaultCamera()
        _cameraState.value = _cameraState.value.copy(
            flash = FlashMode.OFF,
            selfTimer = SelfTimer.OFF
        )
        billingManager.start()
        refreshGallery()
    }

    // ------------------------------------------------------------------
    // Entitlements
    // ------------------------------------------------------------------
    fun startBilling() = billingManager.start()

    fun stopBilling() = billingManager.end()

    private fun refreshEntitlements() {
        _isPro.value = proStore.isPro
        _ownedIds.value = ownedStore.ownedIds()
        AdManager.setAdsAllowed(!proStore.isPro)
    }

    fun isCameraUnlocked(preset: FilmPreset): Boolean =
        preset.free || proStore.isPro || ownedStore.isOwned(preset.id)

    fun priceFor(preset: FilmPreset): String? =
        billingManager.priceFor(ProductIds.productFor(preset.id))

    fun proPrice(): String? = billingManager.priceFor(ProductIds.PRO)

    fun buyCamera(activity: Activity, preset: FilmPreset) {
        billingManager.purchase(activity, ProductIds.productFor(preset.id))
    }

    fun buyPro(activity: Activity) {
        billingManager.purchase(activity, ProductIds.PRO)
    }

    fun restorePurchases() {
        billingManager.restorePurchases()
        _message.value = "Restoring purchases..."
    }

    // ------------------------------------------------------------------
    // Camera selection & controls
    // ------------------------------------------------------------------
    fun selectCamera(preset: FilmPreset) {
        _selectedCamera.value = preset
    }

    fun setDefaultCamera(preset: FilmPreset) {
        settingsStore.defaultCameraId = preset.id
        _message.value = "${preset.name} set as default"
    }

    fun switchLens() {
        val next = if (_cameraState.value.lensFacing == LensFacing.BACK) {
            LensFacing.FRONT
        } else {
            LensFacing.BACK
        }
        _cameraState.value = _cameraState.value.copy(lensFacing = next)
    }

    fun cycleFlash() {
        val next = when (_cameraState.value.flash) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        _cameraState.value = _cameraState.value.copy(flash = next)
    }

    fun cycleSelfTimer() {
        val order = listOf(SelfTimer.OFF, SelfTimer.THREE, SelfTimer.FIVE, SelfTimer.TEN)
        val idx = order.indexOf(_cameraState.value.selfTimer)
        val next = order[(idx + 1) % order.size]
        _cameraState.value = _cameraState.value.copy(selfTimer = next)
    }

    fun onCameraReady(ready: Boolean) {
        _cameraState.value = _cameraState.value.copy(cameraReady = ready)
    }

    // ------------------------------------------------------------------
    // Capture & develop
    // ------------------------------------------------------------------
    /**
     * Apply the full film pipeline to a captured still and persist it to the
     * in-app gallery. Instant cameras show the developing wait first.
     */
    fun developCapture(source: Bitmap, cameraName: String) {
        val preset = _selectedCamera.value ?: return
        val dateStampOn = settingsStore.dateStamp && preset.dateStamp
        val borderOn = settingsStore.border
        val brandingOn = settingsStore.branding
        val skipWait = proStore.isPro || !preset.instant

        viewModelScope.launch {
            _cameraState.value = _cameraState.value.copy(isDeveloping = !skipWait)
            if (!skipWait) {
                developingController.develop(skip = false) { p ->
                    _cameraState.value = _cameraState.value.copy(developProgress = p)
                }
            }

            val dateText = if (dateStampOn) {
                val c = Calendar.getInstance()
                DateStampRenderer.formatDate(
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH)
                )
            } else {
                null
            }

            val options = AnalogEffects.Options(
                preset = preset,
                border = borderOn,
                dateStamp = dateStampOn,
                branding = brandingOn
            )
            val developed = AnalogEffects.develop(
                source = source,
                options = options,
                applyFrame = { bmp -> com.facecam.app.film.OverlayRenderer.drawFrame(bmp, preset.frame) },
                dateStampText = dateText
            )

            val photo = galleryRepository.save(developed, preset.id, cameraName)
            developed.recycle()
            _lastDeveloped.value = photo
            _cameraState.value = _cameraState.value.copy(isDeveloping = false, developProgress = 0f)
            refreshGallery()

            val saves = settingsStore.saveCount + 1
            settingsStore.saveCount = saves
            AdManager.preloadInterstitial(getApplication())
        }
    }

    // ------------------------------------------------------------------
    // Double exposure
    // ------------------------------------------------------------------
    fun beginDoubleExposure(base: Bitmap) {
        pendingDoubleExposureBase = base
    }

    fun completeDoubleExposure(overlay: Bitmap, cameraName: String) {
        val base = pendingDoubleExposureBase ?: overlay
        val merged = AnalogEffects.doubleExpose(
            base,
            overlay,
            android.graphics.PorterDuff.Mode.SCREEN
        )
        pendingDoubleExposureBase = null
        developCapture(merged, cameraName)
    }

    // ------------------------------------------------------------------
    // Gallery
    // ------------------------------------------------------------------
    fun refreshGallery() {
        viewModelScope.launch {
            _gallery.value = galleryRepository.all()
        }
    }

    fun saveToDevice(activity: Activity, photo: GalleryPhoto) {
        viewModelScope.launch {
            val uri = MediaStoreSaver.save(activity, photo)
            if (uri != null) {
                _message.value = "Saved to device"
                AdManager.showInterstitialIfDue(activity, settingsStore.saveCount)
            } else {
                _message.value = "Could not save to device"
            }
        }
    }

    fun deletePhoto(photo: GalleryPhoto) {
        viewModelScope.launch {
            galleryRepository.delete(photo)
            refreshGallery()
        }
    }

    fun sharePhoto(photo: GalleryPhoto) {
        ShareHelper.share(getApplication(), photo)
    }

    fun shareLast() {
        _lastDeveloped.value?.let { ShareHelper.share(getApplication(), it) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.end()
    }
}
