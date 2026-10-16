package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Read real settings before enabling controls. Save failure rolls back the UI, never the device. */
class AutoMonitoringViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    enum class Metric(val labelRes: Int, val readApi: String, val writeApi: String) {
        HEART(R.string.auto_monitoring_heart, "getHeartrateMonitorInterval", "setHeartrateMonitorInterval"),
        SPO2(R.string.auto_monitoring_spo2, "getSpO2MonitorEnable", "setSpO2MonitorEnable"),
        STRESS(R.string.auto_monitoring_stress, "getStressMonitorEnable", "setStressMonitorEnable"),
        SKIN(R.string.auto_monitoring_skin, "getSkinTemperatureMonitorInterval", "setSkinTemperatureMonitorInterval"),
        BREATHS(R.string.auto_monitoring_breaths, "getRespiratoryRateMonitorInterval", "setRespiratoryRateMonitorInterval"),
    }

    data class Setting(
        val value: Int? = null,
        val requested: Boolean? = null,
        val pending: Boolean = false,
        val needsRead: Boolean = true,
        val error: String? = null,
    )
    data class State(
        val settings: Map<Metric, Setting> = Metric.entries.associateWith { Setting() },
        val busy: Boolean = false,
        val message: String = "",
    )

    private companion object {
        const val TAG = "AutoMonitoringVM"
        // Keep the approved demo values; the layout mock does not redefine the SDK arguments.
        const val HEART_INTERVAL = 10
        const val MONITOR_INTERVAL = 60
    }
    private fun str(id: Int, vararg args: Any) =
        LocaleHelper.localizedContext(getApplication()).getString(id, *args)
    private val _uiState = MutableStateFlow(State())
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var version = 0L
    private var deviceMac: String? = null
    private var saving: Metric? = null

    init {
        viewModelScope.launch {
            repository.connectionEvents.collect {
                if (deviceMac != null && !sameDevice()) {
                    cancelRequest("deviceChanged")
                    deviceMac = null
                    _uiState.value = State(message = str(R.string.auto_monitoring_need_connection))
                }
            }
        }
    }

    /** Refresh performs only independent getter calls, serially; rendering their values never sends setters. */
    fun refresh() {
        if (_uiState.value.busy) return
        deviceMac = repository.currentConnectedMac()
        if (deviceMac.isNullOrBlank() || !repository.isConnected()) {
            Log.w(TAG, "refresh rejected: no connected device")
            _uiState.value = State(message = str(R.string.auto_monitoring_need_connection))
            return
        }
        val token = ++version
        _uiState.value = State(
            settings = Metric.entries.associateWith { Setting(pending = true) },
            busy = true, message = str(R.string.auto_monitoring_getting),
        )
        requestJob = viewModelScope.launch {
            Log.i(TAG, "refresh enter request=$token mac=$deviceMac")
            try {
                for (metric in Metric.entries) {
                    try {
                        check(sameDevice()) { str(R.string.auto_monitoring_need_connection) }
                        Log.i(TAG, "${metric.readApi} enter request=$token")
                        val value = read(metric)
                        check(sameDevice()) { str(R.string.auto_monitoring_need_connection) }
                        if (token != version) return@launch
                        require(value in 0..255) { "${metric.readApi} invalid value=$value" }
                        updateSetting(metric) { Setting(value = value, needsRead = false) }
                        Log.i(TAG, "${metric.readApi} success value=$value request=$token")
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (token != version) return@launch
                        Log.e(TAG, "${metric.readApi} failed request=$token", error)
                        val detail = "${metric.readApi}: ${error.message ?: error.javaClass.simpleName}"
                        updateSetting(metric) { Setting(error = str(R.string.auto_monitoring_read_error, detail)) }
                    }
                }
                if (token == version) _uiState.update { state ->
                    state.copy(message = str(if (Metric.entries.any { state.settings.getValue(it).needsRead })
                        R.string.auto_monitoring_read_failed else R.string.auto_monitoring_read_success))
                }
            } finally {
                if (token == version) {
                    requestJob = null
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }.takeIf { it.isActive }
    }

    /** A switch needs a confirmed value. ACK updates it; failure restores display and requires a fresh read. */
    fun setMonitoring(metric: Metric, on: Boolean): Boolean {
        val state = _uiState.value
        val previous = state.settings.getValue(metric)
        if (state.busy || previous.needsRead || previous.value == null) return false
        if (!sameDevice()) {
            _uiState.value = State(message = str(R.string.auto_monitoring_need_connection))
            return false
        }
        if ((previous.value > 0) == on) return false
        val token = ++version
        val value = if (!on) 0 else when (metric) {
            Metric.HEART -> HEART_INTERVAL
            Metric.SKIN, Metric.BREATHS -> MONITOR_INTERVAL
            else -> 1
        }
        val argument = if (metric == Metric.SPO2 || metric == Metric.STRESS) on.toString() else value.toString()
        saving = metric
        _uiState.update { it.copy(busy = true, message = str(R.string.auto_monitoring_setting),
            settings = it.settings + (metric to previous.copy(requested = on, pending = true, error = null))) }
        requestJob = viewModelScope.launch {
            Log.i(TAG, "${metric.writeApi} enter argument=$argument request=$token mac=$deviceMac")
            try {
                write(metric, on, value)
                check(sameDevice()) { str(R.string.auto_monitoring_need_connection) }
                if (token != version) return@launch
                updateSetting(metric) { Setting(value = value, needsRead = false) }
                _uiState.update { it.copy(message = str(R.string.auto_monitoring_set_ok,
                    "${str(metric.labelRes)} · ${metric.writeApi}=$argument")) }
                Log.i(TAG, "${metric.writeApi} acknowledged argument=$argument request=$token")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != version) return@launch
                Log.e(TAG, "${metric.writeApi} failed argument=$argument request=$token", error)
                // A failure/timeout is not proof that an already sent command did not take effect.
                // Restore the old display, but disable further writes until Refresh confirms the device.
                val message = str(R.string.auto_monitoring_save_error, "${metric.writeApi}: ${error.message ?: error.javaClass.simpleName}")
                updateSetting(metric) { previous.copy(needsRead = true, error = message) }
                _uiState.update { it.copy(message = message) }
            } finally {
                if (token == version) {
                    saving = null
                    requestJob = null
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }.takeIf { it.isActive }
        return true
    }

    private suspend fun read(metric: Metric): Int = when (metric) {
        Metric.HEART -> repository.getHeartrateMonitorInterval()
        Metric.SPO2 -> if (repository.getSpO2MonitorEnable()) 1 else 0
        Metric.STRESS -> if (repository.getStressMonitorEnable()) 1 else 0
        Metric.SKIN -> repository.getSkinTemperatureMonitorInterval()
        Metric.BREATHS -> repository.getRespiratoryRateMonitorInterval()
    }

    private suspend fun write(metric: Metric, on: Boolean, value: Int) {
        when (metric) {
            Metric.HEART -> repository.setHeartrateMonitorInterval(value)
            Metric.SPO2 -> repository.setSpO2MonitorEnable(on)
            Metric.STRESS -> repository.setStressMonitorEnable(on)
            Metric.SKIN -> repository.setSkinTemperatureMonitorInterval(value)
            Metric.BREATHS -> repository.setRespiratoryRateMonitorInterval(value)
        }
    }

    private fun updateSetting(metric: Metric, transform: (Setting) -> Setting) {
        _uiState.update { it.copy(settings = it.settings + (metric to transform(it.settings.getValue(metric)))) }
    }

    private fun sameDevice() = repository.isConnected() && deviceMac != null &&
        deviceMac.equals(repository.currentConnectedMac(), ignoreCase = true)

    fun cancelRequest(reason: String) {
        val job = requestJob ?: return
        version++
        requestJob = null
        job.cancel()
        // Cancel this page's wait only. Never clear the SDK queue, turn monitoring off or send a rollback.
        _uiState.update { state -> state.copy(busy = false, message = str(R.string.auto_monitoring_canceled),
            settings = state.settings.mapValues { (metric, setting) ->
                if (setting.pending || metric == saving) setting.copy(requested = null, pending = false, needsRead = true)
                else setting
            }) }
        saving = null
        Log.i(TAG, "cancel request reason=$reason version=$version mac=$deviceMac")
    }

    override fun onCleared() {
        cancelRequest("viewModelCleared")
        super.onCleared()
    }
}
