package com.huawo.nt.sdkdemo.ui.watchface.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.huawo.watchface.qjs.utils.FileUtils

/**
 * Loads glyph PNGs from APK `assets/` (merged from `qjs-watchface-*.aar`) with optional RGB tint.
 *
 * ## Asset naming (hex code points)
 * - Large time: `time_b_30`…`39` (0–9), `time_b_25` (colon `:`)
 * - Data digits: `data_num_30`…`39`, `2d` (`-`), `2e` (`.`), `25` (`%`)
 * - Date: `date_num_30`…`39`
 * - Week: `week_00`…`06` (MON…SUN, Monday = 00)
 * - Icons: `icon_steps` / `icon_distance` / `icon_kcal` / `icon_battery` / `icon_heart`
 * - Hands: `pointer_hour` / `pointer_min` / `pointer_second` / `pointer_center`
 * - Weather icons: `icon_wer_01`…`15` (usually untinted)
 *
 * ## Tint rules
 * @param tintRgb **RGB only** (`0x00RRGGBB`). Negative (e.g. `-1`) skips tint and keeps the original.
 * [FileUtils.changeBitmapColor] maps near-white/gray foreground to the target color via ColorMatrix
 * while preserving alpha — suited to stock white-on-transparent PNGs.
 *
 * ## Split with font rendering
 * - Time hands, icons, weather icons: stay on this class (stock PNG + tint)
 * - Resizable date/week/steps digits: prefer [QjsFontDigitBitmaps] live render;
 *   do **not** run `changeBitmapColor` again on those bitmaps
 */
object QjsAssetBitmaps {

    /**
     * Load a single asset PNG.
     * @return null if decode fails or the file is missing (callers should tolerate gaps)
     */
    fun load(context: Context, assetName: String, tintRgb: Int = -1): Bitmap? {
        return try {
            context.assets.open(assetName).use { input ->
                var bitmap = BitmapFactory.decodeStream(input) ?: return null
                if (tintRgb >= 0) {
                    bitmap = FileUtils.changeBitmapColor(bitmap, tintRgb)
                }
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Load a digit glyph map keyed by Unicode/ASCII code point for [GroupWidget.setImages].
     *
     * @param nameForCode code point → file name, e.g. `{ "time_b_%02x.png".format(it) }`
     * @param codes default `0x30`…`0x39` (`'0'`…`'9'`)
     * @param extraCodes extra symbols, e.g. `0x2e → "data_num_2e.png"` (dot may be narrower)
     */
    fun loadDigitMap(
        context: Context,
        nameForCode: (Int) -> String,
        codes: IntArray = intArrayOf(
            0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
        ),
        tintRgb: Int = -1,
        extraCodes: Map<Int, String> = emptyMap(),
    ): Map<Int, Bitmap> {
        val map = LinkedHashMap<Int, Bitmap>()
        for (code in codes) {
            val bmp = load(context, nameForCode(code), tintRgb) ?: continue
            map[code] = bmp
        }
        for ((code, name) in extraCodes) {
            val bmp = load(context, name, tintRgb) ?: continue
            map[code] = bmp
        }
        return map
    }

    /**
     * Full symbol set for data digits (steps / distance / calorie / battery / HR / temperature):
     * `0–9`, `-`, `.`, `%`. Baseline cell ~20×26 (`.` is 8×26).
     *
     * Note: Studio's editable font-size path uses [QjsFontDigitBitmaps.renderDataDigits].
     * This helper remains for stock-PNG time paths or debugging.
     */
    fun loadDataDigits(context: Context, tintRgb: Int = -1): Map<Int, Bitmap> {
        return loadDigitMap(
            context = context,
            nameForCode = { code -> "data_num_%02x.png".format(code) },
            codes = intArrayOf(
                0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
            ),
            tintRgb = tintRgb,
            extraCodes = mapOf(
                0x2d to "data_num_2d.png",
                0x2e to "data_num_2e.png",
                0x25 to "data_num_25.png",
            ),
        )
    }
}
