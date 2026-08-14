package com.huawo.nt.sdkdemo.ui.watchface

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.OnlineWatchface
import com.huawo.nt.sdkdemo.data.model.WlOnlineWatchfaceTransferCallback
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

enum class JLOnlineWatchfaceInstallPhase { IDLE, CHECKING, SWITCHING, DOWNLOADING, INSTALLING, SUCCESS, FAILED }

data class JLOnlineWatchfaceUiState(
    val loading: Boolean = false,
    val deviceType: String = "",
    val items: List<OnlineWatchface> = emptyList(),
    val error: String? = null,
    val selected: OnlineWatchface? = null,
    val installing: Boolean = false,
    val installPhase: JLOnlineWatchfaceInstallPhase = JLOnlineWatchfaceInstallPhase.IDLE,
    val installProgress: Int = 0,
    val installMessage: String = "",
    val installError: String? = null,
)

/** jieli online watchface flow: query by name, switch if present, otherwise MD5-checked A3 type=4. */
class JLOnlineWatchfaceViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val state = MutableStateFlow(JLOnlineWatchfaceUiState())
    val uiState: StateFlow<JLOnlineWatchfaceUiState> = state.asStateFlow()

    private fun ctx() = LocaleHelper.localizedContext(getApplication())

    fun loadList() {
        if (state.value.installing) return
        viewModelScope.launch {
            state.update { it.copy(loading = true, error = null, items = emptyList()) }
            try {
                val type = resolveDeviceType()
                require(type.isNotBlank()) { ctx().getString(R.string.owf_need_device_type) }
                val items = withContext(Dispatchers.IO) { WatchfaceApi.fetchOnlineWatchfaces(getApplication(), type) }
                state.update {
                    it.copy(
                        loading = false,
                        deviceType = type,
                        items = items,
                        error = if (items.isEmpty()) ctx().getString(R.string.owf_empty) else null,
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "loadList failed", error)
                state.update { it.copy(loading = false, error = error.message ?: ctx().getString(R.string.owf_load_failed)) }
            }
        }
    }

    fun select(item: OnlineWatchface?) {
        if (state.value.installing) return
        state.update {
            it.copy(selected = item, installPhase = JLOnlineWatchfaceInstallPhase.IDLE, installProgress = 0, installMessage = "", installError = null)
        }
    }

    /**
     * Prefers switching an already-installed watchface. A switch failure is non-terminal: it
     * falls through to the MD5-verified package download and fixed A3 type=4 transfer.
     */
    fun installSelected() {
        val item = state.value.selected ?: return
        if (state.value.installing) return
        Log.i(TAG, "installSelected name=${item.name} hasBin=${!item.bin.isNullOrBlank()} hasMd5=${!item.binMd5.isNullOrBlank()}")
        if (!repository.isConnected()) {
            state.update { it.copy(installError = ctx().getString(R.string.owf_need_connected)) }
            return
        }
        if (item.name.isBlank() || item.bin.isNullOrBlank() || item.binMd5.isNullOrBlank()) {
            state.update { it.copy(installError = ctx().getString(R.string.jl_owf_invalid_package)) }
            return
        }
        viewModelScope.launch {
            state.update {
                it.copy(
                    installing = true,
                    installPhase = JLOnlineWatchfaceInstallPhase.CHECKING,
                    installProgress = 0,
                    installMessage = ctx().getString(R.string.owf_checking),
                    installError = null,
                )
            }
            try {
                val installed = repository.getSifliWatchfaces()
                if (installed.any { name -> name.isNotBlank() && item.name.contains(name.trim()) }) {
                    state.update { it.copy(installPhase = JLOnlineWatchfaceInstallPhase.SWITCHING, installMessage = ctx().getString(R.string.owf_switching)) }
                    runCatching { repository.switchSifliWatchfaceBy(item.name) }.onSuccess {
                        success()
                        return@launch
                    }.onFailure { Log.w(TAG, "switch failed, install package", it) }
                }
                downloadAndTransfer(item)
            } catch (error: Exception) {
                failed(error.message ?: ctx().getString(R.string.owf_install_failed))
            }
        }
    }

    /** Reuses only a digest-matching cache file; every other package is re-downloaded and verified. */
    private suspend fun downloadAndTransfer(item: OnlineWatchface) {
        state.update { it.copy(installPhase = JLOnlineWatchfaceInstallPhase.DOWNLOADING, installProgress = 0, installMessage = ctx().getString(R.string.owf_downloading)) }
        val digest = item.binMd5!!.trim()
        val cacheDir = File(getApplication<Application>().cacheDir, "wl_online_watchface").also { it.mkdirs() }
        val packageFile = File(cacheDir, digest)
        val validCache = packageFile.isFile && packageFile.length() > 1L && withContext(Dispatchers.IO) {
            WatchfaceApi.md5Hex(packageFile).equals(digest, ignoreCase = true)
        }
        Log.i(TAG, "downloadAndTransfer name=${item.name} cacheHit=$validCache path=${packageFile.absolutePath}")
        val verified = if (validCache) packageFile else withContext(Dispatchers.IO) {
            WatchfaceApi.downloadToCache(
                getApplication(), item.bin!!, digest, digest, "wl_online_watchface",
            ) { progress ->
                state.update { current -> current.copy(installProgress = progress, installMessage = ctx().getString(R.string.owf_downloading_pct, progress)) }
            }
        }
        require(verified.isFile && verified.length() > 1L) { "WL package is empty after download" }
        state.update { it.copy(installPhase = JLOnlineWatchfaceInstallPhase.INSTALLING, installProgress = 0, installMessage = ctx().getString(R.string.owf_installing)) }
        transfer(verified, item.name)
        success()
    }

    /** Suspends until the repository bridges the watch's final A3 result, not merely transfer readiness. */
    private suspend fun transfer(file: File, name: String) = suspendCancellableCoroutine<Unit> { continuation ->
        repository.pushWlOnlineWatchface(file, name, object : WlOnlineWatchfaceTransferCallback {
            override fun onReady() = Unit
            override fun onProgress(progress: Float) {
                val pct = (progress * 100).toInt().coerceIn(0, 100)
                state.update { it.copy(installProgress = pct, installMessage = ctx().getString(R.string.owf_installing_pct, pct)) }
            }
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }
            override fun onFail(code: Int, message: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("[$code] $message"))
            }
        })
    }

    private fun success() {
        state.update { it.copy(installing = false, installPhase = JLOnlineWatchfaceInstallPhase.SUCCESS, installProgress = 100, installMessage = ctx().getString(R.string.owf_install_ok), installError = null) }
    }

    private fun failed(message: String) {
        Log.e(TAG, "install failed: $message")
        state.update { it.copy(installing = false, installPhase = JLOnlineWatchfaceInstallPhase.FAILED, installError = message) }
    }

    private suspend fun resolveDeviceType(): String {
        val bound = repository.loadBoundDevice()?.deviceInfo?.type?.trim().orEmpty()
        return bound.ifBlank { repository.getDeviceInfo().type?.trim().orEmpty() }
    }

    private companion object { const val TAG = "JLOnlineWatchfaceVM" }
}
