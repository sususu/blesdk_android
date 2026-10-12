package com.huawo.nt.sdkdemo.ui.watchface.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import kotlin.math.roundToInt

/**
 * Renders characters with a TTF / system [Typeface] into QJS-ready glyph [Bitmap]s.
 *
 * ## Why this exists
 * Sifli QJS digit widgets ([GroupWidget] / [OptionWidget]) do not take a font or size —
 * only `Map<code, Bitmap>`. Stock AAR PNGs are fixed-pixel white glyphs.
 * To let Studio change size/color, we paint a matching cell set and call `setImages`.
 *
 * ## Cell sizes (scale = 1, matches AAR)
 * | Use  | Cell WxH | Notes |
 * |------|----------|-------|
 * | data | 20×26    | `.` is 8×26 |
 * | date | 28×40    | widget width ≈ MM`/`DD |
 * | week | 114×40   | whole word MON…SUN, not per-letter |
 *
 * ## Output
 * - ARGB_8888, transparent bg + antialiased tinted glyphs (same RGBA style as stock)
 * - Color is final — **do not** call [FileUtils.changeBitmapColor] again
 *
 * ## Typeface resolution ([typeface])
 * 1. Caller-supplied [Typeface] (built-in font Spinner selection)
 * 2. Explicit asset path under `assets/fonts/`
 * 3. System `sans-serif` Bold / [Typeface.DEFAULT_BOLD]
 */
object QjsFontDigitBitmaps {

    /** Bundled Roboto Bold */
    const val ASSET_ROBOTO_BOLD = "fonts/roboto_bold.ttf"

    /** Bundled Montserrat Bold */
    const val ASSET_MONTSERRAT_BOLD = "fonts/montserrat_bold.ttf"

    /** Data digit baseline width (stock `data_num_3x.png`) */
    const val DATA_DIGIT_W = 20

    /** Data digit baseline height */
    const val DATA_DIGIT_H = 26

    /** Data decimal-point baseline width (stock `data_num_2e.png` is narrower) */
    const val DATA_DOT_W = 8

    /** Date digit baseline width */
    const val DATE_DIGIT_W = 28

    /** Date digit baseline height */
    const val DATE_DIGIT_H = 40

    /** Week label full-image baseline width */
    const val WEEK_W = 114

    /** Week label full-image baseline height */
    const val WEEK_H = 40

    /**
     * Same as stock `week_00`…`week_06`: Monday = 00.
     * OptionWidget keys are `"00"`…`"06"` strings.
     */
    private val WEEK_LABELS =
        arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    /**
     * Resolve the [Typeface] used for rendering.
     * @param custom non-null wins (from the built-in font Spinner)
     */
    fun typeface(context: Context, custom: Typeface? = null): Typeface {
        if (custom != null) return custom
        return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            ?: Typeface.DEFAULT_BOLD
    }

    /** Load a TTF from assets; returns null on failure */
    fun typefaceFromAsset(context: Context, assetPath: String): Typeface? {
        return try {
            Typeface.createFromAsset(context.assets, assetPath)
        } catch (_: Exception) {
            null
        }
    }

