package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentAiCenterBinding
import com.huawo.nt.sdkdemo.ui.watchface.AiWatchfaceFragment

/** AI navigation only; each destination keeps its own SDK initialization and request lifecycle. */
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
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnGuide.setOnClickListener { openFeature(AiGuideFragment()) }
        binding.btnOpenDemo.setOnClickListener { openFeature(AiWatchfaceFragment.newDemoInstance()) }
        binding.btnHealth.setOnClickListener { openReport(AiAnalysisViewModel.Report.HEALTH) }
        binding.btnInsight.setOnClickListener { openReport(AiAnalysisViewModel.Report.INSIGHT) }
        binding.btnBody.setOnClickListener { openReport(AiAnalysisViewModel.Report.BODY) }
        binding.btnSleepDay.setOnClickListener { openReport(AiAnalysisViewModel.Report.DAY) }
        binding.btnSleepWeek.setOnClickListener { openReport(AiAnalysisViewModel.Report.WEEK) }
        binding.btnSleepMonth.setOnClickListener { openReport(AiAnalysisViewModel.Report.MONTH) }
    }

    private fun openReport(report: AiAnalysisViewModel.Report) {
        openFeature(AiAnalysisFragment.newInstance(report))
    }

    private fun openFeature(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
