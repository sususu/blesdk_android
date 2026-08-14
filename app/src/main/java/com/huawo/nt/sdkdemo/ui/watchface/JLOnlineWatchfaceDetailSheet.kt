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

/** jieli online watchface detail. Dismiss is locked until the  transfer has a terminal callback. */
class JLOnlineWatchfaceDetailSheet : BottomSheetDialogFragment() {
    private var bindingRef: DialogOnlineWatchfaceDetailBinding? = null
    private val binding get() = bindingRef!!
    private val viewModel: JLOnlineWatchfaceViewModel by activityViewModels { ViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = DialogOnlineWatchfaceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnInstall.setOnClickListener { viewModel.installSelected() }
        binding.btnClose.setOnClickListener {
            if (!viewModel.uiState.value.installing) {
                viewModel.select(null)
                dismissAllowingStateLoss()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::bind)
            }
        }
    }

    /** Keeps every dismissal path unavailable until the A3 callback reports success or failure. */
    private fun bind(state: JLOnlineWatchfaceUiState) {
        val item = state.selected ?: return
        binding.tvTitle.text = item.name
        binding.tvSize.text = if (item.byteSizeKb > 0) getString(R.string.owf_size_label, item.byteSizeKb) else getString(R.string.owf_size_unknown)
        RemoteImageLoader.load(binding.ivThumb, WatchfaceApi.resolveFileUrl(item.thumbnail))
        val aod = WatchfaceApi.resolveFileUrl(item.aodThumbnail)
        binding.aodPanel.isVisible = aod.isNotBlank()
        if (aod.isNotBlank()) RemoteImageLoader.load(binding.ivAod, aod)
        isCancelable = !state.installing
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            isDraggable = !state.installing
            isHideable = !state.installing
            if (state.installing) this.state = BottomSheetBehavior.STATE_EXPANDED
        }
        binding.progressPanel.isVisible = state.installing || state.installPhase == JLOnlineWatchfaceInstallPhase.SUCCESS
        binding.tvProgress.text = state.installMessage
        binding.progressBar.progress = state.installProgress
        binding.tvError.isVisible = !state.installError.isNullOrBlank()
        binding.tvError.text = state.installError
        binding.btnInstall.isEnabled = !state.installing
        binding.btnInstall.isVisible = state.installPhase != JLOnlineWatchfaceInstallPhase.SUCCESS
        binding.btnClose.isVisible = !state.installing && state.installPhase in setOf(JLOnlineWatchfaceInstallPhase.SUCCESS, JLOnlineWatchfaceInstallPhase.FAILED)
        binding.btnInstall.text = if (state.installPhase == JLOnlineWatchfaceInstallPhase.FAILED) getString(R.string.owf_retry) else getString(R.string.owf_install)
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    companion object { const val TAG = "JLOnlineWatchfaceDetailSheet" }
}
