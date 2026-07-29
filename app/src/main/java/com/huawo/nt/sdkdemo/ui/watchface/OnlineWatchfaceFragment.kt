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
import com.huawo.nt.sdkdemo.databinding.FragmentOnlineWatchfaceBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * **Online** tab content inside [WatchfaceFragment].
 *
 * ## Responsibilities
 * - Trigger [OnlineWatchfaceViewModel.loadList] when first shown (empty + not loading)
 * - Render a 2-column grid of [OnlineWatchfaceAdapter]
 * - On item click: [OnlineWatchfaceViewModel.select] then show [OnlineWatchfaceDetailSheet]
 *
 * ## ViewModel scope
 * Uses `activityViewModels` so the host [WatchfaceFragment] can observe
 * [OnlineWatchfaceUiState.installing] and lock back / tabs during transfer.
 * The detail sheet must use the same scope.
 *
 * ## Notes
 * - List fetch needs a valid bound `deviceType`; otherwise empty panel + retry.
 * - Do not open a second sheet if one with [OnlineWatchfaceDetailSheet.TAG] is already added.
 * - UI only; download/MD5/Sifli push live in the ViewModel / repository.
 */
class OnlineWatchfaceFragment : Fragment() {
    private var _binding: FragmentOnlineWatchfaceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnlineWatchfaceViewModel by activityViewModels { ViewModelFactory() }

    private lateinit var adapter: OnlineWatchfaceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnlineWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter =
            OnlineWatchfaceAdapter { item ->
                // Select first so the sheet’s first StateFlow emission already has data.
                viewModel.select(item)
                if (childFragmentManager.findFragmentByTag(OnlineWatchfaceDetailSheet.TAG) == null) {
                    OnlineWatchfaceDetailSheet().show(
                        childFragmentManager,
                        OnlineWatchfaceDetailSheet.TAG,
                    )
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
                    // Empty panel covers both “true empty” and list-load errors.
                    val showEmpty =
                        !state.loading && (state.items.isEmpty() || !state.error.isNullOrBlank())
                    binding.emptyPanel.isVisible = showEmpty
                    if (!state.error.isNullOrBlank()) {
                        binding.tvEmpty.text = state.error
                    } else if (state.items.isEmpty()) {
                        binding.tvEmpty.text = getString(com.huawo.nt.sdkdemo.R.string.owf_empty)
                    }
                    binding.rvWatchfaces.isVisible = state.items.isNotEmpty()
                }
            }
        }

        // Avoid refetching every time ViewPager recreates the fragment if data is warm.
        if (viewModel.uiState.value.items.isEmpty() && !viewModel.uiState.value.loading) {
            viewModel.loadList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
