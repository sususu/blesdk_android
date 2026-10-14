package com.huawo.nt.sdkdemo.ui.features

import android.widget.LinearLayout
import androidx.fragment.app.activityViewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DataType

class ManualMeasurementFragment : BaseFeatureFragment() {
    // Keep the one device session alive until stop is confirmed, even if the page is reopened.
    private val viewModel: ManualMeasurementViewModel by activityViewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_manual_measure_jl
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        addActionButton(container, R.string.manual_measure_heart) { viewModel.start(DataType.HeartRate) }
        addActionButton(container, R.string.manual_measure_spo2) { viewModel.start(DataType.SpO2) }
        addActionButton(container, R.string.manual_measure_stress) { viewModel.start(DataType.Stress) }
        addActionButton(container, R.string.manual_measure_hrv) { viewModel.start(DataType.HRV) }
        addActionButton(container, R.string.manual_measure_breaths) { viewModel.start(DataType.RespiratoryRate) }
        addActionButton(container, R.string.manual_measure_skin) { viewModel.start(DataType.SkinTemperature) }
        addActionButton(container, R.string.manual_measure_stop, keepEnabledWhileBusy = true) { viewModel.stop() }
    }

    override fun onStop() {
        viewModel.stop("pageStopped")
        super.onStop()
    }
}
