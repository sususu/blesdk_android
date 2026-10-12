package com.huawo.nt.sdkdemo.ui.watchface.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.util.Size
import com.huawo.watchface.qjs.QjsValueType
import com.huawo.watchface.qjs.widgets.AppLinkWidget
import com.huawo.watchface.qjs.widgets.GifWidget
import com.huawo.watchface.qjs.widgets.GroupWidget
import com.huawo.watchface.qjs.widgets.OptionWidget
import com.huawo.watchface.qjs.widgets.PointerWidget
import com.huawo.watchface.qjs.widgets.SingleImageWidget
import com.huawo.watchface.qjs.widgets.Widget
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Factory that turns Studio settings into low-level QJS [Widget]s.
 *
 * ## Role
 * Assemble coordinate + color/size + assets into a watch-readable widget list:
 * - Background / time / hands / icons: stock AAR PNGs ([QjsAssetBitmaps])
 * - Styleable date / week / steps etc.: live TTF glyphs ([QjsFontDigitBitmaps])
 *
 * ## Coordinates
 * All values are **watchface pixels** (same as [StudioWatchfaceConfig.width]/[height]),
 * origin top-left. Preview FrameLayout remaps by scale
 * (see [WatchFaceStudioFragment.layoutPreviewWidgets]).
 *
 * ## Isolation
 * This package **intentionally** does not use `com.huawo.watchface.custom.widget.*`,
 * so it stays separate from [CustomWatchfaceFragment]'s high-level path.
 */
object QjsComponentBuilders {

    /** Full-dial static background; usually the first widget in the list. */
    fun background(image: Bitmap, width: Int, height: Int): SingleImageWidget {
        return SingleImageWidget().apply {
            setImage(image)
            setLocation(Point(0, 0))
            setSize(Size(width, height))
        }
    }

    /**
     * Full-dial GIF background ([GifWidget]), same role as HaWoFit `setGifFile`.
     * @param preview first-frame bitmap for packaging / AOD thumbnail path
     */
    fun backgroundGif(
        gifFile: File,
        preview: Bitmap,
        width: Int,
        height: Int,
    ): GifWidget {
        return GifWidget().apply {
            setGif(gifFile)
            setGifPreview(preview)
            setLocation(Point(0, 0))
            setSize(Size(width, height))
        }
    }

    /**
     * Digital time: `HH` + colon + `mm`.
     * Uses stock large glyphs `time_b_*` (~124ÃÂ96); bounding box 124+40+124 ÃÂ 96.
     * Studio does not expose time font size yet Ã¢ÂÂ tint only.
     */
    fun time(context: Context, x: Int, y: Int, tintRgb: Int): List<Widget> {
        val digits = QjsAssetBitmaps.loadDigitMap(
            context = context,
            nameForCode = { code -> "time_b_%02x.png".format(code) },
            tintRgb = tintRgb,
        )
        val colon = QjsAssetBitmaps.load(context, "time_b_25.png", tintRgb)
        val hourSize = Size(124, 96)
        val minuteSize = Size(124, 96)
        val colonSize = Size(40, 96)

        val hour = GroupWidget().apply {
            setValueType(QjsValueType.HOUR_NUM.value)
            setLocation(Point(x, y))
            setAlignment(Paint.Align.CENTER)
            setSize(hourSize)
            setImages(digits)
        }
        val colonX = x + hourSize.width
        val minuteX = colonX + colonSize.width
        val list = mutableListOf<Widget>(hour)
        if (colon != null) {
            list += SingleImageWidget().apply {
                setLocation(Point(colonX, y))
                setSize(colonSize)
                setImage(colon)
            }
        }
        list += GroupWidget().apply {
            setValueType(QjsValueType.MINUTE_NUM.value)
            setLocation(Point(minuteX, y))
            setAlignment(Paint.Align.CENTER)
            setSize(minuteSize)
            setImages(digits)
        }
        return list
    }

    /** Total time widget width (hour + colon + minute), for centering. */
    fun timeWidth(): Int = 124 + 40 + 124

    fun timeHeight(): Int = 96

