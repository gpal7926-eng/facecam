package com.facecam.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.facecam.app.film.CameraGroup
import com.facecam.app.film.FilmPreset
import com.facecam.app.gallery.GalleryPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The NOMO-CAM-style camera strip that sits across the bottom of the viewfinder:
 * a prominent horizontal row of camera models, each with a small, realistic
 * looking thumbnail of the look and the camera name underneath. The active
 * camera is highlighted with a bright ring and a slight lift.
 *
 * Thumbnails are drawn procedurally with Compose [Canvas] - no texture assets
 * and no network. The little scene is tinted by running a neutral reference
 * colour through the camera's own 20-float colour matrix, so each thumbnail
 * genuinely previews its camera's grade (monochrome cameras read grey, the 70s
 * camera reads orange, and so on).
 */
@Composable
fun CameraStrip(
    cameras: List<FilmPreset>,
    selectedId: String?,
    onSelect: (FilmPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cameras, key = { it.id }) { preset ->
            CameraStripItem(
                preset = preset,
                selected = preset.id == selectedId,
                onClick = { onSelect(preset) }
            )
        }
    }
}

/** A single camera model in the strip: thumbnail on top, name underneath. */
@Composable
private fun CameraStripItem(
    preset: FilmPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ring by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(180),
        label = "stripRing"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.9f,
        animationSpec = tween(180),
        label = "stripScale"
    )
    val nameColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
        animationSpec = tween(180),
        label = "stripName"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .scale(scale)
            .clickable(onClick = onClick)
    ) {
        PresetThumbnail(
            preset = preset,
            selected = selected,
            modifier = Modifier
                .size(width = 66.dp, height = 66.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = ring,
                    shape = RoundedCornerShape(16.dp)
                )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = nameColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A small, realistic-looking preview of a camera's look. The base scene is a
 * simple sun-over-landscape; the camera's colour matrix then tints the whole
 * thing, and analog cameras (vintage / B&W) additionally get grain and a
 * vignette so the thumbnail reads like a tiny film photo.
 */
@Composable
fun PresetThumbnail(
    preset: FilmPreset,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val look = remember(preset.id) { presetLookColor(preset) }
    val skyTop = remember(preset.id) { lighten(look, 0.45f) }
    val skyBottom = remember(preset.id) { darken(look, 0.10f) }
    val ground = remember(preset.id) { darken(look, 0.45f) }
    val sun = remember(preset.id) { lighten(look, 0.80f) }
    val analog = preset.isAnalog

    Canvas(modifier = modifier.background(skyBottom)) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f

        // Sky.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(skyTop, skyBottom),
                startY = 0f,
                endY = horizon
            ),
            size = androidx.compose.ui.geometry.Size(w, horizon)
        )

        // Sun.
        drawCircle(
            color = sun.copy(alpha = 0.9f),
            radius = w * 0.16f,
            center = Offset(w * 0.68f, h * 0.32f)
        )

        // Ground.
        drawRect(
            color = ground,
            topLeft = Offset(0f, horizon),
            size = androidx.compose.ui.geometry.Size(w, h - horizon)
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(lighten(look, 0.05f), darken(look, 0.35f)),
                startY = horizon,
                endY = h
            ),
            topLeft = Offset(0f, horizon),
            size = androidx.compose.ui.geometry.Size(w, h - horizon)
        )

        // A uniting wash of the camera's own grade.
        drawRect(color = look.copy(alpha = 0.22f))

        if (analog) {
            // Light leak hint along one edge.
            if (preset.leak > 0.01f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFB45A).copy(alpha = (preset.leak * 0.7f).coerceIn(0f, 0.7f)),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = w * 0.75f
                    )
                )
            }
            // Grain: a light deterministic speckle.
            drawGrain(preset.id, preset.grain, w, h)
            // Vignette.
            if (preset.vignette > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = (preset.vignette * 0.75f).coerceIn(0f, 0.85f))
                        ),
                        center = Offset(w / 2f, h / 2f),
                        radius = max(w, h) * 0.72f
                    ),
                    radius = max(w, h) * 0.72f,
                    center = Offset(w / 2f, h / 2f)
                )
            }
        }
    }
}

/** A light, deterministic speckle pattern for the analog thumbnails. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrain(
    seedId: String,
    grain: Float,
    w: Float,
    h: Float
) {
    if (grain <= 0.01f) return
    var seed = seedId.hashCode().toLong()
    fun next(): Float {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return (((seed ushr 33).toInt() and 0x7FFFFFFF) % 1000) / 1000f
    }
    val count = (grain.coerceIn(0f, 1f) * 120).toInt()
    for (i in 0 until count) {
        val x = next() * w
        val y = next() * h
        val bright = next() > 0.5f
        drawCircle(
            color = if (bright) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.12f),
            radius = 0.6f + next() * 1.1f,
            center = Offset(x, y)
        )
    }
}

/**
 * The last-shot thumbnail that lives in the bottom-left corner of the
 * viewfinder. It loads the most recent gallery image off the main thread and
 * shows a neutral placeholder when there is nothing yet.
 */
@Composable
fun LastShotThumbnail(
    photo: GalleryPhoto?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(photo?.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo?.id) {
        val target = photo
        bitmap = if (target == null || target.isVideo) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(target.file.absolutePath)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val img = bitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = "Last shot",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp)
            )
        } else {
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Colour helpers
// ---------------------------------------------------------------------------

/**
 * The representative colour a camera's matrix produces: a neutral warm
 * reference colour is pushed through the preset's 4x5 matrix and clamped. This
 * is what gives each thumbnail its characteristic tint (grey for B&W, warm for
 * the 70s camera, cool for the 60s, and so on).
 */
private fun presetLookColor(preset: FilmPreset): Color {
    val m = preset.matrix
    val r = 190f
    val g = 175f
    val b = 150f
    val nr = if (m.size > 4) m[0] * r + m[1] * g + m[2] * b + m[4] else r
    val ng = if (m.size > 9) m[5] * r + m[6] * g + m[7] * b + m[9] else g
    val nb = if (m.size > 14) m[10] * r + m[11] * g + m[12] * b + m[14] else b
    val fallback = when (preset.group) {
        CameraGroup.BW -> Color(0xFF9A9DA6)
        CameraGroup.BEAUTY -> Color(0xFFE8C9A8)
        else -> Color(0xFFD8B48A)
    }
    if (nr <= 1f && ng <= 1f && nb <= 1f) return fallback
    return Color(
        red = (nr / 255f).coerceIn(0f, 1f),
        green = (ng / 255f).coerceIn(0f, 1f),
        blue = (nb / 255f).coerceIn(0f, 1f)
    )
}

private fun lighten(c: Color, amount: Float): Color = Color(
    red = (c.red + (1f - c.red) * amount).coerceIn(0f, 1f),
    green = (c.green + (1f - c.green) * amount).coerceIn(0f, 1f),
    blue = (c.blue + (1f - c.blue) * amount).coerceIn(0f, 1f),
    alpha = 1f
)

private fun darken(c: Color, amount: Float): Color = Color(
    red = (c.red * (1f - amount)).coerceIn(0f, 1f),
    green = (c.green * (1f - amount)).coerceIn(0f, 1f),
    blue = (c.blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = 1f
)
