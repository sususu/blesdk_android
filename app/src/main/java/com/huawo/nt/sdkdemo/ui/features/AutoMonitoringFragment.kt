package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentAutoMonitoringBinding
import com.huawo.nt.sdkdemo.databinding.ItemAutoMonitoringBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/** Switches display SDK reads/ACKs only; programmatic state rendering must never send device commands. */
class AutoMonitoringFragment : Fragment() {
    private var _binding: FragmentAutoMonitoringBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AutoMonitoringViewModel by viewModels { ViewModelFactory() }
    private val rows = mutableMapOf<AutoMonitoringViewModel.Metric, ItemAutoMonitoringBinding>()
    private var rendering = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAutoMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.toolbar.inflateMenu(R.menu.menu_auto_monitoring)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_refresh_monitoring) { viewModel.refresh(); true } else false
        }
        AutoMonitoringViewModel.Metric.entries.forEach { metric ->
            val row = ItemAutoMonitoringBinding.inflate(layoutInflater, binding.monitoringRows, false)
            row.metricName.setText(metric.labelRes)
            row.monitorSwitch.contentDescription = getString(metric.labelRes)
            row.monitorSwitch.setOnCheckedChangeListener { _, checked ->
                if (!rendering && !viewModel.setMonitoring(metric, checked)) render(viewModel.uiState.value)
            }
            binding.monitoringRows.addView(row.root)
            rows[metric] = row
        }
        render(viewModel.uiState.value)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: AutoMonitoringViewModel.State) {
        rendering = true
        try {
            rows.forEach { (metric, row) ->
                val setting = state.settings.getValue(metric)
                row.monitorSwitch.isChecked = setting.requested ?: (setting.value?.let { it > 0 } ?: false)
                row.monitorSwitch.isEnabled = !state.busy && !setting.needsRead && setting.value != null
                row.metricDetail.text = when {
                    setting.error != null -> setting.error
                    setting.pending -> getString(if (setting.requested == null)
                        R.string.auto_monitoring_reading else R.string.auto_monitoring_saving)
                    setting.value == null || setting.needsRead -> getString(R.string.auto_monitoring_unknown)
                    setting.value == 0 -> getString(R.string.reminders_off)
                    metric == AutoMonitoringViewModel.Metric.HEART ->
                        getString(R.string.auto_monitoring_interval_detail, setting.value)
                    metric == AutoMonitoringViewModel.Metric.SKIN || metric == AutoMonitoringViewModel.Metric.BREATHS ->
                        getString(R.string.auto_monitoring_value_detail, setting.value)
                    else -> getString(R.string.reminders_on)
                }
                row.metricDetail.setTextColor(resources.getColor(
                    if (setting.error != null) R.color.home_danger else R.color.home_secondary, requireContext().theme))
            }
            binding.toolbar.menu.findItem(R.id.action_refresh_monitoring)?.isEnabled = !state.busy
            binding.statusText.text = state.message
            binding.statusText.visibility = if (state.message.isBlank()) View.GONE else View.VISIBLE
        } finally {
            rendering = false
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    override fun onStop() {
        viewModel.cancelRequest("pageStopped")
        super.onStop()
    }

    override fun onDestroyView() {
        rows.clear()
        _binding = null
        super.onDestroyView()
    }
}
