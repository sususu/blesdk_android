package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.OtaUpgradeInfo
import com.huawo.nt.sdkdemo.data.remote.OtaFirmwareApi
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.FirmwareVersionUtils
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.nt.sdkdemo.util.SifliOtaHelper
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.UpgradeStatus
import com.sifli.siflidfu.DFUImagePath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OtaUpgradeUiState(
    val status: String = "",
    val macText: String = "",
    val firmwareText: String = "",
    val parsedVersionText: String = "-",
    /** New version display next to current, e.g. `→ 1.0.1(456)`; empty when none. */
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

/** Fragment binds [SifliDFUService] and starts DFU when this is emitted. */
data class SifliDfuStartEvent(
    val mac: String,
    val imagePaths: ArrayList<DFUImagePath>,
)

class OtaUpgradeViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState =
        MutableStateFlow(
            OtaUpgradeUiState(
                status = str(R.string.feature_ready),
                macText = str(R.string.ota_mac, "-"),
                firmwareText = str(R.string.ota_firmware, "-"),
            ),
        )
    val uiState: StateFlow<OtaUpgradeUiState> = _uiState.asStateFlow()

    private val _sifliDfuStart = MutableSharedFlow<SifliDfuStartEvent>(extraBufferCapacity = 1)
    val sifliDfuStart: SharedFlow<SifliDfuStartEvent> = _sifliDfuStart.asSharedFlow()

    private var cachedMac: String = ""
    private var cachedFw: String = ""
    private var cachedType: String = ""
    private var cachedDeviceId: String = ""
    private var upgradeInfo: OtaUpgradeInfo? = null
    private var runningJob: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

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
                    _uiState.update {
                        it.copy(status = str(R.string.status_need_connect_first))
                    }
                    appendLog(str(R.string.status_need_connect_first))
                    return@launch
                }
                val info = repository.getDeviceInfo()
                cachedMac =
                    info.mac?.takeIf { it.isNotBlank() }
                        ?: repository.connectedDeviceMac().orEmpty()
                cachedFw = info.firmwareVersion.orEmpty()
                cachedType = info.type.orEmpty()
                cachedDeviceId = info.id.orEmpty()
                if (cachedFw.isBlank()) {
                    runCatching { cachedFw = repository.getFirmwareVersion() }
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
                appendLog(
                    str(
                        R.string.ota_log_device,
                        cachedMac.ifBlank { "-" },
                        cachedFw.ifBlank { "-" },
                    ),
                )
            } catch (e: Exception) {
                fail(e.message.orEmpty().ifBlank { e.javaClass.simpleName })
            }
        }
    }

    fun checkUpgrade() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            if (cachedMac.isBlank() || cachedFw.isBlank()) {
                if (repository.isConnected()) {
                    runCatching {
                        val info = repository.getDeviceInfo()
                        cachedMac =
                            info.mac?.takeIf { it.isNotBlank() }
                                ?: repository.connectedDeviceMac().orEmpty()
                        cachedFw = info.firmwareVersion.orEmpty()
                        cachedType = info.type.orEmpty()
                        cachedDeviceId = info.id.orEmpty()
                        applyDeviceTexts()
                    }
                }
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
            val major = FirmwareVersionUtils.extractV(cachedFw)
            val build = FirmwareVersionUtils.extractB(cachedFw)
            appendLog(
                str(
                    R.string.ota_log_check_start,
                    cachedMac.ifBlank { "-" },
                    "$major($build)",
                ),
            )
            try {
                val info =
                    withContext(Dispatchers.IO) {
                        OtaFirmwareApi.checkUpgrade(
                            currentFirmwareRaw = cachedFw,
                            productCode = cachedType,
                            deviceId = cachedDeviceId,
                        )
                    }
                val newer = OtaFirmwareApi.isNewerThan(info, cachedFw)
                val canStart = newer && info.firmwares.isNotEmpty()
                upgradeInfo = if (canStart) info else null

                val newDisplay =
                    if (canStart) {
                        formatServerVersion(info)
                    } else {
                        ""
                    }
                val summary =
                    if (canStart) {
                        buildString {
                            append(str(R.string.ota_new_version, newDisplay))
                            append('\n')
                            append(str(R.string.ota_files_count, info.firmwares.size))
                            if (!info.updateContent.isNullOrBlank()) {
                                append('\n').append(info.updateContent)
                            }
                            if (info.resource != null) {
                                append('\n').append(str(R.string.ota_diff_resource))
                            }
                        }
                    } else {
                        str(R.string.ota_check_empty)
                    }
                _uiState.update {
                    it.copy(
                        busy = false,
                        hasUpgradePackage = canStart,
                        newVersionText =
                            if (canStart) {
                                str(R.string.ota_new_version_arrow, newDisplay)
                            } else {
                                ""
                            },
                        upgradeInfoText = summary,
                        status =
                            if (canStart) {
                                str(R.string.ota_check_ok)
                            } else {
                                str(R.string.ota_check_empty)
                            },
                    )
                }
                appendLog(
                    if (canStart) {
                        str(
                            R.string.ota_log_check_ok,
                            newDisplay,
                            info.firmwares.size,
                        )
                    } else {
                        str(R.string.ota_check_empty)
                    },
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
        if (cachedMac.isBlank()) {
            fail(str(R.string.ota_need_mac))
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
                    val battery = runCatching { repository.getBattery() }.getOrNull()
                    if (battery != null) {
                        appendLog(str(R.string.ota_log_battery, battery))
                        if (battery < 30) {
                            throw IllegalStateException(str(R.string.ota_battery_low, battery))
                        }
                    }
                    val status = runCatching { repository.getDeviceUpgradeStatus() }.getOrNull()
                    if (status != null) {
                        appendLog(str(R.string.ota_log_upgrade_status, status.name))
                        if (status != UpgradeStatus.Normal) {
                            throw IllegalStateException(
                                str(R.string.ota_status_not_ready, status.name),
                            )
                        }
                    }

                    setPhase(str(R.string.ota_phase_download), 0)
                    appendLog(str(R.string.ota_log_channel_sifli))
                    val paths =
                        withContext(Dispatchers.IO) {
                            SifliOtaHelper.prepareDfuImagePaths(
                                context = getApplication(),
                                info = info,
                            ) { pct ->
                                // Download occupies 0..40 of overall bar
                                updateProgress(
                                    (pct * 0.4f).toInt().coerceIn(0, 40),
                                    str(R.string.ota_phase_download),
                                )
                            }
                        }
                    appendLog(str(R.string.ota_log_dfu_images, paths.size))
                    paths.forEachIndexed { index, path ->
                        appendLog("DFU[$index] ${path.imagePath}")
                    }

                    setPhase(str(R.string.ota_phase_push), 40)
                    appendLog(str(R.string.ota_log_push_ready))
                    _sifliDfuStart.emit(SifliDfuStartEvent(cachedMac, paths))
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = str(R.string.feature_failed, e.message.orEmpty()),
                        )
                    }
                    appendLog(
                        str(
                            R.string.feature_failed,
                            e.message.orEmpty().ifBlank { e.javaClass.simpleName },
                        ),
                    )
                }
            }
    }

    fun onDfuProgress(progress0to100: Int) {
        val pct = (40 + progress0to100 * 0.6f).toInt().coerceIn(40, 100)
        updateProgress(pct, str(R.string.ota_phase_push))
    }

    fun onDfuLog(message: String) {
        if (message.isNotBlank()) appendLog(message)
    }

    fun onDfuSuccess() {
        updateProgress(100, str(R.string.ota_phase_done))
        _uiState.update {
            it.copy(
                busy = false,
                status = str(R.string.ota_success),
            )
        }
        appendLog(str(R.string.ota_log_success))
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            if (repository.isConnected()) {
                refreshDeviceInfo()
            }
        }
    }

    fun onDfuFail(code: Int, message: String) {
        _uiState.update {
            it.copy(
                busy = false,
                status = str(R.string.feature_failed, "[$code] $message"),
            )
        }
        appendLog(str(R.string.feature_failed, "[$code] $message"))
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    override fun onCleared() {
        runningJob?.cancel()
        super.onCleared()
    }

    private fun formatServerVersion(info: OtaUpgradeInfo): String {
        val v = info.version?.ifBlank { null } ?: "-"
        val b = info.build?.toString() ?: "-"
        return "$v($b)"
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
        val p = progress.coerceIn(0, 100)
        _uiState.update {
            it.copy(
                progressVisible = true,
                progress = p,
                progressText = "$p%",
                phaseText = phase,
            )
        }
    }

    private fun fail(message: String) {
        _uiState.update {
            it.copy(busy = false, status = str(R.string.feature_failed, message))
        }
        appendLog(str(R.string.feature_failed, message))
    }

    private fun appendLog(msg: String) {
        val time = timeFmt.format(Date())
        _uiState.update { state ->
            state.copy(logs = (listOf("$time  $msg") + state.logs).take(120))
        }
    }
}