    /**
     * Date (month/day): [GroupWidget] + [QjsValueType.DATE_NUM].
     * Glyphs include `0Ã¢ÂÂ9` and `/`; width is MM + `/` + DD, scaled by [StudioTextStyle.fontSizePx].
     */
    fun date(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        typeface: Typeface? = null,
    ): List<Widget> {
        val tf = QjsFontDigitBitmaps.typeface(context, typeface)
        val digits = QjsFontDigitBitmaps.renderDateDigits(tf, style.tintRgb, style.fontSizePx)
        val digitH = QjsFontDigitBitmaps.dateCell(style.fontSizePx).second
        val totalW = QjsFontDigitBitmaps.dateWidgetWidth(style.fontSizePx)
        return listOf(
            GroupWidget().apply {
                setValueType(QjsValueType.DATE_NUM.value)
                setLocation(Point(x, y))
                setSize(Size(totalW, digitH))
                setImages(digits)
            },
        )
    }

    fun dateWidth(fontSizePx: Int = QjsFontDigitBitmaps.DATE_DIGIT_H): Int =
        QjsFontDigitBitmaps.dateWidgetWidth(fontSizePx)

    fun dateHeight(fontSizePx: Int = QjsFontDigitBitmaps.DATE_DIGIT_H): Int =
        QjsFontDigitBitmaps.dateCell(fontSizePx).second

    /**
     * Weekday: [OptionWidget] + [QjsValueType.WEEK_DATA].
     * Image keys `"00"`Ã¢ÂÂ¦`"06"` (MonÃ¢ÂÂSun), matching stock week_XX.
     */
    fun week(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        typeface: Typeface? = null,
    ): List<Widget> {
        val tf = QjsFontDigitBitmaps.typeface(context, typeface)
        val images = QjsFontDigitBitmaps.renderWeekLabels(tf, style.tintRgb, style.fontSizePx)
        val (w, h) = QjsFontDigitBitmaps.weekCell(style.fontSizePx)
        return listOf(
            OptionWidget().apply {
                setValueType(QjsValueType.WEEK_DATA.value)
                setLocation(Point(x, y))
                setSize(Size(w, h))
                setImages(images)
            },
        )
    }

    fun weekWidth(fontSizePx: Int = QjsFontDigitBitmaps.WEEK_H): Int =
        QjsFontDigitBitmaps.weekCell(fontSizePx).first

    fun weekHeight(fontSizePx: Int = QjsFontDigitBitmaps.WEEK_H): Int =
        QjsFontDigitBitmaps.weekCell(fontSizePx).second

    /** Steps: icon + up to 5 digits ([QjsValueType.STEPS_NUM]). */
    fun step(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign = StudioIconAlign.LEFT,
        typeface: Typeface? = null,
    ): List<Widget> = iconData(
        context = context,
        x = x,
        y = y,
        style = style,
        align = align,
        typeface = typeface,
        iconAsset = "icon_steps.png",
        valueType = QjsValueType.STEPS_NUM.value,
        digitSlots = 5,
    )

    /** Distance: icon + ~3 digits (includes decimal-point glyph). */
    fun distance(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign = StudioIconAlign.LEFT,
        typeface: Typeface? = null,
    ): List<Widget> = iconData(
        context = context,
        x = x,
        y = y,
        style = style,
        align = align,
        typeface = typeface,
        iconAsset = "icon_distance.png",
        valueType = QjsValueType.DISTANCE_NUM.value,
        digitSlots = 3,
    )

    /** Calories: icon + up to 4 digits. */
    fun calorie(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign = StudioIconAlign.LEFT,
        typeface: Typeface? = null,
    ): List<Widget> = iconData(
        context = context,
        x = x,
        y = y,
        style = style,
        align = align,
        typeface = typeface,
        iconAsset = "icon_kcal.png",
        valueType = QjsValueType.CALORIE_NUM.value,
        digitSlots = 4,
    )

