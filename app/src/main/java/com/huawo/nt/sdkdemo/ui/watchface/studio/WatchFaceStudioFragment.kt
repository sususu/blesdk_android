package com.huawo.nt.sdkdemo.ui.watchface.studio

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Outline
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.huawo.imagepicker.ImagePicker
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentWatchfaceStudioBinding
import com.huawo.nt.sdkdemo.databinding.IncludeWfsWidgetRowBinding
import com.huawo.nt.sdkdemo.util.WatchfaceImageUtils
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.video.VideoGifResultHolder
import com.huawo.video.VideoToGif2Activity
import com.huawo.video.VideoToGifActivity
import com.huawo.watchface.Callback
import com.huawo.watchface.qjs.utils.FileUtils
import com.huawo.watchface.qjs.widgets.Widget
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/**
 * Watch Face Studio — Sifli QJS low-level Widget demo.
 *
 * Preview and [buildWidgets] share watchface-pixel coordinates; colors are `0x00RRGGBB`
 * ([parseWidgetColor]). This package does not use `com.huawo.watchface.custom.widget.*`.
 */
class WatchFaceStudioFragment : Fragment() {
    private var _binding: FragmentWatchfaceStudioBinding? = null
    private val binding get() = _binding!!

    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundBitmap: Bitmap? = null
    /** GIF from Video→GIF; when set, packaging uses [GifWidget] instead of static bitmap. */
    private var backgroundGifFile: File? = null
    private var busy = false
    private var refreshScheduled = false
    private var customTypeface: Typeface? = null

    /** Built-in fonts: system default + two TTFs under assets */
    private data class BuiltinFont(
        val labelRes: Int,
        val assetPath: String?,
    )

    private val builtinFonts =
        listOf(
            BuiltinFont(R.string.wfs_font_default, null),
            BuiltinFont(R.string.wfs_font_roboto, QjsFontDigitBitmaps.ASSET_ROBOTO_BOLD),
            BuiltinFont(R.string.wfs_font_montserrat, QjsFontDigitBitmaps.ASSET_MONTSERRAT_BOLD),
        )

    private data class SizePreset(
        val label: String,
        val width: Int,
        val height: Int,
        val corner: Int,
        val thumbW: Int,
        val thumbH: Int,
        val thumbCorner: Int,
    )

    private data class WidgetStyleRow(
        val row: IncludeWfsWidgetRowBinding,
        val labelRes: Int,
        val defaultRgb: Int,
        val defaultFontSize: Int,
        val preview: TextView,
    )

    private val fontSizeChoices =
        listOf(16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56)

    private val sizePresets =
        listOf(
            SizePreset("466 x 466", 466, 466, 233, 264, 264, 132),
            SizePreset("480 x 480", 480, 480, 240, 264, 264, 132),
            SizePreset("410 x 502", 410, 502, 108, 200, 244, 50),
        )

    private var selectedPreset = sizePresets[0]
    private lateinit var widgetStyleRows: List<WidgetStyleRow>

