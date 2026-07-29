package com.huawo.nt.sdkdemo.ui.watchface

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huawo.nt.sdkdemo.data.model.OnlineWatchface
import com.huawo.nt.sdkdemo.data.remote.WatchfaceApi
import com.huawo.nt.sdkdemo.databinding.ItemOnlineWatchfaceBinding
import com.huawo.nt.sdkdemo.util.RemoteImageLoader

/**
 * Grid adapter for the online watchface catalog.
 *
 * - Thumbnail URLs are resolved via [WatchfaceApi.resolveFileUrl] (relative → absolute).
 * - [OnlineWatchface.byteSizeKb] is already in KB; format with `owf_size_kb`.
 * - Diff uses server [OnlineWatchface.id] as stable identity.
 *
 * Click handling is delegated to the fragment (select + open detail sheet).
 */
class OnlineWatchfaceAdapter(
    private val onClick: (OnlineWatchface) -> Unit,
) : ListAdapter<OnlineWatchface, OnlineWatchfaceAdapter.VH>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding =
            ItemOnlineWatchfaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemOnlineWatchfaceBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OnlineWatchface) {
            binding.tvName.text = item.name
            binding.tvSize.text =
                if (item.byteSizeKb > 0) {
                    binding.root.context.getString(
                        com.huawo.nt.sdkdemo.R.string.owf_size_kb,
                        item.byteSizeKb,
                    )
                } else {
                    ""
                }
            // Thumbnails are often animated GIF; RemoteImageLoader uses ImageDecoder.
            RemoteImageLoader.load(
                binding.ivThumb,
                WatchfaceApi.resolveFileUrl(item.thumbnail),
            )
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<OnlineWatchface>() {
                override fun areItemsTheSame(
                    oldItem: OnlineWatchface,
                    newItem: OnlineWatchface,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: OnlineWatchface,
                    newItem: OnlineWatchface,
                ): Boolean = oldItem == newItem
            }
    }
}
