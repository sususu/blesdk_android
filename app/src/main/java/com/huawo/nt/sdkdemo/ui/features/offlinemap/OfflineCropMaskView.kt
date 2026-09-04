package com.huawo.nt.sdkdemo.ui.features.offlinemap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.huawo.nt.sdkdemo.R

/**
 * Semi-transparent crop mask over the map (ported from HaWoFit OfflineCropMaskView).
 * Touches pass through to the underlying [org.osmdroid.views.MapView].
 */
class OfflineCropMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskColor = Color.parseColor("#60000000")
    private val frameColor = Color.WHITE

    private var scanFrameWidth: Int
    private var scanFrameHeight: Int
    private var frameTopOffset: Int

    private val frame = Rect()
    private var cornerLength: Int

    private var text1: String = "- -"
    private var text2: String = "- -"

    init {
        val density = resources.displayMetrics.density
        var w = (270 * density).toInt()
        var h = (270 * density).toInt()
        var topOff = (-100 * density).toInt()
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.OfflineCropMaskView)
            w = a.getDimensionPixelSize(R.styleable.OfflineCropMaskView_scanFrameWidth, w)
            h = a.getDimensionPixelSize(R.styleable.OfflineCropMaskView_scanFrameHeight, h)
            topOff = a.getDimensionPixelSize(R.styleable.OfflineCropMaskView_frameTopOffset, topOff)
            a.recycle()
        }
        scanFrameWidth = w
        scanFrameHeight = h
        frameTopOffset = topOff
        cornerLength = (30 * density).toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateFramePosition(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    fun getFrameRect(): Rect = Rect(frame)

    fun getFrameWidth(): Int = frame.width()

    fun getFrameHeight(): Int = frame.height()

    fun getFrameTop(): Int = frame.top

    fun setText1(text: String) {
        text1 = text
        invalidate()
    }

    fun setText2(text: String) {
        text2 = text
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        val cornerWidth = 5
        val width = canvas.width
        val height = canvas.height

        paint.color = maskColor
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top.toFloat(), paint)
        canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect(
            (frame.right + 1).toFloat(),
            frame.top.toFloat(),
            width.toFloat(),
            (frame.bottom + 1).toFloat(),
            paint,
        )
        canvas.drawRect(0f, (frame.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), paint)

        paint.color = frameColor
        // Top-left L
        canvas.drawRect(
            frame.left.toFloat(),
            frame.top.toFloat(),
            (frame.left + cornerLength).toFloat(),
            (frame.top + cornerWidth).toFloat(),
            paint,
        )
        canvas.drawRect(
            frame.left.toFloat(),
            frame.top.toFloat(),
            (frame.left + cornerWidth).toFloat(),
            (frame.top + cornerLength).toFloat(),
            paint,
        )
        // Top-right L
        canvas.drawRect(
            (frame.right - cornerLength).toFloat(),
            frame.top.toFloat(),
            frame.right.toFloat(),
            (frame.top + cornerWidth).toFloat(),
            paint,
        )
        canvas.drawRect(
            (frame.right - cornerWidth).toFloat(),
            frame.top.toFloat(),
            frame.right.toFloat(),
            (frame.top + cornerLength).toFloat(),
            paint,
        )
        // Bottom-left L
        canvas.drawRect(
            frame.left.toFloat(),
            (frame.bottom - cornerWidth).toFloat(),
            (frame.left + cornerLength).toFloat(),
            frame.bottom.toFloat(),
            paint,
        )
        canvas.drawRect(
            frame.left.toFloat(),
            (frame.bottom - cornerLength).toFloat(),
            (frame.left + cornerWidth).toFloat(),
            frame.bottom.toFloat(),
            paint,
        )
        // Bottom-right L
        canvas.drawRect(
            (frame.right - cornerLength).toFloat(),
            (frame.bottom - cornerWidth).toFloat(),
            frame.right.toFloat(),
            frame.bottom.toFloat(),
            paint,
        )
        canvas.drawRect(
            (frame.right - cornerWidth).toFloat(),
            (frame.bottom - cornerLength).toFloat(),
            frame.right.toFloat(),
            frame.bottom.toFloat(),
            paint,
        )

        // Decorative ticks (same spirit as HaWoFit)
        canvas.drawRect(
            frame.left.toFloat(),
            (frame.top - 60).toFloat(),
            (frame.left + cornerWidth).toFloat(),
            (frame.top - 12).toFloat(),
            paint,
        )
        canvas.drawRect(
            frame.left.toFloat(),
            (frame.top - 40).toFloat(),
            (frame.left + 200).toFloat(),
            (frame.top - 40 + cornerWidth).toFloat(),
            paint,
        )
        canvas.drawRect(
            frame.right.toFloat(),
            (frame.top - 60).toFloat(),
            (frame.right - cornerWidth).toFloat(),
            (frame.top - 12).toFloat(),
            paint,
        )
        canvas.drawRect(
            frame.right.toFloat(),
            (frame.top - 40).toFloat(),
            (frame.right - 200).toFloat(),
            (frame.top - 40 + cornerWidth).toFloat(),
            paint,
        )
        canvas.drawRect(
            (frame.left - 60).toFloat(),
            frame.top.toFloat(),
            (frame.left - 12).toFloat(),
            (frame.top + cornerWidth).toFloat(),
            paint,
        )
        canvas.drawRect(
            (frame.left - 40).toFloat(),
            frame.top.toFloat(),
            (frame.left - 40 + cornerWidth).toFloat(),
            (frame.top + 200).toFloat(),
            paint,
        )
        canvas.drawRect(
            (frame.left - 60).toFloat(),
            frame.bottom.toFloat(),
            (frame.left - 12).toFloat(),
            (frame.bottom - cornerWidth).toFloat(),
            paint,
        )
        canvas.drawRect(
            (frame.left - 40).toFloat(),
            frame.bottom.toFloat(),
            (frame.left - 40 + cornerWidth).toFloat(),
            (frame.bottom - 200).toFloat(),
            paint,
        )

        paint.color = Color.WHITE
        paint.textSize = 25f
        paint.textAlign = Paint.Align.CENTER

        val text1X = frame.left + frame.width() / 2f
        val text1Y = (frame.top - 30).toFloat()
        canvas.drawText(text1, text1X, text1Y, paint)

        val text2X = (frame.left - 30).toFloat()
        val text2Y = frame.top + frame.height() / 2f
        canvas.save()
        canvas.rotate(-90f, text2X, text2Y)
        canvas.drawText(text2, text2X, text2Y, paint)
        canvas.restore()
    }

    private fun updateFramePosition(w: Int = width, h: Int = height) {
        if (w <= 0 || h <= 0) return
        val centerX = w / 2
        val centerY = h / 2
        frame.left = centerX - scanFrameWidth / 2
        frame.right = centerX + scanFrameWidth / 2
        frame.top = centerY - scanFrameHeight / 2 + frameTopOffset
        frame.bottom = frame.top + scanFrameHeight
    }
}
