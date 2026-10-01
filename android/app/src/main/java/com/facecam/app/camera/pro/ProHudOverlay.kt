package com.facecam.app.camera.pro

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * The manual shooting HUD drawn over the live preview (Blackmagic-Camera style):
 * rule-of-thirds grid, horizontal level, a live luminance histogram and the
 * focus-peaking / zebra / false-colour overlays.
 *
 * The manual-control panel lives in [ProControlsPanel] so the simple mode never
 * pays for it.
 */
@Composable
fun ProHudOverlay(
    state: ProState,
    histogram: IntArray,
    roll: Float,
    overlay: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Live overlay (peaking / zebra / false colour) rendered from frames.
        if (overlay != null && !overlay.isRecycled) {
            Image(
                bitmap = overlay.asImageBitmap(),
                contentDescription = "Manual preview overlay",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Rule-of-thirds grid + level indicator.
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (state.showGrid) {
                val lineColor = Color(0x66FFFFFF)
                val stroke = 1.5f
                val thirdW = size.width / 3f
                val thirdH = size.height / 3f
                drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), stroke)
                drawLine(lineColor, Offset(thirdW * 2, 0f), Offset(thirdW * 2, size.height), stroke)
                drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), stroke)
                drawLine(lineColor, Offset(0f, thirdH * 2), Offset(size.width, thirdH * 2), stroke)
            }

            if (state.showLevel) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val halfLen = size.width * 0.22f
                val level = abs(roll) < 1.2f
                val color = if (level) Color(0xFF39FF6A) else Color(0xCCFFFFFF)
                val rad = Math.toRadians(roll.toDouble())
                val dx = (halfLen * Math.cos(rad)).toFloat()
                val dy = (halfLen * Math.sin(rad)).toFloat()
                drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), 2.5f)
                drawLine(color, Offset(cx - 8f, cy), Offset(cx + 8f, cy), 2.5f)
            }
        }

        // Live histogram, top-right.
        if (state.showHistogram && histogram.isNotEmpty()) {
            HistogramView(
                histogram = histogram,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 120.dp, height = 56.dp)
            )
        }

        // Pro readout strip (ISO / shutter / WB / focus).
        ProReadoutStrip(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 132.dp)
        )
    }
}

@Composable
private fun HistogramView(histogram: IntArray, modifier: Modifier = Modifier) {
    val normalised = HistogramAnalyzer.normalized(histogram)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x99000000))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            if (normalised.isEmpty()) return@Canvas
            val barW = size.width / normalised.size
            for (i in normalised.indices) {
                val barH = normalised[i] * size.height
                drawRect(
                    color = Color(0xFFE8A33D),
                    topLeft = Offset(i * barW, size.height - barH),
                    size = androidx.compose.ui.geometry.Size(barW.coerceAtLeast(1f), barH)
                )
            }
        }
    }
}

@Composable
private fun ProReadoutStrip(state: ProState, modifier: Modifier = Modifier) {
    val mono = FontFamily.Monospace
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ReadoutRow("ISO", if (state.manualIso) state.iso.toString() else "AUTO", mono)
        ReadoutRow(
            "SHUTTER",
            if (state.manualShutter) "${state.exposureLabel()}  ${state.shutterAngle()}deg" else "AUTO",
            mono
        )
        ReadoutRow("WB", if (state.manualWhiteBalance) "${state.whiteBalanceK}K" else "AUTO", mono)
        ReadoutRow("FOCUS", if (state.manualFocus) state.focusLabel() else "AF", mono)
    }
}

@Composable
private fun ReadoutRow(label: String, value: String, family: FontFamily) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color(0xFF8A8578),
            fontSize = 10.sp,
            fontFamily = family,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            color = Color(0xFFE8A33D),
            fontSize = 12.sp,
            fontFamily = family,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The manual control panel: overlay toggles plus manual ISO / shutter / WB / focus
 * sliders. Any control the device does not support is greyed out.
 */
@Composable
fun ProControlsPanel(
    state: ProState,
    capabilities: ProCapabilities,
    onUpdate: ((ProState) -> ProState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Color(0xCC0A0A0A))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Overlay toggles.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProToggle("Peaking", state.focusPeaking) { v -> onUpdate { it.copy(focusPeaking = v) } }
            ProToggle("Zebra", state.zebra) { v -> onUpdate { it.copy(zebra = v) } }
            ProToggle("False", state.falseColor) { v -> onUpdate { it.copy(falseColor = v) } }
            ProToggle("Grid", state.showGrid) { v -> onUpdate { it.copy(showGrid = v) } }
        }

        ProSlider(
            label = "ISO",
            valueText = if (state.manualIso) state.iso.toString() else "AUTO",
            enabled = capabilities.supportsManualIso,
            manual = state.manualIso,
            onManualChange = { v -> onUpdate { it.copy(manualIso = v) } },
            value = state.iso.toFloat(),
            range = capabilities.isoMin.toFloat()..capabilities.isoMax.toFloat(),
            onValueChange = { v -> onUpdate { it.copy(iso = v.toInt()) } }
        )

        ProSlider(
            label = "Shutter",
            valueText = if (state.manualShutter) "${state.exposureLabel()} (${state.shutterAngle()}deg)" else "AUTO",
            enabled = capabilities.supportsManualShutter,
            manual = state.manualShutter,
            onManualChange = { v -> onUpdate { it.copy(manualShutter = v) } },
            value = state.exposureNanos.toFloat(),
            range = capabilities.exposureMinNanos.toFloat()..capabilities.exposureMaxNanos.toFloat(),
            onValueChange = { v -> onUpdate { it.copy(exposureNanos = v.toLong()) } }
        )

        ProSlider(
            label = "WB",
            valueText = if (state.manualWhiteBalance) "${state.whiteBalanceK}K" else "AUTO",
            enabled = capabilities.supportsManualWhiteBalance,
            manual = state.manualWhiteBalance,
            onManualChange = { v -> onUpdate { it.copy(manualWhiteBalance = v) } },
            value = state.whiteBalanceK.toFloat(),
            range = 2000f..10000f,
            onValueChange = { v -> onUpdate { it.copy(whiteBalanceK = v.toInt()) } }
        )

        ProSlider(
            label = "Focus",
            valueText = if (state.manualFocus) state.focusLabel() else "AF",
            enabled = capabilities.supportsManualFocus,
            manual = state.manualFocus,
            onManualChange = { v -> onUpdate { it.copy(manualFocus = v) } },
            value = state.focusDistance,
            range = 0f..capabilities.minFocusDistanceDiopters.coerceAtLeast(0.001f),
            onValueChange = { v -> onUpdate { it.copy(focusDistance = v) } }
        )
    }
}

@Composable
private fun ProToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White, fontSize = 10.sp)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProSlider(
    label: String,
    valueText: String,
    enabled: Boolean,
    manual: Boolean,
    onManualChange: (Boolean) -> Unit,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF8A8578).copy(alpha = alpha),
            fontSize = 11.sp,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = valueText,
            color = Color(0xFFE8A33D).copy(alpha = alpha),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(96.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled && manual,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = manual && enabled,
            onCheckedChange = { if (enabled) onManualChange(it) },
            enabled = enabled,
            modifier = Modifier.padding(start = 8.dp).height(24.dp)
        )
    }
}
