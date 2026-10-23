package com.example.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.example.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads small, downsampled album art for the song list.
 *
 * Media files are read on a background thread with limited concurrency so a long
 * list does not start hundreds of MediaMetadataRetriever instances at once.
 */
object AlbumArtLoader {

    private const val MAX_CACHE_ENTRIES = 48

    private val cache = object : LruCache<Long, Bitmap>(MAX_CACHE_ENTRIES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }
    private val semaphore = Semaphore(3)

    suspend fun load(context: Context, song: Song, targetSizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            cache.get(song.id)?.let { return@withContext it }

            semaphore.withPermit {
                cache.get(song.id)?.let { return@withPermit it }
                decode(song, context, targetSizePx)?.also { cache.put(song.id, it) }
            }
        }

    private fun decode(song: Song, context: Context, targetSizePx: Int): Bitmap? {
        val data = readArtworkBytes(song, context) ?: return null
        return decodeSampled(data, targetSizePx)
    }

    private fun readArtworkBytes(song: Song, context: Context): ByteArray? {
        val artworkUri = song.albumArtUri
        if (!artworkUri.isNullOrBlank()) {
            try {
                val bytes = context.contentResolver
                    .openInputStream(Uri.parse(artworkUri))
                    ?.use { it.readBytes() }
                if (bytes != null) return bytes
            } catch (_: Exception) {
                // Fall back to embedded artwork below.
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(song.uri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun decodeSampled(data: ByteArray, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
            bounds.outHeight / (sampleSize * 2) >= targetSizePx
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            // Thumbnails do not need alpha; RGB_565 halves the memory footprint.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }
}
