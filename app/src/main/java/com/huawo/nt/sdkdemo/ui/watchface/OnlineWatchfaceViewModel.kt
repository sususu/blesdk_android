package com.huawo.nt.sdkdemo.ui.watchface

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.OnlineWatchface
import com.huawo.nt.sdkdemo.data.model.OnlineWatchfaceTransferCallback
import com.huawo.nt.sdkdemo.data.remote.WatchfaceApi
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Phases of a single install attempt. UI maps these to progress text / buttons.
 *
 * - [IDLE]: no active install (or selection just opened)
 * - [CHECKING]: querying watch for already-installed Sifli faces
 * - [SWITCHING]: face already on device → [BluetoothSDK.switchSifliWatchfaceBy] only
 * - [DOWNLOADING]: HTTP download (+ MD5); progress 0..100 is download %
 * - [INSTALLING]: [SifliWatchSDK.syncZipFile] type=5; progress 0..100 is transfer %
 * - [SUCCESS] / [FAILED]: terminal; [OnlineWatchfaceUiState.installing] must be false
 */
enum class OnlineWatchfaceInstallPhase {
    IDLE,
    CHECKING,
    SWITCHING,
    DOWNLOADING,
    INSTALLING,
    SUCCESS,
    FAILED,
}

/**
 * UI state for the Online tab + detail bottom sheet.
 *
 * ## Critical flag: [installing]
 * When `true`, the host must block:
 * - system / toolbar back
 * - tab swipe / tab clicks
 * - bottom-sheet dismiss (back, outside tap, drag-to-hide)
 *
 * This mirrors HaWoFit `WatchfaceInstallActivity` hiding the back button during transfer.
 *
 * ## Ownership
 * Shared via `activityViewModels` so [WatchfaceFragment], [OnlineWatchfaceFragment],
 * and [OnlineWatchfaceDetailSheet] observe the same instance.
 */
data class OnlineWatchfaceUiState(
    /** True while the catalog HTTP request is in flight. */
    val loading: Boolean = false,
    /** Last resolved product code used for the list API (for debug / empty hints). */
    val deviceType: String = "",
    val items: List<OnlineWatchface> = emptyList(),
    /** List-level error or “empty” message; null when items are shown. */
    val error: String? = null,
    /** Row currently shown in the detail sheet; null when sheet should be closed. */
    val selected: OnlineWatchface? = null,
    /**
     * True for the whole critical section (check → switch **or** download → push).
     * Host UI must treat this as a modal lock.
     */
    val installing: Boolean = false,
    val installPhase: OnlineWatchfaceInstallPhase = OnlineWatchfaceInstallPhase.IDLE,
    /** 0..100; meaning depends on [installPhase] (download vs Sifli transfer). */
    val installProgress: Int = 0,
    val installMessage: String = "",
    /** Non-null when the last install attempt failed (sheet stays open for Retry). */
    val installError: String? = null,
)

/**
 * Online watchface list + **Sifli-only** install orchestration.
 *
 * ## Key logic (aligned with HaWoFit `WatchfaceInstallModel` QJS branch)
 * 1. Resolve `deviceType` from bound record (fallback: live `getDeviceInfo`).
 * 2. Load catalog via [WatchfaceApi.fetchOnlineWatchfaces].
 * 3. On Install:
 *    a. Require BLE connected + non-empty `bin` URL.
 *    b. Query installed names (`getSifliWatchfaces`).
 *    c. If [isAlreadyInstalled] → try `switchSifliWatchfaceBy(name)`; on success done.
 *    d. Else (or switch failed): reuse MD5-valid cache **or** download + MD5,
 *       then [BleRepository.pushOnlineWatchfaceZip] (`syncZipFile` type=**5**,
 *       `needByteAlign=false`).
 *
 * ## Caveats
 * - Query-installed failure falls back to download (demo is more resilient than HaWoFit
 *   fail-fast). Switch failure also falls back to download/push.
 * - Match rule for “already installed” uses `name.contains(installedName)` — same as
 *   production; be aware of partial-name collisions.
 * - Only Sifli ZIP path; do not call `BluetoothSDK.setOnlineWatchface` / WL media transfer here.
 */
class OnlineWatchfaceViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(OnlineWatchfaceUiState())
    val uiState: StateFlow<OnlineWatchfaceUiState> = _uiState.asStateFlow()

    /** Localized string context so install messages follow the in-app language setting. */
    private fun ctx() = LocaleHelper.localizedContext(getApplication())

    /**
     * Reload the online catalog. No-op while [OnlineWatchfaceUiState.installing]
     * so a mid-transfer refresh cannot race the lock.
     */
    fun loadList() {
        if (_uiState.value.installing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, error = null, items = emptyList())
            }
            try {
                val deviceType = resolveDeviceType()
                if (deviceType.isBlank()) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = ctx().getString(R.string.owf_need_device_type),
                            deviceType = "",
                        )
                    }
                    return@launch
                }
                val list =
                    withContext(Dispatchers.IO) {
                        WatchfaceApi.fetchOnlineWatchfaces(getApplication(), deviceType)
                    }
                _uiState.update {
                    it.copy(
                        loading = false,
                        deviceType = deviceType,
                        items = list,
                        error = if (list.isEmpty()) ctx().getString(R.string.owf_empty) else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: ctx().getString(R.string.owf_load_failed),
                    )
                }
            }
        }
    }

    /**
     * Select a row for the detail sheet (or clear with `null` when the sheet closes).
     * Resets install phase/error so a previous failure does not stick to the next face.
     */
    fun select(item: OnlineWatchface?) {
        if (_uiState.value.installing) return
        _uiState.update {
            it.copy(
                selected = item,
                installPhase = OnlineWatchfaceInstallPhase.IDLE,
                installProgress = 0,
                installMessage = "",
                installError = null,
            )
        }
    }

    /** Clear install result banners without changing [selected]. */
    fun clearInstallResult() {
        if (_uiState.value.installing) return
        _uiState.update {
            it.copy(
                installPhase = OnlineWatchfaceInstallPhase.IDLE,
                installProgress = 0,
                installMessage = "",
                installError = null,
            )
        }
    }

    /**
     * Start install for [OnlineWatchfaceUiState.selected].
     *
     * Sets [OnlineWatchfaceUiState.installing] = true immediately so the host can lock UI
     * before the first suspend point returns.
     */
    fun installSelected() {
        val item = _uiState.value.selected ?: return
        if (_uiState.value.installing) return
        if (!repository.isConnected()) {
            _uiState.update {
                it.copy(installError = ctx().getString(R.string.owf_need_connected))
            }
            return
        }
        if (item.bin.isNullOrBlank()) {
            _uiState.update {
                it.copy(installError = ctx().getString(R.string.owf_bin_missing))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    installing = true,
                    installPhase = OnlineWatchfaceInstallPhase.CHECKING,
                    installProgress = 0,
                    installMessage = ctx().getString(R.string.owf_checking),
                    installError = null,
                )
            }
            try {
                // If the query fails, treat as “not installed” and proceed to download.
                val installed =
                    runCatching { repository.getSifliWatchfaces() }.getOrElse { emptyList() }
                if (isAlreadyInstalled(item.name, installed)) {
                    _uiState.update {
                        it.copy(
                            installPhase = OnlineWatchfaceInstallPhase.SWITCHING,
                            installMessage = ctx().getString(R.string.owf_switching),
                            installProgress = 0,
                        )
                    }
                    val switched =
                        runCatching {
                            repository.switchSifliWatchfaceBy(item.name)
                            true
                        }.getOrDefault(false)
                    if (switched) {
                        markSuccess()
                        return@launch
                    }
                    // Switch failed → fall through to full download/push (same as HaWoFit).
                }
                downloadAndPush(item)
            } catch (e: Exception) {
                markFailed(e.message ?: ctx().getString(R.string.owf_install_failed))
            }
        }
    }

    /**
     * Download (or reuse cache) then push via Sifli.
     *
     * Cache hit requires: file exists, length > 1, and MD5 equals server [OnlineWatchface.binMd5].
     * Never skip MD5 when the server provided one.
     */
    private suspend fun downloadAndPush(item: OnlineWatchface) {
        _uiState.update {
            it.copy(
                installPhase = OnlineWatchfaceInstallPhase.DOWNLOADING,
                installMessage = ctx().getString(R.string.owf_downloading),
                installProgress = 0,
            )
        }
        val fileName =
            item.bin!!.substringAfterLast('/').ifBlank { "${item.name}.zip" }
        val cacheDir = File(getApplication<Application>().cacheDir, "online_watchface")
        val cached = File(cacheDir, fileName)
        val zip: File =
            if (
                cached.exists() &&
                    cached.length() > 1L &&
                    !item.binMd5.isNullOrBlank() &&
                    withContext(Dispatchers.IO) {
                        WatchfaceApi.md5Hex(cached).equals(item.binMd5, ignoreCase = true)
                    }
            ) {
                cached
            } else {
                withContext(Dispatchers.IO) {
                    WatchfaceApi.downloadToCache(
                        context = getApplication(),
                        url = item.bin,
                        fileName = fileName,
                        expectMd5 = item.binMd5,
                        subDir = "online_watchface",
                        onProgress = { pct ->
                            // StateFlow updates are thread-safe; UI collectors run on Main.
                            _uiState.update {
                                it.copy(
                                    installProgress = pct,
                                    installMessage =
                                        ctx().getString(R.string.owf_downloading_pct, pct),
                                )
                            }
                        },
                    )
                }
            }

        _uiState.update {
            it.copy(
                installPhase = OnlineWatchfaceInstallPhase.INSTALLING,
                installMessage = ctx().getString(R.string.owf_installing),
                installProgress = 0,
            )
        }
        pushZip(zip)
    }

    /**
     * Bridge callback-style [BleRepository.pushOnlineWatchfaceZip] into a suspend function.
     * Progress stays on the main thread via the repository’s handler.
     */
    private suspend fun pushZip(zip: File) {
        suspendCancellableCoroutine { cont ->
            repository.pushOnlineWatchfaceZip(
                zip,
                object : OnlineWatchfaceTransferCallback {
                    override fun onReady() {
                        _uiState.update {
                            it.copy(
                                installMessage = ctx().getString(R.string.owf_installing),
                                installProgress = 0,
                            )
                        }
                    }

                    override fun onProgress(progress: Float) {
                        val pct = (progress * 100).toInt().coerceIn(0, 100)
                        _uiState.update {
                            it.copy(
                                installProgress = pct,
                                installMessage =
                                    ctx().getString(R.string.owf_installing_pct, pct),
                            )
                        }
                    }

                    override fun onSuccess() {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onFail(code: Int, message: String) {
                        if (cont.isActive) {
                            cont.resumeWithException(Exception("[$code] $message"))
                        }
                    }
                },
            )
        }
        markSuccess()
    }

    /** Clears the UI lock ([installing]=false) and marks success for the sheet. */
    private fun markSuccess() {
        _uiState.update {
            it.copy(
                installing = false,
                installPhase = OnlineWatchfaceInstallPhase.SUCCESS,
                installProgress = 100,
                installMessage = ctx().getString(R.string.owf_install_ok),
                installError = null,
            )
        }
    }

    /** Clears the UI lock and surfaces [message] on the sheet (Retry remains available). */
    private fun markFailed(message: String) {
        _uiState.update {
            it.copy(
                installing = false,
                installPhase = OnlineWatchfaceInstallPhase.FAILED,
                installMessage = "",
                installError = message,
            )
        }
    }

    /**
     * Prefer persisted bind info (works offline after bind); otherwise ask the watch live.
     * Empty string → list load shows [R.string.owf_need_device_type].
     */
    private suspend fun resolveDeviceType(): String {
        val bound = repository.loadBoundDevice()?.deviceInfo?.type?.trim().orEmpty()
        if (bound.isNotBlank()) return bound
        return runCatching { repository.getDeviceInfo().type?.trim().orEmpty() }.getOrDefault("")
    }

    /**
     * HaWoFit match: server [name] **contains** an installed name substring.
     * Example: installed `"WF611"` matches catalog name `"JW_HS02WF611"`.
     */
    private fun isAlreadyInstalled(name: String, installed: List<String>): Boolean {
        if (installed.isEmpty()) return false
        for (item in installed) {
            val trimmed = item.trim()
            if (trimmed.isNotEmpty() && name.contains(trimmed)) return true
        }
        return false
    }
}
