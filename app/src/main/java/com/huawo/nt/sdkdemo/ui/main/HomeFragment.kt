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
import com.huawo.nt.sdkdemo.ui.features.AiCenterFragment
import com.huawo.nt.sdkdemo.ui.features.AlarmsFragment
import com.huawo.nt.sdkdemo.ui.features.AlbumTransferFragment
import com.huawo.nt.sdkdemo.ui.features.GoalsFragment
import com.huawo.nt.sdkdemo.ui.features.JLOtaUpgradeFragment
import com.huawo.nt.sdkdemo.ui.features.MusicTransferFragment
import com.huawo.nt.sdkdemo.ui.features.NotifyFragment
import com.huawo.nt.sdkdemo.ui.features.OtaUpgradeFragment
import com.huawo.nt.sdkdemo.ui.scan.ScanConnectFragment
import com.huawo.nt.sdkdemo.ui.unbind.UnbindFlowDialogFragment
import com.huawo.nt.sdkdemo.ui.watchface.WatchfaceFragment
import com.huawo.nt.sdkdemo.ui.watchface.JLWatchfaceFragment
import com.huawo.nt.sdkdemo.util.AppLanguage
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels { ViewModelFactory() }
    private val logAdapter = LogAdapter()

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
        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = logAdapter
        binding.logList.isNestedScrollingEnabled = true
        setupLanguageMenu()

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
        binding.btnSyncJl.setOnClickListener { viewModel.syncJl() }
        binding.btnUnbind.setOnClickListener {
            if (viewModel.prepareUnbind()) {
                UnbindFlowDialogFragment().show(parentFragmentManager, "unbind")
            }
        }
        binding.btnDisconnect.setOnClickListener { viewModel.disconnect() }
        binding.btnGoals.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(GoalsFragment())
        }
        binding.btnAlarms.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AlarmsFragment())
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
        binding.btnAi.setOnClickListener {
            if (viewModel.prepareFeature()) openFeature(AiCenterFragment())
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
        binding.btnAlarms.isEnabled = canFeature
        binding.btnNotify.isEnabled = canFeature
        binding.btnMusic.isEnabled = canFeature
        binding.btnAlbum.isEnabled = canFeature
        binding.btnAgps.isEnabled = canFeature
        binding.btnOta.isEnabled = canFeature
        binding.btnOtaJieli.isEnabled = canFeature
        binding.btnWatchface.isEnabled = canFeature
        binding.btnAi.isEnabled = canFeature
        binding.btnWatchfaceJieli.isEnabled = canFeature

        logAdapter.submit(state.logs)
    }

    private fun openFeature(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
