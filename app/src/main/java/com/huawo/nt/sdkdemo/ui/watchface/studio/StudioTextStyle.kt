package com.huawo.nt.sdkdemo.ui.watchface.studio

/**
 * Text appearance for Studio digit / weekday widgets.
 *
 * @property tintRgb RGB only (`0x00RRGGBB`)
 * @property fontSizePx Font size in watchface pixels; used as glyph cell height.
 *   Width is derived from stock aspect ratios (data 20:26 / date 28:40 / week 114:40).
 *   Typical defaults: 26 for data widgets, 40 for date/week.
 */
data class StudioTextStyle(
    val tintRgb: Int,
    val fontSizePx: Int,
)
