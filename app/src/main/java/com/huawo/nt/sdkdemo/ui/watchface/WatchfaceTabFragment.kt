package com.huawo.nt.sdkdemo.ui.watchface

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentWatchfaceTabBinding

/**
 * Placeholder content for one Watchface tab.
 * Replace with real custom / online / AI flows when requirements are ready.
 */
class WatchfaceTabFragment : Fragment() {
    private var _binding: FragmentWatchfaceTabBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWatchfaceTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val hintRes = arguments?.getInt(ARG_HINT_RES) ?: 0
        if (hintRes != 0) {
            binding.hintText.setText(hintRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_HINT_RES = "hint_res"

        fun newInstance(@StringRes hintRes: Int): WatchfaceTabFragment =
            WatchfaceTabFragment().apply {
                arguments = bundleOf(ARG_HINT_RES to hintRes)
            }
    }
}