    /** Battery: icon + ~3 digits (may include `%` glyph). */
    fun battery(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign = StudioIconAlign.LEFT,
        typeface: Typeface? = null,
    ): List<Widget> = iconData(
        context = context,
        x = x,
        y = y,
        style = style,
        align = align,
        typeface = typeface,
        iconAsset = "icon_battery.png",
        valueType = QjsValueType.BATTERY_NUM.value,
        digitSlots = 3,
    )

    /** Heart rate: icon + ~3 digits. */
    fun heartrate(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign = StudioIconAlign.LEFT,
        typeface: Typeface? = null,
    ): List<Widget> = iconData(
        context = context,
        x = x,
        y = y,
        style = style,
        align = align,
        typeface = typeface,
        iconAsset = "icon_heart.png",
        valueType = QjsValueType.HEARTRATE_NUM.value,
        digitSlots = 3,
    )

    /**
     * Bounding box for icon+data combos (watchface px), shared by preview layout and packaging.
     *
     * @param digitSlots reserved digit slots (digit area width = cellW ÃÂ slots)
     * @param fontSizePx same as [StudioTextStyle.fontSizePx]; affects digit cells and icon box
     */
    fun iconDataSize(
        digitSlots: Int,
        align: StudioIconAlign,
        fontSizePx: Int = QjsFontDigitBitmaps.DATA_DIGIT_H,
    ): Size {
        val (digitW, digitH) = QjsFontDigitBitmaps.dataCell(fontSizePx)
        val dataW = digitW * digitSlots
        val iconBox = iconBoxForFontSize(fontSizePx)
        return when (align) {
            StudioIconAlign.LEFT, StudioIconAlign.RIGHT -> Size(dataW + iconBox, digitH)
            StudioIconAlign.TOP, StudioIconAlign.BOTTOM -> Size(dataW, digitH + iconBox)
        }
    }

    /**
     * Icon box side length: baseline 40px at font size 26, scaled with [fontSizePx]
     * in 0.5ÃÂÃ¢ÂÂ3ÃÂ, minimum 20.
     */
    private fun iconBoxForFontSize(fontSizePx: Int): Int {
        val ratio = fontSizePx / QjsFontDigitBitmaps.DATA_DIGIT_H.toFloat()
        return (40 * ratio.coerceIn(0.5f, 3f)).roundToInt().coerceAtLeast(20)
    }

    /**
     * Generic Ã¢ÂÂicon SingleImage + digit GroupWidgetÃ¢ÂÂ assembly.
     * Digits are font-rendered; icon comes from AAR and is tinted with [StudioTextStyle.tintRgb].
     * [iconOffset] vertically aligns the icon with the digit row.
     */
    private fun iconData(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign,
        typeface: Typeface?,
        iconAsset: String,
        valueType: Int,
        digitSlots: Int,
    ): List<Widget> {
        val size = iconDataSize(digitSlots, align, style.fontSizePx)
        val tf = QjsFontDigitBitmaps.typeface(context, typeface)
        val digits = QjsFontDigitBitmaps.renderDataDigits(tf, style.tintRgb, style.fontSizePx)
        val icon = QjsAssetBitmaps.load(context, iconAsset, style.tintRgb)
        val iconWidget = SingleImageWidget()
        val dataWidget = GroupWidget().apply {
            setValueType(valueType)
            setAlignment(Paint.Align.CENTER)
            setImages(digits)
        }

        val iconOffset = if (icon != null && digits.isNotEmpty()) {
            val dataH = digits.values.first().height
            (icon.height - dataH) / 2
        } else {
            0
        }

        when (align) {
            StudioIconAlign.LEFT -> {
                if (icon != null) {
                    iconWidget.setImage(icon)
                    iconWidget.setSize(Size(icon.width, icon.height))
                    iconWidget.setLocation(Point(x, y - iconOffset))
                }
                val newX = x + (iconWidget.size?.width ?: 0)
                val newW = (size.width - (iconWidget.size?.width ?: 0)).coerceAtLeast(0)
                dataWidget.setLocation(Point(newX, y))
                dataWidget.setSize(Size(newW, size.height))
            }
            StudioIconAlign.RIGHT -> {
                dataWidget.setLocation(Point(x, y))
                if (icon != null) {
                    iconWidget.setImage(icon)
                    iconWidget.setSize(Size(icon.width, icon.height))
                }
                val iconW = iconWidget.size?.width ?: 0
                val newW = (size.width - iconW).coerceAtLeast(0)
                dataWidget.setSize(Size(newW, size.height))
                iconWidget.setLocation(Point(x + size.width - iconW, y - iconOffset))
            }
            StudioIconAlign.BOTTOM -> {
                dataWidget.setLocation(Point(x, y))
                if (icon != null) {
                    iconWidget.setImage(icon)
                    iconWidget.setSize(Size(icon.width, icon.height))
                }
                val iconH = icon?.height ?: 0
                val newH = (size.height - iconH).coerceAtLeast(0)
                dataWidget.setSize(Size(size.width, newH))
                val iconW = iconWidget.size?.width ?: 0
                iconWidget.setLocation(
                    Point(x + (size.width - iconW) / 2, y + size.height - iconH),
                )
            }
            StudioIconAlign.TOP -> {
                if (icon != null) {
                    iconWidget.setImage(icon)
                    iconWidget.setSize(Size(icon.width, icon.height))
                } else {
                    iconWidget.setSize(Size(0, 0))
                }
                val iconW = iconWidget.size?.width ?: 0
                val iconH = icon?.height ?: 0
                iconWidget.setLocation(Point(x + (size.width - iconW) / 2, y))
                dataWidget.setLocation(Point(x, y + iconH))
                dataWidget.setSize(Size(size.width, (size.height - iconH).coerceAtLeast(0)))
            }
        }

        return listOf(iconWidget, dataWidget)
    }

