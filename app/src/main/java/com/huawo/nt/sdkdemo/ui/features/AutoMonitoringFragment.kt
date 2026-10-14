package com.huawo.nt.sdkdemo.ui.features

import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory

/** Independent SDK calls with fixed sample inputs; device support is reported by SDK callbacks. */
class AutoMonitoringFragment : BaseFeatureFragment() {
    private val viewModel: AutoMonitoringViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_auto_monitoring
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        addActionButton(container, R.string.auto_monitoring_heart_get) { viewModel.getHeartrateInterval() }
        addActionButton(container, R.string.auto_monitoring_heart_set) { viewModel.setDemoHeartrateInterval() }
        addActionButton(container, R.string.auto_monitoring_spo2_get) { viewModel.getSpO2Monitoring() }
        addActionButton(container, R.string.auto_monitoring_spo2_on) { viewModel.setSpO2Monitoring(true) }
        addActionButton(container, R.string.auto_monitoring_spo2_off) { viewModel.setSpO2Monitoring(false) }
        addActionButton(container, R.string.auto_monitoring_stress_get) { viewModel.getStressMonitoring() }
        addActionButton(container, R.string.auto_monitoring_stress_on) { viewModel.setStressMonitoring(true) }
        addActionButton(container, R.string.auto_monitoring_stress_off) { viewModel.setStressMonitoring(false) }
        addActionButton(container, R.string.auto_monitoring_skin_get) { viewModel.getSkinTemperatureMonitoring() }
        addActionButton(container, R.string.auto_monitoring_skin_on) { viewModel.setSkinTemperatureMonitoring(true) }
        addActionButton(container, R.string.auto_monitoring_skin_off) { viewModel.setSkinTemperatureMonitoring(false) }
        addActionButton(container, R.string.auto_monitoring_breaths_get) { viewModel.getRespiratoryRateMonitoring() }
        addActionButton(container, R.string.auto_monitoring_breaths_on) { viewModel.setRespiratoryRateMonitoring(true) }
        addActionButton(container, R.string.auto_monitoring_breaths_off) { viewModel.setRespiratoryRateMonitoring(false) }
    }
}
