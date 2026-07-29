package com.huawo.nt.sdkdemo.util

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout

/**
 * Bitmap helpers for the **custom watchface** flow (preview + package assets).
 *
 * Used by [com.huawo.nt.sdkdemo.ui.watchface.CustomWatchfaceFragment]:
 * - Background: scale phone photo → dial Width×Height, then round-rect clip.
 * - Thumbnail: screenshot of the preview FrameLayout → Thumb W×H with its own corner.
 */
object WatchfaceImageUtils {
    /**
     * Scale [source] to exactly [width]×[height], then draw into a round-rect mask.
     *
     * @param cornerRadius Corner radius in the **same pixel space** as width/height
     *   (watch pixels for the dial background; thumb pixels for the thumbnail).
     *   Use `min(width, height) / 2f` for a circular dial.
     * @param backgroundColor Fills behind transparent corners (typically black).
     *
     * Caveat: [Bitmap.createScaledBitmap] may return the same instance if size already matches;
     * that is fine for drawing but callers must not recycle [source] while Sync still needs it.
     */
    fun scaledRoundedBitmap(
        source: Bitmap,
        width: Int,
        height: Int,
        cornerRadius: Float,
        backgroundColor: Int = Color.BLACK,
    ): Bitmap {
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            cornerRadius,
            cornerRadius,
            paint,
        )
        return output
    }

    /**
     * Rasterize a laid-out [view] into a bitmap (used for watchface thumbnail).
     * View must have non-zero measured size; callers usually `post {}` after layout.
     */
    fun viewToBitmap(view: View, fallbackColor: Int = Color.BLACK): Bitmap {
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fallbackColor)
        view.draw(canvas)
        return bitmap
    }

    /** Convenience for the custom-watchface preview container. */
    fun frameLayoutToBitmap(layout: FrameLayout): Bitmap = viewToBitmap(layout)
}
