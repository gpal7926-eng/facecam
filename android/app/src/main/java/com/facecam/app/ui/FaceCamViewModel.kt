package com.facecam.app.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facecam.app.FaceCamApp
import com.facecam.app.camera.CameraUiState
import com.facecam.app.camera.CaptureMode
import com.facecam.app.camera.FlashMode
import com.facecam.app.camera.LensFacing
import com.facecam.app.camera.SelfTimer
import com.facecam.app.camera.pro.ProState
import com.facecam.app.film.AnalogEffects
import com.facecam.app.film.BeautyEffects
import com.facecam.app.film.CameraGroup
import com.facecam.app.film.DateStampRenderer
import com.facecam.app.film.DevelopingController
import com.facecam.app.film.FilmPreset
import com.facecam.app.film.FilmRepository
import com.facecam.app.film.OverlayRenderer
import com.facecam.app.gallery.GalleryPhoto
import com.facecam.app.gallery.GalleryRepository
import com.facecam.app.gallery.MediaStoreSaver
import com.facecam.app.gallery.ShareHelper
import com.facecam.app.ml.FaceMaskProvider
import com.facecam.app.video.SlowMotionExporter
import com.facecam.app.video.SubtitleOverlay
import com.facecam.app.video.VideoEffectProcessor
import com.facecam.app.video.VideoSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

/**
 * Single source of truth for FaceCam's UI state. Owns the selected camera, the
 * viewfinder controls (photo + video), the subtitle state and the in-app gallery.
 *
 * FaceCam is completely free and offline: there is no purchase, ad or
 * entitlement state here at all.
 */
class FaceCamViewModel(app: Application) : AndroidViewModel(app) {

    private val faceCamApp = app as FaceCamApp

    val filmRepository: FilmRepository = faceCamApp.filmRepository
    val settingsStore = faceCamApp.settingsStore
    val galleryRepository = GalleryRepository(app)
    val developingController = DevelopingController()

    /** Subtitle state for the video recorder (typed caption, cues, SRT). */
    val subtitleOverlay = SubtitleOverlay()

    private val _cameraState = MutableStateFlow(CameraUiState())
    val cameraState: StateFlow<CameraUiState> = _cameraState.asStateFlow()

    private val _selectedCamera = MutableStateFlow<FilmPreset?>(null)
    val selectedCamera: StateFlow<FilmPreset?> = _selectedCamera.asStateFlow()

    /** Which picker tab is showing: "vintage" or "beauty". */
    private val _pickerGroup = MutableStateFlow(CameraGroup.VINTAGE)
    val pickerGroup: StateFlow<String> = _pickerGroup.asStateFlow()

    private val _gallery = MutableStateFlow<List<GalleryPhoto>>(emptyList())
    val gallery: StateFlow<List<GalleryPhoto>> = _gallery.asStateFlow()

    private val _lastDeveloped = MutableStateFlow<GalleryPhoto?>(null)
    val lastDeveloped: StateFlow<GalleryPhoto?> = _lastDeveloped.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Live face-bounds hint (0..1 preview space) for the "centre on face" guide. */
    private val _faceHint = MutableStateFlow<android.graphics.RectF?>(null)
    val faceHint: StateFlow<android.graphics.RectF?> = _faceHint.asStateFlow()

    // ---- Manual camera mode (opt-in; simple mode is untouched) ----
    private val _proState = MutableStateFlow(ProState())
    val proState: StateFlow<ProState> = _proState.asStateFlow()

    /** Turn the Blackmagic-style manual HUD on or off. */
    fun toggleProMode() {
        _proState.value = _proState.value.copy(enabled = !_proState.value.enabled)
    }

    /** Generic mutator used by every control in the manual HUD. */
    fun updateProState(transform: (ProState) -> ProState) {
        _proState.value = transform(_proState.value)
    }

    /** For the double-exposure flow. */
    private var pendingDoubleExposureBase: Bitmap? = null

    init {
        _selectedCamera.value = settingsStore.defaultCameraId
            ?.let { filmRepository.get(it) }
            ?: filmRepository.defaultCamera()
        _selectedCamera.value?.let { _pickerGroup.value = it.group }
        _cameraState.value = _cameraState.value.copy(
            flash = FlashMode.OFF,
            selfTimer = SelfTimer.OFF
        )
        refreshGallery()
    }

    // ------------------------------------------------------------------
    // Camera selection & controls
    // ------------------------------------------------------------------
    fun selectCamera(preset: FilmPreset) {
        _selectedCamera.value = preset
        _pickerGroup.value = preset.group
    }

