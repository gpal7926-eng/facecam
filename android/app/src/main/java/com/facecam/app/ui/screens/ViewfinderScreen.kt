package com.facecam.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.facecam.app.camera.CameraController
import com.facecam.app.camera.CaptureMode
import com.facecam.app.camera.FlashMode
import com.facecam.app.camera.LensFacing
import com.facecam.app.camera.SelfTimer
import com.facecam.app.camera.pro.LevelSensor
import com.facecam.app.camera.pro.OverlayMode
import com.facecam.app.camera.pro.ProCameraController
import com.facecam.app.camera.pro.ProCapabilities
import com.facecam.app.camera.pro.ProControlsPanel
import com.facecam.app.camera.pro.ProFrameProcessor
import com.facecam.app.camera.pro.ProHudOverlay
import com.facecam.app.film.CameraGroup
import com.facecam.app.ml.FaceGuideAnalyzer
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.CameraStrip
import com.facecam.app.ui.components.FaceCamBottomBar
import com.facecam.app.ui.components.GlassPill
import com.facecam.app.ui.components.HomeTab
import com.facecam.app.ui.components.LastShotThumbnail
import com.facecam.app.ui.components.ModeChip
import com.facecam.app.ui.components.RecBadge
import com.facecam.app.ui.components.SegmentedTabs
import com.facecam.app.ui.components.glass
import com.facecam.app.ui.theme.FaceCamGradient
import com.facecam.app.ui.theme.Violet
import com.facecam.app.video.LiveCaptionController
import com.facecam.app.video.VideoRecorder
import com.facecam.app.video.VideoSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * The live viewfinder, styled after NOMO CAM: a full-bleed preview with a
 * prominent horizontal strip of camera models across the bottom (each with a
 * realistic-looking thumbnail and the camera name underneath), a big round
 * shutter button centred below the strip, a minimal top bar and the last-shot
 * thumbnail tucked into the bottom-left corner.
 *
 * Under the hood nothing changed: the same CameraX preview, live tint +
 * vignette, on-device framing guide and optional manual HUD are all still here,
 * along with the photo / video controls. Everything stays 100% on-device.
 */
