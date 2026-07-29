package com.huawo.nt.sdkdemo.ui.watchface

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.remote.WatchfaceApi
import com.huawo.nt.sdkdemo.databinding.DialogOnlineWatchfaceDetailBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.util.RemoteImageLoader
import kotlinx.coroutines.launch

/**
 * Bottom sheet for one online watchface: previews + size + Install.
 *
 * ## Install lock (requirement)
 * While [OnlineWatchfaceUiState.installing] is true:
 * - [isCancelable] = false (blocks back / outside dismiss)
 * - BottomSheet [isDraggable] / [isHideable] = false (blocks swipe-away)
 * - Install button disabled; Close hidden
 *
 * Host [WatchfaceFragment] separately blocks toolbar back and tab switching.
 *
 * ## Flow
 * 1. Parent sets [OnlineWatchfaceViewModel.select] then shows this sheet.
 * 2. User taps Install → [OnlineWatchfaceViewModel.installSelected].
 * 3. Progress panel tracks download then Sifli push percentages.
 * 4. Success → Close; Failed → Retry (reuses Install button) + Close.
 *
 * ## Notes
 * - AOD column is hidden when `aodThumbnail` resolves to blank.
 * - Image URLs must go through [WatchfaceApi.resolveFileUrl].
 * - Share ViewModel via `activityViewModels` (same as list + host).
 */
class OnlineWatchfaceDetailSheet : BottomSheetDialogFragment() {
    private var _binding: DialogOnlineWatchfaceDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnlineWatchfaceViewModel by activityViewModels { ViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default cancelable; flipped off dynamically when installing.
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogOnlineWatchfaceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnInstall.setOnClickListener { viewModel.installSelected() }
        binding.btnClose.setOnClickListener {
            // Hard guard: never dismiss mid-transfer even if the button were somehow visible.
            if (!viewModel.uiState.value.installing) {
                dismissAllowingStateLoss()
                viewModel.select(null)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> bindState(state) }
            }
        }
    }

    /**
     * Bind selection + install progress into the sheet widgets.
     * Returns early if [OnlineWatchfaceUiState.selected] is null (sheet may be closing).
     */
    private fun bindState(state: OnlineWatchfaceUiState) {
        val item = state.selected
        if (item == null) {
            return
        }

        binding.tvTitle.text = item.name
        binding.tvSize.text =
            if (item.byteSizeKb > 0) {
                getString(R.string.owf_size_label, item.byteSizeKb)
            } else {
                getString(R.string.owf_size_unknown)
            }

        RemoteImageLoader.load(binding.ivThumb, WatchfaceApi.resolveFileUrl(item.thumbnail))
        val aodUrl = WatchfaceApi.resolveFileUrl(item.aodThumbnail)
        binding.aodPanel.isVisible = aodUrl.isNotBlank()
        if (aodUrl.isNotBlank()) {
            RemoteImageLoader.load(binding.ivAod, aodUrl)
        }

        val installing = state.installing
        // --- Critical-section dismiss locks ---
        isCancelable = !installing
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            isDraggable = !installing
            isHideable = !installing
            if (installing) {
                // Keep fully expanded so progress stays visible.
                this.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        val showProgress =
            installing ||
                state.installPhase == OnlineWatchfaceInstallPhase.SUCCESS ||
                state.installPhase == OnlineWatchfaceInstallPhase.FAILED
        binding.progressPanel.isVisible =
            installing || state.installPhase == OnlineWatchfaceInstallPhase.SUCCESS
        binding.tvProgress.text = state.installMessage
        binding.progressBar.progress = state.installProgress

        binding.tvError.isVisible = !state.installError.isNullOrBlank()
        binding.tvError.text = state.installError

        binding.btnInstall.isEnabled = !installing
        binding.btnInstall.isVisible =
            state.installPhase != OnlineWatchfaceInstallPhase.SUCCESS
        binding.btnClose.isVisible =
            !installing &&
                (
                    state.installPhase == OnlineWatchfaceInstallPhase.SUCCESS ||
                        state.installPhase == OnlineWatchfaceInstallPhase.FAILED ||
                        showProgress
                )
        when (state.installPhase) {
            OnlineWatchfaceInstallPhase.SUCCESS -> {
                binding.btnClose.isVisible = true
                binding.btnClose.text = getString(R.string.close)
            }
            OnlineWatchfaceInstallPhase.FAILED -> {
                // Allow retry without closing the sheet / re-selecting the item.
                binding.btnClose.isVisible = true
                binding.btnInstall.isVisible = true
                binding.btnInstall.text = getString(R.string.owf_retry)
            }
            else -> {
                binding.btnInstall.text = getString(R.string.owf_install)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OnlineWatchfaceDetailSheet"
    }
}
