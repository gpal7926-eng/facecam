package com.facecam.app.ui.screens

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.facecam.app.camera.CameraController
import com.facecam.app.ui.FaceCamViewModel
import kotlinx.coroutines.launch

/**
 * Double-exposure mode: take a first shot, then a second; the two are merged
 * with a blend mode and run through the film pipeline.
 */
@Composable
fun DoubleExposureScreen(
    viewModel: FaceCamViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preset by viewModel.selectedCamera.collectAsState()

    val cameraController = remember { CameraController(context) }
    val previewView = remember { PreviewView(context) }
    var firstShot by remember { mutableStateOf<Bitmap?>(null) }

    // Read the lifecycle owner in composable scope (cannot be read inside the
    // LaunchedEffect block, which is not a @Composable context).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = com.facecam.app.camera.LensFacing.BACK,
            onReady = { }
        )
    }

    // Release the camera binding and any half-finished first shot when leaving.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            cameraController.unbind()
            firstShot?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Double exposure",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (firstShot == null) "Take the first shot" else "Now take the second shot",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        val bmp = runCatching { cameraController.capture() }.getOrNull() ?: return@launch
                        val current = firstShot
                        if (current == null) {
                            firstShot = bmp
                            viewModel.beginDoubleExposure(bmp)
                        } else {
                            viewModel.completeDoubleExposure(bmp, preset?.name ?: "Double")
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (firstShot == null) "Capture #1" else "Capture #2 and blend")
            }
        }
    }
}
