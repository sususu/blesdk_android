package com.huawo.nt.sdkdemo.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.BleDevice
import com.huawo.nt.sdkdemo.data.model.DevicePhase
import com.huawo.nt.sdkdemo.databinding.FragmentHomeBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.bind.BindFlowDialogFragment
import com.huawo.nt.sdkdemo.ui.common.LogAdapter
import com.huawo.nt.sdkdemo.ui.features.AgpsUpdateFragment
import com.huawo.nt.sdkdemo.ui.features.AiHealthAnalysisFragment
import com.huawo.nt.sdkdemo.ui.features.AiCenterFragment
import com.huawo.nt.sdkdemo.ui.features.AlarmsFragment
import com.huawo.nt.sdkdemo.ui.features.JLAlarmsFragment
import com.huawo.nt.sdkdemo.ui.features.JLDataSyncFragment
import com.huawo.nt.sdkdemo.ui.features.AlbumTransferFragment
import com.huawo.nt.sdkdemo.ui.features.AutoMonitoringFragment
import com.huawo.nt.sdkdemo.ui.features.ElectronicCardListFragment
import com.huawo.nt.sdkdemo.ui.features.GoalsFragment
import com.huawo.nt.sdkdemo.ui.features.JLOtaUpgradeFragment
import com.huawo.nt.sdkdemo.ui.features.LiftWristAwakenFragment
import com.huawo.nt.sdkdemo.ui.features.ManualMeasurementFragment
import com.huawo.nt.sdkdemo.ui.features.MusicTransferFragment
import com.huawo.nt.sdkdemo.ui.features.NotifyFragment
import com.huawo.nt.sdkdemo.ui.features.OtaUpgradeFragment
import com.huawo.nt.sdkdemo.ui.features.ReminderListenerFragment
import com.huawo.nt.sdkdemo.ui.features.offlinemap.OfflineMapDownloadFragment
import com.huawo.nt.sdkdemo.ui.scan.ScanConnectFragment
import com.huawo.nt.sdkdemo.ui.unbind.UnbindFlowDialogFragment
import com.huawo.nt.sdkdemo.ui.watchface.WatchfaceFragment
import com.huawo.nt.sdkdemo.ui.watchface.JLWatchfaceFragment
import com.huawo.nt.sdkdemo.ui.watchface.studio.WatchFaceStudioFragment
import com.huawo.nt.sdkdemo.util.AppLanguage
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels { ViewModelFactory() }
    private val logAdapter = LogAdapter()
    private var logsExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logsExpanded = savedInstanceState?.getBoolean(STATE_LOGS_EXPANDED) == true
        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = logAdapter
        binding.logList.isNestedScrollingEnabled = true
        applyLogsExpanded(animateIcon = false)
        setupLanguageMenu()

        // Layout-only toggle: keep collecting logs and leave SDK operations untouched.
        binding.logHeader.setOnClickListener {
            logsExpanded = !logsExpanded
            applyLogsExpanded(animateIcon = true)
            if (logsExpanded && logAdapter.itemCount > 0) {
                binding.logList.scrollToPosition(0)
            }
        }
        binding.btnClearLogs.setOnClickListener { viewModel.clearLogs() }

        binding.btnScan.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScanConnectFragment())
                .addToBackStack("scan")
                .commit()
        }
        binding.btnBind.setOnClickListener {
            if (viewModel.prepareBind()) {
                BindFlowDialogFragment().show(parentFragmentManager, "bind")
            }
        }
        binding.btnSync.setOnClickListener { viewModel.sync() }
        binding.btnSyncJl.setOnClickListener {
            val state = viewModel.uiState.value
            if (!state.busy && (state.bound || state.phase == DevicePhase.CONNECTED)) {
                openFeature(JLDataSyncFragment.newInstance(state.device?.macAddress))
            }
        }
        binding.btnUnbind.setOnClickListener {
            if (viewModel.prepareUnbind()) {
                UnbindFlowDialogFragment().show(parentFragmentManager, "unbind")
            }
        }
        binding.btnDisconnect.setOnClickListener { viewModel.disconnect() }
        binding.btnGoals.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(GoalsFragment())
        }
        binding.btnAiHealth.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AiHealthAnalysisFragment())
        }
        binding.btnAlarms.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AlarmsFragment())
        }
        binding.btnAlarmsJl.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(JLAlarmsFragment())
        }
        // 无屏功能各自打开独立示例页，连接和忙碌状态仍由首页统一校验。
        binding.btnAutoMonitoring.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AutoMonitoringFragment())
        }
        binding.btnReminderListenerJl.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(ReminderListenerFragment())
        }
        binding.btnElectronicCard.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(ElectronicCardListFragment())
        }
        binding.btnLiftWrist.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(LiftWristAwakenFragment())
        }
        binding.btnManualMeasureJl.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(ManualMeasurementFragment())
        }
        binding.btnNotify.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(NotifyFragment())
        }
        binding.btnMusic.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(MusicTransferFragment())
        }
        binding.btnAlbum.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AlbumTransferFragment())
        }
        binding.btnAgps.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AgpsUpdateFragment())
        }
        binding.btnOta.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(OtaUpgradeFragment())
        }
        binding.btnOtaJieli.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(JLOtaUpgradeFragment())
        }
        binding.btnWatchface.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(WatchfaceFragment())
        }
        binding.btnWatchfaceStudio.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(WatchFaceStudioFragment())
        }
        binding.btnAi.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AiCenterFragment())
        }
        binding.btnOfflineMap.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(OfflineMapDownloadFragment())
        }
        binding.btnWatchfaceJieli.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(JLWatchfaceFragment())
        }
        parentFragmentManager.setFragmentResultListener(
            ScanConnectFragment.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val mac = bundle.getString(ScanConnectFragment.KEY_MAC).orEmpty()
            if (mac.isNotEmpty()) {
                viewModel.onDeviceConnected(
                    BleDevice(
                        name = bundle.getString(ScanConnectFragment.KEY_NAME),
                        macAddress = mac,
                        rssi = bundle.getInt(ScanConnectFragment.KEY_RSSI).takeIf { it != Int.MIN_VALUE },
                    ),
                )
            }
        }

        parentFragmentManager.setFragmentResultListener(
            BindFlowDialogFragment.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            if (bundle.getBoolean(BindFlowDialogFragment.KEY_SUCCESS)) {
                viewModel.onBindSuccess(BindFlowDialogFragment.readDeviceInfo(bundle))
            } else {
                viewModel.onBindCancelled()
            }
        }

        parentFragmentManager.setFragmentResultListener(
            UnbindFlowDialogFragment.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            when {
                bundle.getBoolean(UnbindFlowDialogFragment.KEY_SUCCESS) ->
                    viewModel.onUnbindSuccess()
                else ->
                    viewModel.onUnbindCancelled(
                        failed = bundle.getBoolean(UnbindFlowDialogFragment.KEY_FAILED),
                    )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun setupLanguageMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_home)
        binding.toolbar.setOnMenuItemClickListener { item ->
            val language =
                when (item.itemId) {
                    R.id.lang_system -> AppLanguage.SYSTEM
                    R.id.lang_zh -> AppLanguage.CHINESE
                    R.id.lang_en -> AppLanguage.ENGLISH
                    else -> return@setOnMenuItemClickListener false
                }
            val current = LocaleHelper.getLanguage(requireContext())
            if (current == language) return@setOnMenuItemClickListener true
            LocaleHelper.setLanguage(requireContext(), language)
            requireActivity().recreate()
            true
        }
    }

    private fun render(state: HomeUiState) {
        binding.statusText.text = state.status
        binding.statusProgress.isVisible = state.busy
        binding.statusIcon.isVisible = !state.busy
        binding.toolbar.title = getString(R.string.app_name)
        binding.toolbar.subtitle = state.sdkVersion?.let { "v$it" }

        val device = state.device
        binding.deviceCard.isVisible = device != null
        if (device != null) {
            binding.deviceName.text =
                if (device.name.isNullOrBlank()) getString(R.string.unknown_device) else device.name
            binding.deviceMac.text = device.macAddress
            binding.deviceState.text =
                if (state.bound) getString(R.string.bound) else getString(R.string.connected)
        }

        binding.syncSummary.isVisible = state.syncSummary.isNotBlank()
        binding.syncSummary.text = state.syncSummary

        val logCount = state.logs.size
        binding.logCount.isVisible = logCount > 0
        binding.logCount.text = getString(R.string.home_logs_count, logCount)
        binding.logEmpty.isVisible = logsExpanded && logCount == 0
        binding.logList.isVisible = logCount > 0
        binding.btnClearLogs.isEnabled = logCount > 0

        val canBind = !state.busy && device != null && !state.bound
        val canSync = !state.busy && (state.bound || state.phase == DevicePhase.CONNECTED)
        val canUnbind = !state.busy && state.bound
        val canFeature = !state.busy && device != null &&
            (state.bound || state.phase == DevicePhase.CONNECTED)
        binding.btnScan.isEnabled = !state.busy
        binding.btnBind.isEnabled = canBind
        binding.btnSync.isEnabled = canSync
        binding.btnSyncJl.isEnabled = canSync
        binding.btnUnbind.isEnabled = canUnbind
        binding.btnDisconnect.isEnabled = !state.busy && device != null
        binding.btnGoals.isEnabled = canFeature
        binding.btnAiHealth.isEnabled = canFeature
        binding.btnAlarms.isEnabled = canFeature
        binding.btnAlarmsJl.isEnabled = canFeature
        binding.btnAutoMonitoring.isEnabled = canFeature
        binding.btnReminderListenerJl.isEnabled = canFeature
        binding.btnElectronicCard.isEnabled = canFeature
        binding.btnLiftWrist.isEnabled = canFeature
        binding.btnManualMeasureJl.isEnabled = canFeature
        binding.btnNotify.isEnabled = canFeature
        binding.btnMusic.isEnabled = canFeature
        binding.btnAlbum.isEnabled = canFeature
        binding.btnAgps.isEnabled = canFeature
        binding.btnOta.isEnabled = canFeature
        binding.btnOtaJieli.isEnabled = canFeature
        binding.btnWatchface.isEnabled = canFeature
        binding.btnWatchfaceStudio.isEnabled = canFeature
        binding.btnAi.isEnabled = canFeature
        binding.btnOfflineMap.isEnabled = canFeature
        binding.btnWatchfaceJieli.isEnabled = canFeature

        val previousCount = logAdapter.itemCount
        logAdapter.submit(state.logs)
        if (logsExpanded && state.logs.isNotEmpty() && state.logs.size != previousCount) {
            binding.logList.scrollToPosition(0)
        }
    }

    private fun applyLogsExpanded(animateIcon: Boolean) {
        binding.logPanel.isVisible = logsExpanded
        binding.logExpandIcon.contentDescription =
            getString(if (logsExpanded) R.string.home_hide_logs else R.string.home_show_logs)
        val targetRotation = if (logsExpanded) 180f else 0f
        if (animateIcon) {
            binding.logExpandIcon.animate().rotation(targetRotation).setDuration(180L).start()
        } else {
            binding.logExpandIcon.rotation = targetRotation
        }
        binding.logEmpty.isVisible = logsExpanded && logAdapter.itemCount == 0
        binding.logList.isVisible = logAdapter.itemCount > 0
    }

    private fun openFeature(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOGS_EXPANDED, logsExpanded)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STATE_LOGS_EXPANDED = "home_logs_expanded"
    }
}
