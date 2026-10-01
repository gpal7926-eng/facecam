package com.facecam.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facecam.app.ui.theme.Gold
import com.facecam.app.ui.theme.Magenta
import com.facecam.app.ui.theme.Violet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The FaceCam intro sequence - a fully procedural, on-device 3D animation built
 * with Compose [Canvas] only. There is no external 3D library and no network:
 * every dot, blade and letter is drawn by hand each frame.
 *
 * Three acts, about three seconds in total:
 *
 *  1. **Globe** (0.0s - 1.2s) - a rotating 3D sphere drawn as a dotted
 *     wireframe. Latitude and longitude rings are generated in 3D, spun around
 *     the vertical axis, tilted slightly and projected to 2D with a simple
 *     perspective divide. Dots are shaded by depth so the far side reads as
 *     behind the near side.
 *  2. **Shutter** (1.2s - 1.9s) - a camera-shutter flash: six aperture blades
 *     swing open like an iris while a bright glow blooms through, capped by a
 *     full-screen white flash that rises and falls.
 *  3. **Wordmark** (1.9s - 3.0s) - the "FaceCam" wordmark fades and scales in
 *     with the tagline underneath.
 *
 * Tapping anywhere skips straight to the app.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = TOTAL_MS, easing = LinearEasing)
        )
        onFinished()
    }

    val p = progress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onFinished() })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntro(p)
        }

        // Act 3: the wordmark, drawn with real Compose text so it picks up the
        // app typography. It is invisible until its act begins.
        val wordT = wordT(p)
        if (wordT > 0f) {
            val eased = easeOut(wordT)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = eased
                    scaleX = 0.82f + 0.18f * eased
                    scaleY = 0.82f + 0.18f * eased
                }
            ) {
                Text(
                    text = "FaceCam",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "film & beauty camera",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // A gentle "tap to skip" hint that fades out once the wordmark lands.
        val hintAlpha = (0.55f * (1f - wordT)).coerceIn(0f, 1f)
        if (hintAlpha > 0.01f) {
            Text(
                text = "tap to skip",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = hintAlpha),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------

/** Total intro duration in milliseconds. */
private const val TOTAL_MS = 3000

/** End of the globe act, as a fraction of the timeline (1.2s / 3.0s). */
private const val GLOBE_END = 0.40f

/** End of the shutter act, as a fraction of the timeline (1.9s / 3.0s). */
private const val SHUTTER_END = 0.633f

/** Globe opacity: full through its act, then a short fade out. */
private fun globeAlpha(p: Float): Float = when {
    p <= GLOBE_END -> 1f
    p <= GLOBE_END + 0.08f -> 1f - (p - GLOBE_END) / 0.08f
    else -> 0f
}

/** Shutter act progress, 0..1 across its window. */
private fun shutterT(p: Float): Float {
    val span = SHUTTER_END - GLOBE_END
    return ((p - GLOBE_END) / span).coerceIn(0f, 1f)
}

/** Wordmark act progress, 0..1 across its window. */
private fun wordT(p: Float): Float {
    val span = 1f - SHUTTER_END
    return ((p - SHUTTER_END) / span).coerceIn(0f, 1f)
}

/** Simple smoothstep ease-out. */
private fun easeOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntro(p: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val radius = (minOf(w, h) * 0.28f)

    val gAlpha = globeAlpha(p)
    if (gAlpha > 0.001f) {
        drawGlobe(cx = cx, cy = cy, radius = radius, p = p, alpha = gAlpha)
    }

    val sT = shutterT(p)
    if (sT > 0f) {
        drawShutter(cx = cx, cy = cy, radius = radius * 1.1f, t = sT)
    }
}

/**
 * The rotating dotted sphere. Latitude and longitude rings are generated in 3D,
 * rotated around the Y axis, tilted a little around X so the poles are visible,
 * then projected to 2D with a perspective divide (scale = f / (f + z)).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlobe(
    cx: Float,
    cy: Float,
    radius: Float,
    p: Float,
    alpha: Float
) {
    // Spin about 1.5 turns across the globe act.
    val spin = (p.coerceAtMost(GLOBE_END) / GLOBE_END) * (2f * PI.toFloat() * 1.5f)
    val tilt = 0.42f // radians, tilts the poles toward the viewer
    val cosTilt = cos(tilt)
    val sinTilt = sin(tilt)
    val focal = radius * 3.2f

    // A soft glow behind the sphere.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Violet.copy(alpha = 0.20f * alpha), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius * 1.9f
        ),
        radius = radius * 1.9f,
        center = Offset(cx, cy)
    )

    // Longitude rings (meridians).
    val meridians = 12
    for (m in 0 until meridians) {
        val lon = (m.toFloat() / meridians) * 2f * PI.toFloat()
        drawRing(
            cx = cx, cy = cy, radius = radius, spin = spin, lon = lon,
            cosTilt = cosTilt, sinTilt = sinTilt, focal = focal, alpha = alpha,
            isMeridian = true
        )
    }

    // Latitude rings (parallels).
    val parallels = 9
    for (i in 1 until parallels) {
        val lat = (-PI.toFloat() / 2f) + (i.toFloat() / parallels) * PI.toFloat()
        drawRing(
            cx = cx, cy = cy, radius = radius, spin = spin, lat = lat,
            cosTilt = cosTilt, sinTilt = sinTilt, focal = focal, alpha = alpha,
            isMeridian = false
        )
    }
}

/**
 * Draw one ring of dots - either a meridian (varying latitude at a fixed
 * longitude) or a parallel (varying longitude at a fixed latitude).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    cx: Float,
    cy: Float,
    radius: Float,
    spin: Float,
    cosTilt: Float,
    sinTilt: Float,
    focal: Float,
    alpha: Float,
    isMeridian: Boolean,
    lon: Float = 0f,
    lat: Float = 0f
) {
    val steps = if (isMeridian) 22 else 28
    for (s in 0..steps) {
        val f = s.toFloat() / steps
        val phi: Float
        val theta: Float
        if (isMeridian) {
            phi = (-PI.toFloat() / 2f) + f * PI.toFloat()
            theta = lon + spin
        } else {
            phi = lat
            theta = f * 2f * PI.toFloat() + spin
        }

        val x3 = radius * cos(phi) * sin(theta)
        val y3 = radius * sin(phi)
        val z3 = radius * cos(phi) * cos(theta)

        // Tilt around the X axis.
        val y2 = y3 * cosTilt - z3 * sinTilt
        val z2 = y3 * sinTilt + z3 * cosTilt

        val scale = focal / (focal + z2)
        val sx = cx + x3 * scale
        val sy = cy + y2 * scale

        // Depth shading: near dots (positive z2) are bright, far ones dim.
        val depth = ((z2 / radius) + 1f) / 2f // 0 (far) .. 1 (near)
        val dotAlpha = alpha * (0.18f + 0.82f * depth)
        val dotRadius = (1.4f + 1.9f * depth) * scale
        if (dotAlpha <= 0.02f) continue

        val colour = lerpColour(Magenta, Violet, depth)
        drawCircle(
            color = colour.copy(alpha = dotAlpha.coerceIn(0f, 1f)),
            radius = dotRadius.coerceAtLeast(0.6f),
            center = Offset(sx, sy)
        )
    }
}

/**
 * The camera shutter: a bright glow blooming through six iris blades that swing
 * open, topped by a full-screen white flash that rises and falls.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShutter(
    cx: Float,
    cy: Float,
    radius: Float,
    t: Float
) {
    val open = easeOut(t)

    // The glow seen through the opening lens.
    val glowRadius = radius * (0.25f + 0.95f * open)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f * open),
                Gold.copy(alpha = 0.55f * open),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = glowRadius
        ),
        radius = glowRadius,
        center = Offset(cx, cy)
    )

    // Six aperture blades, drawn dark over the glow.
    val blades = 6
    val step = (2f * PI.toFloat()) / blades
    val outer = radius * 1.02f
    val aperture = radius * open
    val bladeColour = Color(0xFF0E0E15)
    val bladeEdge = Color(0x33FFFFFF)

    for (i in 0 until blades) {
        val a0 = i * step
        val a1 = a0 + step
        val skew = step * 0.38f
        val path = Path().apply {
            moveTo(cx + outer * cos(a0), cy + outer * sin(a0))
            lineTo(cx + outer * cos(a1), cy + outer * sin(a1))
            lineTo(cx + aperture * cos(a1 - skew), cy + aperture * sin(a1 - skew))
            lineTo(cx + aperture * cos(a0 - skew), cy + aperture * sin(a0 - skew))
            close()
        }
        drawPath(path = path, color = bladeColour)
        drawPath(path = path, color = bladeEdge, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
    }

    // The full-screen flash: a triangle peaking halfway through the act.
    val flash = (1f - abs(2f * t - 1f)).coerceIn(0f, 1f)
    if (flash > 0.001f) {
        drawRect(color = Color.White.copy(alpha = (flash * 0.92f).coerceIn(0f, 1f)))
    }
}

/** Linear interpolation between two colours, [f] in 0..1. */
private fun lerpColour(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}
