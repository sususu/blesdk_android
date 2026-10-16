package com.huawo.nt.sdkdemo.ui.features

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.ui.ViewModelFactory

/** All JieLi examples run directly on this page; no settings-detail navigation or platform selection. */
class JLAlarmsFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    private val viewModel: JLAlarmsViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_alarms_jl
    override fun uiStateFlow() = viewModel.uiState

    override fun buildActions(container: LinearLayout) {
        val actions = addActionCard(container, R.string.ui_actions)
        // Preserve the shared-ID explanation inside the actions card; these sections do not navigate.
        actions.addView(TextView(requireContext()).apply {
            setText(R.string.jl_alarms_id_hint)
            textSize = 13f
            setTextColor(resources.getColor(R.color.home_secondary, requireContext().theme))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        })
        fun section(titleRes: Int) {
            actions.addView(TextView(requireContext()).apply {
                setText(titleRes)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.home_text, requireContext().theme))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = (12 * resources.displayMetrics.density).toInt() }
            })
        }
        section(R.string.jl_low_battery_title)
        addActionButton(actions, R.string.reminders_low_battery_get) { viewModel.getLowBatteryReminder() }
        addActionButton(actions, R.string.reminders_low_battery_on) { viewModel.setLowBatteryReminder(true) }
        addActionButton(actions, R.string.reminders_low_battery_off) { viewModel.setLowBatteryReminder(false) }

        section(R.string.jl_vibration_title)
        addActionButton(actions, R.string.reminders_vibration_get) { viewModel.getVibration() }
        addActionButton(actions, R.string.reminders_vibration_off) { viewModel.setVibration(false, 66) }
        addActionButton(actions, R.string.reminders_vibration_medium) { viewModel.setVibration(true, 66) }

        section(R.string.jl_auto_detect_title)
        addActionButton(actions, R.string.workout_recognition_switch_get) { viewModel.getAutoDetectWorkoutsSwitch() }
        addActionButton(actions, R.string.workout_recognition_switch_on) { viewModel.setAutoDetectWorkoutsSwitch(true) }
        addActionButton(actions, R.string.workout_recognition_switch_off) { viewModel.setAutoDetectWorkoutsSwitch(false) }
        section(R.string.feature_alarms_jl)
        addActionButton(actions, R.string.jl_alarms_get) { viewModel.getAlarms() }
        addActionButton(actions, R.string.jl_alarms_add_demo) { viewModel.addDemoAlarm() }
        addActionButton(actions, R.string.jl_alarms_delete_all, destructive = true) { viewModel.deleteAllAlarms() }
        addActionButton(actions, R.string.jl_reminders_get) { viewModel.getReminders() }
        addActionButton(actions, R.string.jl_reminders_add_demo) { viewModel.addDemoReminder() }
        addActionButton(actions, R.string.jl_reminders_delete_all, destructive = true) { viewModel.deleteAllReminders() }
    }

    override fun onStop() {
        viewModel.cancelRequest("pageStopped")
        super.onStop()
    }
}
