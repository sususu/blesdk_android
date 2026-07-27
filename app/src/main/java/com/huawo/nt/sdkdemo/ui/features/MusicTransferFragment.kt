package com.huawo.nt.sdkdemo.ui.features

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.LocalMusicFile
import com.huawo.nt.sdkdemo.databinding.FragmentMusicTransferBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.scan.ScanConnectFragment
import com.huawo.nt.sdkdemo.util.MusicFileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Music file push screen (interaction patterned after HUAWO-TOOL [SppTestFragment]).
 *
 * Responsibilities:
 * - Fragment: permissions, scan local music, selection UX, navigate to BLE scan
 * - [MusicTransferViewModel]: device status / storage / pairing / transfer progress
 * - [BleRepository.pushMusicFiles]: channel selection (SPP / Sifli) and upload
 *
 * Layout top → bottom: device card → progress → send/cancel → selection actions → list → Result.
 */
class MusicTransferFragment : Fragment() {
    private var _binding: FragmentMusicTransferBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicTransferViewModel by viewModels { ViewModelFactory() }

    /** Shared mutable list with the adapter; scan results are written here. */
    private val musicFiles = mutableListOf<LocalMusicFile>()
    private lateinit var musicAdapter: MusicFileAdapter

    /**
     * Request media read permission, then scan.
     * Android 13+: READ_MEDIA_AUDIO; older: READ_EXTERNAL_STORAGE.
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                scanMusicFiles()
            } else {
                binding.statusText.text = getString(R.string.music_permission_denied)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMusicTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        // Toolbar refresh ≈ tool-page “refresh list + device status”
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_refresh) {
                requestScanAndRefresh()
                true
            } else {
                false
            }
        }

        musicAdapter =
            MusicFileAdapter(musicFiles) { count, size ->
                // Keep “selected count / total size” in sync with checkboxes
                binding.tvSelectionInfo.text = getString(R.string.music_selected_count, count)
                binding.tvTotalSize.text =
                    getString(R.string.music_total_size, LocalMusicFile.formatSize(size))
            }
        binding.rvMusicFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMusicFiles.adapter = musicAdapter

        // If BLE is down, open scan; after connect, FragmentResult refreshes this page
        binding.btnConnectBle.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScanConnectFragment())
                .addToBackStack("scan_from_music")
                .commit()
        }
        // Classic BT pairing: SPP path needs createBond + profile connection
        binding.btnPairBt.setOnClickListener { viewModel.createBond() }
        binding.btnRefreshStorage.setOnClickListener { viewModel.queryStorage() }
        binding.btnSend.setOnClickListener {
            viewModel.pushSelected(musicAdapter.selectedFiles())
        }
        binding.btnCancel.setOnClickListener { viewModel.cancelTransfer() }
        binding.btnSelectAll.setOnClickListener { musicAdapter.selectAll() }
        binding.btnDeselectAll.setOnClickListener { musicAdapter.deselectAll() }
        binding.btnInvertSelection.setOnClickListener { musicAdapter.invertSelection() }

        parentFragmentManager.setFragmentResultListener(
            ScanConnectFragment.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            // After scan-connect success: refresh BLE/BT/storage without wiping Result
            viewModel.refreshDeviceInfo()
            viewModel.queryStorage(silent = true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }

        // First entry: device info + silent storage query + permission/scan
        viewModel.refreshDeviceInfo()
        viewModel.queryStorage(silent = true)
        requestScanAndRefresh()
    }

    /** Bind ViewModel state; while sending, disable everything except Cancel. */
    private fun bindState(state: MusicTransferUiState) {
        binding.statusText.text = state.status
        binding.resultText.text = state.result
        binding.tvDeviceName.text = state.deviceName
        binding.tvDeviceMac.text = state.deviceMac
        binding.tvBleState.text =
            if (state.bleConnected) {
                getString(R.string.music_ble_connected)
            } else {
                getString(R.string.music_ble_disconnected)
            }
        // BT row shows both bond state and classic-BT connection state
        binding.tvBtState.text =
            buildString {
                append(
                    if (state.bonded) {
                        getString(R.string.music_bt_bonded)
                    } else {
                        getString(R.string.music_bt_unbonded)
                    },
                )
                append(" · ")
                append(
                    if (state.btConnected) {
                        getString(R.string.music_bt_connected)
                    } else {
                        getString(R.string.music_bt_disconnected)
                    },
                )
            }
        binding.tvStorage.text = state.storageText
        binding.progressPanel.isVisible = state.sending
        // Mask blocks list clicks so selection cannot change mid-transfer
        binding.sendingMask.isVisible = state.sending
        binding.progressBar.progress = state.progress
        binding.tvProgress.text = state.progressText

        val idle = !state.sending
        binding.btnConnectBle.isEnabled = idle
        binding.btnPairBt.isEnabled = idle
        binding.btnRefreshStorage.isEnabled = idle
        binding.btnSend.isEnabled = idle
        binding.btnCancel.isEnabled = state.sending
        binding.btnSelectAll.isEnabled = idle
        binding.btnDeselectAll.isEnabled = idle
        binding.btnInvertSelection.isEnabled = idle
    }

    /** Refresh device info, then request media permission; scan after grant. */
    private fun requestScanAndRefresh() {
        viewModel.refreshDeviceInfo()
        viewModel.queryStorage(silent = true)
        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        permissionLauncher.launch(permissions)
    }

    /** Scan MediaStore on IO; update list / empty state on main. */
    private fun scanMusicFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.statusText.text = getString(R.string.music_scanning)
            val files =
                withContext(Dispatchers.IO) {
                    MusicFileScanner.scan(requireContext())
                }
            musicAdapter.submit(files)
            updateListVisibility()
            binding.statusText.text =
                getString(R.string.music_scan_done, files.size)
            viewModel.refreshBtStatus()
        }
    }

    private fun updateListVisibility() {
        val empty = musicFiles.isEmpty()
        binding.tvEmpty.isVisible = empty
        binding.rvMusicFiles.isVisible = !empty
        binding.actionBarLayout.isVisible = !empty
        binding.selectionInfoLayout.isVisible = !empty
    }

    override fun onResume() {
        super.onResume()
        // Returning from scan page or permission settings: refresh connection flags
        viewModel.refreshDeviceInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