    /** Switch the picker tab. */
    fun setPickerGroup(group: String) {
        _pickerGroup.value = group
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

    /** Mark the still-capture pipeline busy so the shutter cannot be re-triggered. */
    fun setCapturing(capturing: Boolean) {
        _cameraState.value = _cameraState.value.copy(isCapturing = capturing)
    }

    // ------------------------------------------------------------------
    // Photo / video mode
    // ------------------------------------------------------------------
    fun setCaptureMode(mode: CaptureMode) {
        _cameraState.value = _cameraState.value.copy(captureMode = mode)
    }

    fun setVideoSpeed(speed: VideoSpeed) {
        _cameraState.value = _cameraState.value.copy(videoSpeed = speed)
    }

    fun setMaxFps(fps: Int) {
        _cameraState.value = _cameraState.value.copy(maxFps = fps)
    }

    fun onRecordingStarted() {
        _cameraState.value = _cameraState.value.copy(isRecording = true, recordingElapsedMs = 0L)
    }

    fun onRecordingElapsed(ms: Long) {
        _cameraState.value = _cameraState.value.copy(recordingElapsedMs = ms)
    }

    fun onRecordingStopped() {
        _cameraState.value = _cameraState.value.copy(isRecording = false, recordingElapsedMs = 0L)
    }

    // ------------------------------------------------------------------
    // Subtitles
    // ------------------------------------------------------------------
    fun setTypedCaption(text: String) {
        subtitleOverlay.setTypedCaption(text)
        _cameraState.value = _cameraState.value.copy(typedCaption = text)
    }

    fun toggleLiveCaptions() {
        setLiveCaptions(!_cameraState.value.liveCaptions)
    }

    /** Turn on-device live captions on or off. */
    fun setLiveCaptions(enabled: Boolean) {
        _cameraState.value = _cameraState.value.copy(liveCaptions = enabled, liveCaptionText = "")
    }

    /** Post a short user-facing message. */
    fun notify(text: String) {
        _message.value = text
    }

    /** Feed a live caption line (from the on-device SpeechRecognizer). */
    fun onLiveCaption(text: String, isFinal: Boolean) {
        val at = _cameraState.value.recordingElapsedMs
        if (isFinal) {
            subtitleOverlay.addLiveCaption(text, at)
            subtitleOverlay.commitLiveCaption(at)
        } else {
            subtitleOverlay.addLiveCaption(text, at)
        }
        _cameraState.value = _cameraState.value.copy(liveCaptionText = text.trim())
    }

    // ------------------------------------------------------------------
    // On-device ML framing guide
    // ------------------------------------------------------------------
    fun setFaceGuide(enabled: Boolean) {
        _cameraState.value = _cameraState.value.copy(faceGuide = enabled)
        if (!enabled) _faceHint.value = null
    }

    fun onFaceHint(bounds: android.graphics.RectF?) {
        _faceHint.value = bounds
    }

    // ------------------------------------------------------------------
    // Capture & develop (stills)
    // ------------------------------------------------------------------
    /**
     * Apply the appropriate pipeline to a captured still and persist it to the
     * in-app gallery:
     *
     *  - Beauty cameras run [BeautyEffects] (clean, no grain / leak / vignette /
     *    frame / date stamp / branding). When on-device face detection finds a
     *    face, the skin smoothing is steered by a feathered face mask.
     *  - Vintage cameras run [AnalogEffects] + the shared frame, date stamp and
     *    [com.facecam.app.film.BrandingRenderer] band.
     *
     * Instant vintage cameras show the "developing" wait first.
     */
    fun developCapture(source: Bitmap, cameraName: String) {
        val preset = _selectedCamera.value ?: return
        val skipWait = !preset.instant

        viewModelScope.launch {
            _cameraState.value = _cameraState.value.copy(isDeveloping = !skipWait)
            try {
                if (!skipWait) {
                    developingController.develop(skip = false) { p ->
                        _cameraState.value = _cameraState.value.copy(developProgress = p)
                    }
                }

                val developed: Bitmap = if (preset.isBeauty) {
                    val faceMask = runCatching { FaceMaskProvider.detect(source) }.getOrNull()
                    val out = BeautyEffects.develop(
                        source = source,
                        options = BeautyEffects.Options(preset = preset, faceMask = faceMask?.mask)
                    )
                    faceMask?.mask?.recycle()
                    out
                } else {
                    val dateStampOn = settingsStore.dateStamp && preset.dateStamp
                    val borderOn = settingsStore.border
                    val brandingOn = settingsStore.branding

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

                    AnalogEffects.develop(
                        source = source,
                        options = AnalogEffects.Options(
                            preset = preset,
                            border = borderOn,
                            dateStamp = dateStampOn,
                            branding = brandingOn
                        ),
                        applyFrame = { bmp -> OverlayRenderer.drawFrame(bmp, preset.frame) },
                        dateStampText = dateText
                    )
                }

                val photo = galleryRepository.save(developed, preset.id, cameraName)
                developed.recycle()
                _lastDeveloped.value = photo
                refreshGallery()
                settingsStore.saveCount = settingsStore.saveCount + 1
            } catch (t: Throwable) {
                Log.e(TAG, "developCapture failed", t)
                _message.value = "Could not develop the photo"
            } finally {
                // The captured still is no longer needed once the pipeline has
                // produced its own (new) bitmap, so always release it.
                if (!source.isRecycled) source.recycle()
                _cameraState.value = _cameraState.value.copy(
                    isDeveloping = false,
                    developProgress = 0f
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Video finishing: effects -> slow motion -> gallery + MediaStore
    // ------------------------------------------------------------------
    /**
     * Post-process a recorded clip: apply the camera look frame by frame, burn
     * the typed caption, re-time for slow motion, then save to the in-app gallery
     * and to MediaStore. A sidecar `.srt` is written when there are cues.
     */
    fun finishVideo(source: File, cameraName: String) {
        val preset = _selectedCamera.value ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _cameraState.value = _cameraState.value.copy(processingVideo = true, videoProgress = 0f)
            val cache = app.cacheDir
            val stamp = System.currentTimeMillis()
            val effFile = File(cache, "facecam_effect_$stamp.mp4")
            val slowFile = File(cache, "facecam_slow_$stamp.mp4")
            try {
                val faceMask = if (preset.isBeauty) {
                    runCatching { FaceMaskProvider.detectFromVideo(source) }.getOrNull()
                } else {
                    null
                }
                val caption = subtitleOverlay.typedCaption.takeIf { it.isNotBlank() }

                val effResult = VideoEffectProcessor.process(
                    input = source,
                    output = effFile,
                    preset = preset,
                    faceMask = faceMask?.mask,
                    caption = caption,
                    border = settingsStore.border,
                    dateStamp = settingsStore.dateStamp && preset.dateStamp,
                    branding = settingsStore.branding,
                    onProgress = { p ->
                        _cameraState.value = _cameraState.value.copy(videoProgress = p * 0.7f)
                    }
                )
                faceMask?.mask?.recycle()

                val slowResult = SlowMotionExporter.export(
                    input = effResult.file,
                    output = slowFile,
                    speed = _cameraState.value.videoSpeed,
                    onProgress = { p ->
                        _cameraState.value = _cameraState.value.copy(videoProgress = 0.7f + p * 0.3f)
                    }
                )

                val photo = galleryRepository.saveVideo(slowResult.file, preset.id, cameraName)
                if (photo != null) {
                    MediaStoreSaver.saveVideo(app, photo)
                    if (subtitleOverlay.allCues().isNotEmpty()) {
                        val srt = File(photo.file.parentFile, photo.file.nameWithoutExtension + ".srt")
                        subtitleOverlay.writeSrt(srt)
                    }
                    _lastDeveloped.value = photo
                    _message.value = buildString {
                        append(slowResult.note)
                        append(" - ")
                        append(if (effResult.lookBaked) "look applied" else effResult.note)
                    }
                    settingsStore.saveCount = settingsStore.saveCount + 1
                } else {
                    _message.value = "Could not save video"
                }
            } catch (t: Throwable) {
                Log.e(TAG, "finishVideo failed", t)
                _message.value = "Could not finish the video"
            } finally {
                runCatching { effFile.delete() }
                runCatching { slowFile.delete() }
                runCatching { source.delete() }
                subtitleOverlay.clear()
                _cameraState.value = _cameraState.value.copy(
                    processingVideo = false,
                    videoProgress = 0f,
                    typedCaption = ""
                )
                refreshGallery()
            }
        }
    }

    // ------------------------------------------------------------------
    // Double exposure
    // ------------------------------------------------------------------
    fun beginDoubleExposure(base: Bitmap) {
        pendingDoubleExposureBase = base
    }

    fun completeDoubleExposure(overlay: Bitmap, cameraName: String) {
        val base = pendingDoubleExposureBase
        pendingDoubleExposureBase = null
        val merged = AnalogEffects.doubleExpose(
            base ?: overlay,
            overlay,
            android.graphics.PorterDuff.Mode.SCREEN
        )
        // The two source shots are no longer needed once they are merged.
        if (base != null && base !== merged && !base.isRecycled) base.recycle()
        if (overlay !== merged && !overlay.isRecycled) overlay.recycle()
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
            _message.value = if (uri != null) "Saved to device" else "Could not save to device"
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

    companion object {
        private const val TAG = "FaceCamViewModel"
    }
}
