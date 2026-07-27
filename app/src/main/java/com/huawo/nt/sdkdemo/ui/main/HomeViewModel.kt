package com.huawo.nt.sdkdemo.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.BleDevice
import com.huawo.nt.sdkdemo.data.model.BleDeviceInfo
import com.huawo.nt.sdkdemo.data.model.ConnectionEvent
import com.huawo.nt.sdkdemo.data.model.DevicePhase
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val status: String = "",
    val phase: DevicePhase = DevicePhase.IDLE,
    val sdkVersion: String? = null,
    val device: BleDevice? = null,
    val bound: Boolean = false,
    val syncSummary: String = "",
    val busy: Boolean = false,
    val logs: List<String> = emptyList(),
)

class HomeViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {

    private val _uiState =
        MutableStateFlow(HomeUiState(status = str(R.string.status_uninitialized)))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var manualDisconnect = false
    private var reconnecting = false
    private var reconnectJob: Job? = null

    init {
        bootstrap()
    }

    private fun ctx() = LocaleHelper.localizedContext(getApplication())

    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    private fun bootstrap() {
        viewModelScope.launch {
            try {
                repository.init(maxMtu = 247)
                val ver = repository.getVersion()
                observeConnection()

                val saved = repository.loadBoundDevice()
                if (saved != null) {
                    val device = BleDevice(name = saved.name, macAddress = saved.macAddress)
                    _uiState.update {
                        it.copy(
                            sdkVersion = ver,
                            device = device,
                            bound = true,
                            phase = DevicePhase.BOUND,
                            status = str(
                                R.string.status_bound_local,
                                saved.name ?: saved.macAddress,
                            ),
                        )
                    }
                    appendLog(str(R.string.status_init_ok, ver, 247))
                    appendLog(str(R.string.log_load_bound, saved.macAddress))
                    saved.deviceInfo?.let { info ->
                        appendLog(
                            str(
                                R.string.log_local_device_info,
                                info.type.orEmpty(),
                                info.firmwareVersion.orEmpty(),
                                info.protocolVersion?.toString().orEmpty(),
                            ),
                        )
                    }
                    runCatching { repository.setBind(true) }
                    enableAutoReconnect()
                    tryReconnect(reason = str(R.string.reason_boot_restore))
                } else {
                    _uiState.update {
                        it.copy(sdkVersion = ver, status = str(R.string.status_sdk_ready))
                    }
                    appendLog(str(R.string.status_init_ok, ver, 247))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(status = str(R.string.status_init_failed, e.message.orEmpty()))
                }
                appendLog(str(R.string.status_init_failed, e.message.orEmpty()))
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            repository.connectionEvents.collect { event ->
                when (event) {
                    is ConnectionEvent.Connected -> {
                        reconnectJob?.cancel()
                        reconnecting = false
                        _uiState.update { state ->
                            val device =
                                if (!event.macAddress.isNullOrEmpty()) {
                                    BleDevice(name = event.deviceName, macAddress = event.macAddress)
                                } else {
                                    state.device
                                }
                            state.copy(
                                device = device,
                                phase = if (state.bound) DevicePhase.BOUND else DevicePhase.CONNECTED,
                                status = str(
                                    R.string.status_connected,
                                    event.deviceName ?: event.macAddress.orEmpty(),
                                ),
                            )
                        }
                        appendLog(str(R.string.log_conn_connected))
                    }
                    ConnectionEvent.Disconnected -> {
                        val state = _uiState.value
                        if (state.phase == DevicePhase.UNBINDING) return@collect
                        _uiState.update {
                            when {
                                !it.bound ->
                                    it.copy(
                                        phase = DevicePhase.IDLE,
                                        status = str(R.string.status_disconnected),
                                    )
                                manualDisconnect ->
                                    it.copy(status = str(R.string.status_bound_manual_disconnect))
                                else ->
                                    it.copy(status = str(R.string.status_bound_reconnect_pending))
                            }
                        }
                        appendLog(
                            if (manualDisconnect) {
                                str(R.string.log_conn_manual_disconnect)
                            } else {
                                str(R.string.log_conn_unexpected_disconnect)
                            },
                        )
                        if (state.bound && !manualDisconnect) {
                            scheduleReconnect()
                        }
                    }
                }
            }
        }
    }

    fun onDeviceConnected(device: BleDevice) {
        manualDisconnect = false
        _uiState.update {
            it.copy(
                device = device,
                phase = if (it.bound) DevicePhase.BOUND else DevicePhase.CONNECTED,
                status = str(R.string.status_connected, device.name ?: device.macAddress),
            )
        }
        appendLog(str(R.string.log_connect_success, device.macAddress))
        if (_uiState.value.bound) {
            enableAutoReconnect()
        }
    }

    fun onBindSuccess(deviceInfo: BleDeviceInfo?) {
        val device = _uiState.value.device ?: return
        repository.saveBoundDevice(device.macAddress, device.name, deviceInfo)
        appendLog(str(R.string.log_bound_saved, device.macAddress))
        deviceInfo?.let {
            appendLog(
                str(
                    R.string.log_device_info,
                    it.type.orEmpty(),
                    it.firmwareVersion.orEmpty(),
                    it.protocolVersion?.toString().orEmpty(),
                    it.battery?.toString().orEmpty(),
                ),
            )
        }
        _uiState.update {
            it.copy(
                bound = true,
                phase = DevicePhase.BOUND,
                status = str(R.string.status_bind_success),
                busy = false,
            )
        }
        appendLog(str(R.string.log_bind_done))
        enableAutoReconnect()
    }

    fun onBindCancelled() {
        _uiState.update {
            it.copy(
                phase = DevicePhase.CONNECTED,
                status = str(R.string.status_bind_incomplete),
                busy = false,
            )
        }
        appendLog(str(R.string.log_bind_incomplete))
    }

    fun onUnbindSuccess() {
        _uiState.update {
            it.copy(
                bound = false,
                device = null,
                phase = DevicePhase.IDLE,
                status = str(R.string.status_unbound),
                syncSummary = "",
                busy = false,
            )
        }
        appendLog(str(R.string.log_bound_cleared))
        appendLog(str(R.string.log_unbind_done))
    }

    fun onUnbindCancelled(failed: Boolean) {
        _uiState.update {
            it.copy(
                phase = DevicePhase.BOUND,
                status = str(
                    if (failed) R.string.status_unbind_incomplete else R.string.status_unbind_cancelled,
                ),
                busy = false,
            )
        }
        appendLog(str(R.string.log_unbind_incomplete))
        if (_uiState.value.device != null) {
            enableAutoReconnect()
        }
    }

    fun prepareBind(): Boolean {
        val state = _uiState.value
        if (state.busy) return false
        if (state.device == null || !repository.isConnected()) {
            _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
            return false
        }
        _uiState.update { it.copy(busy = true) }
        appendLog(str(R.string.log_open_bind))
        return true
    }

    fun prepareUnbind(): Boolean {
        val state = _uiState.value
        if (state.busy) return false
        if (!state.bound) {
            _uiState.update { it.copy(status = str(R.string.status_not_bound)) }
            return false
        }
        _uiState.update {
            it.copy(
                busy = true,
                phase = DevicePhase.UNBINDING,
                status = str(R.string.status_unbinding),
            )
        }
        appendLog(str(R.string.log_open_unbind))
        disableAutoReconnect()
        return true
    }

    fun prepareFeature(): Boolean {
        if (_uiState.value.busy) return false
        if (!repository.isConnected()) {
            _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
            return false
        }
        return true
    }

    fun sync() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busy = true,
                    phase = DevicePhase.SYNCING,
                    status = str(R.string.status_syncing),
                    syncSummary = "",
                )
            }
            appendLog(str(R.string.log_sync_start))
            try {
                if (!repository.isConnected()) {
                    val target = _uiState.value.device
                        ?: error(str(R.string.error_no_device_to_sync))
                    repository.connect(target.macAddress)
                }
                val count = repository.getHealthDataCount()
                val activities =
                    if (count.activityCount > 0) {
                        repository.getActivities(count.activityCount)
                    } else {
                        emptyList()
                    }
                val heartrates = repository.getHeartrates()
                val sleeps = repository.getSleeps()
                val totalSteps = activities.sumOf { it.step }
                val summary =
                    str(
                        R.string.sync_summary,
                        activities.size,
                        totalSteps,
                        heartrates.size,
                        sleeps.size,
                    )
                if (activities.isNotEmpty()) runCatching { repository.deleteSports() }
                if (heartrates.isNotEmpty()) runCatching { repository.deleteHeartrates() }
                if (sleeps.isNotEmpty()) runCatching { repository.deleteSleeps() }

                _uiState.update {
                    it.copy(
                        phase = if (it.bound) DevicePhase.BOUND else DevicePhase.CONNECTED,
                        status = str(R.string.status_sync_done),
                        syncSummary = summary,
                        busy = false,
                    )
                }
                appendLog(str(R.string.log_sync_done, summary.replace("\n", " / ")))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = if (it.bound) DevicePhase.BOUND else DevicePhase.CONNECTED,
                        status = str(R.string.status_sync_failed, e.message.orEmpty()),
                        busy = false,
                    )
                }
                appendLog(str(R.string.log_sync_failed, e.message.orEmpty()))
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                manualDisconnect = true
                disableAutoReconnect()
                repository.disconnect()
                _uiState.update {
                    it.copy(
                        phase = if (it.bound) DevicePhase.BOUND else DevicePhase.IDLE,
                        status = str(
                            if (it.bound) {
                                R.string.status_bound_manual_disconnect
                            } else {
                                R.string.status_disconnected_manual
                            },
                        ),
                    )
                }
                appendLog(str(R.string.log_manual_disconnect))
            } catch (e: Exception) {
                appendLog(str(R.string.log_disconnect_failed, e.message.orEmpty()))
            }
        }
    }

    private fun enableAutoReconnect() {
        manualDisconnect = false
        val mac = _uiState.value.device?.macAddress
        if (mac != null) appendLog(str(R.string.log_auto_reconnect_on, mac))
    }

    private fun disableAutoReconnect() {
        reconnectJob?.cancel()
        reconnecting = false
        appendLog(str(R.string.log_auto_reconnect_off))
    }

    private fun scheduleReconnect(delayMs: Long = 2000L) {
        val state = _uiState.value
        if (!state.bound || manualDisconnect || state.device == null) return
        reconnectJob?.cancel()
        reconnectJob =
            viewModelScope.launch {
                delay(delayMs)
                tryReconnect(reason = str(R.string.reason_reconnect))
            }
    }

    private suspend fun tryReconnect(reason: String) {
        val state = _uiState.value
        val device = state.device ?: return
        if (!state.bound || manualDisconnect) return
        if (reconnecting) return
        if (state.phase == DevicePhase.UNBINDING || state.phase == DevicePhase.SYNCING) return
        if (repository.isConnected()) return

        reconnecting = true
        _uiState.update { it.copy(status = str(R.string.status_reconnecting, reason)) }
        appendLog(str(R.string.log_reconnect_start, reason, device.macAddress))
        try {
            repository.connect(device.macAddress, timeoutSeconds = 30)
            _uiState.update {
                it.copy(
                    phase = DevicePhase.BOUND,
                    status = str(
                        R.string.status_reconnected,
                        device.name ?: device.macAddress,
                    ),
                )
            }
            appendLog(str(R.string.log_reconnect_success))
        } catch (e: Exception) {
            appendLog(str(R.string.log_reconnect_failed, e.message.orEmpty()))
            if (_uiState.value.bound && !manualDisconnect) {
                _uiState.update { it.copy(status = str(R.string.status_reconnect_failed_retry)) }
                scheduleReconnect(delayMs = 5000L)
            }
        } finally {
            reconnecting = false
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _uiState.update { state ->
            val logs = listOf("$time  $msg") + state.logs
            state.copy(logs = logs.take(80))
        }
    }
}
