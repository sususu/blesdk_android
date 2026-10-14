package com.huawo.nt.sdkdemo.ui.features

import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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

/** Fixed health samples, real cloud requests; no watchface/recording or platform selection. */
class AiAnalysisFragment : BaseFeatureFragment() {
    private val viewModel: AiAnalysisViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.ai_analysis_title
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        // Own the controls inside one group so BaseFeatureFragment's generic button rendering
        // cannot re-enable a report whose backend has not finished initialization.
        val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        container.addView(actions)
        actions.addView(TextView(requireContext()).apply { setText(R.string.ai_analysis_hint) })
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
        addActionButton(actions, R.string.ai_analysis_initialize) { initialize() }
        val initializeButton = actions.getChildAt(actions.childCount - 1)
        val reportButtons = mutableMapOf<AiAnalysisViewModel.Report, View>()
        fun addReport(label: Int, report: AiAnalysisViewModel.Report) {
            addActionButton(actions, label) { viewModel.generate(report) }
            reportButtons[report] = actions.getChildAt(actions.childCount - 1)
        }
        addReport(R.string.ai_analysis_health, AiAnalysisViewModel.Report.HEALTH)
        addReport(R.string.ai_analysis_insight, AiAnalysisViewModel.Report.INSIGHT)
        addReport(R.string.ai_analysis_body, AiAnalysisViewModel.Report.BODY)
        addReport(R.string.ai_analysis_sleep_day, AiAnalysisViewModel.Report.DAY)
        addReport(R.string.ai_analysis_sleep_week, AiAnalysisViewModel.Report.WEEK)
        addReport(R.string.ai_analysis_sleep_month, AiAnalysisViewModel.Report.MONTH)

        val inputs = listOf(deviceId, channelId, appKey)
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
                    reportButtons.forEach { (report, button) ->
                        button.isEnabled = !state.busy && (report == AiAnalysisViewModel.Report.HEALTH || ready)
                    }
                }
            }
        }
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
        viewModel.cancelRequest()
        super.onStop()
    }
}
