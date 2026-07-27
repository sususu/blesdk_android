package com.huawo.nt.sdkdemo.ui.features

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.huawo.nt.sdkdemo.data.model.LocalMusicFile
import com.huawo.nt.sdkdemo.databinding.ItemMusicFileBinding

/**
 * Local music list adapter (reference: tool page MusicFileAdapter).
 *
 * - CheckBox is not clickable; row click toggles selection to avoid double triggers
 * - Selection changes are reported via [onSelectionChanged] with count + total bytes for UI updates
 */
class MusicFileAdapter(
    private val files: MutableList<LocalMusicFile>,
    private val onSelectionChanged: (selectedCount: Int, totalSize: Long) -> Unit,
) : RecyclerView.Adapter<MusicFileAdapter.Holder>() {
    inner class Holder(
        private val binding: ItemMusicFileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LocalMusicFile) {
            binding.tvTitle.text = item.title
            val artist = item.artist.ifBlank { "-" }
            val album = item.album.ifBlank { "-" }
            binding.tvSubtitle.text = "$artist · $album"
            binding.tvSize.text = LocalMusicFile.formatSize(item.size)

            // Clear listener before setting isChecked to prevent spurious callbacks during recycle
            binding.cbMusicFile.setOnCheckedChangeListener(null)
            binding.cbMusicFile.isChecked = item.selected
            binding.cbMusicFile.setOnCheckedChangeListener { _, checked ->
                item.selected = checked
                notifySelection()
            }
            binding.root.setOnClickListener {
                item.selected = !item.selected
                binding.cbMusicFile.isChecked = item.selected
                notifySelection()
            }
        }

        private fun notifySelection() {
            onSelectionChanged(
                files.count { it.selected },
                files.filter { it.selected }.sumOf { it.size },
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding =
            ItemMusicFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size

    /** Replace the entire list after scan completes; clears selection. */
    fun submit(list: List<LocalMusicFile>) {
        files.clear()
        files.addAll(list)
        notifyDataSetChanged()
        onSelectionChanged(0, 0)
    }

    fun selectedFiles(): List<LocalMusicFile> = files.filter { it.selected }

    fun selectAll() {
        files.forEach { it.selected = true }
        notifyDataSetChanged()
        onSelectionChanged(files.size, files.sumOf { it.size })
    }

    fun deselectAll() {
        files.forEach { it.selected = false }
        notifyDataSetChanged()
        onSelectionChanged(0, 0)
    }

    fun invertSelection() {
        files.forEach { it.selected = !it.selected }
        notifyDataSetChanged()
        val selected = files.filter { it.selected }
        onSelectionChanged(selected.size, selected.sumOf { it.size })
    }
}
