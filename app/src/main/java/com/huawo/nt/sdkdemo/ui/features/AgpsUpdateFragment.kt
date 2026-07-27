package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentAgpsUpdateBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.common.LogAdapter
import com.huawo.nt.sdkdemo.ui.scan.ScanConnectFragment
import kotlinx.coroutines.launch

/**
 * AGPS update demo:
 * download 7-day XYW → build `agps_xyw.zip` → push via Sifli (type=3).
 */
class AgpsUpdateFragment : Fragment() {
    private var _binding: FragmentAgpsUpdateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AgpsUpdateViewModel by viewModels { ViewModelFactory() }
    private val logAdapter = LogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAgpsUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = logAdapter

        binding.btnConnectBle.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScanConnectFragment())
                .addToBackStack("scan_from_agps")
                .commit()
        }
        binding.btnRefreshGps.setOnClickListener { viewModel.refreshGpsStatus() }
        binding.btnStart.setOnClickListener { viewModel.startUpdate() }
        binding.btnCancel.setOnClickListener { viewModel.cancelUpdate() }
        binding.btnClearLogs.setOnClickListener { viewModel.clearLogs() }

        parentFragmentManager.setFragmentResultListener(
            ScanConnectFragment.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            viewModel.refreshDeviceInfo()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }

        viewModel.refreshDeviceInfo()
    }

    private fun bindState(state: AgpsUpdateUiState) {
        binding.statusText.text = state.status
        binding.tvGpsClip.text = state.gpsClipText
        binding.tvGpsFw.text = state.gpsFwText
        binding.tvAgpsValid.text = state.agpsValidText
        binding.tvBleState.text =
            if (state.bleConnected) {
                getString(R.string.music_ble_connected)
            } else {
                getString(R.string.music_ble_disconnected)
            }
        binding.progressPanel.isVisible = state.busy || state.progress > 0
        binding.progressBar.progress = state.progress
        binding.tvProgress.text = state.progressText
        binding.tvPhase.text = state.phaseText.ifBlank { getString(R.string.feature_agps) }

        val idle = !state.busy
        binding.btnConnectBle.isEnabled = idle
        binding.btnRefreshGps.isEnabled = idle
        binding.btnStart.isEnabled = idle
        binding.btnCancel.isEnabled = state.busy
        binding.btnClearLogs.isEnabled = idle

        logAdapter.submit(state.logs)
        if (state.logs.isNotEmpty()) {
            binding.rvLogs.scrollToPosition(state.logs.lastIndex)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
