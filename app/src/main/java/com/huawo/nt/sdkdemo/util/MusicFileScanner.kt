package com.huawo.nt.sdkdemo.util

import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import com.huawo.nt.sdkdemo.data.model.LocalMusicFile

/**
 * Scans the local music library (aligned with HUAWO-TOOL MusicFileScanner).
 *
 * - Data source: MediaStore.Audio.Media
 * - mp3 (audio/mpeg) and wav (audio/wav) only
 * - [LocalMusicFile.path] comes from the DATA column; verify file exists before push
 *
 * Requires READ_MEDIA_AUDIO (API 33+) or READ_EXTERNAL_STORAGE before calling.
 */
object MusicFileScanner {
    fun scan(context: Context): List<LocalMusicFile> {
        val result = mutableListOf<LocalMusicFile>()
        val projection =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATA,
            )
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf("audio/mpeg", "audio/wav")
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result.add(
                    LocalMusicFile(
                        id = id,
                        title = cursor.getString(titleCol).orEmpty().ifBlank { "Unknown" },
                        artist = cursor.getString(artistCol).orEmpty(),
                        album = cursor.getString(albumCol).orEmpty(),
                        durationMs = cursor.getLong(durationCol),
                        size = cursor.getLong(sizeCol),
                        path = cursor.getString(dataCol).orEmpty(),
                        // content:// URI; fallback for list display or SAF scenarios; push currently uses path
                        uri = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id".toUri(),
                    ),
                )
            }
        }
        return result
    }
}
