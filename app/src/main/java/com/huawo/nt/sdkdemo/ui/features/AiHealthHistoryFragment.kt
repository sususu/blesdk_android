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
import com.google.android.material.card.MaterialCardView
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.local.AiHealthRecord
import com.huawo.nt.sdkdemo.databinding.FragmentAiHealthHistoryBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 历史报告列表。点选后回到详情并展示对应记录。 */
class AiHealthHistoryFragment : Fragment() {
    private var _binding: FragmentAiHealthHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AiHealthAnalysisViewModel by activityViewModels { ViewModelFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiHealthHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it.records) }
            }
        }
    }

    private fun render(records: List<AiHealthRecord>) {
        binding.emptyText.isVisible = records.isEmpty()
        val container = binding.historyList
        container.removeAllViews()
        var lastMonth = ""
        val monthFormat = SimpleDateFormat("yyyy-MM", resources.configuration.locales[0])
        records.forEach { record ->
            val month = monthFormat.format(Date(record.timeMillis))
            if (month != lastMonth) {
                container.addView(monthHeader(month, container.childCount > 0))
                lastMonth = month
            }
            container.addView(recordCard(record))
        }
    }

    private fun monthHeader(month: String, spaced: Boolean): TextView =
        TextView(requireContext()).apply {
            text = month
            setTextColor(resources.getColor(R.color.home_secondary, requireContext().theme))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { if (spaced) it.topMargin = dp(8) }
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

    private fun recordCard(record: AiHealthRecord): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(12) }
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(resources.getColor(R.color.home_card, requireContext().theme))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                viewModel.show(record.id)
                parentFragmentManager.popBackStack()
            }
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(android.R.color.transparent)
        }
        content.addView(
            TextView(requireContext()).apply {
                text = formatTime(record.timeMillis)
                setTextColor(resources.getColor(R.color.home_text, requireContext().theme))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        val preview = record.preview()
        if (preview.isNotBlank()) {
            content.addView(
                TextView(requireContext()).apply {
                    text = preview
                    setTextColor(resources.getColor(R.color.home_secondary, requireContext().theme))
                    textSize = 14f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(6), 0, 0)
                },
            )
        }
        card.addView(content)
        return card
    }

    private fun formatTime(timeMillis: Long): String {
        val locale = resources.configuration.locales[0]
        val timeText = SimpleDateFormat(if (locale.language.startsWith("zh")) "HH:mm" else "h:mm a", locale)
            .format(Date(timeMillis))
        return "${SimpleDateFormat("MM-dd", Locale.ENGLISH).format(Date(timeMillis))} $timeText"
    }

    private fun AiHealthRecord.preview(): String =
        listOf(summary, step, heartRate, sleep, spo2, stress, pai, weight, bp)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .replace('\n', ' ')

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
