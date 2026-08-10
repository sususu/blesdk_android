package com.huawo.nt.sdkdemo.ui.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.util.Size
import com.huawo.watchface.custom.widget.Dot
import com.huawo.watchface.custom.widget.HourPointer
import com.huawo.watchface.custom.widget.MinutePointer
import com.huawo.watchface.custom.widget.SecondPointer
import com.huawo.watchface.qjs.QjsValueType
import com.huawo.watchface.qjs.widgets.SingleImageWidget
import com.huawo.watchface.qjs.widgets.Widget

/**
 * Demo wrappers around stock pointer / Dot widgets so a **custom [Bitmap]** actually reaches
 * the watchface zip.
 *
 * ## Why these classes exist
 * In `qjs-watchface-*.aar` (currently **15.0.16**), each stock
 * [HourPointer] / [MinutePointer] / [SecondPointer] / [Dot] implements `qjsWidgets()` by
 * **always** opening assets (`pointer_hour.png`, `pointer_min.png`, `pointer_second.png`,
 * `pointer_center.png`), optionally applying `tintColor`, then assigning `this.image`.
 *
 * Parent [com.huawo.watchface.custom.widget.PointerWidget.setImage] therefore has **no effect**
 * at pack time: any bitmap you set is overwritten before the QJS [Widget] list is built.
 *
 * [SifliCustomWatchface.makeZip] walks `AWidget.qjsWidgets(context)` polymorphically, so
 * subclassing and overriding `qjsWidgets` is the reliable Demo-side fix without rebuilding
 * the AAR.
 *
 * ## When to use which path
 * | Call | Result |
 * |------|--------|
 * | [setCustomImage] with non-null bitmap | Custom art is packed; **no** `tintColor` matrix |
 * | [setCustomImage]`(null)` or never called | Delegates to `super.qjsWidgets` → stock asset + tint |
 *
 * Always set [center] (dial center in **watch pixels**) before sync, same as the reference
 * Demo: `Point(width / 2, height / 2)`.
 *
 * ## rotationCenter (pivot) — critical for hands
 * QJS [com.huawo.watchface.qjs.widgets.PointerWidget] places the hand with:
 * ```
 * pos   = center - rotationCenter
 * pivot = rotationCenter   // bitmap-local coordinates
 * ```
 * So [rotationCenter] must be the pixel inside the **hand PNG** that sits on the dial center
 * (usually bottom-center of a vertical hand pointing “12 o’clock”).
 *
 * Stock AAR defaults (for reference when clearing custom art):
 * - Hour:   size 14×114, pivot `Point(7, 114)`
 * - Minute: size 14×194, pivot `Point(7, 194)`
 * - Second: size 6×247,  pivot `Point(3, 222)`  ← not the tip; y = h × 222/247
 *
 * ## Artwork expectations
 * Prefer a vertical pointer PNG with transparent background and pivot near the bottom center.
 * Landscape photos / wrong EXIF will still sync but look wrong on-wrist (Fragment applies
 * EXIF when picking from gallery).
 *
 * ## tintColor
 * Constructor `(tintColor: Int)` must be **RGB only** (`0x00RRGGBB`). See
 * [CustomWatchfaceFragment.parseWidgetColor]. Tint applies **only** on the stock-asset path.
 * Custom bitmaps keep their own colors.
 *
 * ## Upstream note
 * sdk-demo `watchface` sources were patched to skip asset reload when `image != null`, but
 * SDKDemo still depends on the old AAR — keep these wrappers until that AAR is rebuilt and
 * replaced under `app/libs/`.
 */

/**
 * Hour hand that can pack a custom bitmap.
 *
 * Stock asset: `pointer_hour.png` (14×114, pivot bottom-center).
 * QJS value type: [QjsValueType.HOUR_POINTER].
 *
 * Usage from [CustomWatchfaceFragment]:
 * ```
 * CustomHourPointer(parseWidgetColor(..., 0x00FF00)).also {
 *     it.center = Point(width / 2, height / 2)
 *     it.setCustomImage(hourPointerBitmap) // null → stock + tint
 *     watchface.addWidget(it)
 * }
 * ```
 */
class CustomHourPointer : HourPointer {
    constructor() : super()

    /**
     * @param tintColor RGB only (`0x00RRGGBB`). Used only when [customImage] is null
     *   (stock asset path). Ignored for custom bitmaps.
     */
    constructor(tintColor: Int) : super(tintColor)