    /**
     * Weather: weather [OptionWidget] (valueType=333, up to 15 states) + temperature [GroupWidget].
     * Weather icons are **not** tinted (`tint=-1`); only temperature digits follow [style].
     */
    fun weather(
        context: Context,
        x: Int,
        y: Int,
        style: StudioTextStyle,
        align: StudioIconAlign = StudioIconAlign.LEFT,
        typeface: Typeface? = null,
    ): List<Widget> {
        val size = iconDataSize(3, align, style.fontSizePx)
        val tf = QjsFontDigitBitmaps.typeface(context, typeface)
        val digits = QjsFontDigitBitmaps.renderDataDigits(tf, style.tintRgb, style.fontSizePx)
        val weatherImages = LinkedHashMap<String, Bitmap>()
        for (i in 0 until 15) {
            val key = String.format(Locale.ENGLISH, "%02d", i)
            val name = String.format(Locale.ENGLISH, "icon_wer_%02d.png", i + 1)
            val bmp = QjsAssetBitmaps.load(context, name, -1) ?: continue
            weatherImages[key] = bmp
        }
        val option = OptionWidget().apply {
            setMaxCount(15)
            setValueType(333)
            setImages(weatherImages)
        }
        val data = GroupWidget().apply {
            setValueType(QjsValueType.TEMPERATURE_NUM.value)
            setAlignment(Paint.Align.CENTER)
            setImages(digits)
        }
        val iconBox = iconBoxForFontSize(style.fontSizePx)
        val iconOffset = if (digits.isNotEmpty()) {
            (iconBox - digits.values.first().height) / 2
        } else {
            0
        }
        when (align) {
            StudioIconAlign.LEFT -> {
                option.setLocation(Point(x, y - iconOffset))
                option.setSize(Size(iconBox, iconBox))
                data.setLocation(Point(x + iconBox, y))
                data.setSize(Size((size.width - iconBox).coerceAtLeast(0), size.height))
            }
            StudioIconAlign.RIGHT -> {
                data.setLocation(Point(x, y))
                data.setSize(Size((size.width - iconBox).coerceAtLeast(0), size.height))
                option.setSize(Size(iconBox, iconBox))
                option.setLocation(Point(x + size.width - iconBox, y - iconOffset))
            }
            StudioIconAlign.BOTTOM -> {
                data.setLocation(Point(x, y))
                data.setSize(Size(size.width, (size.height - iconBox).coerceAtLeast(0)))
                option.setSize(Size(iconBox, iconBox))
                option.setLocation(Point(x + (size.width - iconBox) / 2, y + size.height - iconBox))
            }
            StudioIconAlign.TOP -> {
                option.setSize(Size(iconBox, iconBox))
                option.setLocation(Point(x + (size.width - iconBox) / 2, y))
                data.setLocation(Point(x, y + iconBox))
                data.setSize(Size(size.width, (size.height - iconBox).coerceAtLeast(0)))
            }
        }
        return listOf(option, data)
    }