@Composable
fun ViewfinderScreen(
    viewModel: FaceCamViewModel,
    onOpenCameras: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDoubleExposure: () -> Unit,
    onNavigate: (HomeTab) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraState by viewModel.cameraState.collectAsState()
    val preset by viewModel.selectedCamera.collectAsState()
    val message by viewModel.message.collectAsState()
    val faceHint by viewModel.faceHint.collectAsState()
    val lastShot by viewModel.lastDeveloped.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var captionDraft by remember { mutableStateOf("") }

    // The family whose cameras are shown in the bottom strip.
    val activeGroup = preset?.group ?: CameraGroup.VINTAGE
    val stripCameras = viewModel.filmRepository.ofGroup(activeGroup)

    // Keep the caption field in sync with the ViewModel.
    LaunchedEffect(cameraState.typedCaption) {
        if (captionDraft != cameraState.typedCaption) captionDraft = cameraState.typedCaption
    }

    // ---- Manual mode wiring (opt-in) ----
    val proState by viewModel.proState.collectAsState()
    val proController = remember { ProCameraController() }
    val levelSensor = remember { LevelSensor(context) }
    val frameProcessor = remember { ProFrameProcessor() }
    val histogram by frameProcessor.histogram.collectAsState()
    val proOverlay by frameProcessor.overlay.collectAsState()
    val roll by levelSensor.roll.collectAsState()
    var proCaps by remember { mutableStateOf(ProCapabilities.UNSUPPORTED) }

    val cameraController = remember { CameraController(context) }
    val previewView = remember { PreviewView(context) }
    val videoRecorder = remember { VideoRecorder(context) }
    val faceGuideAnalyzer = remember { FaceGuideAnalyzer() }
    val liveCaptions = remember { LiveCaptionController(context) }

    // Runtime microphone permission (audio in video + on-device live captions).
    // Live captions must NEVER start before RECORD_AUDIO is actually granted, so
    // the grant callback is what starts them - not the request itself.
    var pendingCaptionStart by remember { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingCaptionStart) {
                pendingCaptionStart = false
                liveCaptions.start()
            }
        } else {
            pendingCaptionStart = false
            // Keep the feature disabled and tell the user why.
            viewModel.setLiveCaptions(false)
            viewModel.notify("Microphone permission is needed for audio and live captions")
        }
    }

    // Read the lifecycle owner in composable scope: it cannot be read inside a
    // LaunchedEffect block (that is not a @Composable context).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(cameraState.lensFacing) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = cameraState.lensFacing,
            videoCapture = videoRecorder.videoCapture,
            onReady = { viewModel.onCameraReady(it) }
        )
    }
    LaunchedEffect(cameraState.flash) {
        cameraController.setFlash(cameraState.flash)
    }
    LaunchedEffect(cameraState.cameraReady) {
        if (cameraState.cameraReady) {
            viewModel.setMaxFps(cameraController.highestSupportedFps())
            // A fresh bind (e.g. after a lens switch) recreates the capture use
            // case, so re-apply the currently selected flash mode.
            cameraController.setFlash(cameraState.flash)
        }
    }

    // Attach the manual controller once the camera is bound, then push state to it.
    LaunchedEffect(proState, cameraState.cameraReady, cameraState.lensFacing, cameraState.faceGuide) {
        if (cameraState.cameraReady) {
            val cam = cameraController.currentCamera()
            val info = cameraController.currentCameraInfo()
            if (cam != null && info != null) {
                proController.attach(cam, info)
                proCaps = proController.capabilities
            }
        }
        proController.applyState(proState)
        val mode = when {
            !proState.enabled -> OverlayMode.NONE
            proState.falseColor -> OverlayMode.FALSE_COLOR
            proState.focusPeaking -> OverlayMode.PEAKING
            proState.zebra -> OverlayMode.ZEBRA
            else -> OverlayMode.NONE
        }
        frameProcessor.mode = mode
        frameProcessor.enabled = proState.enabled && mode != OverlayMode.NONE

        // Manual mode takes priority over the face-guide analyzer.
        val analyzer = when {
            proState.enabled -> frameProcessor
            cameraState.faceGuide -> faceGuideAnalyzer
            else -> null
        }
        cameraController.setFrameAnalyzer(analyzer)
    }

    // Publish the framing-guide face bounds to the ViewModel.
    val faceBounds by faceGuideAnalyzer.bounds.collectAsState()
    LaunchedEffect(faceBounds) { viewModel.onFaceHint(faceBounds) }

    // The accelerometer only runs while manual mode is on.
    DisposableEffect(proState.enabled) {
        if (proState.enabled) levelSensor.start() else levelSensor.stop()
        onDispose { levelSensor.stop() }
    }

    // Live captions lifecycle.
    LaunchedEffect(cameraState.liveCaptions) {
        if (cameraState.liveCaptions) {
            liveCaptions.onLine = { text, isFinal -> viewModel.onLiveCaption(text, isFinal) }
            liveCaptions.onError = { viewModel.setLiveCaptions(false) }
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                liveCaptions.start()
            } else {
                // Ask first; only start once the grant callback fires.
                pendingCaptionStart = true
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            pendingCaptionStart = false
            liveCaptions.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            liveCaptions.stop()
            faceGuideAnalyzer.close()
            videoRecorder.release()
            cameraController.unbind()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Full-bleed live preview.
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Lightweight live tint + vignette overlay (cheap, per-frame).
        val tint = when (cameraState.lensFacing) {
            LensFacing.FRONT -> Color(0x1AFFE8C0)
            LensFacing.BACK -> Color(0x14FFD9A0)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000)),
                        radius = 900f
                    )
                )
                .background(tint)
        )

        // On-device "centre on face" framing guide.
        AnimatedVisibility(
            visible = cameraState.faceGuide,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160))
        ) {
            FaceGuideOverlay(bounds = faceHint, modifier = Modifier.fillMaxSize())
        }

        // Live manual HUD: grid, level, histogram and live overlays.
        AnimatedVisibility(
            visible = proState.enabled,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180))
        ) {
            ProHudOverlay(
                state = proState,
                histogram = histogram,
                roll = roll,
                overlay = proOverlay
            )
        }

        // Minimal top bar: a short scrim with just the essential controls.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color(0x00000000))
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setFaceGuide(!cameraState.faceGuide) }) {
                    Icon(
                        Icons.Filled.FaceRetouchingNatural,
                        contentDescription = "Centre on face guide",
                        tint = if (cameraState.faceGuide) Violet else Color.White
                    )
                }
                TextButton(onClick = { viewModel.toggleProMode() }) {
                    Text(
                        text = "MANUAL",
                        color = if (proState.enabled) MaterialTheme.colorScheme.primary else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onOpenCameras) {
                    Icon(Icons.Filled.GridView, contentDescription = "Cameras", tint = Color.White)
                }
                IconButton(onClick = onOpenDoubleExposure) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Double exposure", tint = Color.White)
                }
            }
        }

        // Live caption preview.
        if (cameraState.liveCaptions && cameraState.liveCaptionText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 300.dp, start = 24.dp, end = 24.dp)
                    .glass(shape = RoundedCornerShape(16.dp), alpha = 0.35f)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = cameraState.liveCaptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Countdown / developing / processing overlays.
        if (countdown > 0) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
            }
        }
        if (cameraState.isDeveloping) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Developing...",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { cameraState.developProgress },
                        modifier = Modifier.width(200.dp)
                    )
                }
            }
        }
        if (cameraState.processingVideo) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Violet)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Finishing your clip...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${(cameraState.videoProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // ---- Bottom cluster: the NOMO-CAM-style camera strip + shutter ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recording readout.
            AnimatedVisibility(
                visible = cameraState.isRecording,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    RecBadge()
                    GlassPill(text = formatElapsed(cameraState.recordingElapsedMs))
                }
            }

            // Video extras: speed + captions.
            AnimatedVisibility(
                visible = cameraState.captureMode == CaptureMode.VIDEO,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .glass(shape = RoundedCornerShape(24.dp), alpha = 0.16f)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoSpeed.entries.forEach { speed ->
                            ModeChip(
                                label = speed.label,
                                selected = cameraState.videoSpeed == speed,
                                onClick = { viewModel.setVideoSpeed(speed) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        GlassPill(
                            text = if (cameraState.highFpsAvailable) {
                                "${cameraState.maxFps}fps HD"
                            } else {
                                "high-fps n/a"
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glass(shape = RoundedCornerShape(16.dp), alpha = 0.22f)
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            if (captionDraft.isEmpty()) {
                                Text(
                                    text = "Add a caption...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            BasicTextField(
                                value = captionDraft,
                                onValueChange = {
                                    captionDraft = it
                                    viewModel.setTypedCaption(it)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ModeChip(
                            label = "CC Live",
                            selected = cameraState.liveCaptions,
                            onClick = { viewModel.toggleLiveCaptions() }
                        )
                    }
                }
            }

            // Selected camera name (the strip highlights it too).
            Box(
                modifier = Modifier
                    .glass(shape = RoundedCornerShape(50), alpha = 0.20f)
                    .clickable { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = buildString {
                        append(preset?.name ?: "Select camera")
                        val tag = preset?.tag
                        if (!tag.isNullOrBlank()) {
                            append("  -  ")
                            append(tag)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            // Family pills: Vintage / B&W / Beauty.
            SegmentedTabs(
                options = CameraGroup.ordered.map { CameraGroup.label(it) },
                selectedIndex = CameraGroup.ordered.indexOf(activeGroup).coerceAtLeast(0),
                onSelect = { index ->
                    val group = CameraGroup.ordered.getOrElse(index) { CameraGroup.VINTAGE }
                    viewModel.setPickerGroup(group)
                    if (preset?.group != group) {
                        viewModel.filmRepository.ofGroup(group).firstOrNull()
                            ?.let { viewModel.selectCamera(it) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // The prominent horizontal camera strip.
            CameraStrip(
                cameras = stripCameras,
                selectedId = preset?.id,
                onSelect = { viewModel.selectCamera(it) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Secondary controls: photo / video on the left, flash + timer right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        label = "Photo",
                        selected = cameraState.captureMode == CaptureMode.PHOTO,
                        onClick = { viewModel.setCaptureMode(CaptureMode.PHOTO) }
                    )
                    ModeChip(
                        label = "Video",
                        selected = cameraState.captureMode == CaptureMode.VIDEO,
                        onClick = { viewModel.setCaptureMode(CaptureMode.VIDEO) }
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.cycleFlash() }) {
                    Icon(
                        imageVector = when (cameraState.flash) {
                            FlashMode.ON -> Icons.Filled.FlashOn
                            FlashMode.AUTO -> Icons.Filled.FlashAuto
                            FlashMode.OFF -> Icons.Filled.FlashOff
                        },
                        contentDescription = "Flash",
                        tint = if (cameraState.flash == FlashMode.OFF) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.cycleSelfTimer() }) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = "Self timer",
                        tint = timerTint(cameraState.selfTimer)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // The shutter row: last shot (left), big round shutter (centre),
            // flip camera (right).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LastShotThumbnail(
                    photo = lastShot,
                    onClick = onOpenGallery
                )

                ShutterButton(
                    capturing = cameraState.isCapturing || cameraState.processingVideo,
                    recording = cameraState.isRecording,
                    videoMode = cameraState.captureMode == CaptureMode.VIDEO,
                    onClick = {
                        if (cameraState.captureMode == CaptureMode.VIDEO) {
                            if (videoRecorder.isRecording) {
                                videoRecorder.stop()
                            } else {
                                if (!videoRecorder.canRecordAudio()) {
                                    audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                val file = File(
                                    context.cacheDir,
                                    "facecam_rec_${System.currentTimeMillis()}.mp4"
                                )
                                val started = videoRecorder.start(
                                    file = file,
                                    withAudio = true,
                                    onElapsed = { viewModel.onRecordingElapsed(it) },
                                    onSaved = { saved ->
                                        viewModel.onRecordingStopped()
                                        if (saved != null) {
                                            viewModel.finishVideo(saved, preset?.name ?: "Video")
                                        }
                                    },
                                    onError = { viewModel.notify(it) }
                                )
                                if (started) viewModel.onRecordingStarted()
                            }
                        } else {
                            scope.launch {
                                viewModel.setCapturing(true)
                                try {
                                    if (cameraState.selfTimer.seconds > 0) {
                                        for (s in cameraState.selfTimer.seconds downTo 1) {
                                            countdown = s
                                            delay(1000)
                                        }
                                        countdown = 0
                                    }
                                    val bmp = runCatching { cameraController.capture() }.getOrNull()
                                    val name = preset?.name
                                    if (bmp != null && name != null) {
                                        viewModel.developCapture(bmp, name)
                                    } else if (bmp != null) {
                                        // No camera selected: never leak the capture.
                                        bmp.recycle()
                                    }
                                } finally {
                                    countdown = 0
                                    viewModel.setCapturing(false)
                                }
                            }
                        }
                    }
                )

                IconButton(onClick = { viewModel.switchLens() }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                }
            }
        }

        // Manual-control panel (only when manual mode is enabled).
        AnimatedVisibility(
            visible = proState.enabled,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ProControlsPanel(
                state = proState,
                capabilities = proCaps,
                onUpdate = { transform -> viewModel.updateProState(transform) },
                modifier = Modifier.padding(bottom = 300.dp)
            )
        }

        // Bottom navigation bar.
        FaceCamBottomBar(
            current = HomeTab.CAMERA,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showPicker) {
            CameraPickerSheet(
                viewModel = viewModel,
                onDismiss = { showPicker = false }
            )
        }

        message?.let {
            LaunchedEffect(it) {
                delay(2200)
                viewModel.consumeMessage()
            }
        }
    }
}

/**
 * A bigger, friendlier shutter: a gradient outer ring with an inner disc that
 * eases in while capturing. In video mode the disc turns into a record dot, and
 * a rounded square while recording.
 */
@Composable
private fun ShutterButton(
    capturing: Boolean,
    recording: Boolean,
    videoMode: Boolean,
    onClick: () -> Unit
) {
    val innerScale by animateFloatAsState(
        targetValue = if (capturing) 0.72f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "shutterInner"
    )
    val ring = if (videoMode) {
        Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFE24BC0)))
    } else {
        FaceCamGradient
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(ring)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color(0x33000000))
            .clickable(enabled = !capturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (recording) 40.dp else 76.dp)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .clip(if (recording) RoundedCornerShape(10.dp) else CircleShape)
                .background(
                    when {
                        recording -> Color(0xFFFF3B5C)
                        capturing -> Color.White.copy(alpha = 0.85f)
                        else -> Color.White
                    }
                )
        )
    }
}

/** Draws the "centre on face" framing guide from the detected face bounds. */
@Composable
private fun FaceGuideOverlay(bounds: RectF?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()

        if (bounds != null && wPx > 0f && hPx > 0f) {
            val left = with(density) { (bounds.left * wPx).toDp() }
            val top = with(density) { (bounds.top * hPx).toDp() }
            val width = with(density) { ((bounds.right - bounds.left) * wPx).toDp() }
            val height = with(density) { ((bounds.bottom - bounds.top) * hPx).toDp() }
            Box(
                modifier = Modifier
                    .padding(start = left, top = top)
                    .size(width = width.coerceAtLeast(40.dp), height = height.coerceAtLeast(40.dp))
                    .border(2.dp, Violet, RoundedCornerShape(22.dp))
            )
        } else {
            // A friendly centred hint when no face is detected yet.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp)
                    .border(1.5.dp, Color(0x66FFFFFF), CircleShape)
            )
        }
    }
}

private fun timerTint(timer: SelfTimer): Color = when (timer) {
    SelfTimer.OFF -> Color.White
    else -> Color(0xFFF0B45C)
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
