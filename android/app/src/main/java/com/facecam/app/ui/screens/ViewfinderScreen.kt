package com.facecam.app.ui.screens

import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.facecam.app.camera.CameraController
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
import com.facecam.app.ui.FaceCamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The live viewfinder. Shows a CameraX preview with a lightweight tint + vignette
 * overlay, the full set of capture controls, and an optional manual shooting HUD.
 */
@Composable
fun ViewfinderScreen(
    viewModel: FaceCamViewModel,
    onOpenCameras: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDoubleExposure: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraState by viewModel.cameraState.collectAsState()
    val preset by viewModel.selectedCamera.collectAsState()
    val message by viewModel.message.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }

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

    LaunchedEffect(cameraState.lensFacing) {
        cameraController.bind(
            lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current,
            previewView = previewView,
            lensFacing = cameraState.lensFacing,
            onReady = { viewModel.onCameraReady(it) }
        )
    }
    LaunchedEffect(cameraState.flash) {
        cameraController.setFlash(cameraState.flash)
    }

    // Attach the manual controller once the camera is bound, then push state to it.
    LaunchedEffect(proState, cameraState.cameraReady, cameraState.lensFacing) {
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
        cameraController.setFrameAnalyzer(if (proState.enabled) frameProcessor else null)
    }

    // The accelerometer only runs while manual mode is on.
    DisposableEffect(proState.enabled) {
        if (proState.enabled) levelSensor.start() else levelSensor.stop()
        onDispose { levelSensor.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Live preview.
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

        // Top control bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.toggleProMode() }) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "Manual mode",
                        tint = if (proState.enabled) MaterialTheme.colorScheme.primary else Color.White
                    )
                    Text(
                        text = "MANUAL",
                        color = if (proState.enabled) MaterialTheme.colorScheme.primary else Color.White,
                        fontWeight = FontWeight.Bold
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

        // Countdown / developing overlay.
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

        // Bottom controls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selected camera chip.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x66000000))
                    .clickable { showPicker = true }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = preset?.name ?: "Select camera",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenGallery) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                }
                IconButton(onClick = { viewModel.cycleSelfTimer() }) {
                    Icon(Icons.Filled.Timer, contentDescription = "Self timer", tint = timerTint(cameraState.selfTimer))
                }

                // Shutter button.
                ShutterButton(
                    capturing = cameraState.isCapturing,
                    onClick = {
                        scope.launch {
                            if (cameraState.selfTimer.seconds > 0) {
                                for (s in cameraState.selfTimer.seconds downTo 1) {
                                    countdown = s
                                    delay(1000)
                                }
                                countdown = 0
                            }
                            val bmp = runCatching { cameraController.capture() }.getOrNull()
                            if (bmp != null && preset != null) {
                                viewModel.developCapture(bmp, preset!!.name)
                            }
                        }
                    }
                )

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
                modifier = Modifier.padding(bottom = 172.dp)
            )
        }

        if (showPicker) {
            CameraPickerSheet(
                viewModel = viewModel,
                onDismiss = { showPicker = false }
            )
        }

        message?.let {
            LaunchedEffect(it) {
                delay(1800)
                viewModel.consumeMessage()
            }
        }
    }
}

/**
 * A soft, modern shutter button: a thin outer ring with an inner disc that eases
 * in slightly while a capture is in progress.
 */
@Composable
private fun ShutterButton(
    capturing: Boolean,
    onClick: () -> Unit
) {
    val innerScale by animateFloatAsState(
        targetValue = if (capturing) 0.72f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "shutterInner"
    )
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .border(3.dp, Color.White, CircleShape)
            .clickable(enabled = !capturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                }
                .clip(CircleShape)
                .background(
                    if (capturing) MaterialTheme.colorScheme.primary else Color.White
                )
        )
    }
}

private fun timerTint(timer: SelfTimer): Color = when (timer) {
    SelfTimer.OFF -> Color.White
    else -> Color(0xFFF0B45C)
}
