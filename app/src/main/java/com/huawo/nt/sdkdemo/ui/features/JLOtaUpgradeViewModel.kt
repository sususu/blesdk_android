package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.OtaTransferCallback
import com.huawo.nt.sdkdemo.data.model.OtaUpgradeInfo
import com.huawo.nt.sdkdemo.data.remote.OtaFirmwareApi
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.FirmwareVersionUtils
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the standalone JieLi  OTA SDK example. */
data class JLOtaUpgradeUiState(
    val status: String = "",
    val macText: String = "",
    val firmwareText: String = "",
    val parsedVersionText: String = "-",
    val newVersionText: String = "",
    val upgradeInfoText: String = "",
    val hasUpgradePackage: Boolean = false,
    val busy: Boolean = false,
    val progressVisible: Boolean = false,
    val progress: Int = 0,
    val progressText: String = "0%",
    val phaseText: String = "",
    val logs: List<String> = emptyList(),
)

/**
 * Independent JieLi OTA flow for SDK users.
 *
 * It downloads one complete  OTA file and calls [BleRepository.startWlOta] directly.
 */
class JLOtaUpgradeViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState =
        MutableStateFlow(
            JLOtaUpgradeUiState(
                status = str(R.string.feature_ready),
                macText = str(R.string.ota_mac, "-"),
                firmwareText = str(R.string.ota_firmware, "-"),
            ),
        )
    val uiState: StateFlow<JLOtaUpgradeUiState> = _uiState.asStateFlow()

    private var cachedMac = ""
    private var cachedFw = ""
    private var cachedType = ""
    private var cachedDeviceId = ""
    private var upgradeInfo: OtaUpgradeInfo? = null
    private var runningJob: Job? = null
    private var wlOtaStarted = false
    private var lastTransferProgress = -1
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        appendLog("JL OTA example page opened")
    }

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    /** Reads the connected watch first and falls back to persisted binding data when disconnected. */
    fun refreshDeviceInfo() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(status = str(R.string.ota_refreshing)) }
            try {
                if (!repository.isConnected()) {
                    val bound = repository.loadBoundDevice()
                    cachedMac = bound?.macAddress.orEmpty()
                    cachedFw = bound?.deviceInfo?.firmwareVersion.orEmpty()
                    cachedType = bound?.deviceInfo?.type.orEmpty()
                    cachedDeviceId = bound?.deviceInfo?.id.orEmpty()
                    applyDeviceTexts()
                    _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
                    appendLog(str(R.string.status_need_connect_first))
                    return@launch
                }
                val info = repository.getDeviceInfo()
                cachedMac = info.mac?.takeIf { it.isNotBlank() } ?: repository.connectedDeviceMac().orEmpty()
                cachedFw = info.firmwareVersion.orEmpty()
                cachedType = info.type.orEmpty()
                cachedDeviceId = info.id.orEmpty()
                if (cachedFw.isBlank()) {
                    runCatching { repository.getFirmwareVersion() }
                        .onSuccess { cachedFw = it }
                        .onFailure { appendLog("JL OTA firmware read failed: ${it.message.orEmpty()}") }
                }
                val bound = repository.loadBoundDevice()
                if (bound != null && cachedMac.isNotBlank()) {
                    repository.saveBoundDevice(
                        cachedMac,
                        bound.name,
                        info.copy(
                            mac = cachedMac.ifBlank { info.mac },
                            firmwareVersion = cachedFw.ifBlank { info.firmwareVersion },
                        ),
                    )
                }
                applyDeviceTexts()
                _uiState.update { it.copy(status = str(R.string.ota_refresh_ok)) }
                appendLog(str(R.string.ota_log_device, cachedMac.ifBlank { "-" }, cachedFw.ifBlank { "-" }))
            } catch (e: Exception) {
                fail(e.message.orEmpty().ifBlank { e.javaClass.simpleName })
            }
        }
    }

    /** Resolves the server package without starting OTA; a successful check only enables Start. */
    fun checkUpgrade() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            if (cachedMac.isBlank() || cachedFw.isBlank()) {
                refreshCacheFromConnectedDevice()
            }
            if (cachedFw.isBlank()) {
                fail(str(R.string.ota_need_firmware))
                return@launch
            }
            if (cachedType.isBlank()) {
                fail(str(R.string.ota_need_product_code))
                return@launch
            }
            _uiState.update {
                it.copy(
                    busy = true,
                    hasUpgradePackage = false,
                    newVersionText = "",
                    upgradeInfoText = "",
                    status = str(R.string.ota_checking),
                )
            }
            upgradeInfo = null
            val currentVersion = FirmwareVersionUtils.extractV(cachedFw)
            val currentBuild = FirmwareVersionUtils.extractB(cachedFw)
            appendLog(str(R.string.ota_log_check_start, cachedMac.ifBlank { "-" }, "$currentVersion($currentBuild)"))
            try {
                val info =
                    withContext(Dispatchers.IO) {
                        OtaFirmwareApi.checkUpgrade(
                            context = getApplication(),
                            currentFirmwareRaw = cachedFw,
                            productCode = cachedType,
                            deviceId = cachedDeviceId,
                        )
                    }
                val canStart = OtaFirmwareApi.isNewerThan(info, cachedFw) && info.firmwares.isNotEmpty()
                upgradeInfo = if (canStart) info else null
                val newDisplay = if (canStart) formatServerVersion(info) else ""
                val summary =
                    if (canStart) {
                        buildString {
                            append(str(R.string.ota_new_version, newDisplay))
                            append('\n')
                            append(str(R.string.ota_files_count, info.firmwares.size))
                            if (!info.updateContent.isNullOrBlank()) append('\n').append(info.updateContent)
                        }
                    } else {
                        str(R.string.ota_check_empty)
                    }
                _uiState.update {
                    it.copy(
                        busy = false,
                        hasUpgradePackage = canStart,
                        newVersionText = if (canStart) str(R.string.ota_new_version_arrow, newDisplay) else "",
                        upgradeInfoText = summary,
                        status = if (canStart) str(R.string.ota_check_ok) else str(R.string.ota_check_empty),
                    )
                }
                appendLog(
                    if (canStart) str(R.string.ota_log_check_ok, newDisplay, info.firmwares.size)
                    else str(R.string.ota_check_empty),
                )
            } catch (e: Exception) {
                upgradeInfo = null
                _uiState.update {
                    it.copy(
                        busy = false,
                        hasUpgradePackage = false,
                        newVersionText = "",
                        upgradeInfoText = "",
                    )
                }
                fail(e.message.orEmpty().ifBlank { e.javaClass.simpleName })
            }
        }
    }

    /**
     * Downloads and MD5-verifies the first server firmware, then starts the fixed WL OTA path.
     * Download maps to 0..40%; the SDK callback maps its transport progress to 40..100%.
     */
    fun startUpgrade() {
        if (_uiState.value.busy) return
        val info = upgradeInfo
        if (info == null || info.firmwares.isEmpty()) {
            fail(str(R.string.ota_need_check_first))
            return
        }
        if (!repository.isConnected()) {
            fail(str(R.string.status_need_connect_first))
            return
        }
        runningJob?.cancel()
        runningJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        busy = true,
                        progressVisible = true,
                        progress = 0,
                        progressText = "0%",
                        phaseText = str(R.string.ota_phase_prepare),
                        status = str(R.string.ota_starting),
                    )
                }
                appendLog(str(R.string.ota_log_start, formatServerVersion(info)))
                try {
                    val batteryResult = runCatching { repository.getBattery() }
                    batteryResult.onSuccess { battery ->
                        appendLog(str(R.string.ota_log_battery, battery))
                        if (battery < 30) throw IllegalStateException(str(R.string.ota_battery_low, battery))
                    }.onFailure {
                        appendLog("JL OTA battery check skipped: ${it.message.orEmpty()}")
                    }
                    val firmware = info.firmwares.first()
                    if (firmware.url.isBlank()) throw IllegalStateException("JL OTA firmware url is empty")
                    val fileName =
                        firmware.md5?.takeIf { it.isNotBlank() }
                            ?: firmware.name?.let { File(it).name }?.takeIf { it.isNotBlank() }
                            ?: "wl_ota_${System.currentTimeMillis()}.bin"
                    setPhase(str(R.string.ota_phase_download), 0)
                    appendLog(str(R.string.ota_log_channel_wl))
                    appendLog(str(R.string.ota_log_download, 0, firmware.url))
                    val otaFile =
                        withContext(Dispatchers.IO) {
                            OtaFirmwareApi.downloadToCache(
                                context = getApplication(),
                                url = firmware.url,
                                fileName = fileName,
                                expectMd5 = firmware.md5,
                                subDir = "device/wl",
                            ) { progress ->
                                updateProgress(
                                    (progress * 0.4f).toInt().coerceIn(0, 40),
                                    str(R.string.ota_phase_download),
                                )
                            }
                        }
                    if (!otaFile.isFile || otaFile.length() <= 0L) {
                        throw IllegalStateException("JL OTA download invalid: ${otaFile.absolutePath}")
                    }
                    appendLog(str(R.string.ota_log_download_ok, otaFile.name, otaFile.length()))
                    setPhase(str(R.string.ota_phase_push), 40)
                    appendLog("JL OTA startOta path=${otaFile.absolutePath}")
                    wlOtaStarted = true
                    lastTransferProgress = -1
                    repository.startWlOta(otaFile.absolutePath, wlOtaCallback)
                } catch (e: Exception) {
                    wlOtaStarted = false
                    fail(e.message.orEmpty().ifBlank { e.javaClass.simpleName })
                }
            }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    override fun onCleared() {
        runningJob?.cancel()
        if (wlOtaStarted) {
            // A cleared example page must not leave a  1630 OTA session active.
            repository.forceResetWlOta()
        }
        super.onCleared()
    }

    // This callback is the sole terminal authority for the running 1630 OTA session.
    private val wlOtaCallback =
        object : OtaTransferCallback {
            override fun onReady() {
                appendLog("JL OTA SDK ready")
                setPhase(str(R.string.ota_phase_push), 40)
            }

            override fun onProgress(progress: Float) {
                val transferProgress = (progress.coerceIn(0f, 1f) * 100).toInt()
                if (transferProgress == lastTransferProgress) return
                lastTransferProgress = transferProgress
                updateProgress(
                    (40 + transferProgress * 0.6f).toInt().coerceIn(40, 100),
                    str(R.string.ota_phase_push),
                )
            }

            override fun onSuccess() {
                wlOtaStarted = false
                updateProgress(100, str(R.string.ota_phase_done))
                _uiState.update { it.copy(busy = false, status = str(R.string.ota_success)) }
                appendLog(str(R.string.ota_log_success))
                viewModelScope.launch {
                    delay(1500)
                    if (repository.isConnected()) refreshDeviceInfo()
                }
            }

            override fun onFail(code: Int, message: String) {
                wlOtaStarted = false
                val detail = "[$code] $message, phase=${_uiState.value.phaseText}"
                _uiState.update { it.copy(busy = false, status = str(R.string.feature_failed, detail)) }
                appendLog(str(R.string.feature_failed, detail))
            }
        }

    private suspend fun refreshCacheFromConnectedDevice() {
        if (!repository.isConnected()) return
        runCatching {
            val info = repository.getDeviceInfo()
            cachedMac = info.mac?.takeIf { it.isNotBlank() } ?: repository.connectedDeviceMac().orEmpty()
            cachedFw = info.firmwareVersion.orEmpty()
            cachedType = info.type.orEmpty()
            cachedDeviceId = info.id.orEmpty()
            applyDeviceTexts()
        }.onFailure {
            appendLog("JL OTA device refresh before check failed: ${it.message.orEmpty()}")
        }
    }

    private fun formatServerVersion(info: OtaUpgradeInfo): String {
        val version = info.version?.ifBlank { null } ?: "-"
        val build = info.build?.toString() ?: "-"
        return "$version($build)"
    }

    private fun applyDeviceTexts() {
        val parsed = FirmwareVersionUtils.formatDisplay(cachedFw).ifBlank { "-" }
        _uiState.update {
            it.copy(
                macText = str(R.string.ota_mac, cachedMac.ifBlank { "-" }),
                firmwareText = str(R.string.ota_firmware, cachedFw.ifBlank { "-" }),
                parsedVersionText = parsed,
            )
        }
    }

    private fun setPhase(phase: String, progress: Int) {
        updateProgress(progress, phase)
    }

    private fun updateProgress(progress: Int, phase: String) {
        val value = progress.coerceIn(0, 100)
        _uiState.update {
            it.copy(
                progressVisible = true,
                progress = value,
                progressText = "$value%",
                phaseText = phase,
            )
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(busy = false, status = str(R.string.feature_failed, message)) }
        appendLog(str(R.string.feature_failed, message))
    }

    private fun appendLog(message: String) {
        val time = timeFmt.format(Date())
        _uiState.update { state ->
            state.copy(logs = (listOf("$time  $message") + state.logs).take(120))
        }
    }
}
