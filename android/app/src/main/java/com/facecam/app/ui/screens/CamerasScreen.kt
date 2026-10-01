package com.facecam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facecam.app.film.CameraGroup
import com.facecam.app.film.FilmPreset
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.FaceCamBottomBar
import com.facecam.app.ui.components.HomeTab
import com.facecam.app.ui.components.ModeChip
import com.facecam.app.ui.components.glass
import com.facecam.app.ui.theme.FaceCamGradient
import com.facecam.app.ui.theme.Magenta
import com.facecam.app.ui.theme.Violet

/**
 * The Modes screen: every camera - free for everyone - grouped into the Vintage
 * film, B&W and Beauty families, shown as modern glass cards. Tapping a card
 * makes it the active camera.
 */
@Composable
fun CamerasScreen(
    viewModel: FaceCamViewModel,
    onBack: () -> Unit,
    onNavigate: (HomeTab) -> Unit = {}
) {
    val selected by viewModel.selectedCamera.collectAsState()
    val families = viewModel.filmRepository.byFamily()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Modes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                families.forEach { (group, cameras) ->
                    if (cameras.isEmpty()) return@forEach
                    item(key = "header_$group") {
                        Text(
                            text = familyTitle(group),
                            style = MaterialTheme.typography.titleMedium,
                            color = familyAccentColor(group),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
                        )
                    }
                    items(cameras, key = { it.id }) { preset ->
                        CameraCard(
                            preset = preset,
                            selected = preset.id == selected?.id,
                            accent = familyAccent(group),
                            onUse = { viewModel.selectCamera(preset) }
                        )
                    }
                }

                item { Spacer(Modifier.height(104.dp)) }
            }
        }

        FaceCamBottomBar(
            current = HomeTab.MODES,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CameraCard(
    preset: FilmPreset,
    selected: Boolean,
    accent: Brush,
    onUse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(22.dp), alpha = if (selected) 0.20f else 0.10f)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .border(2.dp, accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.name.take(2),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (preset.tag != null) {
                    Text(
                        text = preset.tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            ModeChip(
                label = if (selected) "In use" else "Use",
                selected = selected,
                accent = accent,
                onClick = { if (!selected) onUse() }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = preset.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Section header label for a camera family. */
private fun familyTitle(group: String): String = when (group) {
    CameraGroup.VINTAGE -> "Vintage film"
    CameraGroup.BW -> "Black & white"
    CameraGroup.BEAUTY -> "Beauty"
    else -> CameraGroup.label(group)
}

/** Accent gradient used on a family's cards and chips. */
private fun familyAccent(group: String): Brush = when (group) {
    CameraGroup.VINTAGE -> FaceCamGradient
    CameraGroup.BW -> Brush.linearGradient(listOf(Color(0xFFB9BCC6), Color(0xFF6E7280)))
    CameraGroup.BEAUTY -> Brush.linearGradient(listOf(Magenta, Violet))
    else -> FaceCamGradient
}

/** Solid accent colour used on a family's section header. */
private fun familyAccentColor(group: String): Color = when (group) {
    CameraGroup.VINTAGE -> Violet
    CameraGroup.BW -> Color(0xFFB9BCC6)
    CameraGroup.BEAUTY -> Magenta
    else -> Violet
}
