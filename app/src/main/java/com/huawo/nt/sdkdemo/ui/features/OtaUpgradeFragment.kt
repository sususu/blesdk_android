package com.huawo.nt.sdkdemo.ui.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.huawo.nt.sdkdemo.databinding.FragmentOtaUpgradeBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.common.LogAdapter
import com.sifli.siflidfu.Protocol
import com.sifli.siflidfu.SifliDFUService
import kotlinx.coroutines.launch

class OtaUpgradeFragment : Fragment() {
    private var _binding: FragmentOtaUpgradeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OtaUpgradeViewModel by viewModels { ViewModelFactory() }
    private val logAdapter = LogAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var dfuReceiver: BroadcastReceiver? = null

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
        binding.toolbar.setNavigationOnClickListener {
            if (!viewModel.uiState.value.busy) {
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
                launch {
                    viewModel.uiState.collect { state -> bindState(state) }
                }
                launch {
                    viewModel.sifliDfuStart.collect { event -> startSifliDfu(event) }
                }
            }
        }

        viewModel.refreshDeviceInfo()
    }

    private fun bindState(state: OtaUpgradeUiState) {
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

        keepScreenOn(state.busy)
        logAdapter.submit(state.logs)
    }

    private fun startSifliDfu(event: SifliDfuStartEvent) {
        registerDfuReceiver()
        // Delay so LocalBroadcast is ready (same as WatchUpgradeNewActivity)
        mainHandler.postDelayed({
            if (!isAdded) return@postDelayed
            try {
                SifliDFUService.startActionDFUNand(
                    requireContext(),
                    event.mac,
                    event.imagePaths,
                    Protocol.DFU_MODE_NORMAL,
                    0,
                )
            } catch (e: Exception) {
                unregisterDfuReceiver()
                viewModel.onDfuFail(-2, e.message.orEmpty().ifBlank { e.javaClass.simpleName })
            }
        }, 1500L)
    }

    private fun registerDfuReceiver() {
        if (dfuReceiver != null) return
        val receiver =
            object : BroadcastReceiver() {
                private var lastProgress = -1f

                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        SifliDFUService.BROADCAST_DFU_PROGRESS -> {
                            val progress = intent.getIntExtra(SifliDFUService.EXTRA_DFU_PROGRESS, 0)
                            val progressFloat = progress / 100f
                            if (lastProgress == progressFloat) return
                            lastProgress = progressFloat
                            viewModel.onDfuProgress(progress)
                        }
                        SifliDFUService.BROADCAST_DFU_LOG -> {
                            val msg = intent.getStringExtra(SifliDFUService.EXTRA_LOG_MESSAGE)
                            if (!msg.isNullOrBlank()) viewModel.onDfuLog(msg)
                        }
                        SifliDFUService.BROADCAST_DFU_STATE -> {
                            val dfuState = intent.getIntExtra(SifliDFUService.EXTRA_DFU_STATE, 0)
                            val result =
                                intent.getIntExtra(SifliDFUService.EXTRA_DFU_STATE_RESULT, 0)
                            if (dfuState == Protocol.DFU_SERVICE_EXIT) {
                                unregisterDfuReceiver()
                                if (result == 0) {
                                    mainHandler.postDelayed({
                                        if (isAdded) viewModel.onDfuSuccess()
                                    }, 3000L)
                                } else {
                                    viewModel.onDfuFail(
                                        result,
                                        "DFU exit with result=$result state=$dfuState",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        dfuReceiver = receiver
        val filter =
            IntentFilter().apply {
                addAction(SifliDFUService.BROADCAST_DFU_LOG)
                addAction(SifliDFUService.BROADCAST_DFU_STATE)
                addAction(SifliDFUService.BROADCAST_DFU_PROGRESS)
            }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, filter)
    }

    private fun unregisterDfuReceiver() {
        val receiver = dfuReceiver ?: return
        runCatching {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver)
        }
        dfuReceiver = null
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
        mainHandler.removeCallbacksAndMessages(null)
        unregisterDfuReceiver()
        keepScreenOn(false)
        super.onDestroyView()
        _binding = null
    }
}
