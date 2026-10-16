package com.huawo.nt.sdkdemo.ui.features

import android.widget.LinearLayout
import androidx.fragment.app.activityViewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DataType

/** 六种手动测量均调用真实 SDK；设备是否支持由实际响应确认。 */
class ManualMeasurementFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    // Keep the one device session alive until stop is confirmed, even if the page is reopened.
    private val viewModel: ManualMeasurementViewModel by activityViewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_manual_measure_jl
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        val actions = addActionCard(container, R.string.ui_manual_actions)
        addActionButton(actions, R.string.manual_measure_heart) { viewModel.start(DataType.HeartRate) }
        addActionButton(actions, R.string.manual_measure_spo2) { viewModel.start(DataType.SpO2) }
        addActionButton(actions, R.string.manual_measure_stress) { viewModel.start(DataType.Stress) }
        addActionButton(actions, R.string.manual_measure_hrv) { viewModel.start(DataType.HRV) }
        addActionButton(actions, R.string.manual_measure_breaths) { viewModel.start(DataType.RespiratoryRate) }
        addActionButton(actions, R.string.manual_measure_skin) { viewModel.start(DataType.SkinTemperature) }
        val controls = addActionCard(container, R.string.ui_measure_controls)
        addActionButton(controls, R.string.manual_measure_stop, keepEnabledWhileBusy = true) { viewModel.stop() }
    }

    override fun onStop() {
        // 页面不可见时请求停止；等待应答和结束事件的会话仍由共享 ViewModel 收尾。
        viewModel.stop("pageStopped")
        super.onStop()
    }
}
