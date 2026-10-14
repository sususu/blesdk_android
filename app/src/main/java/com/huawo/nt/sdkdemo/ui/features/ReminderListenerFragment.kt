package com.huawo.nt.sdkdemo.ui.features

import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory

class ReminderListenerFragment : BaseFeatureFragment() {
    private val viewModel: ReminderListenerViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_reminder_listener_jl
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        addActionButton(container, R.string.reminder_listener_start) { viewModel.startListening() }
        addActionButton(container, R.string.reminder_listener_stop) { viewModel.stopListening() }
    }

    override fun onStop() {
        // Leaving the page or backgrounding the app ends this page's subscription only.
        viewModel.stopListening("pageStopped")
        super.onStop()
    }
}