    /** Create a [Typeface] from a local file */
    fun typefaceFromFile(file: File): Typeface? {
        return try {
            Typeface.createFromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Data cell `(width, height)`.
     * @param fontSizePx cell height in watchface px; width from 20:26
     */
    fun dataCell(fontSizePx: Int): Pair<Int, Int> {
        val h = fontSizePx.coerceIn(8, 120)
        val w = (h * DATA_DIGIT_W / DATA_DIGIT_H.toFloat()).roundToInt().coerceAtLeast(4)
        return w to h
    }

    /** Date cell: height = [fontSizePx], width from 28:40 */
    fun dateCell(fontSizePx: Int): Pair<Int, Int> {
        val h = fontSizePx.coerceIn(8, 120)
        val w = (h * DATE_DIGIT_W / DATE_DIGIT_H.toFloat()).roundToInt().coerceAtLeast(6)
        return w to h
    }

    /** Week full image: height = [fontSizePx], width from 114:40 */
    fun weekCell(fontSizePx: Int): Pair<Int, Int> {
        val h = fontSizePx.coerceIn(10, 120)
        val w = (h * WEEK_W / WEEK_H.toFloat()).roundToInt().coerceAtLeast(24)
        return w to h
    }

    /**
     * Render data digit atlas: `0–9`, `-`(0x2d), `.`(0x2e), `%`(0x25).
     * Map keys must be code-point [Int]s, matching QJS GroupWidget.
     */
    fun renderDataDigits(
        typeface: Typeface,
        tintRgb: Int,
        fontSizePx: Int = DATA_DIGIT_H,
    ): Map<Int, Bitmap> {
        val (digitW, digitH) = dataCell(fontSizePx)
        val dotW = (digitH * DATA_DOT_W / DATA_DIGIT_H.toFloat()).roundToInt().coerceAtLeast(3)
        val rgb = tintRgb and 0xFFFFFF
        val map = LinkedHashMap<Int, Bitmap>()
        for (code in 0x30..0x39) {
            map[code] = renderGlyph(typeface, code.toChar().toString(), rgb, digitW, digitH)
        }
        map[0x2d] = renderGlyph(typeface, "-", rgb, digitW, digitH)
        map[0x2e] = renderGlyph(typeface, ".", rgb, dotW, digitH)
        map[0x25] = renderGlyph(typeface, "%", rgb, digitW, digitH)
        return map
    }

    /**
     * Render date digit atlas: `0–9` + `/`(0x2f).
     * Firmware [QjsValueType.DATE_NUM] usually composes month/day with `/`;
     * the separator must share [fontSizePx] or month/slash look stale when day scales.
     */
    fun renderDateDigits(
        typeface: Typeface,
        tintRgb: Int,
        fontSizePx: Int = DATE_DIGIT_H,
    ): Map<Int, Bitmap> {
        val (digitW, digitH) = dateCell(fontSizePx)
        // stock mid_bar.png ~20×40; scale with font size
        val sepW = (digitH * 20 / DATE_DIGIT_H.toFloat()).roundToInt().coerceAtLeast(6)
        val rgb = tintRgb and 0xFFFFFF
        val map = LinkedHashMap<Int, Bitmap>()
        for (code in 0x30..0x39) {
            map[code] = renderGlyph(typeface, code.toChar().toString(), rgb, digitW, digitH)
        }
        map[0x2f] = renderGlyph(typeface, "/", rgb, sepW, digitH)
        return map
    }

    /** Date widget total width: MM + `/` + DD (5 slots) */
    fun dateWidgetWidth(fontSizePx: Int): Int {
        val (digitW, digitH) = dateCell(fontSizePx)
        val sepW = (digitH * 20 / DATE_DIGIT_H.toFloat()).roundToInt().coerceAtLeast(6)
        return digitW * 4 + sepW
    }

    /**
     * Render week Option atlas.
     * @return keys `"00"`…`"06"`, matching stock `week_XX.png` / OptionWidget
     */
    fun renderWeekLabels(
        typeface: Typeface,
        tintRgb: Int,
        fontSizePx: Int = WEEK_H,
    ): Map<String, Bitmap> {
        val (w, h) = weekCell(fontSizePx)
        val rgb = tintRgb and 0xFFFFFF
        val map = LinkedHashMap<String, Bitmap>()
        for (i in WEEK_LABELS.indices) {
            val key = String.format(java.util.Locale.ENGLISH, "%02d", i)
            // fitWidth: squeeze 3-letter labels into the fixed cell
            map[key] = renderGlyph(typeface, WEEK_LABELS[i], rgb, w, h, fitWidth = true)
        }
        return map
    }

    /**
     * Draw a single glyph / short word centered on a transparent canvas.
     *
     * Size policy: start at ~`cellH * 0.82`, then shrink by measured width
     * so strokes stay inside the cell (esp. `W` / `%` / week letters).
     */
    private fun renderGlyph(
        typeface: Typeface,
        text: String,
        tintRgb: Int,
        cellW: Int,
        cellH: Int,
        fitWidth: Boolean = false,
    ): Bitmap {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.typeface = typeface
                color = Color.argb(
                    255,
                    (tintRgb shr 16) and 0xFF,
                    (tintRgb shr 8) and 0xFF,
                    tintRgb and 0xFF,
                )
                textAlign = Paint.Align.CENTER
                isFakeBoldText = false
            }
        var textSize = cellH * 0.82f
        paint.textSize = textSize
        if (fitWidth) {
            val maxW = cellW * 0.92f
            var measured = paint.measureText(text)
            var guard = 0
            while (measured > maxW && textSize > 6f && guard < 24) {
                textSize *= 0.92f
                paint.textSize = textSize
                measured = paint.measureText(text)
                guard++
            }
        } else {
            val maxW = cellW * 0.95f
            var measured = paint.measureText(text)
            var guard = 0
            while (measured > maxW && textSize > 6f && guard < 20) {
                textSize *= 0.9f
                paint.textSize = textSize
                measured = paint.measureText(text)
                guard++
            }
        }

        val bmp = Bitmap.createBitmap(cellW, cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)
        val fm = paint.fontMetrics
        // Vertical center: baseline puts ascent/descent midpoint on the cell mid-line
        val baseline = cellH / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cellW / 2f, baseline, paint)
        return bmp
    }
}
