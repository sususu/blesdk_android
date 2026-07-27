package com.huawo.nt.sdkdemo.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentScanBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.util.PermissionHelper
import kotlinx.coroutines.launch

class ScanConnectFragment : Fragment() {
    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by viewModels { ViewModelFactory() }
    private lateinit var adapter: DeviceListAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                viewModel.startScan()
            } else {
                binding.statusText.text = getString(R.string.permission_denied)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DeviceListAdapter { device ->
            ensurePermissions { viewModel.connect(device) }
        }
        binding.deviceList.layoutManager = LinearLayoutManager(requireContext())
        binding.deviceList.adapter = adapter
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnScanToggle.setOnClickListener {
            val state = viewModel.uiState.value
            when {
                state.connecting -> Unit
                state.scanning -> viewModel.stopScan()
                else -> ensurePermissions { viewModel.startScan() }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.statusText.text = state.status
                        val busy = state.scanning || state.connecting
                        binding.statusProgress.isVisible = busy
                        binding.statusIcon.isVisible = !busy
                        binding.btnScanToggle.isEnabled = !state.connecting
                        binding.btnScanToggle.text =
                            if (state.scanning) getString(R.string.stop_scan) else getString(R.string.start_scan)
                        adapter.submit(state.devices, state.connecting)
                        binding.emptyView.isVisible = state.devices.isEmpty()
                        binding.emptyView.text =
                            if (state.scanning) getString(R.string.searching) else getString(R.string.no_devices)
                    }
                }
                launch {
                    viewModel.connectedDevice.collect { device ->
                        setFragmentResult(
                            RESULT_KEY,
                            bundleOf(
                                KEY_MAC to device.macAddress,
                                KEY_NAME to device.name,
                                KEY_RSSI to (device.rssi ?: Int.MIN_VALUE),
                            ),
                        )
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }

    private fun ensurePermissions(onGranted: () -> Unit) {
        if (PermissionHelper.hasAllPermissions(requireContext())) {
            onGranted()
        } else {
            permissionLauncher.launch(PermissionHelper.requiredPermissions())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "scan_connect_result"
        const val KEY_MAC = "mac"
        const val KEY_NAME = "name"
        const val KEY_RSSI = "rssi"
    }
}
