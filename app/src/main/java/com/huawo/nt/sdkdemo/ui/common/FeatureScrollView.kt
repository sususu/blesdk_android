package com.huawo.nt.sdkdemo.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView

/** Keep short action panels compact while allowing long panels to use the available screen space. */
class FeatureScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : NestedScrollView(context, attrs) {
    private val contentMaxHeight = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxHeight)).let {
        try { it.getDimensionPixelSize(0, 0) } finally { it.recycle() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)
        // Treat the weighted action panel's allocation as a ceiling, not a minimum.
        // Short content keeps the result card directly below it; long content still fills and scrolls.
        if (mode == MeasureSpec.EXACTLY && ((layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f) > 0f) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST))
            return
        }
        // Preserve the natural-size fallback and the independent result area's height cap.
        val bounded = contentMaxHeight > 0 && mode != MeasureSpec.EXACTLY &&
            (mode == MeasureSpec.UNSPECIFIED || size > contentMaxHeight)
        val heightSpec = if (bounded) MeasureSpec.makeMeasureSpec(contentMaxHeight, MeasureSpec.AT_MOST) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
