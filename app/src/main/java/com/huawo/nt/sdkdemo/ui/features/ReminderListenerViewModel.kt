package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.interfaces.callback.ReminderNotifyCallback
import com.huawo.sdk.bluetoothsdk.interfaces.event.ReminderNotifyType
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ReminderListenerViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "ReminderListenerVM"
        const val MAX_EVENTS = 100
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val events = ArrayDeque<String>()
    private var listener: ReminderNotifyCallback? = null
    private var session = 0L

    private fun str(resId: Int, vararg args: Any): String =
        LocaleHelper.localizedContext(getApplication()).getString(resId, *args)

    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.reminder_listener_ready)))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    fun startListening() {
        Log.i(TAG, "startListening enter registered=${listener != null}")
        if (listener != null) return // Repeated clicks must not add a second subscription.
        val currentSession = ++session
        val callback = object : ReminderNotifyCallback() {
            override fun onNotify(type: ReminderNotifyType, datas: ByteArray?) {
                val receivedAtMs = System.currentTimeMillis()
                // The SDK already decoded the TLV type; retain payload bytes without reinterpreting them.
                val payload = datas?.copyOf()
                mainHandler.post {
                    // Ignore callbacks already queued before stop/restart or page exit.
                    if (currentSession != session || listener == null) return@post
                    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(receivedAtMs))
                    val hex = payload?.joinToString(" ") {
                        (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase(Locale.ROOT)
                    }.orEmpty().ifEmpty { str(R.string.reminder_listener_empty_data) }
                    val enumValue = type.value.toString(16).padStart(2, '0').uppercase(Locale.ROOT)
                    val event = str(R.string.reminder_listener_event, time, type.name, enumValue, payload?.size ?: 0, hex)
                    events.addFirst(event)
                    if (events.size > MAX_EVENTS) events.removeLast()
                    Log.i(TAG, "onNotify session=$currentSession $event")
                    _uiState.update { it.copy(result = events.joinToString("\n\n")) }
                }
            }
        }
        try {
            listener = callback
            repository.addReminderNotifyListener(callback)
            _uiState.update { it.copy(status = str(R.string.reminder_listener_listening)) }
            Log.i(TAG, "startListening registered session=$currentSession")
        } catch (error: Exception) {
            session++
            listener = null
            showError("addReminderNotifyListener", error)
        }
    }

    fun stopListening(reason: String = "button") {
        Log.i(TAG, "stopListening enter reason=$reason registered=${listener != null}")
        val callback = listener ?: return
        session++
        try {
            // Remove the exact instance owned by this page; never clear other SDK consumers.
            repository.removeReminderNotifyListener(callback)
            listener = null
            _uiState.update { it.copy(status = str(R.string.reminder_listener_stopped)) }
            Log.i(TAG, "stopListening removed reason=$reason")
        } catch (error: Exception) {
            // Retain the instance so a later stop/clear can retry its removal.
            showError("removeReminderNotifyListener reason=$reason", error)
        }
    }

    private fun showError(api: String, error: Exception) {
        Log.e(TAG, "$api failed", error)
        _uiState.update {
            it.copy(status = str(R.string.reminder_listener_failed, api, error.message.orEmpty()))
        }
    }

    override fun onCleared() {
        stopListening("viewModelCleared")
        super.onCleared()
    }
}
