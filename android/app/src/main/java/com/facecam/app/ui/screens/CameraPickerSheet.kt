package com.facecam.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.facecam.app.film.CameraGroup
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.CameraChip
import com.facecam.app.ui.components.SegmentedTabs

/**
 * Bottom sheet for choosing the active camera, split into three tabs -
 * Vintage, B&W and Beauty. Every camera is free, so nothing is ever locked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPickerSheet(
    viewModel: FaceCamViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val selected by viewModel.selectedCamera.collectAsState()
    val group by viewModel.pickerGroup.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Cameras",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            SegmentedTabs(
                options = CameraGroup.ordered.map { CameraGroup.label(it) },
                selectedIndex = CameraGroup.ordered.indexOf(group).coerceAtLeast(0),
                onSelect = { index ->
                    viewModel.setPickerGroup(
                        CameraGroup.ordered.getOrElse(index) { CameraGroup.VINTAGE }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Crossfade(
                targetState = group,
                label = "pickerGroup"
            ) { activeGroup ->
                val cameras = viewModel.filmRepository.ofGroup(activeGroup)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cameras, key = { it.id }) { preset ->
                        CameraChip(
                            name = preset.name,
                            caption = preset.tag,
                            selected = preset.id == selected?.id,
                            onClick = {
                                viewModel.selectCamera(preset)
                                onDismiss()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
