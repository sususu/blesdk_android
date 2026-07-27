package com.huawo.nt.sdkdemo.ui.features

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
import androidx.recyclerview.widget.GridLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentAlbumTransferBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.util.AlbumBinConverter
import kotlinx.coroutines.launch

/**
 * Album push screen (standalone Fragment).
 *
 * Responsibilities:
 * 1. Pick local images (system document picker, multi-select; business cap = 10)
 * 2. Preview selected images in a grid after returning to the app
 * 3. Accept watch screen size (default 466×466) used for crop/convert on push
 * 4. Query watch album free space and start / cancel push
 *
 * UI handles interaction and display only. Copying, slot allocation, and channel
 * selection live in [AlbumTransferViewModel] /
 * [com.huawo.nt.sdkdemo.data.repository.BleRepository].
 */
class AlbumTransferFragment : Fragment() {
    private var _binding: FragmentAlbumTransferBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlbumTransferViewModel by viewModels { ViewModelFactory() }
    private lateinit var photoAdapter: AlbumPhotoAdapter

    /**
     * System multi-document picker callback.
     * OpenMultipleDocuments does not enforce a count limit; the ViewModel truncates
     * to [AlbumTransferViewModel.MAX_PHOTOS]. Returned content Uris are copied into
     * cache before preview/push so Uri permission expiry is not a problem.
     */
    private val pickAlbumLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) {
                viewModel.onFilesPicked(uris)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAlbumTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Prefill size fields with the demo watch resolution; user may edit before push
        if (binding.etWidth.text.isNullOrBlank()) {
            binding.etWidth.setText(AlbumBinConverter.DEFAULT_WIDTH.toString())
        }
        if (binding.etHeight.text.isNullOrBlank()) {
            binding.etHeight.setText(AlbumBinConverter.DEFAULT_HEIGHT.toString())
        }

        // Preview grid: 3 columns; corner button removes one item from the pending list
        photoAdapter =
            AlbumPhotoAdapter { item ->
                viewModel.removePhoto(item)
            }
        binding.rvPhotos.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvPhotos.adapter = photoAdapter

        binding.btnQueryStorage.setOnClickListener { viewModel.queryStorage() }
        binding.btnPick.setOnClickListener {
            // Declare jpeg/png and image/* for better OEM picker filter compatibility
            pickAlbumLauncher.launch(arrayOf("image/jpeg", "image/png", "image/*"))
        }
        binding.btnPush.setOnClickListener {
            // Read current size inputs; invalid/empty falls back to default 466
            viewModel.pushSelected(readWidth(), readHeight())
        }
        binding.btnCancel.setOnClickListener { viewModel.cancelTransfer() }

        // Collect only while STARTED to avoid background refreshes / leaks
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }
    }

    /** Sync ViewModel state to views: texts, preview list, busy mask, button enablement. */
    private fun bindState(state: AlbumTransferUiState) {
        binding.statusText.text = state.status
        binding.resultText.text = state.result
        binding.tvSelectionInfo.text =
            getString(R.string.album_selected_count, state.photos.size, AlbumTransferViewModel.MAX_PHOTOS)
        photoAdapter.submitList(state.photos)
        binding.tvEmpty.isVisible = state.photos.isEmpty()
        binding.rvPhotos.isVisible = state.photos.isNotEmpty()
        // Cover the list while busy so remove / re-pick cannot race the transfer
        binding.busyMask.isVisible = state.busy

        val idle = !state.busy
        binding.btnQueryStorage.isEnabled = idle
        binding.btnPick.isEnabled = idle
        binding.btnPush.isEnabled = idle
        // Cancel is only enabled during transfer (same pattern as music push)
        binding.btnCancel.isEnabled = state.busy
        binding.etWidth.isEnabled = idle
        binding.etHeight.isEnabled = idle
    }

    /** Parse width; fall back to default so push never receives 0. */
    private fun readWidth(): Int =
        binding.etWidth.text?.toString()?.toIntOrNull()
            ?: AlbumBinConverter.DEFAULT_WIDTH

    /** Parse height; fall back to default on invalid input. */
    private fun readHeight(): Int =
        binding.etHeight.text?.toString()?.toIntOrNull()
            ?: AlbumBinConverter.DEFAULT_HEIGHT

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
