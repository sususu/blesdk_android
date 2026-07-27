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
                "BluetoothSDK.removeBond()",
                str(R.string.unbind_step_remove_bond),
                str(R.string.unbind_note_remove_bond),
            ),
            FlowStep(
                "BluetoothSDK.disconnect()",
                str(R.string.unbind_step_disconnect),
                str(R.string.unbind_note_disconnect),
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
                runSoft(0) { repository.removeBond() }
                runSoft(1) { repository.disconnect() }
                if (!runHard(2) { repository.setBind(false) }) return@launch
                if (!runHard(3) { repository.clearBoundDevice() }) return@launch
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
