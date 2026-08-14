package com.huawo.nt.sdkdemo.ui.watchface

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentWatchfaceBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/** Fixed JieLi watchface sample */
class JLWatchfaceFragment : Fragment() {
    private var bindingRef: FragmentWatchfaceBinding? = null
    private val binding get() = bindingRef!!
    private val onlineViewModel: JLOnlineWatchfaceViewModel by activityViewModels { ViewModelFactory() }
    private var mediator: TabLayoutMediator? = null
    private var childTransferBusy = false
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Toast.makeText(requireContext(), R.string.owf_back_blocked, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = FragmentWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        binding.toolbar.title = getString(R.string.feature_watchface_jl)
        binding.toolbar.setNavigationOnClickListener {
            if (isTransferBusy()) {
                Toast.makeText(requireContext(), R.string.owf_back_blocked, Toast.LENGTH_SHORT).show()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        val titles = listOf(R.string.watchface_tab_custom, R.string.watchface_tab_online, R.string.watchface_tab_ai)
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = titles.size
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> JLCustomWatchfaceFragment()
                1 -> JLOnlineWatchfaceFragment()
                2 -> JLAiWatchfaceFragment()
                else -> error("Unknown JL watchface tab: $position")
            }
        }
        mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position -> tab.setText(titles[position]) }.also { it.attach() }
        childFragmentManager.setFragmentResultListener(TRANSFER_RESULT, viewLifecycleOwner) { _, result ->
            childTransferBusy = result.getBoolean(TRANSFER_BUSY, false)
            updateTransferLock()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiState.collect { updateTransferLock() }
            }
        }
    }

    // Custom and AI tabs publish their state through FragmentResult; online install is ViewModel-owned.
    private fun isTransferBusy() = childTransferBusy || onlineViewModel.uiState.value.installing

    /** Locks Back and tab changes so no child page is destroyed while its SDK transfer is active. */
    private fun updateTransferLock() {
        val busy = isTransferBusy()
        backCallback.isEnabled = busy
        binding.viewPager.isUserInputEnabled = !busy
        binding.tabLayout.isEnabled = !busy
        for (index in 0 until binding.tabLayout.tabCount) {
            binding.tabLayout.getTabAt(index)?.view?.isEnabled = !busy
            binding.tabLayout.getTabAt(index)?.view?.isClickable = !busy
        }
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        bindingRef = null
        super.onDestroyView()
    }

    companion object {
        const val TRANSFER_RESULT = "jl_watchface_transfer"
        const val TRANSFER_BUSY = "busy"
    }
}
