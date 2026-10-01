package com.facecam.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facecam.app.ui.theme.Coral
import com.facecam.app.ui.theme.Cyan
import com.facecam.app.ui.theme.Gold
import com.facecam.app.ui.theme.Ink
import com.facecam.app.ui.theme.Magenta
import com.facecam.app.ui.theme.Violet
import kotlinx.coroutines.delay

/**
 * Animated 3D intro.
 *
 * A fan of photo cards swings in from depth on a perspective camera, a shutter
 * "clicks" with a white flash, and the FaceCam wordmark rises into place - a
 * short, skippable animation before the app hands off to onboarding or the
 * viewfinder. Pure Compose animation; no assets, no network.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    // 0 -> 1 drives the whole sequence.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(2600, easing = FastOutSlowInEasing))
        delay(250)
        finish()
    }
    val p = progress.value

    val cardColors = listOf(
        Brush.linearGradient(listOf(Color(0xFFF4D9A8), Color(0xFFB06A3C))),
        Brush.linearGradient(listOf(Color(0xFFFFD0E6), Magenta)),
        Brush.linearGradient(listOf(Color(0xFF9BE7FF), Color(0xFF3B6BD6))),
        Brush.linearGradient(listOf(Color(0xFFFFC9A0), Coral)),
        Brush.linearGradient(listOf(Color(0xFFD7FFD0), Color(0xFF2F9E6B)))
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF241A33), Ink),
                    radius = 1400f
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { finish() },
        contentAlignment = Alignment.Center
    ) {
        // ---- the 3D card fan ----
        Box(
            modifier = Modifier
                .size(width = 300.dp, height = 260.dp)
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationX = lerp(24f, 6f, p)
                    rotationY = lerp(-34f, 0f, p)
                    scaleX = lerp(0.7f, 1f, p)
                    scaleY = lerp(0.7f, 1f, p)
                },
            contentAlignment = Alignment.Center
        ) {
            for (i in -2..2) {
                val spread = lerp(i * 78f, i * 52f, p)
                Box(
                    modifier = Modifier
                        .offset(x = spread.dp)
                        .size(width = 92.dp, height = 118.dp)
                        .graphicsLayer {
                            cameraDistance = 12f * density
                            rotationY = lerp(i * 42f, i * 10f, p)
                            translationY = lerp(20f, 0f, p)
                            alpha = (p * 4f).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardColors[i + 2])
                )
            }

            // shutter ring + button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer {
                        val s = shutterScale(p)
                        scaleX = s
                        scaleY = s
                        alpha = shutterAlpha(p)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x22000000)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }
        }

        // ---- shutter flash ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flashAlpha(p) }
                .background(Color.White)
        )

        // ---- wordmark + subtitle ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val titleT = ((p - 0.58f) / 0.22f).coerceIn(0f, 1f)
            Text(
                text = "FaceCam",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 46.sp,
                    brush = Brush.linearGradient(listOf(Violet, Magenta, Cyan))
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = titleT
                    translationY = (1f - titleT) * 40f
                    scaleX = 0.9f + 0.1f * titleT
                    scaleY = 0.9f + 0.1f * titleT
                }
            )
            val subT = ((p - 0.72f) / 0.22f).coerceIn(0f, 1f)
            Text(
                text = "50s \u00B7 60s \u00B7 70s \u00B7 80s \u00B7 90s \u00B7 B&W",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .graphicsLayer { alpha = subT; translationY = (1f - subT) * 24f }
            )
        }

        // ---- skip ----
        Text(
            text = "Skip",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { finish() }
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun shutterScale(p: Float): Float = when {
    p < 0.40f -> 0.6f
    p < 0.48f -> lerp(0.6f, 1f, (p - 0.40f) / 0.08f)
    p < 0.60f -> 1f
    p < 0.72f -> lerp(1f, 1.25f, (p - 0.60f) / 0.12f)
    else -> 1.25f
}

private fun shutterAlpha(p: Float): Float = when {
    p < 0.40f -> 0f
    p < 0.48f -> (p - 0.40f) / 0.08f
    p < 0.60f -> 1f
    p < 0.72f -> 1f - (p - 0.60f) / 0.12f
    else -> 0f
}

private fun flashAlpha(p: Float): Float = when {
    p < 0.46f -> 0f
    p < 0.52f -> ((p - 0.46f) / 0.06f) * 0.9f
    p < 0.60f -> 0.9f - ((p - 0.52f) / 0.08f) * 0.9f
    else -> 0f
}
