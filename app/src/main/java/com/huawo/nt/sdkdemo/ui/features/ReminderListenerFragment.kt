package com.huawo.nt.sdkdemo.ui.features

import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory

/** 提醒与运动识别分别订阅；本页仅展示监听操作与事件，不控制设备开关。 */
class ReminderListenerFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    private val viewModel: ReminderListenerViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_reminder_listener_jl
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        val actions = addActionCard(container, R.string.ui_actions)
        addActionButton(actions, R.string.reminder_listener_start) { viewModel.startListening() }
        addActionButton(actions, R.string.reminder_listener_stop) { viewModel.stopListening() }
        addActionButton(actions, R.string.workout_recognition_listener_start) { viewModel.startWorkoutRecognitionListening() }
        addActionButton(actions, R.string.workout_recognition_listener_stop) { viewModel.stopWorkoutRecognitionListening() }
    }

    override fun onStop() {
        // Leaving the page only removes this page's two subscriptions.
        viewModel.stopListening("pageStopped")
        viewModel.stopWorkoutRecognitionListening("pageStopped")
        super.onStop()
    }
}
