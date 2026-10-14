package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AutoMonitoringViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "AutoMonitoringViewModel"
        const val HEART_RATE_SAMPLE_INTERVAL = 10
        // Match HaWoFit's actual skin-temperature/respiratory-rate arguments: enabled=60, disabled=0.
        const val MONITOR_SAMPLE_INTERVAL = 60
    }

    private fun str(resId: Int, vararg args: Any): String =
        LocaleHelper.localizedContext(getApplication()).getString(resId, *args)

    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    fun getHeartrateInterval() = runAction("getHeartrateMonitorInterval", R.string.auto_monitoring_getting) {
        intervalResult(R.string.auto_monitoring_heart, repository.getHeartrateMonitorInterval())
    }

    fun setDemoHeartrateInterval() = runAction(
        "setHeartrateMonitorInterval interval=$HEART_RATE_SAMPLE_INTERVAL",
        R.string.auto_monitoring_setting,
    ) {
        repository.setHeartrateMonitorInterval(HEART_RATE_SAMPLE_INTERVAL)
        str(R.string.auto_monitoring_set_ok, intervalResult(R.string.auto_monitoring_heart, HEART_RATE_SAMPLE_INTERVAL))
    }

    fun getSpO2Monitoring() = runAction("getSpO2MonitorEnable", R.string.auto_monitoring_getting) {
        switchResult(R.string.auto_monitoring_spo2, repository.getSpO2MonitorEnable())
    }

    fun setSpO2Monitoring(on: Boolean) = runAction("setSpO2MonitorEnable on=$on", R.string.auto_monitoring_setting) {
        repository.setSpO2MonitorEnable(on)
        str(R.string.auto_monitoring_set_ok, switchResult(R.string.auto_monitoring_spo2, on))
    }

    fun getStressMonitoring() = runAction("getStressMonitorEnable", R.string.auto_monitoring_getting) {
        switchResult(R.string.auto_monitoring_stress, repository.getStressMonitorEnable())
    }

    fun setStressMonitoring(on: Boolean) = runAction("setStressMonitorEnable on=$on", R.string.auto_monitoring_setting) {
        repository.setStressMonitorEnable(on)
        str(R.string.auto_monitoring_set_ok, switchResult(R.string.auto_monitoring_stress, on))
    }

    fun getSkinTemperatureMonitoring() = runAction("getSkinTemperatureMonitorInterval", R.string.auto_monitoring_getting) {
        intervalResult(R.string.auto_monitoring_skin, repository.getSkinTemperatureMonitorInterval())
    }

    fun setSkinTemperatureMonitoring(on: Boolean) {
        val interval = if (on) MONITOR_SAMPLE_INTERVAL else 0
        runAction("setSkinTemperatureMonitorInterval interval=$interval", R.string.auto_monitoring_setting) {
            repository.setSkinTemperatureMonitorInterval(interval)
            str(R.string.auto_monitoring_set_ok, intervalResult(R.string.auto_monitoring_skin, interval))
        }
    }

    fun getRespiratoryRateMonitoring() = runAction("getRespiratoryRateMonitorInterval", R.string.auto_monitoring_getting) {
        intervalResult(R.string.auto_monitoring_breaths, repository.getRespiratoryRateMonitorInterval())
    }

    fun setRespiratoryRateMonitoring(on: Boolean) {
        val interval = if (on) MONITOR_SAMPLE_INTERVAL else 0
        runAction("setRespiratoryRateMonitorInterval interval=$interval", R.string.auto_monitoring_setting) {
            repository.setRespiratoryRateMonitorInterval(interval)
            str(R.string.auto_monitoring_set_ok, intervalResult(R.string.auto_monitoring_breaths, interval))
        }
    }

    private fun switchResult(labelRes: Int, on: Boolean): String =
        str(
            R.string.auto_monitoring_switch_value,
            str(labelRes),
            str(if (on) R.string.reminders_on else R.string.reminders_off),
            on.toString(),
        )

    private fun intervalResult(labelRes: Int, interval: Int): String =
        str(
            R.string.auto_monitoring_interval_value,
            str(labelRes),
            str(if (interval > 0) R.string.reminders_on else R.string.reminders_off),
            interval,
        )

    private fun runAction(api: String, statusRes: Int, block: suspend () -> String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            // Clear the last result so an old successful setting is not shown during a failed read.
            _uiState.update { it.copy(busy = true, status = str(statusRes), result = "") }
            Log.i(TAG, "$api enter")
            try {
                val result = block()
                Log.i(TAG, "$api success: $result")
                _uiState.update { it.copy(busy = false, status = str(R.string.feature_success), result = result) }
            } catch (error: CancellationException) {
                Log.i(TAG, "$api canceled")
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "$api failed", error)
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = str(R.string.feature_failed, error.message.orEmpty()),
                        result = "$api: ${error.message.orEmpty()}",
                    )
                }
            }
        }
    }
}
