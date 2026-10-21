package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.local.AiHealthRecord
import com.huawo.nt.sdkdemo.databinding.FragmentAiHealthBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 对应 HaWoFit 的运动健康分析页：没有记录时提示在手表上生成，有记录时展示最近一次报告。
 */
class AiHealthAnalysisFragment : Fragment() {
    private var _binding: FragmentAiHealthBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AiHealthAnalysisViewModel by activityViewModels { ViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.startListening()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiHealthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.toolbar.inflateMenu(R.menu.menu_ai_health)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_ai_health_history) {
                openHistory()
                true
            } else {
                false
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AiHealthAnalysisViewModel.State) {
        binding.statusText.text = state.status
        val report = state.records.firstOrNull { it.id == state.selectedId } ?: state.records.firstOrNull()
        binding.toolbar.menu.findItem(R.id.action_ai_health_history)?.isVisible = state.records.isNotEmpty()
        binding.emptyGroup.isVisible = report == null
        binding.reportScroll.isVisible = report != null
        if (report != null) showReport(report)
    }

    private fun showReport(report: AiHealthRecord) {
        binding.reportTime.text = formatReportTime(report.timeMillis)
        val sections = binding.reportSections
        sections.removeAllViews()
        report.metrics().forEach { (titleRes, content) ->
            if (content.isBlank()) return@forEach
            val block = layoutInflater.inflate(R.layout.item_ai_health_section, sections, false)
            block.findViewById<TextView>(R.id.section_title).setText(titleRes)
            block.findViewById<TextView>(R.id.section_body).text = content
            sections.addView(block)
        }
        val hasSummary = report.summary.isNotBlank()
        binding.summaryBox.isVisible = hasSummary
        binding.summaryBody.text = report.summary
    }

    private fun AiHealthRecord.metrics(): List<Pair<Int, String>> =
        listOf(
            R.string.ai_health_steps to step,
            R.string.ai_health_heart_rate to heartRate,
            R.string.ai_health_sleep to sleep,
            R.string.ai_health_spo2 to spo2,
            R.string.ai_health_stress to stress,
            R.string.ai_health_pai to pai,
            R.string.ai_health_weight to weight,
            R.string.ai_health_bp to bp,
        )

    private fun formatReportTime(timeMillis: Long): String {
        val locale = resources.configuration.locales[0]
        val timeText = SimpleDateFormat(if (locale.language.startsWith("zh")) "HH:mm" else "h:mm a", locale)
            .format(Date(timeMillis))
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        val dateText = if (sameDay) {
            getString(R.string.ai_health_today)
        } else {
            SimpleDateFormat("MM-dd", Locale.ENGLISH).format(Date(timeMillis))
        }
        return "$dateText $timeText"
    }

    private fun openHistory() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AiHealthHistoryFragment())
            .addToBackStack(AiHealthHistoryFragment::class.java.simpleName)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        viewModel.stopListening()
        super.onDestroy()
    }
}