    /**
     * Analog hands: hour/minute/second [PointerWidget] + center cap [SingleImageWidget].
     *
     * Defaults use stock `pointer_*.png` with tint;
     * a custom bitmap skips tint (keeps original colors).
     * Rotation [pivot] is tied to stock image size (second hand pivotY=222/247, not the tip).
     */
    fun pointers(
        context: Context,
        center: Point,
        hourTint: Int,
        minuteTint: Int,
        secondTint: Int,
        dotTint: Int,
        hourImage: Bitmap? = null,
        minuteImage: Bitmap? = null,
        secondImage: Bitmap? = null,
        dotImage: Bitmap? = null,
    ): List<Widget> {
        val list = mutableListOf<Widget>()
        list += pointerHand(
            context = context,
            valueType = QjsValueType.HOUR_POINTER.value,
            center = center,
            asset = "pointer_hour.png",
            defaultW = 14,
            defaultH = 114,
            pivot = Point(7, 114),
            tintRgb = hourTint,
            custom = hourImage,
            customPivot = { bmp -> Point(bmp.width / 2, bmp.height) },
        )
        list += pointerHand(
            context = context,
            valueType = QjsValueType.MINUTE_POINTER.value,
            center = center,
            asset = "pointer_min.png",
            defaultW = 14,
            defaultH = 194,
            pivot = Point(7, 194),
            tintRgb = minuteTint,
            custom = minuteImage,
            customPivot = { bmp -> Point(bmp.width / 2, bmp.height) },
        )
        list += pointerHand(
            context = context,
            valueType = QjsValueType.SECOND_POINTER.value,
            center = center,
            asset = "pointer_second.png",
            defaultW = 6,
            defaultH = 247,
            pivot = Point(3, 222),
            tintRgb = secondTint,
            custom = secondImage,
            customPivot = { bmp ->
                Point(bmp.width / 2, (bmp.height * 222 / 247).coerceAtLeast(1))
            },
        )
        val dotBmp = dotImage
            ?: QjsAssetBitmaps.load(context, "pointer_center.png", dotTint)
        if (dotBmp != null) {
            list += SingleImageWidget().apply {
                setImage(dotBmp)
                setSize(Size(dotBmp.width, dotBmp.height))
                setLocation(Point(center.x - dotBmp.width / 2, center.y - dotBmp.height / 2))
            }
        }
        return list
    }

    /** Single hand: load/tint bitmap, set dial center and rotation pivot. */
    private fun pointerHand(
        context: Context,
        valueType: Int,
        center: Point,
        asset: String,
        defaultW: Int,
        defaultH: Int,
        pivot: Point,
        tintRgb: Int,
        custom: Bitmap?,
        customPivot: (Bitmap) -> Point,
    ): PointerWidget {
        val image = custom ?: QjsAssetBitmaps.load(context, asset, tintRgb)
        val rotationCenter = when {
            custom != null -> customPivot(custom)
            else -> pivot
        }
        return PointerWidget().apply {
            setValueType(valueType)
            setCenterPoint(center)
            setRotationCenterPoint(rotationCenter)
            setSize(Size(image?.width ?: defaultW, image?.height ?: defaultH))
            if (image != null) setImage(image)
        }
    }

    /**
     * Tap hot-zone shortcut.
     * @param launcherType see [StudioLauncherType]
     */
    fun appLink(x: Int, y: Int, width: Int, height: Int, launcherType: Int): AppLinkWidget {
        return AppLinkWidget().apply {
            setLocation(Point(x, y))
            setSize(Size(width, height))
            setValueType(launcherType)
        }
    }
}
