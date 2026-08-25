package com.huawo.nt.sdkdemo.ui.bind

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.BleDeviceInfo
import com.huawo.nt.sdkdemo.data.model.FlowStep
import com.huawo.nt.sdkdemo.data.model.FlowStepStatus
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BindUiState(
    val steps: List<FlowStep> = emptyList(),
    val finished: Boolean = false,
    val failed: Boolean = false,
    val error: String? = null,
    val deviceInfo: BleDeviceInfo? = null,
)

class BindFlowViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {

    private fun ctx() = LocaleHelper.localizedContext(getApplication())

    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    private val _uiState = MutableStateFlow(BindUiState(steps = buildSteps()))
    val uiState: StateFlow<BindUiState> = _uiState.asStateFlow()

    private fun buildSteps(): List<FlowStep> =
        listOf(
            FlowStep("BluetoothSDK.startBind()", str(R.string.bind_step_start), str(R.string.bind_note_62)),
            FlowStep("BluetoothSDK.setDeviceTime()", str(R.string.bind_step_time), str(R.string.bind_note_env)),
            FlowStep("BluetoothSDK.setUserInfo()", str(R.string.bind_step_user), str(R.string.bind_note_env)),
            FlowStep("BluetoothSDK.setUnit()", str(R.string.bind_step_unit), str(R.string.bind_note_env)),
            FlowStep("BluetoothSDK.setLanguage()", str(R.string.bind_step_language), str(R.string.bind_note_env)),
            FlowStep(
                "BluetoothSDK.getDeviceInfo()",
                str(R.string.bind_step_device_info),
                str(R.string.bind_note_save_local),
            ),
            FlowStep("BluetoothSDK.endBind()", str(R.string.bind_step_end), str(R.string.bind_note_62)),
            FlowStep("BluetoothSDK.isBonded()", str(R.string.bind_step_is_bonded), str(R.string.bind_note_61)),
            // HaWoFit createPair: turn on watch classic BT before createBond / when already bonded.
            FlowStep(
                "BluetoothSDK.setBTSwitch / turnOnBTSwitchWithOption",
                str(R.string.bind_step_bt_on),
                str(R.string.bind_note_bt_on),
            ),
            FlowStep(
                "BluetoothSDK.createBond()",
                str(R.string.bind_step_create_bond),
                str(R.string.bind_note_61_skip),
            ),
            FlowStep(
                "BluetoothSDK.setBind(true)",
                str(R.string.bind_step_set_bind),
                str(R.string.bind_note_set_bind),
            ),
        )

    fun start() {
        viewModelScope.launch {
            if (!repository.isConnected()) {
                _uiState.update {
                    it.copy(failed = true, error = str(R.string.error_device_not_connected))
                }
                return@launch
            }
            try {
                if (!runHard(0) { repository.startBind() }) return@launch
                if (!runHard(1) { repository.setDeviceTime() }) return@launch
                if (!runHard(2) { repository.setUserInfo() }) return@launch
                if (!runHard(3) { repository.setUnit(metric = true) }) return@launch
                if (!runHard(4) { repository.setLanguage(0) }) return@launch
                if (!fetchDeviceInfo(5)) return@launch
                if (!runHard(6) { repository.endBind() }) return@launch

                markRunning(7)
                val bonded = repository.isBonded()
                markDone(7, if (bonded) str(R.string.detail_paired) else str(R.string.detail_not_paired))

                // Turn on watch classic BT (required for createBond / HFP / SPP / AI SCO).
                // Already bonded → turnOnBTSwitchWithOption(autoConnect=true);
                // not bonded → setBTSwitch(true), wait briefly, then createBond.
                if (bonded) {
                    runSoft(8) { repository.turnOnBTSwitchWithOption(autoConnect = true) }
                    markSkipped(9, str(R.string.detail_skip_create_bond))
                } else {
                    runSoft(8) {
                        repository.setBTSwitch(true)
                        kotlinx.coroutines.delay(2_000)
                    }
                    runSoft(9) { repository.createBond() }
                }
                if (!runHard(10) { repository.setBind(true) }) return@launch
                _uiState.update { it.copy(finished = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(failed = true, error = e.message) }
            }
        }
    }

    private suspend fun fetchDeviceInfo(index: Int): Boolean {
        markRunning(index)
        return try {
            val info = repository.getDeviceInfo()
            val summary =
                listOfNotNull(
                    info.type?.let { "type=$it" },
                    info.firmwareVersion?.let { "fw=$it" },
                    info.mac?.let { "mac=$it" },
                    info.battery?.let { "bat=$it" },
                    info.protocolVersion?.let { "proto=$it" },
                ).joinToString(", ")
            markDone(index, summary.ifEmpty { str(R.string.detail_fetched) })
            _uiState.update { it.copy(deviceInfo = info) }
            true
        } catch (e: Exception) {
            markFailed(index, e)
            false
        }
    }

    private suspend fun runHard(index: Int, action: suspend () -> Unit): Boolean {
        markRunning(index)
        return try {
            action()
            markDone(index)
            true
        } catch (e: Exception) {
            markFailed(index, e)
            false
        }
    }

    private suspend fun runSoft(index: Int, action: suspend () -> Unit): Boolean {
        markRunning(index)
        return try {
            action()
            markDone(index)
            true
        } catch (e: Exception) {
            markSkipped(index, str(R.string.detail_skipped_fail, e.message.orEmpty()))
            false
        }
    }

    private fun markRunning(index: Int) = updateStep(index) {
        it.copy(status = FlowStepStatus.RUNNING, detail = null)
    }

    private fun markDone(index: Int, detail: String? = null) = updateStep(index) {
        it.copy(status = FlowStepStatus.DONE, detail = detail)
    }

    private fun markSkipped(index: Int, detail: String? = null) = updateStep(index) {
        it.copy(status = FlowStepStatus.SKIPPED, detail = detail)
    }

    private fun markFailed(index: Int, e: Exception) {
        updateStep(index) { it.copy(status = FlowStepStatus.FAILED, detail = e.message) }
        _uiState.update { it.copy(failed = true, error = e.message) }
    }

    private fun updateStep(index: Int, transform: (FlowStep) -> FlowStep) {
        _uiState.update { state ->
            val steps = state.steps.toMutableList()
            steps[index] = transform(steps[index])
            state.copy(steps = steps)
        }
    }
}
