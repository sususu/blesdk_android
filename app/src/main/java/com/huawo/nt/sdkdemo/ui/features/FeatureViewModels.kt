package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Goal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeatureUiState(
    val status: String = "",
    val result: String = "",
    val busy: Boolean = false,
)

class GoalsViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    fun getGoals() = runAction(R.string.goals_getting) {
        val goal = repository.getGoals()
        formatGoal(goal)
    }

    fun setDemoGoals() = runAction(R.string.goals_setting) {
        repository.setDemoGoals()
        val goal = repository.getGoals()
        str(R.string.goals_set_ok) + "\n" + formatGoal(goal)
    }

    private fun formatGoal(goal: Goal): String =
        buildString {
            appendLine(str(R.string.goals_step, goal.step * 100, goal.step))
            appendLine(str(R.string.goals_calorie, goal.calorie))
            appendLine(str(R.string.goals_distance, goal.distance))
            appendLine(str(R.string.goals_sleep, goal.sleep))
            appendLine(str(R.string.goals_duration, goal.duration))
            appendLine(str(R.string.goals_ot_km, goal.otDistance / 10.0))
            append(str(R.string.goals_ot_mile, goal.otDistanceMile / 10.0))
        }

    private fun runAction(statusRes: Int, block: suspend () -> String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = str(statusRes)) }
            try {
                val result = block()
                _uiState.update {
                    it.copy(busy = false, status = str(R.string.feature_success), result = result)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = str(R.string.feature_failed, e.message.orEmpty()),
                        result = e.message.orEmpty(),
                    )
                }
            }
        }
    }
}

class AlarmsViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    fun getAlarms() = runAction(R.string.alarms_getting) {
        val list = repository.getAlarms()
        if (list.isEmpty()) str(R.string.alarms_empty)
        else list.joinToString("\n") { alarm ->
            val time = alarm.firstTimePoint?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--"
            "#${alarm.id} $time on=${alarm.isOn} ${alarm.content.orEmpty()} week=${alarm.weekStringForDebug}"
        }
    }

    fun addDemoAlarm() = runAction(R.string.alarms_adding) {
        val alarm = repository.createDemoAlarm(content = str(R.string.alarms_demo_content))
        repository.addAlarm(alarm)
        str(R.string.alarms_add_ok)
    }

    fun deleteAllAlarms() = runAction(R.string.alarms_deleting) {
        repository.delAllAlarms()
        str(R.string.alarms_delete_all_ok)
    }

    fun getSedentary() = runAction(R.string.reminders_getting) {
        val r = repository.getSedentaryReminder()
        "on=${r.isOn} ${r.startTime}-${r.endTime} interval=${r.interval}s week=${r.weekStringForDebug}"
    }

    fun setSedentary() = runAction(R.string.reminders_setting) {
        repository.setSedentaryReminder(repository.createDemoSedentaryReminder())
        str(R.string.reminders_sedentary_ok)
    }

    fun setDrinkWater() = runAction(R.string.reminders_setting) {
        repository.setDrinkWaterReminder(repository.createDemoDrinkWaterReminder())
        str(R.string.reminders_drink_ok)
    }

    fun setWashHand() = runAction(R.string.reminders_setting) {
        repository.setWashHandReminder(repository.createDemoWashHandReminder())
        str(R.string.reminders_wash_ok)
    }

    private fun runAction(statusRes: Int, block: suspend () -> String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = str(statusRes)) }
            try {
                val result = block()
                _uiState.update {
                    it.copy(busy = false, status = str(R.string.feature_success), result = result)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = str(R.string.feature_failed, e.message.orEmpty()),
                        result = e.message.orEmpty(),
                    )
                }
            }
        }
    }
}

class NotifyViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    private val demoName = "Demo Caller"
    private val demoNumber = "10086"

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    fun getSwitches() = runAction(R.string.notify_getting_switches) {
        val list = repository.getSocialAppSwitches()
        if (list.isEmpty()) str(R.string.notify_switches_empty)
        else list.joinToString("\n") { "type=${it.type} on=${it.s()}" }
    }

    fun enableDemoSwitches() = runAction(R.string.notify_setting_switches) {
        repository.setSocialAppSwitch(
            com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialType.Wechat,
            true,
        )
        repository.setSocialAppSwitch(
            com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialType.SMS,
            true,
        )
        repository.setSocialAppSwitch(
            com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialType.IncomingCall,
            true,
        )
        str(R.string.notify_switches_ok)
    }

    fun pushDemoMessage() = runAction(R.string.notify_pushing) {
        repository.pushMessage(
            type = com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialType.SMS,
            title = str(R.string.notify_demo_title),
            content = str(R.string.notify_demo_content),
        )
        str(R.string.notify_push_ok)
    }

    fun hangUpCall() = runAction(R.string.notify_hanging) {
        repository.hangUpCall(demoName, demoNumber)
        str(R.string.notify_hangup_ok)
    }

    fun setDemoContacts() = runAction(R.string.notify_setting_contacts) {
        repository.setContacts(repository.createDemoContacts())
        str(R.string.notify_contacts_ok)
    }

    fun setEmergency() = runAction(R.string.notify_setting_emergency) {
        repository.setEmergencyContact("Emergency", "120")
        str(R.string.notify_emergency_ok)
    }

    private fun runAction(statusRes: Int, block: suspend () -> String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = str(statusRes)) }
            try {
                val result = block()
                _uiState.update {
                    it.copy(busy = false, status = str(R.string.feature_success), result = result)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = str(R.string.feature_failed, e.message.orEmpty()),
                        result = e.message.orEmpty(),
                    )
                }
            }
        }
    }
}
