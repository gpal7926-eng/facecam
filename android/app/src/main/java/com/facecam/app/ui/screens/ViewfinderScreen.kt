package com.facecam.app.ui.screens

import android.app.Activity
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.facecam.app.camera.CameraController
import com.facecam.app.camera.FlashMode
import com.facecam.app.camera.LensFacing
import com.facecam.app.camera.SelfTimer
import com.facecam.app.ui.FaceCamViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The live viewfinder. Shows a CameraX preview with a lightweight tint + vignette
 * overlay, and the full set of capture controls.
 */
@Composable
fun ViewfinderScreen(
    viewModel: FaceCamViewModel,
    onOpenShop: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDoubleExposure: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val cameraState by viewModel.cameraState.collectAsState()
    val preset by viewModel.selectedCamera.collectAsState()
    val lastDeveloped by viewModel.lastDeveloped.collectAsState()
    val message by viewModel.message.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }

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
            Row {
                IconButton(onClick = onOpenShop) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = "Camera shop", tint = Color.White)
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
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selected camera chip.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x66000000))
                    .clickable { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = preset?.name ?: "Select camera",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(4.dp, Color(0x55FFFFFF), CircleShape)
                        .clickable(enabled = !cameraState.isCapturing) {
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

        if (showPicker) {
            CameraPickerSheet(
                viewModel = viewModel,
                onDismiss = { showPicker = false },
                onLocked = {
                    showPicker = false
                    onOpenShop()
                }
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

private fun timerTint(timer: SelfTimer): Color = when (timer) {
    SelfTimer.OFF -> Color.White
    else -> Color(0xFFE8A33D)
}
