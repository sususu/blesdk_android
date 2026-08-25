package com.huawo.nt.sdkdemo.ui.unbind

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.FlowStep
import com.huawo.nt.sdkdemo.data.model.FlowStepStatus
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnbindUiState(
    val steps: List<FlowStep> = emptyList(),
    val finished: Boolean = false,
    val failed: Boolean = false,
    val error: String? = null,
)

/**
 * Unbind flow aligned with HaWoFit [DeviceComplexWorkflow.removePair]:
 *
 * 1. [BluetoothSDK.setBTSwitch]`(false)` — ask watch to turn **classic BT** off
 * 2. [BluetoothSDK.disconnect] — drop BLE
 * 3. [BluetoothSDK.removeBond]`(mac)` — remove classic BT pairing on the phone
 *    (must use MAC after disconnect; skip if not bonded)
 * 4. [BluetoothSDK.setBind]`(false)` + clear local bound record
 *
 * Classic BT unpair is required so the next bind/SCO/SPP cycle does not reuse a stale bond.
 */
class UnbindFlowViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {

    private fun ctx() = LocaleHelper.localizedContext(getApplication())

    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    private val _uiState = MutableStateFlow(UnbindUiState(steps = buildSteps()))
    val uiState: StateFlow<UnbindUiState> = _uiState.asStateFlow()

    private fun buildSteps(): List<FlowStep> =
        listOf(
            FlowStep(
                "BluetoothSDK.setBTSwitch(false)",
                str(R.string.unbind_step_bt_off),
                str(R.string.unbind_note_bt_off),
            ),
            FlowStep(
                "BluetoothSDK.disconnect()",
                str(R.string.unbind_step_disconnect),
                str(R.string.unbind_note_disconnect),
            ),
            FlowStep(
                "BluetoothSDK.removeBond(mac)",
                str(R.string.unbind_step_remove_bond),
                str(R.string.unbind_note_remove_bond),
            ),
            FlowStep(
                "BluetoothSDK.setBind(false)",
                str(R.string.unbind_step_set_bind),
                str(R.string.unbind_note_set_bind),
            ),
            FlowStep(
                "BoundDeviceStore.clear()",
                str(R.string.unbind_step_clear_local),
                str(R.string.unbind_note_local),
            ),
        )

    fun start() {
        viewModelScope.launch {
            try {
                // Capture MAC before disconnect so removeBond still works afterward.
                val mac =
                    repository.connectedDeviceMac()
                        ?: repository.loadBoundDevice()?.macAddress
                        ?: ""

                // 1) Turn off watch classic BT radio (HaWoFit removePair first step).
                runSoft(0) { repository.setBTSwitch(false) }

                // 2) Disconnect BLE GATT.
                runSoft(1) { repository.disconnect() }

                // 3) Remove classic BT pairing from the phone system bond list.
                if (mac.isBlank()) {
                    markSkipped(2, str(R.string.unbind_detail_no_mac))
                } else if (!repository.isBonded(mac)) {
                    markSkipped(2, str(R.string.unbind_detail_not_bonded, mac))
                } else {
                    runSoft(2) { repository.removeBondByMac(mac) }
                }

                // 4–5) Clear SDK bind flag + local persistence.
                if (!runHard(3) { repository.setBind(false) }) return@launch
                if (!runHard(4) { repository.clearBoundDevice() }) return@launch
                _uiState.update { it.copy(finished = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(failed = true, error = e.message) }
            }
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
