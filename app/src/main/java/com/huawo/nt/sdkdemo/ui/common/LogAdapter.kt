package com.huawo.nt.sdkdemo.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.huawo.nt.sdkdemo.databinding.ItemLogBinding

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {
    private val items = mutableListOf<String>()

    fun submit(logs: List<String>) {
        items.clear()
        items.addAll(logs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.binding.logText.text = items[position]
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)
}
