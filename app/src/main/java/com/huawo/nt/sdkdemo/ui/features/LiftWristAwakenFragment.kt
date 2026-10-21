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
import com.huawo.nt.sdkdemo.databinding.FragmentLiftWristAwakenBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/** The switch shows the device value. Programmatic rendering must not send a write. */
class LiftWristAwakenFragment : Fragment() {
    private var _binding: FragmentLiftWristAwakenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LiftWristAwakenViewModel by viewModels { ViewModelFactory() }
    private var rendering = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiftWristAwakenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.toolbar.inflateMenu(R.menu.menu_lift_wrist)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_refresh_lift_wrist) {
                viewModel.refresh()
                true
            } else {
                false
            }
        }
        binding.liftWristSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering && !viewModel.setEnabled(checked)) render(viewModel.uiState.value)
        }
        render(viewModel.uiState.value)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: LiftWristAwakenViewModel.State) {
        rendering = true
        try {
            binding.liftWristSwitch.isChecked = state.requested ?: (state.enabled ?: false)
            binding.liftWristSwitch.isEnabled = !state.busy && !state.needsRead && state.enabled != null
            binding.liftWristDetail.text = when {
                state.error != null -> state.error
                state.pending -> getString(
                    if (state.requested == null) R.string.lift_wrist_reading else R.string.lift_wrist_saving,
                )
                state.enabled == null || state.needsRead -> getString(R.string.auto_monitoring_unknown)
                state.enabled -> getString(R.string.reminders_on)
                else -> getString(R.string.reminders_off)
            }
            binding.liftWristDetail.setTextColor(
                resources.getColor(
                    if (state.error != null) R.color.home_danger else R.color.home_secondary,
                    requireContext().theme,
                ),
            )
            binding.toolbar.menu.findItem(R.id.action_refresh_lift_wrist)?.isEnabled = !state.busy
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
        _binding = null
        super.onDestroyView()
    }
}
