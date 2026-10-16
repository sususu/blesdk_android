package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adf.oa.OTek
import com.adf.oa.contracts.init.DeviceConfig
import com.adf.oa.contracts.init.InitConfig
import com.google.gson.Gson
import com.huawo.ai.AiCenter
import com.huawo.ai.handler.impl.AFlashBodyStatusReportHandler
import com.huawo.ai.handler.impl.AFlashInsightReportHandler
import com.huawo.ai.handler.impl.AFlashScreenlessHealthAnalysisHandler
import com.huawo.ai.handler.impl.AFlashSleepReportHandler
import com.huawo.ai.handler.model.AiBodyStatusRequest
import com.huawo.ai.handler.model.AiDeviceInfo
import com.huawo.ai.handler.model.AiInsightRequest
import com.huawo.ai.handler.model.AiSleepRequest
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.SdkException
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.HealthData
import com.qw.lib.AFlash
import com.qw.lib.data.WatchInfo
import com.qw.lib.enums.PaymentModel
import com.qw.lib.enums.Region
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * 无屏 AI 分析示例：固定健康入参实际请求云端，结果仅供页面展示。
 * 洞察、身体状态和三种睡眠报告共用页面进入时完成的 OmniAgent 初始化；
 * 运动健康分析保留独立的 AFlash 初始化流程，本页分别管理初始化等待与报告生成任务。
 */
class AiAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    enum class Report { HEALTH, INSIGHT, BODY, DAY, WEEK, MONTH }
    private companion object {
        const val TAG = "AiAnalysisVM"
        // Demo defaults match HaWoFit NoScreen's initializeOmniAgent configuration.
        const val DEMO_CHANNEL_ID = "3ba3da7850ef1216"
        const val DEMO_APP_KEY = "wvpN7oWEDNMobV5NqlZ2KMVgNy3UVYIHGTBilfcPDWmu6akkAn+Ha7K5C5oKGAgbB+ZKtog7tNo/hv2/dHf5BQbSDngVlbT+HD7SPQ1b4F1psCR2PaTPlLS6dauYYQc4iNgxR0LurPjpBfcxXgkQUq9R"
    }
    private val context get() = getApplication<Application>()
    private fun str(id: Int, vararg args: Any) = LocaleHelper.localizedContext(context).getString(id, *args)
    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var initializationJob: Job? = null
    // 每次开始或取消任务都会更新版本，防止旧页面的完成／取消处理覆盖新任务状态。
    private var operationVersion = 0L
    private val _omniReady = MutableStateFlow(false)
    val omniReady = _omniReady.asStateFlow()
    var deviceId = ""
        private set
    var channelId = DEMO_CHANNEL_ID
        private set
    // Editable overrides stay in this ViewModel; do not log channel credentials.
    var appKey = DEMO_APP_KEY
        private set

    /** 输入变化只使旧配置失效；鉴权请求由重新初始化按钮发起，避免逐字输入时重复请求。 */
    fun updateConfiguration(deviceId: String, channelId: String, appKey: String) {
        if (this.deviceId == deviceId && this.channelId == channelId && this.appKey == appKey) return
        // Editable parameters invalidate the previous identity/auth state, not only the UI flag.
        clearOmniReadiness()
        this.deviceId = deviceId
        this.channelId = channelId
        this.appKey = appKey
        _uiState.update { it.copy(status = str(R.string.ai_analysis_config_changed), result = "") }
    }

    /**
     * 页面进入或手动重试时初始化 OmniAgent，成功后才允许提交对应五项报告。
     * deviceId、channelId、appKey 为本次配置快照；此入口不初始化 AFlash。
     */
    fun initialize(deviceId: String, channelId: String, appKey: String) {
        if (_uiState.value.busy) return
        val version = ++operationVersion
        clearOmniReadiness()
        this.deviceId = deviceId
        this.channelId = channelId
        this.appKey = appKey
        initializationJob = viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = str(R.string.ai_analysis_initializing), result = "") }
            Log.i(TAG, "Omni initialization enter deviceId=$deviceId")
            try {
                require(deviceId.isNotBlank()) { str(R.string.ai_analysis_need_id) }
                require(channelId.isNotBlank() && appKey.isNotBlank()) { str(R.string.ai_analysis_need_channel) }
                withTimeout(30_000L) { initializeOmni(deviceId, channelId, appKey) }
                if (version != operationVersion) return@launch
                _omniReady.value = true
                _uiState.update { it.copy(busy = false, status = str(R.string.ai_analysis_initialized)) }
            } catch (error: TimeoutCancellationException) {
                if (version != operationVersion) return@launch
                clearOmniReadiness()
                Log.e(TAG, "Omni initialization timeout deviceId=$deviceId", error)
                _uiState.update { it.copy(busy = false, status = str(R.string.feature_failed, "OMNI_INIT_TIMEOUT (30s)")) }
            } catch (error: CancellationException) {
                if (version != operationVersion) throw error
                clearOmniReadiness()
                _uiState.update { it.copy(busy = false, status = str(R.string.ai_analysis_canceled)) }
                Log.i(TAG, "Omni initialization canceled deviceId=$deviceId")
                throw error
            } catch (error: Exception) {
                if (version != operationVersion) return@launch
                clearOmniReadiness()
                Log.e(TAG, "Omni initialization failed deviceId=$deviceId", error)
                _uiState.update { it.copy(busy = false, status = str(R.string.feature_failed, error.message.orEmpty())) }
            }
        }
    }

    /** 只清除此页已标记就绪且设备身份一致的 SDK 状态。 */
    private fun clearOmniReadiness() {
        val center = AiCenter.getInstance()
        if (_omniReady.value && center.isOmniAgentReadyFor(deviceId)) center.setOmniAgentReadyDeviceId(null)
        _omniReady.value = false
    }

    /** 同时只生成一项报告；OmniAgent 报告复用就绪状态，HEALTH 继续走原 AFlash 流程。 */
    fun generate(report: Report) {
        if (_uiState.value.busy) return
        if (report != Report.HEALTH && (!_omniReady.value || !AiCenter.getInstance().isOmniAgentReadyFor(deviceId))) {
            clearOmniReadiness()
            _uiState.update { it.copy(status = str(R.string.ai_analysis_config_changed)) }
            return
        }
        val version = ++operationVersion
        requestJob = viewModelScope.launch {
            var input = ""
            _uiState.update { it.copy(busy = true, status = str(R.string.ai_analysis_running, report.name), result = "") }
            Log.i(TAG, "request enter type=$report deviceId=$deviceId")
            try {
                require(deviceId.isNotBlank()) { str(R.string.ai_analysis_need_id) }
                // Report buttons reuse page initialization; they do not reinitialize OmniAgent.
                val result = withTimeout(120_000L) {
                    if (report == Report.HEALTH) {
                        val sample = healthSample()
                        input = Gson().toJson(sample)
                        showInput(input)
                        initializeHealth(deviceId)
                        healthReport(sample)
                    } else {
                        val payload = when (report) {
                            Report.INSIGHT -> insightSample()
                            Report.BODY -> JSONArray().put(bodySample()).toString()
                            else -> {
                                val days = when (report) { Report.WEEK -> 7; Report.MONTH -> 30; else -> 1 }
                                JSONObject().put("dimension", report.name).put("is24Hour", true)
                                    .put("date", dateRange(days))
                                    .put("sleep_h", List(days) { "23:00:00" }.joinToString(","))
                                    .put("getUp_h", List(days) { "07:00:00" }.joinToString(",")).toString()
                            }
                        }
                        input = payload
                        showInput(input)
                        when (report) {
                            Report.INSIGHT -> insightReport(AiInsightRequest(deviceId, payload))
                            Report.BODY -> bodyReport(AiBodyStatusRequest(deviceId, payload))
                            else -> {
                                val json = JSONObject(payload)
                                sleepReport(AiSleepRequest(deviceId, report.name, json.getString("date"),
                                    json.getString("sleep_h"), json.getString("getUp_h"), true))
                            }
                        }
                    }
                }
                if (version != operationVersion) return@launch
                Log.i(TAG, "request success type=$report resultChars=${result.length}")
                _uiState.update { it.copy(busy = false, status = str(R.string.feature_success),
                    result = str(R.string.ai_analysis_result, input, result)) }
            } catch (error: TimeoutCancellationException) {
                if (version != operationVersion) return@launch
                showFailure(report, input, "AI_REQUEST_TIMEOUT (120s)", error)
            } catch (error: CancellationException) {
                Log.i(TAG, "request canceled type=$report")
                if (version == operationVersion) {
                    _uiState.update { it.copy(busy = false, status = str(R.string.ai_analysis_canceled)) }
                }
                throw error
            } catch (error: Exception) {
                if (version != operationVersion) return@launch
                showFailure(report, input, error.message.orEmpty(), error)
            }
        }
    }

    private fun showInput(input: String) {
        Log.i(TAG, "sample input=$input")
        _uiState.update { it.copy(result = str(R.string.ai_analysis_input, input)) }
    }

    private fun showFailure(report: Report, input: String, message: String, error: Exception) {
        Log.e(TAG, "request failed type=$report deviceId=$deviceId", error)
        _uiState.update { it.copy(busy = false, status = str(R.string.feature_failed, message),
            result = str(R.string.ai_analysis_result, input, "$report: $message")) }
    }

    /** 将 SDK 完成回调转为协程等待，取消后的迟到回调不再恢复本页就绪状态。 */
    private suspend fun initializeOmni(id: String, channel: String, key: String) {
        // Do not mark ready on submission, or reuse readiness after credentials/device change.
        val center = AiCenter.getInstance()
        center.setOmniAgentReadyDeviceId(null)
        suspendCancellableCoroutine<Unit> { continuation ->
            val device = DeviceConfig(id).apply { systemLanguage = Locale.getDefault().toLanguageTag() }
            OTek.initialize(context, InitConfig(channel, key, device), {
                if (continuation.isActive) continuation.resume(Unit)
            }, { code, message ->
                if (continuation.isActive) continuation.resumeWithException(SdkException(code, "OTek.initialize: $message"))
            })
        }
        center.setOmniAgentReadyDeviceId(id)
        Log.i(TAG, "Omni initialized deviceId=$id")
    }

    /**
     * 沿用 AFlash 初始化示例：setDeviceInfo 内部已触发初始化，后面的显式 initialize
     * 用于取得完成回调，当前仍会重复初始化；这不是 OmniAgent 页面初始化的一部分。
     */
    private suspend fun initializeHealth(id: String) {
        val center = AiCenter.getInstance()
        val language = LocaleHelper.localizedContext(context).resources.configuration.locales[0].language
        center.init(context, false)
        // Match the existing AI demo's sample dimensions; do not initialize with SDK defaults 0*0.
        center.setDeviceInfo(AiDeviceInfo().apply {
            setId(id)
            setWidth(480)
            setHeight(480)
            setThumbnailWidth(264)
            setThumbnailHeight(264)
            setCurrentLocale(language)
            setSupportedLocales(arrayOf(language))
        })
        // setDeviceInfo submits initialization asynchronously; await the vendor callback before
        // analysis rather than treating submitted metadata as successful authorization.
        suspendCancellableCoroutine<Unit> { continuation ->
            val watch = WatchInfo(PaymentModel.LICENSE_PAY, id).apply {
                this.language = language
                supportedLanguages = language
                resolution = "480*480"
                thuResolution = "264*264"
            }
            AFlash.instance.initialize(context, Region.OVERSEAS, arrayOf(watch), {
                if (continuation.isActive) continuation.resume(Unit)
            }, { error ->
                if (continuation.isActive) continuation.resumeWithException(
                    SdkException(error.code.toIntOrNull() ?: -1, "AFlash.initialize code=${error.code}: ${error.message}"))
            })
        }
        Log.i(TAG, "AFlash initialized deviceId=$id")
    }

    /** 直接分析 App 提供的数据，无屏 Handler 只返回本次结果，不向设备下发报告。 */
    private suspend fun healthReport(sample: HealthData): String = suspendCancellableCoroutine { continuation ->
        val handler = AFlashScreenlessHealthAnalysisHandler { result, code, message ->
            if (continuation.isActive) {
                if (code == 0 && result != null) continuation.resume(Gson().toJson(result))
                else continuation.resumeWithException(SdkException(code, "healthAnalysis: $message"))
            }
        }
        // App-only handler: no BLE read, device-result downlink, cache or database write.
        continuation.invokeOnCancellation { handler.cancel() }
        handler.start(sample)
    }

    private suspend fun insightReport(request: AiInsightRequest): String = suspendCancellableCoroutine { continuation ->
        val handler = AiCenter.getInstance().generateInsightReport(context, request, object : AFlashInsightReportHandler.Callback {
            override fun onSuccess(json: String) { if (continuation.isActive) continuation.resume(json) }
            override fun onFailure(code: Int, message: String?) {
                if (continuation.isActive) continuation.resumeWithException(SdkException(code, "insightReport: $message"))
            }
        })
        continuation.invokeOnCancellation { handler.cancel() }
    }

    private suspend fun bodyReport(request: AiBodyStatusRequest): String = suspendCancellableCoroutine { continuation ->
        val handler = AiCenter.getInstance().generateBodyStatusReport(context, request, object : AFlashBodyStatusReportHandler.Callback {
            override fun onSuccess(json: String) { if (continuation.isActive) continuation.resume(json) }
            override fun onFailure(code: Int, message: String?) {
                if (continuation.isActive) continuation.resumeWithException(SdkException(code, "bodyStatusReport: $message"))
            }
        })
        continuation.invokeOnCancellation { handler.cancel() }
    }

    private suspend fun sleepReport(request: AiSleepRequest): String = suspendCancellableCoroutine { continuation ->
        val handler = AiCenter.getInstance().generateSleepReport(context, request, object : AFlashSleepReportHandler.Callback {
            override fun onSuccess(json: String) { if (continuation.isActive) continuation.resume(json) }
            override fun onFailure(code: Int, message: String?) {
                if (continuation.isActive) continuation.resumeWithException(SdkException(code, "sleepReport: $message"))
            }
        })
        continuation.invokeOnCancellation { handler.cancel() }
    }

    /** 页面停止时取消自身等待／生成任务；保留已完成的初始化，不销毁全局 AI 单例。 */
    fun cancelRequest() {
        val initializing = initializationJob?.isActive == true
        if (!initializing && requestJob?.isActive != true) return
        // View recreation may start a new operation before canceled coroutines finish unwinding.
        // Old cancellation/result handlers must not overwrite that new page's busy/ready state.
        operationVersion++
        initializationJob?.cancel()
        requestJob?.cancel()
        if (initializing) clearOmniReadiness()
        Log.i(TAG, "page request canceled initializing=$initializing deviceId=$deviceId")
        _uiState.update { it.copy(busy = false, status = str(R.string.ai_analysis_canceled)) }
    }

    private fun date(daysAgo: Int, pattern: String = "yyyy/MM/dd"): String {
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        return SimpleDateFormat(pattern, Locale.US).format(day.time)
    }

    private fun dateRange(days: Int) = if (days == 1) date(0) else "${date(0)}-${date(days - 1)}"

    private fun healthSample() = HealthData().apply {
        setGender(1); setAge(30); setWeight(700); setHeight(175); setUnit(0)
        // SDK weight is 0.1 kg; sleep is minutes; day=0 means today, day=6 means six days ago.
        setDailyDataList(List(7) { index -> HealthData.DailyData().apply {
            setDay(index); setStep(8000); setMinHr(55); setMaxHr(125); setAvgHr(72)
            setMinSpO2(95); setMaxSpO2(99); setAvgSpO2(98)
            setMinStress(15); setMaxStress(60); setAvgStress(30); setSleep(480); setPAI(50)
        } })
    }

    private fun insightSample(): String {
        fun week(value: String) = List(7) { value }.joinToString(",")
        // Insight takes an object (SDK wraps it); seven slots are today -> six days ago.
        return JSONObject().put("date", dateRange(7)).put("stepGoal", "10000")
            .put("activityDurationGoal_min", "30").put("activitycalorieGoal_kcal", "500")
            .put("sleepQuality", "85").put("activityDuration_min", week("40"))
            .put("steps", week("8000")).put("sleepDuration_min", week("480"))
            .put("mood", week("心情平和")).put("calorieGoal_kcal", week("350"))
            .put("lightSleep_min", week("300")).put("deepSleep_min", week("120"))
            .put("heartRate_bpm", week("72")).put("heartratevariability(hrv)", week("45"))
            .put("stress", week("30")).put("restingheartRate_bpm", week("58")).toString()
    }

    private fun bodySample(): JSONObject {
        // Body Status keeps one day's object; these four mock daily averages are single-value strings.
        return JSONObject().put("date", date(0, "yyyy/M/d")).put("stepGoal", "10000")
            .put("steps", "8000").put("activityDuration_min", "40").put("calorieGoal_kcal", "350")
            .put("restingheartRate_bpm", "58").put("heartRate_bpm", "72")
            .put("heartratevariability(hrv)", "45").put("stress", "30")
            .put("sleep", "480").put("sleepQuality", "85").put("lightSleep_min", "300").put("deepSleep_min", "120")
    }
}
