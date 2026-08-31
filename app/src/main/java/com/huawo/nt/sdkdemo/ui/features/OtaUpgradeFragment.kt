package com.huawo.nt.sdkdemo.ui.features

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
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
import com.huawo.nt.sdkdemo.SdkDemoApp
import com.huawo.nt.sdkdemo.databinding.FragmentOtaUpgradeBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.common.LogAdapter
import com.sifli.siflidfu.ISifliDFUService
import com.sifli.siflidfu.Protocol
import com.sifli.siflidfu.SifliDFUService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OTA screen UI.
 *
 * Aligns with HaWoFit [WatchUpgradeNewActivity]:
 * - bind [SifliDFUService] early
 * - before DFU: block Home auto-reconnect (production uses isQJSOTAServiceRunning / isDfuBusy)
 * - do **not** disconnect BluetoothSDK before DFU
 * - register LocalBroadcast, delay ~1.5s, then [ISifliDFUService.startActionDFUNand]
 */
class OtaUpgradeFragment : Fragment() {
    private var _binding: FragmentOtaUpgradeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OtaUpgradeViewModel by viewModels { ViewModelFactory() }
    private val logAdapter = LogAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository get() = SdkDemoApp.instance.bleRepository

    private var dfuReceiver: BroadcastReceiver? = null

    private var sifliDfuService: ISifliDFUService? = null
    private var dfuBound = false
    /** True after [Context.bindService]; must [Context.unbindService] even if not yet connected. */
    private var dfuBindRequested = false
    private val dfuConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val dfuBinder = binder as? SifliDFUService.SifliDFUBinder
                sifliDfuService = dfuBinder?.dfuService
                dfuBound = sifliDfuService != null
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                sifliDfuService = null
                dfuBound = false
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
        binding.toolbar.setNavigationOnClickListener {
            // Do not pop while download/DFU is running.
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

        bindDfuService()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> bindState(state) }
                }
                launch {
                    // ViewModel emits after images are prepared; Fragment owns DFU service lifecycle.
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
        // Start stays disabled until check finds a newer package.
        binding.btnStart.isEnabled = idle && state.hasUpgradePackage
        binding.btnClearLogs.isEnabled = idle

        keepScreenOn(state.busy)
        logAdapter.submit(state.logs)
    }

    /** bindService early so DFU is ready when download/prepare finishes. */
    private fun bindDfuService() {
        if (dfuBindRequested) return
        val intent = Intent(requireContext(), SifliDFUService::class.java)
        dfuBindRequested = requireContext().bindService(intent, dfuConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindDfuService() {
        if (!dfuBindRequested) return
        runCatching { requireContext().unbindService(dfuConnection) }
        dfuBindRequested = false
        sifliDfuService = null
        dfuBound = false
    }

    /**
     * Same kickoff as HaWoFit WatchUpgradeNewActivity:
     * wait for bind → block Home auto-reconnect → register broadcasts →
     * delay 1.5s → [ISifliDFUService.startActionDFUNand].
     *
     * Do **not** call [BleRepository.disconnectWithoutClean] here — production keeps
     * the BluetoothSDK link and lets SifliDFU open its own GATT alongside.
     */
    private fun startSifliDfu(event: SifliDfuStartEvent) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                bindDfuService()
                val service =
                    withTimeoutOrNull(8_000L) {
                        while (sifliDfuService == null || !dfuBound) {
                            delay(50)
                        }
                        sifliDfuService
                    }
                if (service == null) {
                    viewModel.onDfuFail(-2, "SifliDFUService not bound")
                    return@launch
                }

                // Block Home reconnect while DFU owns / races the radio (HaWoFit uses isDfuBusy).
                repository.setSifliOtaInProgress(true)
                viewModel.onDfuLog("OTA: pause auto-reconnect")

                if (SifliDFUService.isDfuBusy()) {
                    viewModel.onDfuLog("OTA: previous DFU busy, stop()")
                    SifliDFUService.stop(requireContext())
                    delay(300)
                }

                registerDfuReceiver()
                delay(1500L)
                if (!isAdded) return@launch

                val dfu = sifliDfuService
                if (dfu == null || !dfuBound) {
                    finishOtaGate()
                    unregisterDfuReceiver()
                    viewModel.onDfuFail(-2, "SifliDFUService disconnected before start")
                    return@launch
                }
                if (SifliDFUService.isDfuBusy()) {
                    finishOtaGate()
                    unregisterDfuReceiver()
                    viewModel.onDfuFail(-2, "SifliDFUService is busy")
                    return@launch
                }

                viewModel.onDfuLog(
                    "OTA: startActionDFUNand mac=${event.mac} images=${event.imagePaths.size}",
                )
                dfu.startActionDFUNand(
                    requireContext(),
                    event.mac,
                    event.imagePaths,
                    Protocol.DFU_MODE_NORMAL,
                    0,
                )
            } catch (e: Exception) {
                finishOtaGate()
                unregisterDfuReceiver()
                viewModel.onDfuFail(-2, e.message.orEmpty().ifBlank { e.javaClass.simpleName })
            }
        }
    }

    private fun finishOtaGate() {
        repository.setSifliOtaInProgress(false)
    }

    /**
     * Listen for Sifli DFU LocalBroadcasts:
     * - PROGRESS → overall bar 40..100
     * - LOG → append to UI log list
     * - STATE EXIT → success (result==0, delayed) or failure
     */
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
                            // Deduplicate identical progress ticks.
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
                                // Keep reconnect blocked a bit after exit (device may reboot).
                                mainHandler.postDelayed({ finishOtaGate() }, 5_000L)
                                if (result == 0) {
                                    // Delay success UI so the device can settle / reboot.
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

    /** Keep screen awake during download / DFU so the process is not interrupted. */
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
        // Leaving the screen mid-OTA: stop DFU and allow reconnect again.
        if (repository.sifliOtaInProgress || SifliDFUService.isDfuBusy()) {
            runCatching { SifliDFUService.stop(requireContext()) }
        }
        finishOtaGate()
        unbindDfuService()
        keepScreenOn(false)
        super.onDestroyView()
        _binding = null
    }
}