    private val pickImageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            when {
                result.resultCode == Activity.RESULT_OK && data?.data != null -> {
                    loadBackgroundFromUri(data.data!!)
                }
                result.resultCode == ImagePicker.RESULT_ERROR -> {
                    setPreviewLoading(false)
                    Toast.makeText(
                        requireContext(),
                        ImagePicker.getError(data),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> setPreviewLoading(false)
            }
        }

    private val pickVideoResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) launchVideoToGif(uri)
        }

    private val videoToGifResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val path =
                result.data?.getStringExtra("git_path")
                    ?.takeIf { it.isNotBlank() }
                    ?: VideoGifResultHolder.take()
            if (result.resultCode == Activity.RESULT_OK && !path.isNullOrBlank()) {
                applyGifBackground(path)
            } else {
                VideoGifResultHolder.clear()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWatchfaceStudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.spinnerSize.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                sizePresets.map { it.label },
            )
        binding.spinnerSize.setSelection(0)
        binding.spinnerSize.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    selectedPreset = sizePresets[position.coerceIn(0, sizePresets.lastIndex)]
                    schedulePreviewRefresh()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.btnAlbum.setOnClickListener { pickBackground(gallery = true) }
        binding.btnCamera.setOnClickListener { pickBackground(gallery = false) }
        binding.btnVideo.setOnClickListener { pickVideoResult.launch("video/*") }
        binding.btnClearBg.setOnClickListener {
            clearBackground()
            schedulePreviewRefresh()
        }
        binding.btnSync.setOnClickListener { syncToWatch() }
        setupBuiltinFontSpinner()

        // 0 date, 1 week, 2 step, 3 distance, 4 calorie, 5 battery, 6 hr, 7 weather
        widgetStyleRows =
            listOf(
                WidgetStyleRow(binding.rowDate, R.string.wfs_enable_date, 0xFFFFFF, 40, binding.tvDate),
                WidgetStyleRow(binding.rowWeek, R.string.wfs_enable_week, 0xFFFFFF, 40, binding.tvWeek),
                WidgetStyleRow(binding.rowStep, R.string.wfs_enable_step, 0xFF0000, 26, binding.tvStep),
                WidgetStyleRow(
                    binding.rowDistance,
                    R.string.wfs_enable_distance,
                    0x55AAFF,
                    26,
                    binding.tvDistance,
                ),
                WidgetStyleRow(
                    binding.rowCalorie,
                    R.string.wfs_enable_calorie,
                    0xFFAA00,
                    26,
                    binding.tvCalorie,
                ),
                WidgetStyleRow(
                    binding.rowBattery,
                    R.string.wfs_enable_battery,
                    0x55FF55,
                    26,
                    binding.tvBattery,
                ),
                WidgetStyleRow(binding.rowHr, R.string.wfs_enable_hr, 0xFF66AA, 26, binding.tvHr),
                WidgetStyleRow(
                    binding.rowWeather,
                    R.string.wfs_enable_weather,
                    0xFFFFFF,
                    26,
                    binding.tvWeather,
                ),
            )
        for (row in widgetStyleRows) {
            setupWidgetStyleRow(row)
        }
        setupPointerColors()

        for (cb in listOf(
            binding.cbLinkPhone,
            binding.cbLinkNotify,
            binding.cbLinkActivity,
            binding.cbLinkWeather,
        )) {
            cb.setOnCheckedChangeListener { _, _ -> schedulePreviewRefresh() }
        }

        binding.cbPointers.setOnCheckedChangeListener { _, checked ->
            binding.rowPointerColors.isVisible = checked
            schedulePreviewRefresh()
        }

        binding.tvStatus.text = getString(R.string.wfs_ready)
        binding.flPreview.post { refreshPreview() }
    }

    private fun setupWidgetStyleRow(row: WidgetStyleRow) {
        row.row.checkbox.setText(row.labelRes)
        row.row.checkbox.isChecked = true
        val hex = String.format("#%06X", row.defaultRgb and 0xFFFFFF)
        row.row.tvColor.text = hex
        applySwatchColor(row.row.swatch, row.defaultRgb)
        // Color via swatch only; hex is not shown in the UI
        bindColorPicker(row.row.tvColor, row.row.swatch) { schedulePreviewRefresh() }
        setupFontSizeSpinner(row.row.spinnerFontSize, row.defaultFontSize)
        row.row.checkbox.setOnCheckedChangeListener { _, _ -> schedulePreviewRefresh() }
    }

    /**
     * Safe insets from dial corner radius so round faces do not clip corner widgets.
     * Full circle (corner ≈ half side) uses larger insets; squircle uses smaller fixed ones.
     */
    private data class LayoutInsets(
        val side: Int,
        val top: Int,
        val bottom: Int,
        val link: Int,
        val timeY: Int,
    )

    private fun layoutInsets(width: Int, height: Int, corner: Int): LayoutInsets {
        val round = width == height && corner >= width / 2 - 2
        return if (round) {
            val m = (width * 0.17f).toInt().coerceIn(64, 120)
            LayoutInsets(
                side = m,
                top = m,
                bottom = m,
                link = (width * 0.12f).toInt().coerceIn(40, 80),
                timeY = (width * 0.15f).toInt().coerceIn(56, 100),
            )
        } else {
            LayoutInsets(side = 40, top = 20, bottom = 40, link = 16, timeY = 60)
        }
    }

    /** Built-in font Spinner; default Roboto Bold (index=1) */
    private fun setupBuiltinFontSpinner() {
        val labels = builtinFonts.map { getString(it.labelRes) }
        binding.spinnerFont.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            )
        binding.spinnerFont.setSelection(1)
        applyBuiltinFont(1)
        binding.spinnerFont.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    applyBuiltinFont(position)
                    schedulePreviewRefresh()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun applyBuiltinFont(position: Int) {
        val opt = builtinFonts.getOrElse(position) { builtinFonts[0] }
        customTypeface =
            opt.assetPath?.let { path ->
                QjsFontDigitBitmaps.typefaceFromAsset(requireContext(), path)
            }
    }

    private fun setupFontSizeSpinner(spinner: Spinner, defaultFontSize: Int) {
        // Collapsed state shows digits only; "px" sits in an outer TextView to avoid arrow clipping
        val labels = fontSizeChoices.map { it.toString() }
        spinner.adapter =
            ArrayAdapter(
                requireContext(),
                R.layout.wfs_spinner_item,
                labels,
            ).also { it.setDropDownViewResource(R.layout.wfs_spinner_dropdown_item) }
        val index = fontSizeChoices.indexOf(defaultFontSize).coerceAtLeast(0)
        spinner.setSelection(index)
        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    schedulePreviewRefresh()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun setupPointerColors() {
        binding.etHourColor.text = "#00FF00"
        binding.etMinuteColor.text = "#0000FF"
        binding.etSecondColor.text = "#A0FF55"
        binding.etDotColor.text = "#FFFFFF"
        applySwatchColor(binding.swatchHour, 0x00FF00)
        applySwatchColor(binding.swatchMinute, 0x0000FF)
        applySwatchColor(binding.swatchSecond, 0xA0FF55)
        applySwatchColor(binding.swatchDot, 0xFFFFFF)
        bindColorPicker(binding.etHourColor, binding.swatchHour) { schedulePreviewRefresh() }
        bindColorPicker(binding.etMinuteColor, binding.swatchMinute) { schedulePreviewRefresh() }
        bindColorPicker(binding.etSecondColor, binding.swatchSecond) { schedulePreviewRefresh() }
        bindColorPicker(binding.etDotColor, binding.swatchDot) { schedulePreviewRefresh() }
    }

    private fun bindColorPicker(
        colorTarget: TextView,
        swatch: View,
        onPicked: (() -> Unit)? = null,
    ) {
        colorTarget.isClickable = true
        val open = View.OnClickListener {
            showColorPicker(colorTarget, swatch) { onPicked?.invoke() }
        }
        colorTarget.setOnClickListener(open)
        swatch.setOnClickListener(open)
    }

    private fun showColorPicker(
        target: TextView,
        swatch: View,
        onPicked: (() -> Unit)? = null,
    ) {
        ColorPickerDialog.Builder(requireContext())
            .setTitle(getString(R.string.wfs_pick_color))
            .setPreferenceName("WfsColorPicker_${target.id}")
            .setPositiveButton(
                getString(R.string.confirm),
                ColorEnvelopeListener { envelope: ColorEnvelope, _: Boolean ->
                    val rgb = 0xFFFFFF and envelope.color
                    target.text = String.format("#%06X", rgb)
                    applySwatchColor(swatch, rgb)
                    onPicked?.invoke()
                },
            )
            .setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(12)
            .show()
    }

    private fun applySwatchColor(swatch: View, color: Int) {
        val gd =
            (swatch.background as? GradientDrawable)?.mutate() as? GradientDrawable
                ?: GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = resources.displayMetrics.density * 4f
                    setStroke(
                        resources.displayMetrics.density.toInt().coerceAtLeast(1),
                        0x33000000,
                    )
                }
        gd.setColor(toUiColor(color))
        swatch.background = gd
    }

    private fun readStyle(row: WidgetStyleRow): StudioTextStyle {
        val rgb = parseWidgetColor(row.row.tvColor.text?.toString(), row.defaultRgb)
        val fontSizePx =
            fontSizeChoices.getOrElse(row.row.spinnerFontSize.selectedItemPosition) {
                row.defaultFontSize
            }
        return StudioTextStyle(tintRgb = rgb, fontSizePx = fontSizePx)
    }

    private fun schedulePreviewRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        binding.flPreview.post {
            refreshScheduled = false
            refreshPreview()
        }
    }

    private fun refreshPreview() {
        if (_binding == null) return
        val width = selectedPreset.width
        val height = selectedPreset.height
        val corner = selectedPreset.corner
        updatePreviewAspect(width, height)
        applyPreviewCornerClip(width, corner)
        applyPreviewTypeface()

        for (row in widgetStyleRows) {
            val checked = row.row.checkbox.isChecked
            row.preview.isVisible = checked
            if (checked) {
                val style = readStyle(row)
                row.preview.setTextColor(toUiColor(style.tintRgb))
                applySwatchColor(
                    row.row.swatch,
                    parseWidgetColor(row.row.tvColor.text?.toString(), row.defaultRgb),
                )
            }
        }

        binding.tvLinkPhone.isVisible = binding.cbLinkPhone.isChecked
        binding.tvLinkNotify.isVisible = binding.cbLinkNotify.isChecked
        binding.tvLinkActivity.isVisible = binding.cbLinkActivity.isChecked
        binding.tvLinkWeather.isVisible = binding.cbLinkWeather.isChecked

        val pointers = binding.cbPointers.isChecked
        binding.viewPointerHour.isVisible = pointers
        binding.viewPointerMinute.isVisible = pointers
        binding.viewPointerSecond.isVisible = pointers
        binding.viewCenterDot.isVisible = pointers
        if (pointers) {
            val hour = parseWidgetColor(binding.etHourColor.text?.toString(), 0x00FF00)
            val minute = parseWidgetColor(binding.etMinuteColor.text?.toString(), 0x0000FF)
            val second = parseWidgetColor(binding.etSecondColor.text?.toString(), 0xA0FF55)
            val dot = parseWidgetColor(binding.etDotColor.text?.toString(), 0xFFFFFF)
            setPointerPreviewImage(binding.viewPointerHour, "pointer_hour.png", hour)
            setPointerPreviewImage(binding.viewPointerMinute, "pointer_min.png", minute)
            setPointerPreviewImage(binding.viewPointerSecond, "pointer_second.png", second)
            setPointerPreviewImage(binding.viewCenterDot, "pointer_center.png", dot)
            applySwatchColor(binding.swatchHour, hour)
            applySwatchColor(binding.swatchMinute, minute)
            applySwatchColor(binding.swatchSecond, second)
            applySwatchColor(binding.swatchDot, dot)
        }

        binding.flPreview.post {
            if (_binding == null) return@post
            layoutPreviewWidgets(width, height)
            applyPreviewCornerClip(width, corner)
        }
    }

    /** Apply the Spinner-selected typeface to all preview TextViews. */
    private fun applyPreviewTypeface() {
        val tf = QjsFontDigitBitmaps.typeface(requireContext(), customTypeface)
        binding.tvTime.typeface = tf
        binding.tvDate.typeface = tf
        binding.tvWeek.typeface = tf
        binding.tvStep.typeface = tf
        binding.tvDistance.typeface = tf
        binding.tvCalorie.typeface = tf
        binding.tvBattery.typeface = tf
        binding.tvHr.typeface = tf
        binding.tvWeather.typeface = tf
    }

    private fun updatePreviewAspect(width: Int, height: Int) {
        val preview = binding.flPreview
        val lp = preview.layoutParams
        val density = resources.displayMetrics.density
        val base = (300 * density).toInt()
        if (width >= height) {
            lp.width = base
            lp.height = (base * height / width.toFloat()).toInt().coerceAtLeast(1)
        } else {
            lp.height = base
            lp.width = (base * width / height.toFloat()).toInt().coerceAtLeast(1)
        }
        preview.layoutParams = lp
    }

    private fun applyPreviewCornerClip(watchWidth: Int, cornerWatchPx: Int) {
        val preview = binding.flPreview
        val pw = preview.width
        if (pw <= 0 || watchWidth <= 0) return
        val radius = cornerWatchPx * pw / watchWidth.toFloat()
        preview.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        preview.clipToOutline = true
    }

    private fun clampWatchRect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        watchWidth: Int,
        watchHeight: Int,
    ): Pair<Int, Int> {
        var cx = x
        var cy = y
        if (cx < 0) cx = 0
        if (cy < 0) cy = 0
        if (cx + w > watchWidth) cx = (watchWidth - w).coerceAtLeast(0)
        if (cy + h > watchHeight) cy = (watchHeight - h).coerceAtLeast(0)
        return cx to cy
    }

    private fun layoutPreviewWidgets(watchWidth: Int, watchHeight: Int) {
        val preview = binding.flPreview
        val pw = preview.width.takeIf { it > 0 } ?: return
        val ph = preview.height.takeIf { it > 0 } ?: return
        val sx = pw / watchWidth.toFloat()
        val sy = ph / watchHeight.toFloat()
        val inset = layoutInsets(watchWidth, watchHeight, selectedPreset.corner)

        val timeW = QjsComponentBuilders.timeWidth()
        val timeH = QjsComponentBuilders.timeHeight()
        val timeX = (watchWidth - timeW) / 2
        val timeY = inset.timeY
        place(binding.tvTime, timeX, timeY, timeW, timeH, sx, sy, watchWidth, watchHeight)
        binding.tvTime.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            (timeH * sy * 0.55f).coerceAtLeast(8f),
        )

        val dateStyle = readStyle(widgetStyleRows[0])
        val weekStyle = readStyle(widgetStyleRows[1])
        val dateW = QjsComponentBuilders.dateWidth(dateStyle.fontSizePx)
        val weekW = QjsComponentBuilders.weekWidth(weekStyle.fontSizePx)
        val dateH = QjsComponentBuilders.dateHeight(dateStyle.fontSizePx)
        val weekH = QjsComponentBuilders.weekHeight(weekStyle.fontSizePx)
        val rowY = timeY + timeH + 10
        val dateChecked = widgetStyleRows[0].row.checkbox.isChecked
        val weekChecked = widgetStyleRows[1].row.checkbox.isChecked
        val rowTotalW = (if (dateChecked) dateW else 0) + (if (weekChecked) weekW else 0)
        var rowX = (watchWidth - rowTotalW) / 2
        if (dateChecked) {
            placeText(
                binding.tvDate,
                rowX,
                rowY,
                dateW,
                dateH,
                sx,
                sy,
                dateStyle.fontSizePx,
                watchWidth,
                watchHeight,
            )
            rowX += dateW
        }
        if (weekChecked) {
            placeText(
                binding.tvWeek,
                rowX,
                rowY,
                weekW,
                weekH,
                sx,
                sy,
                weekStyle.fontSizePx,
                watchWidth,
                watchHeight,
            )
        }

        val stepStyle = readStyle(widgetStyleRows[2])
        val distStyle = readStyle(widgetStyleRows[3])
        val calStyle = readStyle(widgetStyleRows[4])
        val batStyle = readStyle(widgetStyleRows[5])
        val hrStyle = readStyle(widgetStyleRows[6])
        val weatherStyle = readStyle(widgetStyleRows[7])

        val stepSize =
            QjsComponentBuilders.iconDataSize(5, StudioIconAlign.LEFT, stepStyle.fontSizePx)
        val stepY = watchHeight - stepSize.height - inset.bottom
        placeText(
            binding.tvStep,
            inset.side,
            stepY,
            stepSize.width,
            stepSize.height,
            sx,
            sy,
            stepStyle.fontSizePx,
            watchWidth,
            watchHeight,
        )

        val distSize =
            QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, distStyle.fontSizePx)
        placeText(
            binding.tvDistance,
            inset.side,
            stepY - distSize.height - 8,
            distSize.width,
            distSize.height,
            sx,
            sy,
            distStyle.fontSizePx,
            watchWidth,
            watchHeight,
        )

        val calSize =
            QjsComponentBuilders.iconDataSize(4, StudioIconAlign.LEFT, calStyle.fontSizePx)
        placeText(
            binding.tvCalorie,
            watchWidth - calSize.width - inset.side,
            stepY - calSize.height - 8,
            calSize.width,
            calSize.height,
            sx,
            sy,
            calStyle.fontSizePx,
            watchWidth,
            watchHeight,
        )

        val batSize =
            QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, batStyle.fontSizePx)
        placeText(
            binding.tvBattery,
            inset.side,
            inset.top,
            batSize.width,
            batSize.height,
            sx,
            sy,
            batStyle.fontSizePx,
            watchWidth,
            watchHeight,
        )

        val hrSize = QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, hrStyle.fontSizePx)
        placeText(
            binding.tvHr,
            watchWidth - hrSize.width - inset.side,
            inset.top,
            hrSize.width,
            hrSize.height,
            sx,
            sy,
            hrStyle.fontSizePx,
            watchWidth,
            watchHeight,
        )

        val weatherSize =
            QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, weatherStyle.fontSizePx)
        placeText(
            binding.tvWeather,
            watchWidth - weatherSize.width - inset.side,
            watchHeight - weatherSize.height - inset.bottom,
            weatherSize.width,
            weatherSize.height,
            sx,
            sy,
            weatherStyle.fontSizePx,
            watchWidth,
            watchHeight,
        )

        val link = 72
        place(
            binding.tvLinkPhone,
            inset.link,
            watchHeight / 2 - link / 2,
            link,
            link,
            sx,
            sy,
            watchWidth,
            watchHeight,
        )
        place(
            binding.tvLinkNotify,
            watchWidth - link - inset.link,
            watchHeight / 2 - link / 2,
            link,
            link,
            sx,
            sy,
            watchWidth,
            watchHeight,
        )
        place(
            binding.tvLinkActivity,
            watchWidth / 2 - link / 2,
            watchHeight - link - inset.link,
            link,
            link,
            sx,
            sy,
            watchWidth,
            watchHeight,
        )
        place(
            binding.tvLinkWeather,
            watchWidth / 2 - link / 2,
            inset.link,
            link,
            link,
            sx,
            sy,
            watchWidth,
            watchHeight,
        )

        val cx = watchWidth / 2
        val cy = watchHeight / 2
        placePointer(binding.viewPointerHour, cx, cy, 14, 114, sx, sy)
        placePointer(binding.viewPointerMinute, cx, cy, 14, 194, sx, sy)
        // Stock second-hand pivot is at 222/247, not the tip
        placePointer(binding.viewPointerSecond, cx, cy, 6, 247, sx, sy, pivotYRatio = 222f / 247f)
        val dotBmp = (binding.viewCenterDot.drawable as? BitmapDrawable)?.bitmap
        val dotW = dotBmp?.width ?: 16
        val dotH = dotBmp?.height ?: 16
        place(
            binding.viewCenterDot,
            cx - dotW / 2,
            cy - dotH / 2,
            dotW,
            dotH,
            sx,
            sy,
            watchWidth,
            watchHeight,
        )
    }

    /** Load stock pointer PNG with RGB tint into the preview ImageView. */
    private fun setPointerPreviewImage(view: ImageView, asset: String, tintRgb: Int) {
        view.setImageBitmap(QjsAssetBitmaps.load(requireContext(), asset, tintRgb))
    }

    private fun place(
        view: View,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        sx: Float,
        sy: Float,
        watchWidth: Int = 0,
        watchHeight: Int = 0,
    ) {
        var cx = x
        var cy = y
        if (watchWidth > 0 && watchHeight > 0) {
            val clamped = clampWatchRect(cx, cy, w, h, watchWidth, watchHeight)
            cx = clamped.first
            cy = clamped.second
        }
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = (w * sx).toInt().coerceAtLeast(1)
        lp.height = (h * sy).toInt().coerceAtLeast(1)
        lp.leftMargin = (cx * sx).toInt()
        lp.topMargin = (cy * sy).toInt()
        view.layoutParams = lp
    }

    private fun placeText(
        view: TextView,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        sx: Float,
        sy: Float,
        fontSizePx: Int,
        watchWidth: Int,
        watchHeight: Int,
    ) {
        place(view, x, y, w, h, sx, sy, watchWidth, watchHeight)
        view.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            (fontSizePx * sy * 0.85f).coerceAtLeast(8f),
        )
    }

    private fun placePointer(
        view: ImageView,
        cx: Int,
        cy: Int,
        defaultW: Int,
        defaultH: Int,
        sx: Float,
        sy: Float,
        pivotYRatio: Float = 1f,
    ) {
        val bmp = (view.drawable as? BitmapDrawable)?.bitmap
        val w = bmp?.width ?: defaultW
        val h = bmp?.height ?: defaultH
        val pw = (w * sx).toInt().coerceAtLeast(1)
        val ph = (h * sy).toInt().coerceAtLeast(1)
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = pw
        lp.height = ph
        // Anchor so rotation pivot sits on dial center
        lp.leftMargin = (cx * sx).toInt() - pw / 2
        lp.topMargin = (cy * sy).toInt() - (ph * pivotYRatio).toInt()
        view.layoutParams = lp
        view.pivotX = pw / 2f
        view.pivotY = ph * pivotYRatio
    }

    private fun pickBackground(gallery: Boolean) {
        val width = selectedPreset.width
        val height = selectedPreset.height
        val corner = selectedPreset.corner
        val builder =
            ImagePicker.with(this)
                .crop()
                .compress(2048)
                .fixedResultSize(width, height, corner)
        if (gallery) builder.galleryOnly() else builder.cameraOnly()
        setPreviewLoading(true)
        builder.createIntent { intent -> pickImageResult.launch(intent) }
    }

    private fun clearBackground() {
        backgroundBitmap = null
        backgroundGifFile = null
        binding.imgBg.setImageDrawable(null)
    }

    /** Same flow as HaWoFit [WatchfaceEditHR04Activity]: pick video → VideoToGif2Activity. */
    private fun launchVideoToGif(uri: Uri) {
        val videoPath = resolveVideoPath(uri).orEmpty()
        val intent =
            Intent(requireContext(), VideoToGif2Activity::class.java).apply {
                putExtra(VideoToGifActivity.VIDEO_URI_KEY, uri)
                putExtra(VideoToGifActivity.VIDEO_PATH_KEY, videoPath)
                putExtra(VideoToGifActivity.GIF_WIDTH_KEY, selectedPreset.width)
                putExtra(VideoToGifActivity.GIF_HEIGHT_KEY, selectedPreset.height)
                putExtra(VideoToGifActivity.GIF_RADIUS_KEY, selectedPreset.corner)
                putExtra(VideoToGifActivity.GIF_MAX_DURATION_KEY, 5)
                putExtra(VideoToGifActivity.GIF_MIN_DURATION_KEY, 2)
            }
        videoToGifResult.launch(intent)
    }

    private fun resolveVideoPath(uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        // Best-effort: copy content URI into cache so FFmpeg has a real path fallback
        return try {
            val name =
                requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                } ?: "video_${System.currentTimeMillis()}.mp4"
            val out = File(requireContext().cacheDir, "wfs_$name")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            if (out.exists()) out.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    private fun applyGifBackground(gifPath: String) {
        val file = File(gifPath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.wfs_gif_fail, Toast.LENGTH_SHORT).show()
            return
        }
        setPreviewLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val firstFrame =
                withContext(Dispatchers.IO) {
                    FileUtils.getFirstFrameBitmapOfGif(requireContext(), file)
                        ?: BitmapFactory.decodeFile(gifPath)
                }
            if (_binding == null) return@launch
            if (firstFrame == null) {
                setPreviewLoading(false)
                Toast.makeText(requireContext(), R.string.wfs_gif_fail, Toast.LENGTH_SHORT).show()
                return@launch
            }
            backgroundGifFile = file
            backgroundBitmap = firstFrame
            showGifPreview(file, firstFrame)
            schedulePreviewRefresh()
            setPreviewLoading(false)
            Toast.makeText(requireContext(), R.string.wfs_gif_ready, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showGifPreview(file: File, fallback: Bitmap) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                binding.imgBg.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable.start()
                }
                return
            }
        } catch (_: Exception) {
            // fall through to static first frame
        }
        binding.imgBg.setImageBitmap(fallback)
    }

    private fun setPreviewLoading(loading: Boolean) {
        if (_binding == null) return
        binding.progressPreview.isVisible = loading
    }

    private fun loadBackgroundFromUri(uri: Uri) {
        setPreviewLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp =
                withContext(Dispatchers.IO) {
                    try {
                        requireContext().contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            if (_binding == null) return@launch
            if (bmp != null) {
                backgroundGifFile = null
                backgroundBitmap = bmp
                binding.imgBg.setImageBitmap(bmp)
                schedulePreviewRefresh()
                setPreviewLoading(false)
            } else {
                setPreviewLoading(false)
                Toast.makeText(
                    requireContext(),
                    R.string.wfs_bg_load_fail,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun syncToWatch() {
        if (busy) return
        if (!BluetoothSDK.isConnected()) {
            Toast.makeText(requireContext(), R.string.status_need_connect_first, Toast.LENGTH_SHORT)
                .show()
            return
        }
        val mac = BluetoothSDK.getConnectedDevice()?.mac?.takeIf { it.isNotBlank() }
        if (mac.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.ota_need_mac, Toast.LENGTH_SHORT).show()
            return
        }

        val width = selectedPreset.width
        val height = selectedPreset.height
        val corner = selectedPreset.corner.toFloat()
        val thumbW = selectedPreset.thumbW
        val thumbH = selectedPreset.thumbH
        val thumbCorner = selectedPreset.thumbCorner.toFloat()
        val name = binding.etName.text?.toString()?.trim().orEmpty().ifBlank { "studio" }

        refreshPreview()
        setBusy(true, getString(R.string.wfs_making))
        binding.flPreview.post {
            try {
                val preview = WatchfaceImageUtils.frameLayoutToBitmap(binding.flPreview)
                val thumbnail =
                    WatchfaceImageUtils.scaledRoundedBitmap(preview, thumbW, thumbH, thumbCorner)
                val gifFile = backgroundGifFile?.takeIf { it.exists() }
                val bg =
                    backgroundBitmap?.let {
                        WatchfaceImageUtils.scaledRoundedBitmap(it, width, height, corner)
                    }
                val widgets = buildWidgets(width, height)
                QjsWatchfacePackager.push(
                    context = requireContext(),
                    mac = mac,
                    config =
                        StudioWatchfaceConfig(
                            name = name,
                            width = width,
                            height = height,
                            thumbnail = thumbnail,
                            background = bg,
                            backgroundGif = gifFile,
                            widgets = widgets,
                        ),
                    callback =
                        object : Callback {
                            override fun onZipCompleted(zip: java.io.File?) {
                                mainHandler.post {
                                    setBusy(true, getString(R.string.wfs_syncing))
                                }
                            }

                            override fun onProgress(current: Long, total: Long) {
                                val pct =
                                    if (total > 0L) {
                                        ((current * 100) / total).toInt().coerceIn(0, 100)
                                    } else {
                                        0
                                    }
                                mainHandler.post {
                                    binding.progress.isVisible = true
                                    binding.progress.progress = pct
                                    binding.tvStatus.text =
                                        getString(R.string.wfs_syncing_pct, pct)
                                }
                            }

                            override fun onSuccess() {
                                mainHandler.post {
                                    setBusy(false, getString(R.string.wfs_sync_ok))
                                    Toast.makeText(
                                        requireContext(),
                                        R.string.wfs_sync_ok,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }

                            override fun onError(code: Int) {
                                mainHandler.post {
                                    setBusy(false, getString(R.string.wfs_sync_fail, code))
                                }
                            }
                        },
                )
            } catch (e: Exception) {
                setBusy(false, e.message.orEmpty().ifBlank { e.javaClass.simpleName })
            }
        }
    }

    private fun buildWidgets(width: Int, height: Int): List<Widget> {
        val ctx = requireContext()
        val tf = customTypeface
        val list = mutableListOf<Widget>()
        val inset = layoutInsets(width, height, selectedPreset.corner)
        val white = 0xFFFFFF
        val timeW = QjsComponentBuilders.timeWidth()
        val timeH = QjsComponentBuilders.timeHeight()
        val timeX = (width - timeW) / 2
        val timeY = inset.timeY
        list += QjsComponentBuilders.time(ctx, timeX, timeY, white)

        val dateStyle = readStyle(widgetStyleRows[0])
        val weekStyle = readStyle(widgetStyleRows[1])
        val dateW = QjsComponentBuilders.dateWidth(dateStyle.fontSizePx)
        val weekW = QjsComponentBuilders.weekWidth(weekStyle.fontSizePx)
        val rowY = timeY + timeH + 10
        var rowX = (width - dateW - weekW) / 2
        if (widgetStyleRows[0].row.checkbox.isChecked) {
            list += QjsComponentBuilders.date(ctx, rowX, rowY, dateStyle, tf)
            rowX += dateW
        }
        if (widgetStyleRows[1].row.checkbox.isChecked) {
            list += QjsComponentBuilders.week(ctx, rowX, rowY, weekStyle, tf)
        }

        val stepStyle = readStyle(widgetStyleRows[2])
        val stepSize =
            QjsComponentBuilders.iconDataSize(5, StudioIconAlign.LEFT, stepStyle.fontSizePx)
        val stepY = height - stepSize.height - inset.bottom
        if (widgetStyleRows[2].row.checkbox.isChecked) {
            list += QjsComponentBuilders.step(ctx, inset.side, stepY, stepStyle, typeface = tf)
        }
        if (widgetStyleRows[3].row.checkbox.isChecked) {
            val style = readStyle(widgetStyleRows[3])
            val size = QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, style.fontSizePx)
            list +=
                QjsComponentBuilders.distance(
                    ctx,
                    inset.side,
                    stepY - size.height - 8,
                    style,
                    typeface = tf,
                )
        }
        if (widgetStyleRows[4].row.checkbox.isChecked) {
            val style = readStyle(widgetStyleRows[4])
            val size = QjsComponentBuilders.iconDataSize(4, StudioIconAlign.LEFT, style.fontSizePx)
            list +=
                QjsComponentBuilders.calorie(
                    ctx,
                    width - size.width - inset.side,
                    stepY - size.height - 8,
                    style,
                    typeface = tf,
                )
        }
        if (widgetStyleRows[5].row.checkbox.isChecked) {
            list +=
                QjsComponentBuilders.battery(
                    ctx,
                    inset.side,
                    inset.top,
                    readStyle(widgetStyleRows[5]),
                    typeface = tf,
                )
        }
        if (widgetStyleRows[6].row.checkbox.isChecked) {
            val style = readStyle(widgetStyleRows[6])
            val size = QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, style.fontSizePx)
            list +=
                QjsComponentBuilders.heartrate(
                    ctx,
                    width - size.width - inset.side,
                    inset.top,
                    style,
                    typeface = tf,
                )
        }
        if (widgetStyleRows[7].row.checkbox.isChecked) {
            val style = readStyle(widgetStyleRows[7])
            val size = QjsComponentBuilders.iconDataSize(3, StudioIconAlign.LEFT, style.fontSizePx)
            list +=
                QjsComponentBuilders.weather(
                    ctx,
                    width - size.width - inset.side,
                    height - size.height - inset.bottom,
                    style,
                    typeface = tf,
                )
        }
        if (binding.cbPointers.isChecked) {
            list +=
                QjsComponentBuilders.pointers(
                    context = ctx,
                    center = Point(width / 2, height / 2),
                    hourTint = parseWidgetColor(binding.etHourColor.text?.toString(), 0x00FF00),
                    minuteTint = parseWidgetColor(binding.etMinuteColor.text?.toString(), 0x0000FF),
                    secondTint = parseWidgetColor(binding.etSecondColor.text?.toString(), 0xA0FF55),
                    dotTint = parseWidgetColor(binding.etDotColor.text?.toString(), 0xFFFFFF),
                )
        }

        val link = 72
        if (binding.cbLinkPhone.isChecked) {
            list +=
                QjsComponentBuilders.appLink(
                    inset.link,
                    height / 2 - link / 2,
                    link,
                    link,
                    StudioLauncherType.PHONE,
                )
        }
        if (binding.cbLinkNotify.isChecked) {
            list +=
                QjsComponentBuilders.appLink(
                    width - link - inset.link,
                    height / 2 - link / 2,
                    link,
                    link,
                    StudioLauncherType.NOTIFICATIONS,
                )
        }
        if (binding.cbLinkActivity.isChecked) {
            list +=
                QjsComponentBuilders.appLink(
                    width / 2 - link / 2,
                    height - link - inset.link,
                    link,
                    link,
                    StudioLauncherType.ACTIVITY,
                )
        }
        if (binding.cbLinkWeather.isChecked) {
            list +=
                QjsComponentBuilders.appLink(
                    width / 2 - link / 2,
                    inset.link,
                    link,
                    link,
                    StudioLauncherType.WEATHER,
                )
        }
        return list
    }

    private fun parseWidgetColor(raw: String?, fallbackRgb: Int): Int {
        val fallback = fallbackRgb and 0xFFFFFF
        if (raw.isNullOrBlank()) return fallback
        val s = raw.trim().removePrefix("#")
        return try {
            when (s.length) {
                6 -> s.toInt(16) and 0xFFFFFF
                8 -> (s.toLong(16).toInt()) and 0xFFFFFF
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun toUiColor(rgbOrArgb: Int): Int = rgbOrArgb or 0xFF000000.toInt()

    private fun setBusy(value: Boolean, status: String) {
        busy = value
        binding.btnSync.isEnabled = !value
        binding.btnAlbum.isEnabled = !value
        binding.btnCamera.isEnabled = !value
        binding.progress.isVisible = value
        if (!value) binding.progress.progress = 0
        binding.tvStatus.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
