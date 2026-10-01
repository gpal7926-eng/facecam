package com.facecam.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.facecam.app.gallery.GalleryPhoto
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.AdBanner
import com.facecam.app.ui.components.EmptyState
import com.facecam.app.ui.components.SectionTitle

/**
 * In-app gallery, grouped by camera. Tapping a photo opens a full-screen view
 * with save-to-device, delete and share actions.
 */
@Composable
fun GalleryScreen(
    viewModel: FaceCamViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val photos by viewModel.gallery.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    var fullScreen by remember { mutableStateOf<GalleryPhoto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Gallery",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (photos.isEmpty()) {
            EmptyState(text = "No photos yet. Shoot something on film!")
        } else {
            val grouped = photos.groupBy { it.cameraId }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                grouped.forEach { (cameraId, list) ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SectionTitle(
                            text = viewModel.filmRepository.get(cameraId)?.name ?: cameraId,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(list, key = { it.id }) { photo ->
                        AsyncImage(
                            model = photo.file,
                            contentDescription = photo.id,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { fullScreen = photo }
                        )
                    }
                }
            }
        }

        AdBanner(showAds = !isPro)
    }

    fullScreen?.let { photo ->
        AlertDialog(
            onDismissRequest = { fullScreen = null },
            confirmButton = {},
            title = {
                Text(
                    text = photo.cameraName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    AsyncImage(
                        model = photo.file,
                        contentDescription = photo.id,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = {
                            if (activity != null) viewModel.saveToDevice(activity, photo)
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = "Save to device")
                        }
                        IconButton(onClick = { viewModel.sharePhoto(photo) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            viewModel.deletePhoto(photo)
                            fullScreen = null
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFD96A5A)
                            )
                        }
                    }
                }
            }
        )
    }
}
