package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.AlbumPhotoItem
import com.huawo.nt.sdkdemo.data.model.AlbumTransferCallback
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for the album push screen.
 *
 * @param status Top status line (querying / selected / progress, etc.)
 * @param result Bottom result area (storage info, progress, success/failure details)
 * @param busy   true while querying, copying, or transferring; UI should disable most actions
 * @param photos Prepared photos pending push (watch album index + local cache file)
 */
data class AlbumTransferUiState(
    val status: String = "",
    val result: String = "",
    val busy: Boolean = false,
    val photos: List<AlbumPhotoItem> = emptyList(),
)

/**
 * Album push business logic.
 *
 * Flow:
 * pick Uri → copy to cache → allocate free watch album indices → preview list
 * → call [BleRepository.pushAlbumFiles] with user width/height
 *   (Repository converts to ezip and chooses SPP or Sifli)
 */
class AlbumTransferViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState =
        MutableStateFlow(AlbumTransferUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<AlbumTransferUiState> = _uiState.asStateFlow()

    /** Active transfer channel name ("SPP" / "Sifli") for progress copy. */
    private var transferChannel: String = ""

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    /** Query watch album free KB and existing file ID list. */
    fun queryStorage() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = str(R.string.album_querying_storage)) }
            try {
                val storage = repository.getAlbumIdleStorage()
                // File-ID list failure must not block showing free space
                val ids = runCatching { repository.getAlbumFileIdList() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = str(R.string.feature_success),
                        result = str(
                            R.string.album_storage_result,
                            storage.availableKb,
                            ids.size,
                            ids.joinToString(",").ifEmpty { "-" },
                        ),
                    )
                }
            } catch (e: Exception) {
                fail(e.message.orEmpty())
            }
        }
    }

    /**
     * Handle multi-image Uris returned by the system picker.
     *
     * - More than [MAX_PHOTOS] → keep the first N and show a capped message
     * - Copy Uris on IO to `cacheDir/album_push`; preview and convert read local files
     * - Allocate watch slots via [BleRepository.allocateAlbumIndices] (free indices in 1..50)
     * - Each pick replaces the current list (not append)
     */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = str(R.string.album_preparing_files)) }
            try {
                val limited = uris.take(MAX_PHOTOS)
                val truncated = uris.size > MAX_PHOTOS
                val files =
                    withContext(Dispatchers.IO) {
                        limited.mapNotNull { uri -> copyUriToCache(getApplication(), uri) }
                    }
                if (files.isEmpty()) {
                    fail(str(R.string.album_no_files))
                    return@launch
                }
                // alreadySelected empty: this pick replaces the whole list
                val indices = repository.allocateAlbumIndices(files.size, emptyList())
                if (indices.isEmpty()) {
                    fail(str(R.string.album_no_slots))
                    return@launch
                }
                // If fewer free slots than files, keep only what was allocated
                val photos =
                    files.take(indices.size).mapIndexed { i, file ->
                        AlbumPhotoItem(indices[i], file)
                    }
                val status =
                    if (truncated) {
                        str(R.string.album_files_selected_capped, photos.size, MAX_PHOTOS)
                    } else {
                        str(R.string.album_files_selected, photos.size)
                    }
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = status,
                        photos = photos,
                        result = "",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(photos = emptyList()) }
                fail(e.message.orEmpty())
            }
        }
    }

    /** Remove one photo from the preview list; blocked while transferring. */
    fun removePhoto(item: AlbumPhotoItem) {
        if (_uiState.value.busy) return
        _uiState.update { state ->
            val next = state.photos.filterNot {
                it.index == item.index && it.file.absolutePath == item.file.absolutePath
            }
            state.copy(
                photos = next,
                status =
                    if (next.isEmpty()) {
                        str(R.string.feature_ready)
                    } else {
                        str(R.string.album_files_selected, next.size)
                    },
            )
        }
    }

    /**
     * Push currently selected photos to the watch.
     *
     * @param width  Watch screen width in px, clamped to 1..2000
     * @param height Watch screen height in px, clamped to 1..2000
     *
     * Convert and channel selection (classic BT SPP / Sifli ZIP type=3) are done in
     * the Repository; this method only validates and drives UI from callbacks.
     */
    fun pushSelected(width: Int, height: Int) {
        if (_uiState.value.busy) return
        val photos = _uiState.value.photos
        if (photos.isEmpty()) {
            _uiState.update { it.copy(status = str(R.string.album_no_files)) }
            return
        }
        val w = width.coerceIn(1, 2000)
        val h = height.coerceIn(1, 2000)
        if (!repository.isConnected()) {
            _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
            return
        }
        _uiState.update {
            it.copy(
                busy = true,
                status = str(R.string.album_pushing),
                result = str(R.string.album_size_hint, w, h),
            )
        }
        transferChannel = ""
        repository.pushAlbumFiles(
            photos,
            object : AlbumTransferCallback {
                override fun onChannel(channel: String) {
                    transferChannel = channel
                    _uiState.update {
                        it.copy(status = str(R.string.album_pushing_channel, channel))
                    }
                }

                override fun onReady() {
                    _uiState.update {
                        it.copy(status = str(R.string.album_ready, transferChannel))
                    }
                }

                override fun onProgress(progress: Float) {
                    // Repository reports 0f..1f; display as percent
                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                    _uiState.update {
                        it.copy(
                            status = str(R.string.album_progress, transferChannel, percent),
                            result = str(R.string.album_progress_result, transferChannel, percent),
                        )
                    }
                }

                override fun onSuccess() {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = str(R.string.feature_success),
                            result = str(R.string.album_push_ok, transferChannel),
                        )
                    }
                    // Quietly refresh storage so the result area shows watch-side state
                    queryStorageQuiet()
                }

                override fun onFail(code: Int, message: String) {
                    fail("[$code] $message")
                }
            },
            width = w,
            height = h,
        )
    }

    /** Cancel an in-progress SPP / Sifli album transfer. */
    fun cancelTransfer() {
        repository.cancelAlbumTransfer()
        _uiState.update {
            it.copy(
                busy = false,
                status = str(R.string.album_cancelled),
                result = str(R.string.album_cancelled),
            )
        }
    }

    /** Append storage info after a successful push; failures are ignored. */
    private fun queryStorageQuiet() {
        viewModelScope.launch {
            runCatching {
                val storage = repository.getAlbumIdleStorage()
                val ids = repository.getAlbumFileIdList()
                storage to ids
            }.onSuccess { (storage, ids) ->
                _uiState.update {
                    it.copy(
                        result = it.result + "\n" +
                            str(
                                R.string.album_storage_result,
                                storage.availableKb,
                                ids.size,
                                ids.joinToString(",").ifEmpty { "-" },
                            ),
                    )
                }
            }
        }
    }

    private fun fail(message: String) {
        _uiState.update {
            it.copy(
                busy = false,
                status = str(R.string.feature_failed, message),
                result = message,
            )
        }
    }

    /**
     * Copy a content Uri into app cache.
     *
     * Filename comes from DISPLAY_NAME (illegal chars replaced), with a timestamp
     * prefix to avoid collisions. Returns null on copy failure or empty file.
     */
    private fun copyUriToCache(context: Context, uri: Uri): File? {
        val resolver = context.contentResolver
        val displayName =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
                ?: "album_${System.currentTimeMillis()}.jpg"
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dir = File(context.cacheDir, "album_push").also { it.mkdirs() }
        val dest = File(dir, "${System.currentTimeMillis()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return dest.takeIf { it.exists() && it.length() > 0 }
    }

    companion object {
        /** Max photos per push (product limit; the system picker may allow more). */
        const val MAX_PHOTOS = 10
    }
}
