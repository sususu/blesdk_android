package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentAiCenterBinding
import com.huawo.nt.sdkdemo.ui.watchface.AiWatchfaceFragment

/**
 * Home → Device features → AI
 *
 * Card-based [com.huawo.ai.AiCenter] integration guide (localized strings).
 * Opens [AiWatchfaceFragment] for the live demo.
 */
class AiCenterFragment : Fragment() {
    private var _binding: FragmentAiCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAiCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnOpenDemo.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AiWatchfaceFragment())
                .addToBackStack(AiWatchfaceFragment::class.java.simpleName)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
