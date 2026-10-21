package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.ai.AiCenter
import com.huawo.ai.event.AiEvent
import com.huawo.ai.handler.model.AiDeviceInfo
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.local.AiHealthRecord
import com.huawo.nt.sdkdemo.data.local.AiHealthReportStore
import com.huawo.nt.sdkdemo.util.AppLanguage
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.AiMeetingContent
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.AppStatus
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.HealthAnalysisResult
import com.huawo.watchface.custom.SifliCustomWatchface
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 手表侧运动健康分析：初始化 AiCenter 并接收 [AiEvent.healthDataAnalysisCompleted]。
 * 页面只展示和保存结果，不主动用模拟数据向云端要报告。
 */
class AiHealthAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    data class State(
        val mac: String = "",
        val status: String = "",
        val records: List<AiHealthRecord> = emptyList(),
        val selectedId: Long? = null,
    )

    private val store = AiHealthReportStore(application)
    private val context get() = getApplication<Application>()
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var bridge: HealthAiEvent? = null

    fun startListening() {
        val mac = BluetoothSDK.getConnectedDevice()?.mac.orEmpty()
        _state.update { current ->
            val records = store.list(mac)
            current.copy(
                mac = mac,
                records = records,
                selectedId = current.selectedId?.takeIf { id -> records.any { it.id == id } },
                status = if (mac.isBlank()) str(R.string.ai_health_need_device) else str(R.string.ai_health_waiting),
            )
        }
        if (mac.isBlank()) return
        installBridge()
        prepareCenter(mac)
    }

    fun stopListening() {
        val current = bridge ?: return
        bridge = null
        val center = AiCenter.getInstance()
        if (center.aiEvent === current) {
            center.setAiEvent(current.previous)
        }
    }

    fun show(id: Long) {
        if (_state.value.records.none { it.id == id }) return
        _state.update { it.copy(selectedId = id) }
    }

    private fun installBridge() {
        if (bridge != null) return
        val center = AiCenter.getInstance()
        val created = HealthAiEvent(center.aiEvent, ::onHealthResult)
        bridge = created
        center.setAiEvent(created)
    }

    private fun prepareCenter(mac: String) {
        try {
            val center = AiCenter.getInstance()
            val language = reportLanguage()
            val deviceId = mac.replace(":", "").replace("-", "").lowercase(Locale.ROOT)
            BluetoothSDK.setAppStatus(AppStatus.Foreground)
            center.init(context, false)
            center.setKeyAndSecret(AI_KEY, AI_SECRET)
            val connected = BluetoothSDK.getConnectedDevice()
            center.setDeviceInfo(
                AiDeviceInfo().apply {
                    setMac(mac)
                    setType(connected?.name.orEmpty())
                    setId(deviceId)
                    setWidth(480)
                    setHeight(480)
                    setThumbnailWidth(264)
                    setThumbnailHeight(264)
                    setCurrentLocale(language)
                    setSupportedLocales(arrayOf(language))
                },
            )
            center.startWorking()
            // startWorking 可能换掉监听；健康分析回调必须仍回到本页。
            if (center.aiEvent !== bridge) {
                bridge?.previous = center.aiEvent
                center.setAiEvent(bridge)
            }
            Log.i(TAG, "AiCenter ready for health analysis deviceId=$deviceId locale=$language")
        } catch (error: Exception) {
            Log.e(TAG, "AiCenter prepare failed", error)
            _state.update { it.copy(status = str(R.string.ai_health_init_failed, error.message ?: error.javaClass.simpleName)) }
        }
    }

    private fun onHealthResult(result: HealthAnalysisResult?, code: Int, message: String?) {
        viewModelScope.launch {
            val mac = _state.value.mac
            Log.i(TAG, "healthDataAnalysisCompleted code=$code hasResult=${result != null}")
            if (code == 0 && result != null && mac.isNotBlank()) {
                val saved = store.upsert(mac, result)
                _state.update {
                    it.copy(
                        status = str(R.string.ai_health_received),
                        records = store.list(mac),
                        selectedId = saved.id,
                    )
                }
            } else {
                val detail = "$code ${message.orEmpty()}".trim()
                _state.update { it.copy(status = str(R.string.ai_health_failed, detail)) }
            }
        }
    }

    private fun reportLanguage(): String {
        val localized = LocaleHelper.localizedContext(context)
        return when (LocaleHelper.getLanguage(context)) {
            AppLanguage.CHINESE -> "zh"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.SYSTEM ->
                if (localized.resources.configuration.locales[0].language.startsWith("zh")) "zh" else "en"
        }
    }

    private fun str(id: Int, vararg args: Any) =
        LocaleHelper.localizedContext(context).getString(id, *args)

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private class HealthAiEvent(
        var previous: AiEvent?,
        private val onHealth: (HealthAnalysisResult?, Int, String?) -> Unit,
    ) : AiEvent {
        override fun healthDataAnalysisCompleted(result: HealthAnalysisResult?, code: Int, msg: String?) {
            onHealth(result, code, msg)
            previous?.healthDataAnalysisCompleted(result, code, msg)
        }

        override fun initFailed(code: Int) {
            previous?.initFailed(code)
        }

        override fun deviceEnteredAiQA() {
            previous?.deviceEnteredAiQA()
        }

        override fun deviceExitedAiQA() {
            previous?.deviceExitedAiQA()
        }

        override fun deviceEnteredAiWatchface() {
            previous?.deviceEnteredAiWatchface()
        }

        override fun deviceExitedAiWatchface() {
            previous?.deviceExitedAiWatchface()
        }

        override fun deviceStartRecording(type: Int) {
            previous?.deviceStartRecording(type)
        }

        override fun deviceStopRecording(type: Int) {
            previous?.deviceStopRecording(type)
        }

        override fun deviceRequestAiAnswer() {
            previous?.deviceRequestAiAnswer()
        }

        override fun deviceRequestAiTranslateResult() {
            previous?.deviceRequestAiTranslateResult()
        }

        override fun deviceRequestAiAgentResult() {
            previous?.deviceRequestAiAgentResult()
        }

        override fun meetingResultCompleted(
            meetingTime: Date?,
            meetingContent: AiMeetingContent?,
            voiceFilePath: String?,
            code: Int,
            msg: String?,
        ) {
            previous?.meetingResultCompleted(meetingTime, meetingContent, voiceFilePath, code, msg)
        }

        override fun deviceShowingAiAnswer(text: String?, code: Int, msg: String?) {
            previous?.deviceShowingAiAnswer(text, code, msg)
        }

        override fun deviceShowingAiAgentResult(text: String?, code: Int, msg: String?) {
            previous?.deviceShowingAiAgentResult(text, code, msg)
        }

        override fun playingAnswerVoice(text: String?, code: Int, msg: String?) {
            previous?.playingAnswerVoice(text, code, msg)
        }

        override fun deviceAiImageCallback(image: Bitmap?, code: Int, message: String?) {
            previous?.deviceAiImageCallback(image, code, message)
        }

        override fun devicePerviewImageCallback(bitmap: Bitmap?, code: Int, msg: String?) {
            previous?.devicePerviewImageCallback(bitmap, code, msg)
        }

        override fun deviceRequestWathfacePreview(type: Int) {
            previous?.deviceRequestWathfacePreview(type)
        }

        override fun deviceRequestWatchface(type: Int) {
            previous?.deviceRequestWatchface(type)
        }

        override fun deviceStartedOtaWatchface() {
            previous?.deviceStartedOtaWatchface()
        }

        override fun deviceOtaWatchfaceProgressUpdated(progress: Float) {
            previous?.deviceOtaWatchfaceProgressUpdated(progress)
        }

        override fun deviceOtaWatchfaceDone(watchface: SifliCustomWatchface?, code: Int, errorMsg: String?) {
            previous?.deviceOtaWatchfaceDone(watchface, code, errorMsg)
        }

        override fun cancelAll() {
            previous?.cancelAll()
        }
    }

    private companion object {
        const val TAG = "AiHealthAnalysis"
        // 与现有 AI 表盘 Demo、HaWoFit NoScreen 使用同一套示例密钥。
        const val AI_KEY = "1f34e8ae-4bfc-4464-8fb0-1282200d7bac"
        const val AI_SECRET = "b66e29bb1125d8c5d27139f467822c16"
    }
}
