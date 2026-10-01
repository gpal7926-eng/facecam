package com.facecam.app.gallery

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes a developed photo into the device's shared image collection via
 * MediaStore. Uses the version-appropriate collection:
 *  - API 29+ : MediaStore.Images with RELATIVE_PATH (scoped storage).
 *  - API 24-28: MediaStore.Images with DATA + WRITE_EXTERNAL_STORAGE.
 */
object MediaStoreSaver {

    suspend fun save(context: Context, photo: GalleryPhoto): Uri? =
        withContext(Dispatchers.IO) {
            if (photo.isVideo) return@withContext saveVideo(context, photo)
            try {
                val name = photo.file.name
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/FaceCam"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collection, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    photo.file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } catch (t: Throwable) {
                Log.e(TAG, "MediaStore save failed", t)
                null
            }
        }

    private const val TAG = "MediaStoreSaver"

    /**
     * Publish a video clip into the shared video collection via MediaStore.
     * Uses the version-appropriate collection (scoped storage on API 29+).
     */
    suspend fun saveVideo(context: Context, photo: GalleryPhoto): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val name = photo.file.name
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_MOVIES + "/FaceCam"
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collection, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    photo.file.inputStream().use { input -> input.copyTo(out) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } catch (t: Throwable) {
                Log.e(TAG, "MediaStore video save failed", t)
                null
            }
        }
}
