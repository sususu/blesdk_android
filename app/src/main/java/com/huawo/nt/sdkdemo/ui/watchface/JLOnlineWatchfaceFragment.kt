package com.huawo.nt.sdkdemo.ui.watchface

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
import androidx.recyclerview.widget.GridLayoutManager
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentOnlineWatchfaceBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/** jieli online catalog UI. Transport is intentionally fixed to JLOnlineWatchfaceViewModel's A3 type=4 path. */
class JLOnlineWatchfaceFragment : Fragment() {
    private var bindingRef: FragmentOnlineWatchfaceBinding? = null
    private val binding get() = bindingRef!!
    private val viewModel: JLOnlineWatchfaceViewModel by activityViewModels { ViewModelFactory() }
    private lateinit var adapter: OnlineWatchfaceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = FragmentOnlineWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Detail sheet is intentionally not opened while an A3 transfer is in flight.
        adapter = OnlineWatchfaceAdapter { item ->
            if (viewModel.uiState.value.installing) return@OnlineWatchfaceAdapter
            viewModel.select(item)
            if (childFragmentManager.findFragmentByTag(JLOnlineWatchfaceDetailSheet.TAG) == null) {
                JLOnlineWatchfaceDetailSheet().show(childFragmentManager, JLOnlineWatchfaceDetailSheet.TAG)
            }
        }
        binding.rvWatchfaces.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvWatchfaces.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadList() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.loading
                    adapter.submitList(state.items)
                    binding.rvWatchfaces.isVisible = state.items.isNotEmpty()
                    binding.emptyPanel.isVisible = !state.loading && (state.items.isEmpty() || !state.error.isNullOrBlank())
                    binding.tvEmpty.text = state.error ?: getString(R.string.owf_empty)
                }
            }
        }
        if (viewModel.uiState.value.items.isEmpty() && !viewModel.uiState.value.loading) viewModel.loadList()
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }
}
