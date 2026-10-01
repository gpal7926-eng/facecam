package com.facecam.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings: date stamp, border, shutter sound, default camera, restore
 * purchases and the privacy policy link.
 */
@Composable
fun SettingsScreen(
    viewModel: FaceCamViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var dateStamp by remember { mutableStateOf(viewModel.settingsStore.dateStamp) }
    var border by remember { mutableStateOf(viewModel.settingsStore.border) }
    var shutterSound by remember { mutableStateOf(viewModel.settingsStore.shutterSound) }
    val selected by viewModel.selectedCamera.collectAsState()
    var defaultCameraId by remember {
        mutableStateOf(viewModel.settingsStore.defaultCameraId ?: selected?.id)
    }
    var menuOpen by remember { mutableStateOf(false) }

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
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(8.dp))

        SettingSwitch(
            title = "Date stamp",
            subtitle = "Burn the date into the photo (like old film cameras)",
            checked = dateStamp
        ) {
            dateStamp = it
            viewModel.settingsStore.dateStamp = it
        }

        SettingSwitch(
            title = "Border",
            subtitle = "Draw the camera's film frame around each photo",
            checked = border
        ) {
            border = it
            viewModel.settingsStore.border = it
        }

        SettingSwitch(
            title = "Shutter sound",
            subtitle = "Play a shutter click when you take a photo",
            checked = shutterSound
        ) {
            shutterSound = it
            viewModel.settingsStore.shutterSound = it
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Default camera picker.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Default camera",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        TextButton(
            onClick = { viewModel.restorePurchases() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restore purchases")
        }

        TextButton(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://example.com/facecam/privacy")
                )
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Privacy policy")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "FaceCam v1.0.0 - all data stays on your device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
