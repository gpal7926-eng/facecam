package com.facecam.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.facecam.app.gallery.GalleryPhoto
import com.facecam.app.ui.FaceCamViewModel
import com.facecam.app.ui.components.EmptyState
import com.facecam.app.ui.components.FaceCamBottomBar
import com.facecam.app.ui.components.HomeTab
import com.facecam.app.ui.components.SectionTitle
import com.facecam.app.ui.components.glass
import com.facecam.app.ui.theme.Violet

/**
 * In-app gallery, grouped by camera. Tapping a photo opens a full-screen view
 * with save-to-device, delete and share actions; video clips get a play badge
 * and open in the device player.
 */
@Composable
fun GalleryScreen(
    viewModel: FaceCamViewModel,
    onBack: () -> Unit,
    onNavigate: (HomeTab) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val photos by viewModel.gallery.collectAsState()
    var fullScreen by remember { mutableStateOf<GalleryPhoto?>(null) }

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
                    text = "Gallery",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            if (photos.isEmpty()) {
                EmptyState(text = "No photos yet. Shoot something on film!")
            } else {
                val grouped = photos.groupBy { it.cameraId }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { (cameraId, list) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitle(
                                text = viewModel.filmRepository.get(cameraId)?.name ?: cameraId,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(list, key = { it.id }) { photo ->
                            GalleryTile(
                                photo = photo,
                                onClick = { fullScreen = photo }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        FaceCamBottomBar(
            current = HomeTab.GALLERY,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
                    if (photo.isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1D1D27), Color(0xFF2A2140))
                                    )
                                )
                                .clickable { openVideo(context, photo) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = photo.file,
                            contentDescription = photo.id,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(360.dp)
                        )
                    }
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

@Composable
private fun GalleryTile(photo: GalleryPhoto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (photo.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF1D1D27), Color(0xFF2A2140)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        } else {
            AsyncImage(
                model = photo.file,
                contentDescription = photo.id,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (photo.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Violet)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "MP4",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Open a saved clip in the device's video player via the FileProvider. */
private fun openVideo(context: android.content.Context, photo: GalleryPhoto) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photo.file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        // No player available - ignore.
    }
}
