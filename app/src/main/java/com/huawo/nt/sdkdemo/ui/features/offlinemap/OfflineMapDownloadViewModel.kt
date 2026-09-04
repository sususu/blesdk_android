package com.huawo.nt.sdkdemo.ui.features.offlinemap

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.OfflineMapTransferCallback
import com.huawo.nt.sdkdemo.data.remote.OfflineMapApi
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.MapTheme
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LatLngBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLon: Double get() = (minLon + maxLon) / 2.0
}

enum class OfflineMapProgressPhase {
    Downloading,
    Installing,
}

data class OfflineMapSpaceWarning(
    val neededKb: Double,
    val availableKb: Long,
)

data class OfflineMapUiState(
    val status: String = "",
    val busy: Boolean = false,
    val distanceText: String = "- -",
    val errorMessage: String? = null,
    val showProgressDialog: Boolean = false,
    /** Download progress 0f..1f. */
    val downloadProgress: Float = 0f,
    val installPercent: Int = 0,
    val progressPhase: OfflineMapProgressPhase = OfflineMapProgressPhase.Downloading,
    val showThemeDialog: Boolean = false,
    val spaceWarning: OfflineMapSpaceWarning? = null,
)

/**
 * Offline map install: authorize → request package → push zip (SPP/BLE).
 */
class OfflineMapDownloadViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OfflineMapUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<OfflineMapUiState> = _uiState.asStateFlow()

    private var mapAuthorizedLocal = false

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    fun updateDistanceText(text: String) {
        _uiState.update { it.copy(distanceText = text) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, spaceWarning = null) }
    }

    fun clearThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = false) }
    }

    fun clearSpaceWarning() {
        _uiState.update { it.copy(spaceWarning = null) }
    }

    /**
     * Fetch auth code from cloud and write to device; skips if already marked authorized.
     */
    suspend fun ensureMapAuthorized() {
        if (mapAuthorizedLocal) return
        val info = repository.getDeviceInfo()
        if (info.mapAuthorized) {
            mapAuthorizedLocal = true
            return
        }
        val mapUuid = info.mapUuid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(str(R.string.offline_map_no_map_uuid))
        val authCode =
            withContext(Dispatchers.IO) {
                OfflineMapApi.fetchAuthCode(getApplication(), mapUuid)
            }
        repository.setDeviceMapAuthCode(mapUuid, authCode)
        mapAuthorizedLocal = true
        val bound = repository.loadBoundDevice()
        if (bound != null) {
            repository.saveBoundDevice(
                bound.macAddress,
                bound.name,
                (bound.deviceInfo ?: info).copy(mapUuid = mapUuid, mapAuthorized = true),
            )
        }
    }

    fun install(bounds: LatLngBox) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            if (!repository.isConnected()) {
                _uiState.update {
                    it.copy(
                        status = str(R.string.status_need_connect_first),
                        errorMessage = str(R.string.status_need_connect_first),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    busy = true,
                    status = str(R.string.offline_map_authorizing),
                    errorMessage = null,
                    spaceWarning = null,
                    showProgressDialog = false,
                    showThemeDialog = false,
                    downloadProgress = 0f,
                    installPercent = 0,
                    progressPhase = OfflineMapProgressPhase.Downloading,
                )
            }
            try {
                ensureMapAuthorized()
                val availableKb =
                    runCatching { repository.getDeviceMapAvailableStorageKb() }
                        .getOrDefault(-1)
                val device = repository.getDeviceInfo()
                val mapUuid = device.mapUuid?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(str(R.string.offline_map_no_map_uuid))
                val productCode = device.type?.takeIf { it.isNotBlank() } ?: "unknown"
                val box = clampBoxWidth(bounds, MAX_SPAN_METRES)
                _uiState.update { it.copy(status = str(R.string.offline_map_requesting)) }
                val info =
                    withContext(Dispatchers.IO) {
                        OfflineMapApi.requestOfflinePackage(
                            context = getApplication(),
                            mapUuid = mapUuid,
                            productCode = productCode,
                            minLon = box.minLon,
                            minLat = box.minLat,
                            maxLon = box.maxLon,
                            maxLat = box.maxLat,
                            centerLat = box.centerLat,
                            centerLon = box.centerLon,
                        )
                    }
                val uncompressed = info.uncompressedSize
                if (uncompressed != null && uncompressed > 0 && availableKb > 0) {
                    val neededKb = uncompressed / 1024.0 + 16.0
                    if (neededKb > availableKb) {
                        _uiState.update {
                            it.copy(
                                busy = false,
                                status = str(R.string.offline_map_space_short),
                                spaceWarning =
                                    OfflineMapSpaceWarning(
                                        neededKb = neededKb,
                                        availableKb = availableKb.toLong(),
                                    ),
                            )
                        }
                        return@launch
                    }
                }
                val center =
                    Location("").apply {
                        latitude = box.centerLat
                        longitude = box.centerLon
                    }
                repository.setDeviceMapRange(
                    center,
                    box.maxLon,
                    box.minLon,
                    box.maxLat,
                    box.minLat,
                )
                _uiState.update {
                    it.copy(
                        status = str(R.string.offline_map_downloading),
                        showProgressDialog = true,
                        progressPhase = OfflineMapProgressPhase.Downloading,
                        downloadProgress = 0f,
                        installPercent = 0,
                    )
                }
                repository.pushOfflineMap(
                    getApplication(),
                    info,
                    object : OfflineMapTransferCallback {
                        override fun onDownloadProgress(progress: Float) {
                            _uiState.update {
                                it.copy(
                                    progressPhase = OfflineMapProgressPhase.Downloading,
                                    downloadProgress = progress.coerceIn(0f, 1f),
                                    status = str(R.string.offline_map_downloading),
                                )
                            }
                        }

                        override fun onInstallProgress(percent: Int) {
                            _uiState.update {
                                it.copy(
                                    progressPhase = OfflineMapProgressPhase.Installing,
                                    installPercent = percent.coerceIn(0, 100),
                                    downloadProgress = 1f,
                                    status = str(R.string.offline_map_installing),
                                )
                            }
                        }

                        override fun onSuccess() {
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    showProgressDialog = false,
                                    showThemeDialog = true,
                                    status = str(R.string.offline_map_install_ok),
                                    downloadProgress = 1f,
                                    installPercent = 100,
                                )
                            }
                        }

                        override fun onFail(code: Int, message: String?) {
                            val msg =
                                message?.takeIf { it.isNotBlank() }
                                    ?: str(R.string.feature_failed, code.toString())
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    showProgressDialog = false,
                                    status = msg,
                                    errorMessage = msg,
                                )
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                val msg = e.message.orEmpty().ifBlank { e.javaClass.simpleName }
                _uiState.update {
                    it.copy(
                        busy = false,
                        showProgressDialog = false,
                        status = msg,
                        errorMessage = msg,
                    )
                }
            }
        }
    }

    fun cancelInstall() {
        repository.cancelOfflineMapTransfer()
        _uiState.update {
            it.copy(
                busy = false,
                showProgressDialog = false,
                status = str(R.string.offline_map_cancelled),
            )
        }
    }

    fun setTheme(light: Boolean) {
        viewModelScope.launch {
            try {
                repository.setDeviceMapTheme(if (light) MapTheme.Light else MapTheme.Dark)
                _uiState.update {
                    it.copy(
                        showThemeDialog = false,
                        status = str(R.string.feature_success),
                    )
                }
            } catch (e: Exception) {
                val msg = e.message.orEmpty().ifBlank { e.javaClass.simpleName }
                _uiState.update {
                    it.copy(
                        showThemeDialog = false,
                        status = msg,
                        errorMessage = msg,
                    )
                }
            }
        }
    }

    companion object {
        /** HaWoFit max span minus 1 km ≈ 49 km. */
        private const val MAX_SPAN_METRES = 49_000f

        fun clampBoxWidth(box: LatLngBox, maxMetres: Float = MAX_SPAN_METRES): LatLngBox {
            var minLat = min(box.minLat, box.maxLat)
            var maxLat = max(box.minLat, box.maxLat)
            var minLon = min(box.minLon, box.maxLon)
            var maxLon = max(box.minLon, box.maxLon)
            val detal = 0.01 / 8.0
            val results = FloatArray(1)
            var guard = 0
            while (guard++ < 10_000) {
                Location.distanceBetween(maxLat, maxLon, maxLat, minLon, results)
                if (results[0] <= maxMetres) break
                minLat += detal
                minLon += detal
                maxLat -= detal
                maxLon -= detal
            }
            return LatLngBox(
                minLat = min(minLat, maxLat),
                minLon = min(minLon, maxLon),
                maxLat = max(minLat, maxLat),
                maxLon = max(minLon, maxLon),
            )
        }
    }
}
