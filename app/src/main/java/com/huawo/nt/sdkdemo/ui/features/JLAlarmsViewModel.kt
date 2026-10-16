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

/** Demo ID reservations are not firmware capability detection; each button calls its own SDK workflow. */
class JLAlarmsViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "JLAlarmsViewModel"
        val ALARM_IDS = 1..5
        val REMINDER_IDS = 6..10
    }

    private fun str(id: Int, vararg args: Any): String =
        LocaleHelper.localizedContext(getApplication()).getString(id, *args)

    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0L

    fun getAlarms() = runAction("getAlarms") {
        val list = repository.getAlarms()
        if (list.isEmpty()) str(R.string.alarms_empty)
        else list.joinToString("\n") { "#${it.id} ${it.timeStringForDebug} on=${it.isOn} ${it.content.orEmpty()} week=${it.weekStringForDebug}" }
    }

    fun addDemoAlarm() = runAction("addAlarmV2 weekdays=07:30 snooze=10") {
        val usedIds = readUsedIds()
        val alarmId = ALARM_IDS.firstOrNull { it !in usedIds }
            ?: error(str(R.string.jl_alarms_no_available_id))
        val alarm = repository.createDemoAlarm(content = str(R.string.alarms_demo_content)).apply {
            id = alarmId
            snooze = 10
        }
        Log.i(TAG, "addAlarmV2 selectedId=$alarmId usedIds=$usedIds")
        repository.addAlarmV2(alarm)
        "${str(R.string.alarms_add_ok)} #$alarmId"
    }

    fun deleteAllAlarms() = runAction("delAllAlarms") {
        repository.delAllAlarms()
        str(R.string.alarms_delete_all_ok)
    }

    fun getReminders() = runAction("getReminders") {
        val list = repository.getReminders()
        if (list.isEmpty()) str(R.string.jl_reminders_empty)
        else list.joinToString("\n") { "#${it.id} ${it.timeStringForDebug} on=${it.isOn} type=${it.type?.name.orEmpty()} ${it.content.orEmpty()} week=${it.weekStringForDebug}" }
    }

    fun addDemoReminder() = runAction("addReminderV2 weekdays=10:00 type=Custom") {
        val usedIds = readUsedIds()
        val reminderId = REMINDER_IDS.firstOrNull { it !in usedIds }
            ?: error(str(R.string.jl_reminders_no_available_id))
        val reminder = repository.createDemoReminder(str(R.string.jl_reminders_demo_content)).apply { id = reminderId }
        Log.i(TAG, "addReminderV2 selectedId=$reminderId usedIds=$usedIds")
        repository.addReminderV2(reminder)
        "${str(R.string.jl_reminders_add_ok)} #$reminderId"
    }

    fun deleteAllReminders() = runAction("delAllReminder") {
        repository.delAllReminders()
        str(R.string.jl_reminders_delete_all_ok)
    }

    /** IDs are shared: check both collections before selecting an unused reserved ID. Read failure aborts creation. */
    private suspend fun readUsedIds(): Set<Int> {
        val alarms = repository.getAlarms()
        val reminders = repository.getReminders()
        return (alarms.map { it.id } + reminders.map { it.id }).toSet()
    }

    fun getLowBatteryReminder() = runAction("getLowBatteryReminderSwitch") {
        val on = repository.getLowBatteryReminderSwitch()
        str(R.string.reminders_low_battery_value, switchValue(on))
    }

    fun setLowBatteryReminder(on: Boolean) = runAction("setLowBatteryReminderSwitch on=$on") {
        repository.setLowBatteryReminderSwitch(on)
        str(R.string.reminders_low_battery_set_ok, switchValue(on))
    }

    fun getVibration() = runAction("getVibrationSettings") {
        val settings = repository.getVibrationSettings()
        formatVibration(settings.isOn, settings.intensity)
    }

    fun setVibration(on: Boolean, intensity: Int) = runAction("setVibrationSettings on=$on intensity=$intensity") {
        repository.setVibrationSettings(on, intensity)
        str(R.string.reminders_vibration_set_ok, formatVibration(on, intensity))
    }

    private fun formatVibration(on: Boolean, intensity: Int): String {
        if (!on) return str(R.string.reminders_vibration_disabled)
        val level = when (intensity) {
            33 -> R.string.reminders_level_weak
            66 -> R.string.reminders_level_medium
            99 -> R.string.reminders_level_strong
            else -> R.string.reminders_level_unknown
        }
        return str(R.string.reminders_vibration_value, str(level), intensity)
    }

    fun getAutoDetectWorkoutsSwitch() = runAction("getAutoDetectWorkoutsSwitch") {
        val enabled = repository.getAutoDetectWorkoutsSwitch()
        str(R.string.workout_recognition_switch_value, switchValue(enabled), enabled.toString())
    }

    fun setAutoDetectWorkoutsSwitch(enabled: Boolean) = runAction("setAutoDetectWorkoutsSwitch enabled=$enabled") {
        repository.setAutoDetectWorkoutsSwitch(enabled)
        // An ACK is not a readback or an incoming recognition/reminder event.
        str(R.string.workout_recognition_switch_set_ok, switchValue(enabled), enabled.toString())
    }

    private fun switchValue(on: Boolean): String = str(if (on) R.string.reminders_on else R.string.reminders_off)

    private fun runAction(api: String, block: suspend () -> String) {
        if (_uiState.value.busy) return
        val version = ++requestVersion
        _uiState.update { it.copy(busy = true, status = str(R.string.jl_alarms_running, api), result = "") }
        requestJob = viewModelScope.launch {
            Log.i(TAG, "$api enter request=$version")
            try {
                val result = block()
                if (version != requestVersion) return@launch
                Log.i(TAG, "$api success request=$version: $result")
                _uiState.update { it.copy(status = str(R.string.feature_success), result = result) }
            } catch (error: CancellationException) {
                Log.i(TAG, "$api canceled request=$version")
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                Log.e(TAG, "$api failed request=$version", error)
                _uiState.update { it.copy(status = str(R.string.feature_failed, error.message.orEmpty()),
                    result = "$api: ${error.message.orEmpty()}") }
            } finally {
                if (version == requestVersion) {
                    requestJob = null
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }.takeIf { it.isActive }
    }

    fun cancelRequest(reason: String) {
        val job = requestJob ?: return
        requestVersion++
        requestJob = null
        job.cancel()
        // Stop waiting for our result; queued/sent SDK commands may still take effect. Never send a rollback.
        _uiState.update { it.copy(busy = false, status = str(R.string.jl_alarms_request_canceled), result = "") }
        Log.i(TAG, "cancelRequest reason=$reason request=$requestVersion")
    }

    override fun onCleared() {
        cancelRequest("viewModelCleared")
        super.onCleared()
    }
}
