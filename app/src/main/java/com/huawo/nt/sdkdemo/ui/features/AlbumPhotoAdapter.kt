package com.huawo.nt.sdkdemo.ui.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huawo.nt.sdkdemo.data.model.AlbumPhotoItem
import com.huawo.nt.sdkdemo.databinding.ItemAlbumPhotoBinding
import kotlin.math.max

/**
 * Album preview grid adapter.
 *
 * - Shows a local-cache thumbnail plus watch slot index / file name
 * - Corner remove button invokes [onRemove] (Fragment forwards to ViewModel)
 * - Uses [ListAdapter] + DiffUtil to avoid full-list flicker
 *
 * No image library: decode with inSampleSize based on ImageView size, and verify
 * the bound path after async decode to avoid wrong-image / recycled-holder bugs.
 */
class AlbumPhotoAdapter(
    private val onRemove: (AlbumPhotoItem) -> Unit,
) : ListAdapter<AlbumPhotoItem, AlbumPhotoAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding =
            ItemAlbumPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    inner class VH(
        private val binding: ItemAlbumPhotoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        /** Path currently bound; used after decode to confirm the holder still matches. */
        private var boundPath: String? = null

        fun bind(item: AlbumPhotoItem) {
            boundPath = item.file.absolutePath
            binding.tvIndex.text = "#${item.index} ${item.file.name}"
            binding.btnRemove.setOnClickListener { onRemove(item) }
            binding.ivPhoto.setImageBitmap(null)
            // post: wait for layout so width/height are non-zero for inSampleSize
            binding.ivPhoto.post {
                if (boundPath != item.file.absolutePath) return@post
                val w = max(1, binding.ivPhoto.width)
                val h = max(1, binding.ivPhoto.height)
                val bitmap = decodeSampled(item.file.absolutePath, w, h)
                if (boundPath == item.file.absolutePath) {
                    binding.ivPhoto.setImageBitmap(bitmap)
                } else {
                    // Holder was rebound to another item; drop this decode result
                    bitmap?.recycle()
                }
            }
        }

        fun clear() {
            boundPath = null
            binding.ivPhoto.setImageBitmap(null)
            binding.btnRemove.setOnClickListener(null)
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<AlbumPhotoItem>() {
        override fun areItemsTheSame(old: AlbumPhotoItem, new: AlbumPhotoItem) =
            old.index == new.index && old.file.absolutePath == new.file.absolutePath

        override fun areContentsTheSame(old: AlbumPhotoItem, new: AlbumPhotoItem) = old == new
    }
}

/**
 * Decode a downsampled bitmap for the target view size to keep preview memory low.
 * Preview-only; push conversion uses [com.huawo.nt.sdkdemo.util.AlbumBinConverter].
 */
private fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > reqW * 2 && bounds.outHeight / sample > reqH * 2) {
            sample *= 2
        }
        val opts =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = max(1, sample)
            }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull()
