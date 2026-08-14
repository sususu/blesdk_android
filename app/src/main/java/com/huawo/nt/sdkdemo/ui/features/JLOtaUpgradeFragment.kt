package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentOtaUpgradeBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.common.LogAdapter
import kotlinx.coroutines.launch

/**
 * Standalone JieLi  OTA SDK example page.
 *
 * The page always starts the  OTA API selected by this button; it deliberately does
 * not inspect the connected-device platform or route to another OTA implementation.
 */
class JLOtaUpgradeFragment : Fragment() {
    private var _binding: FragmentOtaUpgradeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JLOtaUpgradeViewModel by viewModels { ViewModelFactory() }
    private val logAdapter = LogAdapter()

    // OTA must not be left by system Back while the SDK owns the active 1630 transfer session.
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Toast.makeText(requireContext(), R.string.ota_back_blocked, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOtaUpgradeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        binding.toolbar.title = getString(R.string.feature_ota_jl)
        binding.toolbar.setNavigationOnClickListener {
            if (viewModel.uiState.value.busy) {
                Toast.makeText(requireContext(), R.string.ota_back_blocked, Toast.LENGTH_SHORT).show()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = logAdapter
        binding.btnRefresh.setOnClickListener { viewModel.refreshDeviceInfo() }
        binding.btnCheck.setOnClickListener { viewModel.checkUpgrade() }
        binding.btnStart.setOnClickListener { viewModel.startUpgrade() }
        binding.btnClearLogs.setOnClickListener { viewModel.clearLogs() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> bindState(state) }
            }
        }
        viewModel.refreshDeviceInfo()
    }

    /** Renders one state snapshot and keeps all OTA-starting controls disabled while busy. */
    private fun bindState(state: JLOtaUpgradeUiState) {
        binding.statusText.text = state.status
        binding.tvMac.text = state.macText
        binding.tvFirmware.text = state.firmwareText
        binding.tvParsedVersion.text = state.parsedVersionText
        binding.tvNewVersion.isVisible = state.newVersionText.isNotBlank()
        binding.tvNewVersion.text = state.newVersionText
        binding.tvUpgradeInfo.isVisible = state.upgradeInfoText.isNotBlank()
        binding.tvUpgradeInfo.text = state.upgradeInfoText
        binding.progressPanel.isVisible = state.progressVisible
        binding.tvPhase.text = state.phaseText
        binding.tvProgress.text = state.progressText
        binding.progressBar.progress = state.progress

        val idle = !state.busy
        binding.btnRefresh.isEnabled = idle
        binding.btnCheck.isEnabled = idle
        binding.btnStart.isEnabled = idle && state.hasUpgradePackage
        binding.btnClearLogs.isEnabled = idle
        backCallback.isEnabled = state.busy
        keepScreenOn(state.busy)
        logAdapter.submit(state.logs)
    }

    private fun keepScreenOn(keep: Boolean) {
        activity?.window?.let { window ->
            if (keep) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onDestroyView() {
        keepScreenOn(false)
        super.onDestroyView()
        _binding = null
    }
}
