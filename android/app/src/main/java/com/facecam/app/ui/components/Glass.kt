package com.facecam.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facecam.app.ui.theme.FaceCamGradient
import com.facecam.app.ui.theme.Magenta
import com.facecam.app.ui.theme.Violet

/**
 * Glassmorphic helpers and the large rounded chips that give FaceCam its modern,
 * social-camera feel.
 */

/** A translucent, rounded, hairline-bordered "glass" surface. */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Color.White,
    alpha: Float = 0.12f,
    borderAlpha: Float = 0.22f
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = alpha))
    .border(1.dp, tint.copy(alpha = borderAlpha), shape)

/** A larger frosted card built on [glass]. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.glass(shape = shape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * A large, rounded mode chip (Photo / Video, Vintage / Beauty). The selected
 * chip fills with the signature gradient and scales up slightly.
 */
@Composable
fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Brush = FaceCamGradient
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = tween(180),
        label = "modeChipScale"
    )
    val bg: Brush = if (selected) {
        accent
    } else {
        Brush.linearGradient(listOf(Color(0x22FFFFFF), Color(0x11FFFFFF)))
    }
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(180),
        label = "modeChipFg"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f),
                RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** A compact glass pill used for small readouts (speed, fps hint). */
@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .clip(RoundedCornerShape(50))
        .background(Color(0x33FFFFFF))
        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = clickable.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** A small red "REC" badge with a pulsing dot. */
@Composable
fun RecBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x66FF3B5C))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B5C))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "REC",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Accent colours exposed for callers that need a solid tint. */
val AccentViolet: Color = Violet
val AccentMagenta: Color = Magenta
