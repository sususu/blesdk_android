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
import com.huawo.sdk.bluetoothsdk.interfaces.callback.WorkoutTypeRecognitionCallback
import com.huawo.sdk.bluetoothsdk.interfaces.event.ReminderNotifyType
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 提醒与运动识别是两组独立订阅，设备自识别开关在自动监测页独立操作。
 * 分别保存监听版本；退出只清理本页订阅，不关闭设备开关或启动运动。
 */
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
    // 本地接收状态与实例分开：移除失败仍保留实例供重试，但旧会话不再展示事件。
    private var reminderListening = false
    private var workoutRecognitionListener: WorkoutTypeRecognitionCallback? = null
    private var workoutRecognitionSession = 0L
    private var workoutRecognitionListening = false

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
                // SDK 没有在此回调中提供设备事件时间，这里记录手机实际接收时间。
                val receivedAtMs = System.currentTimeMillis()
                // The SDK already decoded the TLV type; retain payload bytes without reinterpreting them.
                val payload = datas?.copyOf()
                mainHandler.post {
                    // Ignore callbacks already queued before stop/restart or page exit.
                    if (currentSession != session || listener == null) return@post
                    val time = receivedTime(receivedAtMs)
                    val hex = payload?.joinToString(" ") {
                        (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase(Locale.ROOT)
                    }.orEmpty().ifEmpty { str(R.string.reminder_listener_empty_data) }
                    val enumValue = type.value.toString(16).padStart(2, '0').uppercase(Locale.ROOT)
                    val event = str(R.string.reminder_listener_event, time, type.name, enumValue, payload?.size ?: 0, hex)
                    appendEvent(event)
                    Log.i(TAG, "onNotify session=$currentSession $event")
                }
            }
        }
        try {
            listener = callback
            repository.addReminderNotifyListener(callback)
            reminderListening = true
            showListeningStatus()
            Log.i(TAG, "startListening registered session=$currentSession")
        } catch (error: Exception) {
            session++
            listener = null
            reminderListening = false
            showError("addReminderNotifyListener", error)
        }
    }

    fun stopListening(reason: String = "button") {
        Log.i(TAG, "stopListening enter reason=$reason registered=${listener != null}")
        val callback = listener ?: return
        session++
        reminderListening = false
        try {
            // Remove the exact instance owned by this page; never clear other SDK consumers.
            repository.removeReminderNotifyListener(callback)
            listener = null
            showListeningStatus()
            Log.i(TAG, "stopListening removed reason=$reason")
        } catch (error: Exception) {
            // Retain the instance so a later stop/clear can retry its removal.
            showError("removeReminderNotifyListener reason=$reason", error)
        }
    }

    fun startWorkoutRecognitionListening() {
        Log.i(TAG, "startWorkoutRecognitionListening enter registered=${workoutRecognitionListener != null}")
        if (workoutRecognitionListener != null) return
        val currentSession = ++workoutRecognitionSession
        val callback = object : WorkoutTypeRecognitionCallback() {
            override fun onUpdate(cate: Int) {
                // SDK 只给出类别，不带设备事件时间或附带字节；这里记录手机接收时间。
                val receivedAtMs = System.currentTimeMillis()
                mainHandler.post {
                    // 独立版本只屏蔽旧运动识别事件，不影响同时开启的提醒监听。
                    if (currentSession != workoutRecognitionSession || workoutRecognitionListener == null) return@post
                    val event = str(
                        R.string.workout_recognition_listener_event,
                        receivedTime(receivedAtMs),
                        cate,
                        workoutRecognitionName(cate),
                    )
                    appendEvent(event)
                    Log.i(TAG, "onWorkoutRecognition session=$currentSession cate=$cate $event")
                }
            }
        }
        try {
            workoutRecognitionListener = callback
            repository.addWorkoutTypeRecognitionCallback(callback)
            workoutRecognitionListening = true
            showListeningStatus()
            Log.i(TAG, "startWorkoutRecognitionListening registered session=$currentSession")
        } catch (error: Exception) {
            workoutRecognitionSession++
            workoutRecognitionListener = null
            workoutRecognitionListening = false
            showError("addWorkoutTypeRecognitionCallback", error)
        }
    }

    fun stopWorkoutRecognitionListening(reason: String = "button") {
        Log.i(TAG, "stopWorkoutRecognitionListening enter reason=$reason registered=${workoutRecognitionListener != null}")
        val callback = workoutRecognitionListener ?: return
        workoutRecognitionSession++
        workoutRecognitionListening = false
        try {
            repository.removeWorkoutTypeRecognitionCallback(callback)
            workoutRecognitionListener = null
            showListeningStatus()
            Log.i(TAG, "stopWorkoutRecognitionListening removed reason=$reason")
        } catch (error: Exception) {
            // 失败时保留原实例供再次停止重试；旧会话的排队事件已失效。
            showError("removeWorkoutTypeRecognitionCallback reason=$reason", error)
        }
    }

    private fun receivedTime(receivedAtMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(receivedAtMs))

    /** cate 沿用 HaWoFit 的类别映射，不直接转换为 WorkoutType；未知类别仍展示原始值。 */
    private fun workoutRecognitionName(cate: Int): String = str(
        when (cate) {
            1 -> R.string.workout_recognition_walking
            2 -> R.string.workout_recognition_running
            3 -> R.string.workout_recognition_cycling
            4 -> R.string.workout_recognition_elliptical
            5 -> R.string.workout_recognition_rowing
            else -> R.string.workout_recognition_unknown
        },
    )

    private fun appendEvent(event: String) {
        events.addFirst(event)
        if (events.size > MAX_EVENTS) events.removeLast()
        _uiState.update { it.copy(result = events.joinToString("\n\n")) }
    }

    private fun showListeningStatus() {
        val status = str(
            R.string.reminder_listener_status,
            str(if (reminderListening) R.string.reminder_listener_listening else R.string.reminder_listener_stopped),
            str(if (workoutRecognitionListening) R.string.workout_recognition_listener_listening else R.string.workout_recognition_listener_stopped),
        )
        _uiState.update { it.copy(status = status) }
    }

    private fun showError(api: String, error: Exception) {
        Log.e(TAG, "$api failed", error)
        _uiState.update {
            it.copy(status = str(R.string.reminder_listener_failed, api, error.message.orEmpty()))
        }
    }

    override fun onCleared() {
        stopListening("viewModelCleared")
        stopWorkoutRecognitionListening("viewModelCleared")
        super.onCleared()
    }
}
