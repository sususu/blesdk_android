package com.huawo.nt.sdkdemo.data.model

import android.net.Uri

/**
 * A local music entry from a MediaStore scan.
 * [path] is used to read the file for push; [uri] is a fallback; [selected] is list checkbox state (mutable).
 */
data class LocalMusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val path: String,
    val uri: Uri?,
    var selected: Boolean = false,
) {
    companion object {
        fun formatSize(size: Long): String =
            when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
                size < 1024L * 1024 * 1024 ->
                    String.format("%.2f MB", size / (1024.0 * 1024.0))
                else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
            }
    }
}
