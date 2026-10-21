package com.example.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class AlbumArtManager(private val context: Context) {

    fun getAlbumArtBitmap(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val embedded = retriever.embeddedPicture
            if (embedded != null) {
                BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun cacheBitmap(fileName: String, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "album_art")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
