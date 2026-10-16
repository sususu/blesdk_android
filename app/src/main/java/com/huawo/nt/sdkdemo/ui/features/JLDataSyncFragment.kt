package com.huawo.nt.sdkdemo.ui.features

import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory

/** Entry and view recreation only build controls; SDK requests start exclusively from a command button. */
class JLDataSyncFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    companion object {
        private const val ARG_DEVICE_MAC = "device_mac"

        fun newInstance(mac: String?) = JLDataSyncFragment().apply {
            arguments = bundleOf(ARG_DEVICE_MAC to mac)
        }
    }

    private val viewModel: JLDataSyncViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.sync_data_jl
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        viewModel.setTargetMac(arguments?.getString(ARG_DEVICE_MAC))
        val actions = addActionCard(container, R.string.ui_actions)
        addActionButton(actions, R.string.jl_data_read_step) { viewModel.readStep() }
        addActionButton(actions, R.string.jl_data_read_heart) { viewModel.readHeartRate() }
        addActionButton(actions, R.string.jl_data_read_sleep) { viewModel.readSleep() }
        addActionButton(actions, R.string.jl_data_read_hrv) { viewModel.readHrv() }
        addActionButton(actions, R.string.jl_data_read_spo2) { viewModel.readSpo2() }
        addActionButton(actions, R.string.jl_data_read_stress) { viewModel.readStress() }
        addActionButton(actions, R.string.jl_data_read_skin) { viewModel.readSkinTemperature() }
        addActionButton(actions, R.string.jl_data_read_breaths) { viewModel.readBreaths() }
        addActionButton(actions, R.string.jl_data_read_workout) { viewModel.readWorkout() }

        addActionButton(actions, R.string.jl_data_delete_step, destructive = true) { viewModel.deleteStep() }
        addActionButton(actions, R.string.jl_data_delete_heart, destructive = true) { viewModel.deleteHeartRate() }
        addActionButton(actions, R.string.jl_data_delete_sleep, destructive = true) { viewModel.deleteSleep() }
        addActionButton(actions, R.string.jl_data_delete_hrv, destructive = true) { viewModel.deleteHrv() }
        addActionButton(actions, R.string.jl_data_delete_spo2, destructive = true) { viewModel.deleteSpo2() }
        addActionButton(actions, R.string.jl_data_delete_stress, destructive = true) { viewModel.deleteStress() }
        addActionButton(actions, R.string.jl_data_delete_skin, destructive = true) { viewModel.deleteSkinTemperature() }
        addActionButton(actions, R.string.jl_data_delete_breaths, destructive = true) { viewModel.deleteBreaths() }
        addActionButton(actions, R.string.jl_data_delete_workout, destructive = true) { viewModel.deleteWorkout() }
    }

    override fun onStop() {
        viewModel.cancelRequest("pageStopped")
        super.onStop()
    }
}
