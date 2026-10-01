package com.facecam.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.glass
import com.facecam.app.ui.theme.Violet

/**
 * Settings, refreshed with the modern glass look: film-look toggles, capture
 * options and the default camera. The date stamp, border and watermark only
 * affect vintage film cameras; Beauty cameras always render clean.
 */
@Composable
fun SettingsScreen(
    viewModel: FaceCamViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var dateStamp by remember { mutableStateOf(viewModel.settingsStore.dateStamp) }
    var border by remember { mutableStateOf(viewModel.settingsStore.border) }
    var branding by remember { mutableStateOf(viewModel.settingsStore.branding) }
    var shutterSound by remember { mutableStateOf(viewModel.settingsStore.shutterSound) }
    val selected by viewModel.selectedCamera.collectAsState()
    var defaultCameraId by remember {
        mutableStateOf(viewModel.settingsStore.defaultCameraId ?: selected?.id)
    }
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingsGroup(title = "Film look") {
                SettingSwitch(
                    title = "Date stamp",
                    subtitle = "Burn the date into vintage film photos",
                    checked = dateStamp
                ) {
                    dateStamp = it
                    viewModel.settingsStore.dateStamp = it
                }
                SettingSwitch(
                    title = "Border",
                    subtitle = "Draw the film frame around each vintage photo",
                    checked = border
                ) {
                    border = it
                    viewModel.settingsStore.border = it
                }
                SettingSwitch(
                    title = "FaceCam watermark",
                    subtitle = "Add the FaceCam caption band under each vintage photo",
                    checked = branding
                ) {
                    branding = it
                    viewModel.settingsStore.branding = it
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Capture") {
                SettingSwitch(
                    title = "Shutter sound",
                    subtitle = "Play a shutter click when you take a photo",
                    checked = shutterSound
                ) {
                    shutterSound = it
                    viewModel.settingsStore.shutterSound = it
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Default camera",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "The camera FaceCam opens with",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Text(viewModel.filmRepository.get(defaultCameraId ?: "")?.name ?: "Choose")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            viewModel.filmRepository.all().forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        defaultCameraId = preset.id
                                        viewModel.settingsStore.defaultCameraId = preset.id
                                        viewModel.selectCamera(preset)
                                        menuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingsGroup(title = "Offline & privacy") {
                Text(
                    text = "FaceCam works fully offline. No accounts, no ads, no analytics - " +
                        "photos, videos and captions never leave your device. Live captions " +
                        "use Android's on-device speech recognition.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://example.com/facecam/privacy")
                        )
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Text("Privacy policy")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "FaceCam v3.0.0 - free, offline, all data stays on your device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A titled glass group wrapping a set of settings rows. */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Violet,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(24.dp), alpha = 0.10f)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet
            )
        )
    }
}
