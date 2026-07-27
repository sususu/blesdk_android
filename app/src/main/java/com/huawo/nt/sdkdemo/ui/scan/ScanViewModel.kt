package com.huawo.nt.sdkdemo.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.BleDevice
import com.huawo.nt.sdkdemo.data.model.ScanEvent
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val devices: List<BleDevice> = emptyList(),
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val status: String = "",
)

class ScanViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {

    private val _uiState =
        MutableStateFlow(ScanUiState(status = str(R.string.status_scan_hint)))
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _connectedDevice = MutableSharedFlow<BleDevice>(extraBufferCapacity = 1)
    val connectedDevice: SharedFlow<BleDevice> = _connectedDevice.asSharedFlow()

    private var scanJob: Job? = null

    private fun ctx() = LocaleHelper.localizedContext(getApplication())

    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    fun startScan() {
        if (_uiState.value.scanning || _uiState.value.connecting) return
        scanJob?.cancel()
        _uiState.update {
            it.copy(
                scanning = true,
                devices = emptyList(),
                status = str(R.string.status_scanning),
            )
        }
        scanJob =
            viewModelScope.launch {
                try {
                    repository.scanDevices(timeoutMs = 10_000L).collect { event ->
                        when (event) {
                            is ScanEvent.Started ->
                                _uiState.update {
                                    it.copy(
                                        status = str(
                                            if (event.success) {
                                                R.string.status_scanning
                                            } else {
                                                R.string.status_scan_start_failed
                                            },
                                        ),
                                    )
                                }
                            is ScanEvent.Result ->
                                _uiState.update { state ->
                                    val merged = state.devices.toMutableList()
                                    val index =
                                        merged.indexOfFirst { it.macAddress == event.device.macAddress }
                                    if (index >= 0) merged[index] = event.device else merged.add(event.device)
                                    merged.sortByDescending { it.rssi ?: -999 }
                                    state.copy(devices = merged)
                                }
                            is ScanEvent.Finished ->
                                _uiState.update {
                                    it.copy(
                                        devices = event.devices.sortedByDescending { d -> d.rssi ?: -999 },
                                        scanning = false,
                                        status = str(R.string.status_scan_finished, event.devices.size),
                                    )
                                }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            status = str(R.string.status_scan_failed, e.message.orEmpty()),
                        )
                    }
                } finally {
                    _uiState.update { it.copy(scanning = false) }
                }
            }
    }

    fun stopScan() {
        scanJob?.cancel()
        repository.stopScan()
        _uiState.update {
            it.copy(scanning = false, status = str(R.string.status_scan_stopped))
        }
    }

    fun connect(device: BleDevice) {
        if (_uiState.value.connecting) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connecting = true,
                    status = str(
                        R.string.status_connecting,
                        device.name ?: device.macAddress,
                    ),
                )
            }
            try {
                repository.stopScan()
                scanJob?.cancel()
                val connected = repository.connect(device.macAddress, timeoutSeconds = 30)
                _connectedDevice.emit(connected)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connecting = false,
                        status = str(R.string.status_connect_failed, e.message.orEmpty()),
                    )
                }
            }
        }
    }
}
