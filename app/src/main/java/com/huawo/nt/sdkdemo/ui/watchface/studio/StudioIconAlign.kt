package com.huawo.nt.sdkdemo.ui.watchface.studio

/**
 * Icon position relative to digits in icon+data widgets (steps, distance, battery, …).
 *
 * Used by [QjsComponentBuilders.iconDataSize] / `iconData` for bounding box and child layout:
 * - [LEFT]: icon left, digits right (Studio default)
 * - [RIGHT]: digits left, icon right
 * - [TOP]: icon above, digits below
 * - [BOTTOM]: digits above, icon below
 *
 * Weather (weather OptionWidget + temperature GroupWidget) uses the same alignment rules.
 */
enum class StudioIconAlign {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}
