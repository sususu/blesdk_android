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
import com.huawo.nt.sdkdemo.databinding.FragmentFeatureCardsBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseFeatureFragment : Fragment() {
    private var _binding: FragmentFeatureActionsBinding? = null
    private var _cardBinding: FragmentFeatureCardsBinding? = null
    protected val binding get() = _binding!!

    // Only redesigned pages opt in; other feature screens retain their original layout.
    protected open val cardLayout: Boolean = false
    // AI analysis combines busy and backend readiness itself; the generic renderer must not overwrite it.
    protected open val customActionState: Boolean = false
    protected abstract val titleRes: Int
    protected abstract fun buildActions(container: LinearLayout)
    protected abstract fun uiStateFlow(): StateFlow<FeatureUiState>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        if (cardLayout) {
            _cardBinding = FragmentFeatureCardsBinding.inflate(inflater, container, false)
            return _cardBinding!!.root
        }
        _binding = FragmentFeatureActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = _cardBinding?.toolbar ?: binding.toolbar
        val actions = _cardBinding?.actionsContainer ?: binding.actionsContainer
        val status = _cardBinding?.statusText ?: binding.statusText
        val result = _cardBinding?.resultText ?: binding.resultText
        toolbar.title = getString(titleRes)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        actions.removeAllViews()
        buildActions(actions)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiStateFlow().collect { state ->
                    status.text = state.status
                    result.text = if (cardLayout && state.result.isBlank()) {
                        getString(R.string.ui_result_placeholder)
                    } else state.result
                    if (cardLayout) {
                        if (!customActionState) updateCardButtons(actions, state.busy)
                    } else {
                        for (i in 0 until actions.childCount) {
                            val child = actions.getChildAt(i)
                            val keep = child.tag == true
                            child.isEnabled = !state.busy || keep
                        }
                    }
                }
            }
        }
    }

    protected fun addActionButton(
        container: LinearLayout,
        textRes: Int,
        keepEnabledWhileBusy: Boolean = false,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ): MaterialButton {
        if (cardLayout) {
            val lastRow = container.getChildAt(container.childCount - 1) as? LinearLayout
            val row = lastRow?.takeIf { it.childCount < 3 } ?: LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                weightSum = 3f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { if (container.childCount > 0) it.topMargin = (8 * resources.displayMetrics.density).toInt() }
                container.addView(this)
            }
            val button = layoutInflater.inflate(R.layout.item_feature_tile, row, false) as MaterialButton
            if (row.childCount == 2) (button.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
            if (destructive) button.setTextColor(resources.getColorStateList(R.color.home_destructive_button_text, requireContext().theme))
            button.text = getString(textRes)
            button.tag = keepEnabledWhileBusy
            button.setOnClickListener { onClick() }
            row.addView(button)
            return button
        }
        val button =
            MaterialButton(requireContext()).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { it.bottomMargin = (8 * resources.displayMetrics.density).toInt() }
                isAllCaps = false
                text = getString(textRes)
                tag = keepEnabledWhileBusy
                setOnClickListener { onClick() }
            }
        container.addView(button)
        return button
    }

    protected fun addActionCard(container: LinearLayout, titleRes: Int): LinearLayout {
        val card = layoutInflater.inflate(R.layout.item_feature_action_card, container, false)
        card.findViewById<android.widget.TextView>(R.id.card_title).setText(titleRes)
        container.addView(card)
        return card.findViewById<LinearLayout>(R.id.card_actions)
    }

    private fun updateCardButtons(view: View, busy: Boolean) {
        // Nested cards/rows must not bypass the original busy guard or disable the retry-stop action.
        if (view is MaterialButton) view.isEnabled = !busy || view.tag == true
        if (view is ViewGroup) for (i in 0 until view.childCount) updateCardButtons(view.getChildAt(i), busy)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _cardBinding = null
    }
}

class GoalsFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    private val viewModel: GoalsViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_goals
    override fun uiStateFlow() = viewModel.uiState
    override fun buildActions(container: LinearLayout) {
        val actions = addActionCard(container, R.string.ui_actions)
        addActionButton(actions, R.string.goals_get) { viewModel.getGoals() }
        addActionButton(actions, R.string.goals_set_demo) { viewModel.setDemoGoals() }
    }
}

class AlarmsFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    private val viewModel: AlarmsViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_alarms
    override fun uiStateFlow() = viewModel.uiState
    override fun buildActions(container: LinearLayout) {
        val actions = addActionCard(container, R.string.ui_actions)
        addActionButton(actions, R.string.alarms_get) { viewModel.getAlarms() }
        addActionButton(actions, R.string.alarms_add_demo) { viewModel.addDemoAlarm() }
        addActionButton(actions, R.string.alarms_delete_all, destructive = true) { viewModel.deleteAllAlarms() }
        addActionButton(actions, R.string.reminders_get_sedentary) { viewModel.getSedentary() }
        addActionButton(actions, R.string.reminders_set_sedentary) { viewModel.setSedentary() }
        addActionButton(actions, R.string.reminders_set_drink) { viewModel.setDrinkWater() }
        addActionButton(actions, R.string.reminders_set_wash) { viewModel.setWashHand() }
    }
}

class NotifyFragment : BaseFeatureFragment() {
    override val cardLayout: Boolean = true
    private val viewModel: NotifyViewModel by viewModels { ViewModelFactory() }
    override val titleRes: Int = R.string.feature_notify
    override fun uiStateFlow() = viewModel.uiState
    override fun buildActions(container: LinearLayout) {
        val actions = addActionCard(container, R.string.ui_actions)
        addActionButton(actions, R.string.notify_get_switches) { viewModel.getSwitches() }
        addActionButton(actions, R.string.notify_enable_switches) { viewModel.enableDemoSwitches() }
        addActionButton(actions, R.string.notify_push_message) { viewModel.pushDemoMessage() }
        addActionButton(actions, R.string.notify_hang_up) { viewModel.hangUpCall() }
        addActionButton(actions, R.string.notify_set_contacts) { viewModel.setDemoContacts() }
        addActionButton(actions, R.string.notify_set_emergency) { viewModel.setEmergency() }
    }
}
