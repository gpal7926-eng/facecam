package com.facecam.app.gallery

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.facecam.app.film.FilmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores developed photos in the app's private files directory, grouped by
 * camera. The in-app gallery reads from here; publishing to the device's shared
 * MediaStore is a separate, explicit action ([MediaStoreSaver]).
 */
class GalleryRepository(private val context: Context) {

    private val metaCache = ConcurrentHashMap<String, CameraMeta>()

    private val rootDir: File
        get() = File(context.filesDir, "gallery").apply { if (!exists()) mkdirs() }

    data class CameraMeta(val cameraId: String, val cameraName: String)

    /** Persist a developed bitmap and return the resulting [GalleryPhoto]. */
    suspend fun save(
        bitmap: Bitmap,
        cameraId: String,
        cameraName: String
    ): GalleryPhoto = withContext(Dispatchers.IO) {
        val dir = File(rootDir, cameraId).apply { if (!exists()) mkdirs() }
        val timestamp = System.currentTimeMillis()
        val id = "${cameraId}_$timestamp.jpg"
        val file = File(dir, id)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        metaCache[id] = CameraMeta(cameraId, cameraName)
        GalleryPhoto(
            id = id,
            file = file,
            cameraId = cameraId,
            cameraName = cameraName,
            timestamp = timestamp
        )
    }

    /** All photos, newest first. */
    suspend fun all(): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GalleryPhoto>()
        val base = rootDir
        base.listFiles()?.forEach { cameraDir ->
            if (!cameraDir.isDirectory) return@forEach
            val cameraId = cameraDir.name
            cameraDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".jpg")) {
                    val meta = metaCache[f.name]
                    result.add(
                        GalleryPhoto(
                            id = f.name,
                            file = f,
                            cameraId = meta?.cameraId ?: cameraId,
                            cameraName = meta?.cameraName ?: cameraId,
                            timestamp = f.lastModified()
                        )
                    )
                }
            }
        }
        result.sortedByDescending { it.timestamp }
    }

    /** Photos grouped by camera id, each group newest first. */
    suspend fun grouped(): Map<String, List<GalleryPhoto>> =
        all().groupBy { it.cameraId }

    suspend fun delete(photo: GalleryPhoto): Boolean = withContext(Dispatchers.IO) {
        try {
            photo.file.delete()
        } catch (t: Throwable) {
            Log.w(TAG, "delete failed", t)
            false
        }
    }

    /** Resolve a display name for a camera id, falling back to the id. */
    fun displayName(cameraId: String, filmRepository: FilmRepository): String =
        filmRepository.get(cameraId)?.name ?: cameraId

    companion object {
        private const val TAG = "GalleryRepository"
    }
}
