package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentFeatureActionsBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseFeatureFragment : Fragment() {
    private var _binding: FragmentFeatureActionsBinding? = null
    protected val binding get() = _binding!!

    protected abstract val titleRes: Int
    protected abstract fun buildActions(container: LinearLayout)
    protected abstract fun uiStateFlow(): StateFlow<FeatureUiState>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFeatureActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.title = getString(titleRes)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.actionsContainer.removeAllViews()
        buildActions(binding.actionsContainer)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiStateFlow().collect { state ->
                    binding.statusText.text = state.status
                    binding.resultText.text = state.result
                    for (i in 0 until binding.actionsContainer.childCount) {
                        binding.actionsContainer.getChildAt(i).isEnabled = !state.busy
                    }
                }
            }
        }
    }

    protected fun addActionButton(
        container: LinearLayout,
        textRes: Int,
        onClick: () -> Unit,
    ) {
        val button =
            MaterialButton(requireContext()).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { it.bottomMargin = (8 * resources.displayMetrics.density).toInt() }
                isAllCaps = false
                text = getString(textRes)
                setOnClickListener { onClick() }
            }
        container.addView(button)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class GoalsFragment : BaseFeatureFragment() {
    private val viewModel: GoalsViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_goals
    override fun uiStateFlow() = viewModel.uiState
    override fun buildActions(container: LinearLayout) {
        addActionButton(container, R.string.goals_get) { viewModel.getGoals() }
        addActionButton(container, R.string.goals_set_demo) { viewModel.setDemoGoals() }
    }
}

class AlarmsFragment : BaseFeatureFragment() {
    private val viewModel: AlarmsViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_alarms
    override fun uiStateFlow() = viewModel.uiState
    override fun buildActions(container: LinearLayout) {
        addActionButton(container, R.string.alarms_get) { viewModel.getAlarms() }
        addActionButton(container, R.string.alarms_add_demo) { viewModel.addDemoAlarm() }
        addActionButton(container, R.string.alarms_delete_all) { viewModel.deleteAllAlarms() }
        addActionButton(container, R.string.reminders_get_sedentary) { viewModel.getSedentary() }
        addActionButton(container, R.string.reminders_set_sedentary) { viewModel.setSedentary() }
        addActionButton(container, R.string.reminders_set_drink) { viewModel.setDrinkWater() }
        addActionButton(container, R.string.reminders_set_wash) { viewModel.setWashHand() }
    }
}

class NotifyFragment : BaseFeatureFragment() {
    private val viewModel: NotifyViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_notify
    override fun uiStateFlow() = viewModel.uiState
    override fun buildActions(container: LinearLayout) {
        addActionButton(container, R.string.notify_get_switches) { viewModel.getSwitches() }
        addActionButton(container, R.string.notify_enable_switches) { viewModel.enableDemoSwitches() }
        addActionButton(container, R.string.notify_push_message) { viewModel.pushDemoMessage() }
        addActionButton(container, R.string.notify_hang_up) { viewModel.hangUpCall() }
        addActionButton(container, R.string.notify_set_contacts) { viewModel.setDemoContacts() }
        addActionButton(container, R.string.notify_set_emergency) { viewModel.setEmergency() }
    }
}
