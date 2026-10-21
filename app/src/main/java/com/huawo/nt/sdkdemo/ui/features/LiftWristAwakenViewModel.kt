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

/** Reads the device switch before enabling the control. A failed save restores the display only. */
class LiftWristAwakenViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    data class State(
        val enabled: Boolean? = null,
        val requested: Boolean? = null,
        val pending: Boolean = false,
        val needsRead: Boolean = true,
        val error: String? = null,
        val busy: Boolean = false,
        val message: String = "",
    )

    private companion object {
        const val TAG = "LiftWristAwakenVM"
    }

    private fun str(id: Int, vararg args: Any) =
        LocaleHelper.localizedContext(getApplication()).getString(id, *args)

    private val _uiState = MutableStateFlow(State())
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var version = 0L
    private var deviceMac: String? = null

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

    fun refresh() {
        if (_uiState.value.busy) return
        deviceMac = repository.currentConnectedMac()
        if (deviceMac.isNullOrBlank() || !repository.isConnected()) {
            Log.w(TAG, "refresh rejected: no connected device")
            _uiState.value = State(message = str(R.string.auto_monitoring_need_connection))
            return
        }
        val token = ++version
        _uiState.value = State(pending = true, busy = true, message = str(R.string.lift_wrist_reading))
        requestJob = viewModelScope.launch {
            Log.i(TAG, "getFlipLightUpEnable enter request=$token mac=$deviceMac")
            try {
                check(sameDevice()) { str(R.string.auto_monitoring_need_connection) }
                val enabled = repository.getFlipLightUpEnable()
                check(sameDevice()) { str(R.string.auto_monitoring_need_connection) }
                if (token != version) return@launch
                _uiState.value = State(
                    enabled = enabled,
                    needsRead = false,
                    message = str(R.string.auto_monitoring_read_success),
                )
                Log.i(TAG, "getFlipLightUpEnable success value=$enabled request=$token")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != version) return@launch
                Log.e(TAG, "getFlipLightUpEnable failed request=$token", error)
                val detail = "getFlipLightUpEnable: ${error.message ?: error.javaClass.simpleName}"
                val message = str(R.string.auto_monitoring_read_error, detail)
                _uiState.value = State(needsRead = true, error = message, message = str(R.string.lift_wrist_read_failed))
            } finally {
                if (token == version) {
                    requestJob = null
                    _uiState.update { it.copy(busy = false, pending = false) }
                }
            }
        }.takeIf { it.isActive }
    }

    /** A toggle needs a confirmed value. ACK stores it; failure restores the old display. */
    fun setEnabled(on: Boolean): Boolean {
        val previous = _uiState.value
        if (previous.busy || previous.needsRead || previous.enabled == null) return false
        if (!sameDevice()) {
            _uiState.value = State(message = str(R.string.auto_monitoring_need_connection))
            return false
        }
        if (previous.enabled == on) return false
        val token = ++version
        _uiState.value = previous.copy(
            requested = on,
            pending = true,
            error = null,
            busy = true,
            message = str(R.string.lift_wrist_saving),
        )
        requestJob = viewModelScope.launch {
            Log.i(TAG, "setFlipLightUpEnable enter on=$on request=$token mac=$deviceMac")
            try {
                repository.setFlipLightUpEnable(on)
                check(sameDevice()) { str(R.string.auto_monitoring_need_connection) }
                if (token != version) return@launch
                _uiState.value = State(
                    enabled = on,
                    needsRead = false,
                    message = str(
                        R.string.auto_monitoring_set_ok,
                        "${str(R.string.home_lift_wrist)} · setFlipLightUpEnable=$on",
                    ),
                )
                Log.i(TAG, "setFlipLightUpEnable acknowledged on=$on request=$token")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != version) return@launch
                Log.e(TAG, "setFlipLightUpEnable failed on=$on request=$token", error)
                val message = str(
                    R.string.auto_monitoring_save_error,
                    "setFlipLightUpEnable: ${error.message ?: error.javaClass.simpleName}",
                )
                _uiState.value = previous.copy(needsRead = true, error = message, message = message, busy = false)
            } finally {
                if (token == version) {
                    requestJob = null
                    _uiState.update { it.copy(busy = false, pending = false, requested = null) }
                }
            }
        }.takeIf { it.isActive }
        return true
    }

    private fun sameDevice() = repository.isConnected() && deviceMac != null &&
        deviceMac.equals(repository.currentConnectedMac(), ignoreCase = true)

    fun cancelRequest(reason: String) {
        val job = requestJob ?: return
        version++
        requestJob = null
        job.cancel()
        _uiState.update {
            it.copy(
                busy = false,
                pending = false,
                requested = null,
                needsRead = true,
                message = str(R.string.auto_monitoring_canceled),
            )
        }
        Log.i(TAG, "cancel request reason=$reason version=$version mac=$deviceMac")
    }

    override fun onCleared() {
        cancelRequest("viewModelCleared")
        super.onCleared()
    }
}