    /** Non-null → custom path in [qjsWidgets]; null → [HourPointer.qjsWidgets] (assets). */
    private var customImage: Bitmap? = null

    /**
     * Sets or clears the custom hour-hand image.
     *
     * - Non-null: stores bitmap, mirrors into parent `image`, updates [width]/[height]/
     *   [rotationCenter] to match the bitmap (pivot = bottom-center, like stock 14×114).
     * - Null: clears parent `image` and restores stock width/height/pivot so a reused
     *   instance does not keep a stale pivot from a previous custom size.
     *   (Fragment currently constructs a new widget per sync; restore is defensive.)
     */
    fun setCustomImage(bitmap: Bitmap?) {
        customImage = bitmap
        setImage(bitmap)
        if (bitmap != null) {
            width = bitmap.width
            height = bitmap.height
            // Stock hour pivot is bottom-center of the PNG (Point(7, 114) on 14×114).
            rotationCenter = Point(bitmap.width / 2, bitmap.height)
        } else {
            // Restore stock metrics if this instance is reused after clear.
            width = 14
            height = 114
            rotationCenter = Point(7, 114)
        }
    }

    /**
     * Called by [com.huawo.watchface.custom.SifliCustomWatchface] while building the zip.
     *
     * @return one QJS [com.huawo.watchface.qjs.widgets.PointerWidget] bound to hour data,
     *   either from [customImage] or from the stock AAR asset path via `super`.
     */
    override fun qjsWidgets(context: Context): List<Widget> {
        val custom = customImage ?: return super.qjsWidgets(context)
        image = custom
        return listOf(buildPointer(QjsValueType.HOUR_POINTER.value, custom))
    }

    /** Builds the low-level QJS pointer widget (same fields stock [HourPointer] sets). */
    private fun buildPointer(valueType: Int, bitmap: Bitmap): Widget {
        val pointerWidget = com.huawo.watchface.qjs.widgets.PointerWidget()
        pointerWidget.setValueType(valueType)
        pointerWidget.setCenterPoint(center)
        pointerWidget.setRotationCenterPoint(rotationCenter)
        pointerWidget.setImage(bitmap)
        return pointerWidget
    }
}

/**
 * Minute hand that can pack a custom bitmap.
 *
 * Stock asset: `pointer_min.png` (14×194, pivot bottom-center).
 * QJS value type: [QjsValueType.MINUTE_POINTER].
 *
 * Behavior mirrors [CustomHourPointer]: custom art skips tint; null uses stock + tint.
 */
class CustomMinutePointer : MinutePointer {
    constructor() : super()

    /**
     * @param tintColor RGB only (`0x00RRGGBB`). Applied only on the stock-asset path.
     */
    constructor(tintColor: Int) : super(tintColor)

    /** Non-null → custom path in [qjsWidgets]; null → [MinutePointer.qjsWidgets] (assets). */
    private var customImage: Bitmap? = null

    /**
     * Sets or clears the custom minute-hand image.
     *
     * Pivot policy matches stock minute hand: bottom-center
     * (`Point(7, 194)` on the default 14×194 PNG).
     */
    fun setCustomImage(bitmap: Bitmap?) {
        customImage = bitmap
        setImage(bitmap)
        if (bitmap != null) {
            width = bitmap.width
            height = bitmap.height
            // Stock minute pivot is bottom-center (Point(7, 194) on 14×194).
            rotationCenter = Point(bitmap.width / 2, bitmap.height)
        } else {
            width = 14
            height = 194
            rotationCenter = Point(7, 194)
        }
    }

    /**
     * Packs either [customImage] or the stock minute asset (via `super`).
     *
     * @see CustomHourPointer.qjsWidgets
     */
    override fun qjsWidgets(context: Context): List<Widget> {
        val custom = customImage ?: return super.qjsWidgets(context)
        image = custom
        val pointerWidget = com.huawo.watchface.qjs.widgets.PointerWidget()
        pointerWidget.setValueType(QjsValueType.MINUTE_POINTER.value)
        pointerWidget.setCenterPoint(center)
        pointerWidget.setRotationCenterPoint(rotationCenter)
        pointerWidget.setImage(custom)
        return listOf(pointerWidget)
    }
}

