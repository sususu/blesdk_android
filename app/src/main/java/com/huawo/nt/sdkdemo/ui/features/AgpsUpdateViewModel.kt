package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.AgpsTransferCallback
import com.huawo.nt.sdkdemo.data.model.BleGpsStatus
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.AgpsXywBuilder
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AgpsUpdateUiState(
    val status: String = "",
    val bleConnected: Boolean = false,
    val gpsClipText: String = "- -",
    val gpsFwText: String = "- -",
    val agpsValidText: String = "- -",
    val busy: Boolean = false,
    /** Overall 0..100 (download/build 0..50, push 50..100). */
    val progress: Int = 0,
    val progressText: String = "0%",
    val phaseText: String = "",
    val logs: List<String> = emptyList(),
)

/**
 * AGPS update: download 7-day XYW data → build `agps_xyw.zip` → push via Sifli type=3.
 */
class AgpsUpdateViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState =
        MutableStateFlow(AgpsUpdateUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<AgpsUpdateUiState> = _uiState.asStateFlow()

    private var lastZip: File? = null
    private var runningJob: kotlinx.coroutines.Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    fun refreshDeviceInfo() {
        _uiState.update { it.copy(bleConnected = repository.isConnected()) }
        refreshGpsStatus(silent = true)
    }

    /**
     * Query [BluetoothSDK.getDeviceGpsStatus] and show chip / FW / AGPS validity.
     */
    fun refreshGpsStatus(silent: Boolean = false) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            if (!repository.isConnected()) {
                _uiState.update {
                    it.copy(
                        bleConnected = false,
                        gpsClipText = str(R.string.agps_gps_clip, "-"),
                        gpsFwText = str(R.string.agps_gps_fw, "-", "-"),
                        agpsValidText = str(R.string.agps_valid_unknown),
                        status =
                            if (silent) {
                                it.status
                            } else {
                                str(R.string.status_need_connect_first)
                            },
                    )
                }
                return@launch
            }
            if (!silent) {
                _uiState.update { it.copy(status = str(R.string.agps_querying_status)) }
            }
            try {
                val gps = repository.getDeviceGpsStatus()
                applyGpsStatus(gps, updateStatus = !silent)
                appendLog(str(R.string.agps_log_status_ok, formatGpsLog(gps)))
            } catch (e: Exception) {
                if (!silent) {
                    fail(e.message.orEmpty().ifBlank { e.javaClass.simpleName })
                } else {
                    appendLog(
                        str(
                            R.string.feature_failed,
                            e.message.orEmpty().ifBlank { e.javaClass.simpleName },
                        ),
                    )
                }
            }
        }
    }

    fun clearLogs() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(logs = emptyList()) }
    }

    /**
     * Full flow: build zip then push to watch.
     */
    fun startUpdate() {
        if (_uiState.value.busy) return
        if (!repository.isConnected()) {
            _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
            appendLog(str(R.string.status_need_connect_first))
            return
        }
        _uiState.update {
            it.copy(
                busy = true,
                progress = 0,
                progressText = "0%",
                phaseText = str(R.string.agps_phase_download),
                status = str(R.string.agps_downloading),
            )
        }
        appendLog(str(R.string.agps_log_start))
        runningJob =
            viewModelScope.launch {
                try {
                    val result =
                        withContext(Dispatchers.IO) {
                            AgpsXywBuilder.build(
                                getApplication(),
                                object : AgpsXywBuilder.Listener {
                                    override fun onLog(message: String) {
                                        appendLog(message)
                                    }

                                    override fun onProgress(progress: Int) {
                                        val overall = (progress * 50 / 100).coerceIn(0, 50)
                                        _uiState.update {
                                            it.copy(
                                                progress = overall,
                                                progressText = "$overall%",
                                                phaseText = str(R.string.agps_phase_download),
                                                status = str(R.string.agps_downloading),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    lastZip = result.zipFile
                    appendLog(
                        str(
                            R.string.agps_log_zip_ready,
                            result.zipFile.name,
                            result.zipFile.length(),
                            result.validStartTimeMs,
                            result.validEndTimeMs,
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            progress = 50,
                            progressText = "50%",
                            phaseText = str(R.string.agps_phase_push),
                            status = str(R.string.agps_pushing),
                        )
                    }
                    appendLog(str(R.string.agps_log_push_start))
                    pushZip(result.zipFile)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        appendLog(str(R.string.agps_cancelled))
                        return@launch
                    }
                    fail(e.message.orEmpty().ifBlank { e.javaClass.simpleName })
                }
            }
    }

    fun cancelUpdate() {
        runningJob?.cancel()
        runningJob = null
        repository.cancelAgpsTransfer()
        _uiState.update {
            it.copy(
                busy = false,
                status = str(R.string.agps_cancelled),
                phaseText = "",
            )
        }
        appendLog(str(R.string.agps_cancelled))
    }

    private fun pushZip(zipFile: File) {
        repository.pushAgpsZip(
            zipFile,
            object : AgpsTransferCallback {
                override fun onReady() {
                    appendLog(str(R.string.agps_log_push_ready))
                    _uiState.update {
                        it.copy(
                            status = str(R.string.agps_pushing),
                            phaseText = str(R.string.agps_phase_push),
                        )
                    }
                }

                override fun onProgress(progress: Float) {
                    val overall = (50 + progress * 50).toInt().coerceIn(50, 100)
                    _uiState.update {
                        it.copy(
                            progress = overall,
                            progressText = "$overall%",
                            status = str(R.string.agps_push_progress, overall),
                        )
                    }
                }

                override fun onSuccess() {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            progress = 100,
                            progressText = "100%",
                            status = str(R.string.feature_success),
                            phaseText = str(R.string.agps_phase_done),
                        )
                    }
                    appendLog(str(R.string.agps_log_push_ok))
                    refreshGpsStatus(silent = true)
                }

                override fun onFail(code: Int, message: String) {
                    fail("[$code] $message")
                }
            },
        )
    }

    private fun applyGpsStatus(gps: BleGpsStatus, updateStatus: Boolean) {
        val clip = gps.gpsClipType?.ifBlank { null } ?: "-"
        val version = gps.gpsFirmwareVersion?.ifBlank { null } ?: "-"
        val build = if (gps.gpsFirmwareBuild > 0) gps.gpsFirmwareBuild.toString() else "-"
        val validText = formatAgpsValid(gps.agpsValidStartTimeMs, gps.agpsValidEndTimeMs)
        _uiState.update {
            it.copy(
                bleConnected = repository.isConnected(),
                gpsClipText = str(R.string.agps_gps_clip, clip),
                gpsFwText = str(R.string.agps_gps_fw, version, build),
                agpsValidText = validText,
                status = if (updateStatus) str(R.string.feature_success) else it.status,
            )
        }
    }

    private fun formatAgpsValid(startMs: Long, endMs: Long): String {
        if (startMs <= 0L && endMs <= 0L) {
            return str(R.string.agps_valid_unknown)
        }
        val start = if (startMs > 0L) dateFmt.format(Date(startMs)) else "-"
        val end = if (endMs > 0L) dateFmt.format(Date(endMs)) else "-"
        val expired = endMs in 1 until System.currentTimeMillis()
        return if (expired) {
            str(R.string.agps_valid_expired, start, end)
        } else {
            str(R.string.agps_valid_range, start, end)
        }
    }

    private fun formatGpsLog(gps: BleGpsStatus): String =
        "clip=${gps.gpsClipType}, fw=${gps.gpsFirmwareVersion}/${gps.gpsFirmwareBuild}, " +
            "agps=${gps.agpsValidStartTimeMs}..${gps.agpsValidEndTimeMs}"

    private fun fail(message: String) {
        _uiState.update {
            it.copy(
                busy = false,
                status = str(R.string.feature_failed, message),
                phaseText = "",
            )
        }
        appendLog(str(R.string.feature_failed, message))
    }

    private fun appendLog(message: String) {
        val line = "${timeFmt.format(Date())}  $message"
        _uiState.update { state ->
            val next = (state.logs + line).takeLast(MAX_LOGS)
            state.copy(logs = next)
        }
    }

    companion object {
        private const val MAX_LOGS = 200
    }
}
