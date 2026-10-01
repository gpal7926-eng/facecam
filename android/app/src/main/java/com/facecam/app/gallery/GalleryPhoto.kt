package com.facecam.app.gallery

import android.net.Uri
import java.io.File

/**
 * One saved photo in the in-app gallery.
 *
 * @param id stable identifier (file name).
 * @param file the on-disk JPEG in the app's private gallery folder.
 * @param cameraId the film camera used, used to group the gallery.
 * @param cameraName display name of the camera.
 * @param timestamp epoch millis the photo was created.
 * @param uri the MediaStore uri once the photo has been published to the device.
 */
data class GalleryPhoto(
    val id: String,
    val file: File,
    val cameraId: String,
    val cameraName: String,
    val timestamp: Long,
    val uri: Uri? = null
) {
    val path: String get() = file.absolutePath
}