/**
 * Second hand that can pack a custom bitmap.
 *
 * Stock asset: `pointer_second.png` (6×247).
 * Stock pivot is **not** the bitmap tip: AAR uses `Point(3, 222)` on 6×247, i.e.
 * `y = height * 222 / 247` (~90% down). Tip-as-pivot would make the second hand look
 * offset vs the default asset.
 *
 * QJS value type: [QjsValueType.SECOND_POINTER].
 */
class CustomSecondPointer : SecondPointer {
    constructor() : super()

    /**
     * @param tintColor RGB only (`0x00RRGGBB`). Applied only on the stock-asset path.
     */
    constructor(tintColor: Int) : super(tintColor)

    /** Non-null → custom path in [qjsWidgets]; null → [SecondPointer.qjsWidgets] (assets). */
    private var customImage: Bitmap? = null

    /**
     * Sets or clears the custom second-hand image.
     *
     * When non-null, pivot Y uses the stock ratio `222/247` so custom art behaves like
     * `pointer_second.png`. When null, restores size 6×247 and pivot `Point(3, 222)`.
     */
    fun setCustomImage(bitmap: Bitmap?) {
        customImage = bitmap
        setImage(bitmap)
        if (bitmap != null) {
            width = bitmap.width
            height = bitmap.height
            // Stock second pivot is Point(3, 222) on 6×247 → y = h * 222 / 247 (not tip).
            rotationCenter =
                Point(
                    bitmap.width / 2,
                    (bitmap.height * 222 / 247).coerceAtLeast(1),
                )
        } else {
            width = 6
            height = 247
            rotationCenter = Point(3, 222)
        }
    }

    /**
     * Packs either [customImage] or the stock second asset (via `super`).
     *
     * @see CustomHourPointer.qjsWidgets
     */
    override fun qjsWidgets(context: Context): List<Widget> {
        val custom = customImage ?: return super.qjsWidgets(context)
        image = custom
        val pointerWidget = com.huawo.watchface.qjs.widgets.PointerWidget()
        pointerWidget.setValueType(QjsValueType.SECOND_POINTER.value)
        pointerWidget.setCenterPoint(center)
        pointerWidget.setRotationCenterPoint(rotationCenter)
        pointerWidget.setImage(custom)
        return listOf(pointerWidget)
    }
}

/**
 * Center cap / hub that can pack a custom bitmap.
 *
 * Stock asset: `pointer_center.png`. Unlike the hands, [Dot] packs as a
 * [SingleImageWidget] (static image centered on [center]), not a rotating
 * [com.huawo.watchface.qjs.widgets.PointerWidget].
 *
 * AAR [Dot.qjsWidgets] also always reloads assets, so this override is required for
 * custom hub art — same reason as the hand wrappers.
 *
 * No [rotationCenter]: the image is placed with top-left
 * `(center.x - w/2, center.y - h/2)`.
 */
class CustomDot : Dot {
    constructor() : super()

    /**
     * @param tintColor RGB only (`0x00RRGGBB`). Applied only on the stock-asset path.
     *   Stock [Dot] default tint is `0xFFFFFF` when using the no-arg constructor.
     */
    constructor(tintColor: Int) : super(tintColor)

    /** Non-null → custom path in [qjsWidgets]; null → [Dot.qjsWidgets] (assets). */
    private var customImage: Bitmap? = null

    /**
     * Sets or clears the custom center-dot image.
     *
     * Updates [width]/[height] to the bitmap size when non-null. Placement uses the
     * bitmap’s intrinsic size in [qjsWidgets], not these fields, but keeping them in sync
     * matches how other widgets expose size for layout helpers.
     */
    fun setCustomImage(bitmap: Bitmap?) {
        customImage = bitmap
        setImage(bitmap)
        if (bitmap != null) {
            width = bitmap.width
            height = bitmap.height
        }
    }

    /**
     * Packs a centered [SingleImageWidget] from [customImage], or stock hub via `super`.
     *
     * @return empty list if [center] was never set (cannot place the image); callers must
     *   assign [center] before sync (Fragment always does).
     */
    override fun qjsWidgets(context: Context): List<Widget> {
        val custom = customImage ?: return super.qjsWidgets(context)
        val c = center ?: return emptyList()
        val imageWidget = SingleImageWidget()
        val x = c.x - custom.width / 2
        val y = c.y - custom.height / 2
        imageWidget.setLocation(Point(x, y))
        imageWidget.setSize(Size(custom.width, custom.height))
        imageWidget.setImage(custom)
        return listOf(imageWidget)
    }
}
