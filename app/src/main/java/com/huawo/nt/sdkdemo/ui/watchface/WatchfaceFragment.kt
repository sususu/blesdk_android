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

/**
 * Watchface host page with three top tabs: Custom / Online / AI.
 *
 * ## Tabs
 * - 0 Custom → [CustomWatchfaceFragment]
 * - 1 Online → [OnlineWatchfaceFragment] (catalog + Sifli install)
 * - 2 AI → [AiWatchfaceFragment] (or placeholder if not ready)
 *
 * ## Online install UI lock
 * Observes [OnlineWatchfaceViewModel] (`activityViewModels`) so that while
 * [OnlineWatchfaceUiState.installing] is true we:
 * - Enable [backCallback] to swallow system Back (toast instead of pop)
 * - Ignore toolbar navigation clicks
 * - Disable ViewPager swipe and TabLayout clicks
 *
 * The detail [OnlineWatchfaceDetailSheet] independently disables cancel/drag.
 * Together this matches the product requirement: no leave / dismiss mid-transfer.
 */
class WatchfaceFragment : Fragment() {
    private var _binding: FragmentWatchfaceBinding? = null
    private val binding get() = _binding!!

    /**
     * Shared with Online tab + detail sheet. Activity scope survives tab switches
     * inside this host so install progress is not lost when swiping away (swipe is
     * disabled while installing anyway).
     */
    private val onlineViewModel: OnlineWatchfaceViewModel by activityViewModels { ViewModelFactory() }

    private var tabMediator: TabLayoutMediator? = null

    /**
     * When enabled, consumes Back and shows a toast.
     * Enabled only while an online watchface install is in the critical section.
     */
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Toast.makeText(requireContext(), R.string.owf_back_blocked, Toast.LENGTH_SHORT)
                    .show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.toolbar.setNavigationOnClickListener {
            if (onlineViewModel.uiState.value.installing) {
                Toast.makeText(requireContext(), R.string.owf_back_blocked, Toast.LENGTH_SHORT)
                    .show()
                return@setNavigationOnClickListener
            }
            parentFragmentManager.popBackStack()
        }

        val pages =
            listOf(
                TabPage(R.string.watchface_tab_custom, R.string.watchface_hint_custom),
                TabPage(R.string.watchface_tab_online, R.string.watchface_hint_online),
                TabPage(R.string.watchface_tab_ai, R.string.watchface_hint_ai),
            )

        binding.viewPager.adapter =
            object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = pages.size

                override fun createFragment(position: Int): Fragment =
                    when (position) {
                        0 -> CustomWatchfaceFragment()
                        1 -> OnlineWatchfaceFragment()
                        2 -> AiWatchfaceFragment()
                        else -> WatchfaceTabFragment.newInstance(pages[position].hintRes)
                    }
            }

        tabMediator =
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.setText(pages[position].titleRes)
            }.also { it.attach() }

        // Mirror install lock onto host chrome (back + tabs + pager).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                onlineViewModel.uiState.collect { state ->
                    val busy = state.installing
                    backCallback.isEnabled = busy
                    binding.viewPager.isUserInputEnabled = !busy
                    binding.tabLayout.isEnabled = !busy
                    for (i in 0 until binding.tabLayout.tabCount) {
                        binding.tabLayout.getTabAt(i)?.view?.isClickable = !busy
                        binding.tabLayout.getTabAt(i)?.view?.isEnabled = !busy
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        super.onDestroyView()
        _binding = null
    }

    private data class TabPage(
        val titleRes: Int,
        val hintRes: Int,
    )
}
