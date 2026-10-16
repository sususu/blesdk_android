package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.SdkException
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.interfaces.callback.BoolCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.FloatValueWithTimeCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.IntValueWithTimeCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.MonitorStateChangedCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.NotProperlyWornCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DataType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 管理一次真实设备测量：指令确认、测量值和设备结束事件分别处理。
 * 测量未确认停止时保留当前会话并允许重试，防止其他开始按钮启动重叠测量。
 * 会话编号隔离上一轮迟到回调，设备断开或切换时清理本地等待状态。
 */
class ManualMeasurementViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "ManualMeasurementVM"
        const val COMMAND_TIMEOUT_MS = 30_000L
        const val MEASURE_TIMEOUT_MS = 60_000L
    }

    private enum class Phase { IDLE, STARTING, MEASURING, STOPPING, BLOCKED }
    private val main = Handler(Looper.getMainLooper())
    private var phase = Phase.IDLE
    private var activeType: DataType? = null
    private var deviceMac: String? = null
    private var generation = 0L
    private var stopRequested = false
    private var stopAck = false
    private var stopEvent = false
    // 同一会话可能多次重试停止，只有最新一次停止指令的应答可以更新状态。
    private var stopAttempt = 0
    private var commandTimeout: Runnable? = null
    private var measureTimeout: Runnable? = null
    private val valueRemovers = mutableListOf<() -> Unit>()
    private var stopRemover: (() -> Unit)? = null

    private fun str(resId: Int, vararg args: Any): String =
        LocaleHelper.localizedContext(getApplication()).getString(resId, *args)

    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connectionEvents.collect {
                if (activeType != null && !sameDevice()) {
                    Log.w(TAG, "connection lost/changed type=$activeType mac=$deviceMac")
                    finish(R.string.manual_measure_disconnected)
                }
            }
        }
    }

    /** 连接有效且没有未结束会话时，先注册本次监听，再发送指定类型的开始指令。 */
    fun start(type: DataType) {
        Log.i(TAG, "start enter type=$type phase=$phase")
        if (activeType != null) return
        if (!repository.isConnected() || repository.currentConnectedMac().isNullOrBlank()) {
            Log.w(TAG, "start rejected: no connected device type=$type")
            _uiState.update { it.copy(status = str(R.string.manual_measure_disconnected), result = "") }
            return
        }
        val token = ++generation
        activeType = type
        deviceMac = repository.currentConnectedMac()
        phase = Phase.STARTING
        stopRequested = false
        stopAttempt = 0
        _uiState.value = FeatureUiState(status = str(R.string.manual_measure_starting, type.name), busy = true)
        try {
            registerCallbacks(token, type)
            armCommandTimeout(token) { startFailed(token, TimeoutException("startMonitoring type=$type timeout")) }
            repository.startMonitoring(type, object : BoolCallback() {
                override fun onSuccess() = postCurrent(token) {
                    if (phase != Phase.STARTING) return@postCurrent
                    clearCommandTimeout()
                    phase = Phase.MEASURING
                    Log.i(TAG, "start acknowledged type=$type session=$token stopRequested=$stopRequested")
                    // ACK means the command was accepted, not that a measurement has completed.
                    if (stopRequested) stop("deferredUntilStartAck") else {
                        _uiState.update { it.copy(status = str(R.string.manual_measure_measuring, type.name)) }
                        measureTimeout = Runnable {
                            if (isCurrent(token) && phase == Phase.MEASURING) {
                                Log.w(TAG, "measurement timeout type=$type session=$token")
                                _uiState.update { it.copy(result = str(R.string.manual_measure_timeout)) }
                                stop("measurementTimeout")
                            }
                        }.also { main.postDelayed(it, MEASURE_TIMEOUT_MS) }
                    }
                }

                override fun onFail(code: Int) = postCurrent(token) {
                    // SetTask forwards a signed device status byte; SDK timeout/send/parse/cancel
                    // errors use codes outside that range and leave the device start outcome uncertain.
                    val confirmedFailure = code != 0 && code in Byte.MIN_VALUE..Byte.MAX_VALUE
                    startFailed(token, SdkException(code, "startMonitoring failed type=$type"), confirmedFailure)
                }
            })
        } catch (error: Exception) {
            startFailed(token, error)
        }
    }

    private fun registerCallbacks(token: Long, type: DataType) {
        // Timestamped numeric callbacks do not carry a metric type: only this session may consume them.
        if (type == DataType.SkinTemperature) {
            val valueCallback = FloatValueWithTimeCallback { value, timeMs ->
                postCurrent(token) { receiveValue(value, timeMs) }
            }
            repository.addRealtimeFloatWithTimeListener(valueCallback)
            valueRemovers += { repository.removeRealtimeFloatWithTimeListener(valueCallback) }
        } else {
            val valueCallback = IntValueWithTimeCallback { value, timeMs ->
                postCurrent(token) { receiveValue(value, timeMs) }
            }
            repository.addRealtimeIntWithTimeListener(valueCallback)
            valueRemovers += { repository.removeRealtimeIntWithTimeListener(valueCallback) }
        }
        val wornCallback = object : NotProperlyWornCallback() {
            override fun onAppear() = postCurrent(token) {
                if (phase == Phase.MEASURING && !stopRequested) {
                    Log.w(TAG, "not properly worn type=$type session=$token")
                    _uiState.update { it.copy(status = str(R.string.manual_measure_worn), result = "") }
                }
            }
        }
        repository.addNotProperlyWornCallback(wornCallback)
        valueRemovers += { repository.removeNotProperlyWornCallback(wornCallback) }
        val stoppedCallback = object : MonitorStateChangedCallback() {
            override fun onStop(stoppedType: DataType) = postCurrent(token) {
                if (stoppedType != activeType) return@postCurrent
                Log.i(TAG, "device stopped type=$stoppedType phase=$phase session=$token")
                when (phase) {
                    Phase.MEASURING -> finish(R.string.manual_measure_completed)
                    Phase.STOPPING, Phase.BLOCKED -> {
                        stopEvent = true
                        finishStopIfReady()
                    }
                    else -> Log.i(TAG, "ignore stop event before start acknowledgement")
                }
            }
        }
        repository.addMonitorStateChangedCallback(stoppedCallback)
        stopRemover = { repository.removeMonitorStateChangedCallback(stoppedCallback) }
    }

    private fun receiveValue(value: Number, timeMs: Long) {
        if (phase != Phase.MEASURING || stopRequested) return
        if (!sameDevice()) { finish(R.string.manual_measure_disconnected); return }
        // Keep Celsius floating-point and SDK millisecond timestamps; do not invent missing readings.
        if (!value.toDouble().isFinite() || value.toDouble() <= 0 || timeMs <= 0) {
            Log.w(TAG, "invalid reading type=$activeType value=$value timeMs=$timeMs")
            _uiState.update { it.copy(result = str(R.string.manual_measure_invalid, value.toString(), timeMs)) }
            return
        }
        val type = activeType ?: return
        val reading = if (type == DataType.SkinTemperature) "$value °C" else value.toString()
        val deviceTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMs))
        Log.i(TAG, "value type=$type value=$value timeMs=$timeMs session=$generation")
        _uiState.update {
            it.copy(
                status = str(R.string.manual_measure_measuring, type.name),
                result = str(R.string.manual_measure_value, type.name, reading, deviceTime, timeMs),
            )
        }
    }

    /** 按钮、超时或页面退出均复用此入口；等待开始确认时先记录停止意图。 */
    fun stop(reason: String = "button") {
        val type = activeType ?: return
        Log.i(TAG, "stop enter type=$type phase=$phase reason=$reason")
        stopRequested = true
        // Stop delivering values immediately on exit, but retain the stop callback until confirmation.
        removeValueCallbacks()
        measureTimeout?.let { main.removeCallbacks(it) }
        if (!sameDevice()) { finish(R.string.manual_measure_disconnected); return }
        // A page exit during STARTING still has to stop the device after its start ACK arrives.
        if (phase == Phase.STARTING) {
            _uiState.update { it.copy(status = str(R.string.manual_measure_stop_pending)) }
            return
        }
        if (phase == Phase.STOPPING) return
        val token = generation
        val attempt = ++stopAttempt
        phase = Phase.STOPPING
        stopAck = false
        stopEvent = false
        _uiState.update { it.copy(status = str(R.string.manual_measure_stopping), busy = true) }
        armCommandTimeout(token) { stopFailed(TimeoutException("stopMonitoring type=$type confirmation timeout")) }
        try {
            repository.stopMonitoring(type, object : BoolCallback() {
                override fun onSuccess() = postCurrent(token) {
                    if (attempt != stopAttempt) return@postCurrent
                    Log.i(TAG, "stop acknowledged type=$type attempt=$attempt session=$token")
                    stopAck = true
                    finishStopIfReady()
                }

                override fun onFail(code: Int) = postCurrent(token) {
                    if (attempt == stopAttempt) stopFailed(SdkException(code, "stopMonitoring failed type=$type"))
                }
            })
        } catch (error: Exception) {
            stopFailed(error)
        }
    }

    private fun startFailed(token: Long, error: Exception, confirmedFailure: Boolean = false) {
        if (!isCurrent(token) || phase != Phase.STARTING) return
        Log.e(TAG, "start failed type=$activeType session=$token confirmedFailure=$confirmedFailure", error)
        clearCommandTimeout()
        _uiState.update { it.copy(result = error.message.orEmpty()) }
        if (confirmedFailure) {
            // A rejected start needs local cleanup, not another device stop command or stop event.
            finish(R.string.manual_measure_start_failed)
            return
        }
        // Timeout, transport/parsing failure or cancellation does not prove the device never started.
        phase = Phase.BLOCKED
        stop("startFailed")
    }

    private fun stopFailed(error: Exception) {
        Log.e(TAG, "stop unconfirmed type=$activeType ack=$stopAck event=$stopEvent", error)
        clearCommandTimeout()
        phase = Phase.BLOCKED
        _uiState.update { it.copy(status = str(R.string.manual_measure_stop_failed, error.message.orEmpty()), busy = true) }
    }

    private fun finishStopIfReady() {
        // 停止应答和设备结束事件分别记录，不能收到其中一个就提前释放会话。
        if (stopAck && stopEvent) finish(R.string.manual_measure_stopped)
    }

    private fun finish(statusRes: Int) {
        Log.i(TAG, "finish type=$activeType phase=$phase status=$statusRes session=$generation")
        // 先令已排队的旧回调失效，再解绑监听和恢复按钮，避免清理期间被旧数据回写。
        generation++
        clearCommandTimeout()
        measureTimeout?.let { main.removeCallbacks(it) }
        removeValueCallbacks()
        try { stopRemover?.invoke() } catch (error: Exception) { Log.e(TAG, "remove stop listener failed", error) }
        stopRemover = null
        activeType = null
        deviceMac = null
        phase = Phase.IDLE
        _uiState.update {
            it.copy(status = str(statusRes), busy = false, result = it.result.ifBlank { str(R.string.manual_measure_no_value) })
        }
    }

    private fun removeValueCallbacks() {
        valueRemovers.forEach { remove ->
            try { remove() } catch (error: Exception) { Log.e(TAG, "remove measurement listener failed", error) }
        }
        valueRemovers.clear()
    }

    private fun sameDevice(): Boolean = repository.isConnected() && deviceMac != null &&
        deviceMac.equals(repository.currentConnectedMac(), ignoreCase = true)

    private fun isCurrent(token: Long): Boolean = token == generation && activeType != null

    private fun postCurrent(token: Long, action: () -> Unit) {
        main.post { if (isCurrent(token)) action() }
    }

    private fun clearCommandTimeout() {
        commandTimeout?.let { main.removeCallbacks(it) }
        commandTimeout = null
    }

    private fun armCommandTimeout(token: Long, action: () -> Unit) {
        clearCommandTimeout()
        commandTimeout = Runnable { if (isCurrent(token)) action() }.also { main.postDelayed(it, COMMAND_TIMEOUT_MS) }
    }

    override fun onCleared() {
        // Final activity teardown cannot rely on a canceled coroutine waiting for the start ACK.
        val type = activeType
        if (type != null && phase != Phase.STOPPING && sameDevice()) {
            try {
                repository.stopMonitoring(type, object : BoolCallback() {
                    override fun onSuccess() { Log.i(TAG, "teardown stop acknowledged type=$type") }
                    override fun onFail(code: Int) { Log.e(TAG, "teardown stop failed type=$type code=$code") }
                })
            } catch (error: Exception) { Log.e(TAG, "teardown stop failed type=$type", error) }
        }
        finish(R.string.manual_measure_stopped)
        main.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
