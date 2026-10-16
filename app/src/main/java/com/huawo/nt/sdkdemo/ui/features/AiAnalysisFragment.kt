package com.huawo.nt.sdkdemo.ui.features

import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One selected report with the existing inputs, instructions and real cloud request flow. */
class AiAnalysisFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    override val customActionState: Boolean = true
    companion object {
        private const val ARG_REPORT = "report"

        fun newInstance(report: AiAnalysisViewModel.Report) = AiAnalysisFragment().apply {
            arguments = bundleOf(ARG_REPORT to report.name)
        }
    }

    private val report: AiAnalysisViewModel.Report by lazy {
        arguments?.getString(ARG_REPORT)?.let { AiAnalysisViewModel.Report.valueOf(it) }
            ?: AiAnalysisViewModel.Report.HEALTH
    }
    private val viewModel: AiAnalysisViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int
        get() = when (report) {
            AiAnalysisViewModel.Report.HEALTH -> R.string.ai_analysis_health
            AiAnalysisViewModel.Report.INSIGHT -> R.string.ai_analysis_insight
            AiAnalysisViewModel.Report.BODY -> R.string.ai_analysis_body
            AiAnalysisViewModel.Report.DAY -> R.string.ai_analysis_sleep_day
            AiAnalysisViewModel.Report.WEEK -> R.string.ai_analysis_sleep_week
            AiAnalysisViewModel.Report.MONTH -> R.string.ai_analysis_sleep_month
        }
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        // Card placement changes only presentation; the existing collector still owns busy/readiness gates.
        val actions = addActionCard(container, R.string.ui_parameters)
        actions.addView(TextView(requireContext()).apply {
            setText(R.string.ai_analysis_hint)
            textSize = 13f
            setTextColor(resources.getColor(R.color.home_secondary, requireContext().theme))
        })
        val deviceId = addInput(actions, R.string.ai_analysis_device_id).apply {
            // Use this connection, not an ID saved by the separate AI watchface demo.
            val mac = BluetoothSDK.getConnectedDevice()?.mac.orEmpty()
            setText(mac.replace(":", "").replace("-", "").lowercase(Locale.ROOT))
        }
        val channelId = addInput(actions, R.string.ai_analysis_channel_id).apply { setText(viewModel.channelId) }
        val appKey = addInput(actions, R.string.ai_analysis_app_key, password = true).apply { setText(viewModel.appKey) }

        fun initialize() {
            viewModel.initialize(
                deviceId.text?.toString()?.trim().orEmpty(),
                channelId.text?.toString()?.trim().orEmpty(),
                appKey.text?.toString()?.trim().orEmpty(),
            )
        }
        val commands = addActionCard(container, R.string.ui_actions)
        val initializeButton = addActionButton(commands, R.string.ai_analysis_initialize) { initialize() }
        val reportButtons = mutableMapOf<AiAnalysisViewModel.Report, View>()
        fun addReport(label: Int, report: AiAnalysisViewModel.Report) {
            reportButtons[report] = addActionButton(commands, label) { viewModel.generate(report) }
        }
        // The menu selects the report; this page keeps only its matching action, not the other five.
        addReport(titleRes, report)

        val inputs = listOf(deviceId, channelId, appKey)
        // 配置编辑与初始化分开：修改任一身份参数后，先禁用使用旧配置的报告入口。
        inputs.forEach { input ->
            input.doAfterTextChanged {
                viewModel.updateConfiguration(
                    deviceId.text?.toString()?.trim().orEmpty(),
                    channelId.text?.toString()?.trim().orEmpty(),
                    appKey.text?.toString()?.trim().orEmpty(),
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.uiState, viewModel.omniReady) { state, ready -> state to ready }.collect { (state, ready) ->
                    initializeButton.isEnabled = !state.busy
                    inputs.forEach { it.isEnabled = !state.busy }
                    // 运动健康分析依赖 AFlash，OmniAgent 初始化失败不影响它的独立入口。
                    reportButtons.forEach { (report, button) ->
                        button.isEnabled = !state.busy && (report == AiAnalysisViewModel.Report.HEALTH || ready)
                    }
                }
            }
        }
        // 预填参数完成后，进入页面立即初始化；后续失败或配置变更由重试按钮处理。
        initialize()
    }

    private fun addInput(container: LinearLayout, hintRes: Int, password: Boolean = false): TextInputEditText {
        val layout = TextInputLayout(requireContext()).apply { hint = getString(hintRes) }
        val input = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
        }
        layout.addView(input)
        container.addView(layout)
        return input
    }

    override fun onStop() {
        // 页面离开或进入后台后，取消本页任务，由 ViewModel 屏蔽已过期的异步结果。
        viewModel.cancelRequest()
        super.onStop()
    }
}
