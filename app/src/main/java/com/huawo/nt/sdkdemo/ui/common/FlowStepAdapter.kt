package com.huawo.nt.sdkdemo.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.FlowStep
import com.huawo.nt.sdkdemo.data.model.FlowStepStatus
import com.huawo.nt.sdkdemo.databinding.ItemFlowStepBinding

class FlowStepAdapter : RecyclerView.Adapter<FlowStepAdapter.VH>() {
    private val items = mutableListOf<FlowStep>()

    fun submit(steps: List<FlowStep>) {
        items.clear()
        items.addAll(steps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFlowStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position + 1, items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemFlowStepBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(index: Int, step: FlowStep) {
            binding.stepTitle.text = "$index. ${step.description}"
            binding.stepApi.text = step.api
            binding.stepNote.visibility = if (step.platformNote.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.stepNote.text = step.platformNote
            binding.stepDetail.visibility = if (step.detail.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.stepDetail.text = step.detail

            val ctx = binding.root.context
            val running = step.status == FlowStepStatus.RUNNING
            binding.stepProgress.visibility = if (running) View.VISIBLE else View.GONE
            binding.stepIcon.visibility = if (running) View.GONE else View.VISIBLE

            val (icon, color) =
                when (step.status) {
                    FlowStepStatus.PENDING ->
                        android.R.drawable.radiobutton_off_background to
                            ContextCompat.getColor(ctx, android.R.color.darker_gray)
                    FlowStepStatus.RUNNING ->
                        android.R.drawable.ic_popup_sync to
                            ContextCompat.getColor(ctx, R.color.brand_blue)
                    FlowStepStatus.DONE ->
                        android.R.drawable.checkbox_on_background to
                            ContextCompat.getColor(ctx, R.color.success_green)
                    FlowStepStatus.SKIPPED ->
                        android.R.drawable.checkbox_on_background to
                            ContextCompat.getColor(ctx, android.R.color.holo_orange_dark)
                    FlowStepStatus.FAILED ->
                        android.R.drawable.ic_delete to
                            ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
                }
            binding.stepIcon.setImageResource(icon)
            binding.stepIcon.setColorFilter(color)
            binding.stepDetail.setTextColor(
                if (step.status == FlowStepStatus.FAILED) {
                    ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
                } else {
                    ContextCompat.getColor(ctx, android.R.color.darker_gray)
                },
            )
        }
    }
}
