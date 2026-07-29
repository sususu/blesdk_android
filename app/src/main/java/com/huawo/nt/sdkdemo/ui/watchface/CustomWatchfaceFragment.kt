package com.huawo.nt.sdkdemo.ui.watchface

import android.app.Activity
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.huawo.imagepicker.ImagePicker
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentCustomWatchfaceBinding
import com.huawo.nt.sdkdemo.util.WatchfaceImageUtils
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.watchface.Callback
import com.huawo.watchface.custom.SifliCustomWatchface
import com.huawo.watchface.custom.widget.Date
import com.huawo.watchface.custom.widget.Dot
import com.huawo.watchface.custom.widget.HourPointer
import com.huawo.watchface.custom.widget.IconData
import com.huawo.watchface.custom.widget.MinutePointer
import com.huawo.watchface.custom.widget.SecondPointer
import com.huawo.watchface.custom.widget.Step
import com.huawo.watchface.custom.widget.Time
import com.huawo.watchface.custom.widget.WeatherTA
import com.huawo.watchface.custom.widget.Week
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.io.File

/**
 * Custom (Sifli) watchface editor — Tab "Custom" under Watchface.
 *
 * ## What this screen builds
 * A [SifliCustomWatchface] package that the watch can install:
 * - **Background bitmap** (optional): full-screen image at watch resolution, rounded by Corner.
 * - **Thumbnail bitmap**: small preview used in the watch's watchface picker; we capture the
 *   on-screen preview FrameLayout and scale it to Thumb W × Thumb H.
 * - **Widgets**: Time / Date / Week / Step / Weather / analog pointers + center Dot.
 *
 * ## Coordinate system (critical)
 * All X / Y / Width / Height / Corner values are in **watch pixels**, not Android dp.
 * Example: for a 466×466 round watch, Corner ≈ 233 means a full circle.
 * The phone preview is a scaled-down FrameLayout; we map watch → preview with
 * `sx = previewW / watchWidth`, `sy = previewH / watchHeight`.
 *
 * ## Empty / "auto" fields
 * Several X/Y fields use TextInputLayout `placeholderText="auto"`.
 * Empty text means the demo applies the same default placement as the reference SDK sample
 * (e.g. time centered horizontally, step near bottom). Do **not** put `android:hint` on the
 * EditText itself — that fights Material's floating label and looks like the label is inside
 * the box.
 *
 * ## Widget tint colors (important)
 * Pass **RGB only** (`0x00RRGGBB`), matching reference literals like `new Step(0xff0000)`.
 * Do **not** pass Android opaque ARGB (`0xFFrrggbb`): widget code gates tint with
 * `if (getTintColor() >= 0)`, and any alpha `0xFF` makes the signed int negative → tint skipped.
 * Phone preview Views still need ARGB via [toUiColor].
 *
 * ## Prerequisites before Sync
 * 1. [com.huawo.watchface.WatchfaceSDK] must be initialized (see [com.huawo.nt.sdkdemo.SdkDemoApp]).
 *    Otherwise `syncToWatch` asserts: "Please call WatchfaceSDK…init…".
 * 2. BLE connected and a non-blank MAC available via [BluetoothSDK].
 * 3. Prefer matching Width/Height/Corner to the real device panel (use size presets).
 *
 * ## Push pipeline (Sync button)
 * 1. Build [SifliCustomWatchface] with width/height.
 * 2. Optional: `setBackgroundImage(scaledRoundedBitmap(...))`.
 * 3. Snapshot preview → `setThumbnailImage(...)`.
 * 4. `addWidget(...)` for each enabled component (positions in watch pixels).
 * 5. `syncToWatch(mac, Callback)` → SDK zips assets → WatchfaceSDK file push to device.
 *
 * ## Preview vs real watch
 * Preview overlays (TextViews / pointer Views) are **approximate** for UI feedback.
 * Exact glyph sizes and fonts on device come from AAR widget assets (`Time`, `Date`, …).
 * Pointer angles in preview are static decorative rotations, not live clock hands.
 */
class CustomWatchfaceFragment : Fragment() {
    private var _binding: FragmentCustomWatchfaceBinding? = null
    private val binding get() = _binding!!

    /** Marshals WatchfaceSDK / BLE callbacks back to the UI thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Last background chosen via Album/Camera (decoded bitmap).
     * Cleared by "Clear". Sync re-scales and rounds this to Width×Height×Corner.
     */
    private var backgroundBitmap: Bitmap? = null

    /** True while zip/push is in progress — disables Sync / pickers to avoid re-entry. */
    private var busy = false

    /**
     * Coalesces rapid TextWatcher / checkbox events into one preview refresh per frame.
     * Without this, typing "466" would refresh three times and thrash layout.
     */
    private var refreshScheduled = false

    /**
     * Common Sifli panel presets: width, height, corner, thumbnail size, thumb corner.
     * Corner ≈ half of the smaller side for round dials; rectangular dials use a smaller radius.
     */
    private val sizePresets =
        listOf(
            SizePreset("466 x 466", 466, 466, 233, 264, 264, 132),
            SizePreset("480 x 480", 480, 480, 240, 264, 264, 132),
            SizePreset("410 x 502", 410, 502, 108, 200, 244, 50),
        )

    /**
     * Result of [ImagePicker] Activity.
     * - RESULT_OK + data Uri → decode as background.
     * - [ImagePicker.RESULT_ERROR] → show SDK error string (permission / crop failure, etc.).
     * User cancel is ignored silently.
     */
    private val pickImageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            when {
                result.resultCode == Activity.RESULT_OK && data?.data != null -> {
                    loadBackgroundFromUri(data.data!!)
                }
                result.resultCode == ImagePicker.RESULT_ERROR -> {
                    Toast.makeText(
                        requireContext(),
                        ImagePicker.getError(data),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSizeSpinner()
        setupAlignSpinner()
        setupColorPickers()
        setupPreviewListeners()
        binding.tvStatus.setText(R.string.feature_ready)

        binding.btnAlbum.setOnClickListener { pickBackgroundFromAlbum() }
        binding.btnCamera.setOnClickListener { pickBackgroundFromCamera() }
        binding.btnClearBg.setOnClickListener {
            backgroundBitmap = null
            binding.imgBg.setImageDrawable(null)
            schedulePreviewRefresh()
        }
        binding.btnSync.setOnClickListener { syncToWatch() }

        // First layout pass may have width=0; post so corner clip + widget layout see real size.
        binding.flPreview.post { refreshPreview() }
    }

    /** Fills Width/Height/Corner/Thumb fields from a panel preset when the spinner changes. */
    private fun setupSizeSpinner() {
        binding.spinnerSizePreset.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                sizePresets.map { it.label },
            )
        binding.spinnerSizePreset.setSelection(0)
        binding.spinnerSizePreset.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    applyPreset(sizePresets[position])
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    /**
     * Step widget icon alignment relative to the step number (SDK [IconData.Alignment]).
     * Index 0 = RIGHT (default in reference demo).
     */
    private fun setupAlignSpinner() {
        val labels =
            listOf(
                getString(R.string.cwf_align_right),
                getString(R.string.cwf_align_left),
                getString(R.string.cwf_align_top),
                getString(R.string.cwf_align_bottom),
            )
        binding.spinnerStepAlign.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            )
        binding.spinnerStepAlign.setSelection(0)
    }

    /**
     * Any geometry / color / checkbox change should update the live preview.
     * Color EditTexts are non-focusable (picker only) but still fire TextWatcher when setText.
     */
    private fun setupPreviewListeners() {
        val watcher =
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                    Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) = schedulePreviewRefresh()
            }
        listOf(
            binding.etWidth,
            binding.etHeight,
            binding.etCorner,
            binding.etTimeColor,
            binding.etTimeX,
            binding.etTimeY,
            binding.etStepColor,
            binding.etStepX,
            binding.etStepY,
            binding.etWeatherColor,
            binding.etWeatherX,
            binding.etWeatherY,
            binding.etHourColor,
            binding.etMinuteColor,
            binding.etSecondColor,
            binding.etDotColor,
        ).forEach { it.addTextChangedListener(watcher) }

        val checkListener = { _: android.widget.CompoundButton, _: Boolean ->
            schedulePreviewRefresh()
        }
        binding.cbDate.setOnCheckedChangeListener(checkListener)
        binding.cbWeek.setOnCheckedChangeListener(checkListener)
        binding.cbStep.setOnCheckedChangeListener(checkListener)
        binding.cbWeather.setOnCheckedChangeListener(checkListener)
        binding.cbPointers.setOnCheckedChangeListener(checkListener)
    }

    /**
     * Wire hex field + color swatch to [ColorPickerDialog].
     * Swatch shows the current RGB so users need not parse `#RRGGBB` mentally.
     */
    private fun setupColorPickers() {
        bindColorPicker(binding.etTimeColor, binding.swatchTime) { refreshPreview() }
        bindColorPicker(binding.etStepColor, binding.swatchStep) { refreshPreview() }
        bindColorPicker(binding.etWeatherColor, binding.swatchWeather) { refreshPreview() }
        bindColorPicker(binding.etHourColor, binding.swatchHour) { refreshPreview() }
        bindColorPicker(binding.etMinuteColor, binding.swatchMinute) { refreshPreview() }
        bindColorPicker(binding.etSecondColor, binding.swatchSecond) { refreshPreview() }
        bindColorPicker(binding.etDotColor, binding.swatchDot) { refreshPreview() }
        updateAllSwatches()
    }

    /**
     * Color fields are picker-driven (not free typing) to avoid invalid hex strings.
     * Both the EditText and the swatch open the same dialog.
     */
    private fun bindColorPicker(
        edit: TextInputEditText,
        swatch: View,
        onPicked: (() -> Unit)? = null,
    ) {
        edit.isFocusable = false
        edit.isClickable = true
        val open = View.OnClickListener {
            showColorPicker(edit, swatch) { onPicked?.invoke() }
        }
        edit.setOnClickListener(open)
        swatch.setOnClickListener(open)
    }

    /**
     * Skydoves [ColorPickerDialog].
     * - Alpha slide bar off: widgets use opaque RGB; alpha is not part of the watchface color API here.
     * - Preference name is per-view so each field can remember last hue independently.
     * Stored text is `#RRGGBB` (6 hex digits); sync path forces opaque via [parseColor].
     */
    private fun showColorPicker(
        target: TextInputEditText,
        swatch: View,
        onPicked: (() -> Unit)? = null,
    ) {
        ColorPickerDialog.Builder(requireContext())
            .setTitle(getString(R.string.cwf_pick_color))
            .setPreferenceName("CwfColorPicker_${target.id}")
            .setPositiveButton(
                getString(R.string.confirm),
                ColorEnvelopeListener { envelope: ColorEnvelope, _: Boolean ->
                    // Store RGB only (#RRGGBB). Widget tint APIs expect 0x00RRGGBB;
                    // ARGB with alpha 0xFF is a negative signed int and skips tinting.
                    val rgb = 0xFFFFFF and envelope.color
                    target.setText(String.format("#%06X", rgb))
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

    /** Sync all swatch Views from their companion hex EditTexts (e.g. after preset / restore). */
    private fun updateAllSwatches() {
        applySwatchColor(binding.swatchTime, parseColor(binding.etTimeColor.text?.toString(), Color.WHITE))
        applySwatchColor(binding.swatchStep, parseColor(binding.etStepColor.text?.toString(), 0xFF0000))
        applySwatchColor(
            binding.swatchWeather,
            parseColor(binding.etWeatherColor.text?.toString(), 0xFFFFFF),
        )
        applySwatchColor(binding.swatchHour, parseColor(binding.etHourColor.text?.toString(), 0x00FF00))
        applySwatchColor(
            binding.swatchMinute,
            parseColor(binding.etMinuteColor.text?.toString(), 0x0000FF),
        )
        applySwatchColor(
            binding.swatchSecond,
            parseColor(binding.etSecondColor.text?.toString(), 0xA0FF55),
        )
        applySwatchColor(binding.swatchDot, parseColor(binding.etDotColor.text?.toString(), 0xFFFFFF))
    }

    /** Fills the swatch with opaque ARGB; keeps a light stroke so white-on-white stays visible. */
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
        // Swatch needs opaque ARGB for Android drawing; [color] may be RGB-only.
        gd.setColor(toUiColor(color))
        swatch.background = gd
    }

    /** Writes preset numbers into the form and refreshes preview aspect + clip. */
    private fun applyPreset(preset: SizePreset) {
        binding.etWidth.setText(preset.width.toString())
        binding.etHeight.setText(preset.height.toString())
        binding.etCorner.setText(preset.corner.toString())
        binding.etThumbW.setText(preset.thumbW.toString())
        binding.etThumbH.setText(preset.thumbH.toString())
        binding.etThumbCorner.setText(preset.thumbCorner.toString())
        updatePreviewAspect(preset.width, preset.height)
        binding.flPreview.post { refreshPreview() }
    }

    /**
     * Keeps preview FrameLayout aspect = watch Width:Height.
     * Base edge is 220dp so the preview stays readable on phones without dominating the form.
     */
    private fun updatePreviewAspect(width: Int, height: Int) {
        val density = resources.displayMetrics.density
        val basePx = (220 * density).toInt()
        val lp = binding.flPreview.layoutParams
        lp.width = basePx
        lp.height =
            if (width == height) {
                basePx
            } else {
                (basePx * height.toFloat() / width.toFloat()).toInt()
            }
        binding.flPreview.layoutParams = lp
    }

    /** Debounce: at most one [refreshPreview] queued on the next message-loop pass. */
    private fun schedulePreviewRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        binding.flPreview.post {
            refreshScheduled = false
            refreshPreview()
        }
    }

    /**
     * Rebuild live preview: colors, visibility from checkboxes, corner clip, widget placement.
     *
     * Layout of overlays runs in a nested `post` because aspect-ratio change may not have
     * produced final width/height until the next layout.
     */
    private fun refreshPreview() {
        if (_binding == null) return
        val width = intOr(binding.etWidth, 466).coerceAtLeast(1)
        val height = intOr(binding.etHeight, 466).coerceAtLeast(1)
        val corner = intOr(binding.etCorner, width / 2).coerceAtLeast(0)
        updatePreviewAspect(width, height)
        applyPreviewCornerClip(width, corner)

        val timeColor = parseColor(binding.etTimeColor.text?.toString(), Color.WHITE)
        val stepColor = parseColor(binding.etStepColor.text?.toString(), 0xFF0000)
        val weatherColor = parseColor(binding.etWeatherColor.text?.toString(), 0xFFFFFF)
        val hourColor = parseColor(binding.etHourColor.text?.toString(), 0x00FF00)
        val minuteColor = parseColor(binding.etMinuteColor.text?.toString(), 0x0000FF)
        val secondColor = parseColor(binding.etSecondColor.text?.toString(), 0xA0FF55)
        val dotColor = parseColor(binding.etDotColor.text?.toString(), 0xFFFFFF)
        updateAllSwatches()

        // Preview Views need opaque ARGB; widget constructors get RGB via [parseWidgetColor].
        binding.tvTimePreview.setTextColor(toUiColor(timeColor))
        binding.tvDatePreview.setTextColor(toUiColor(timeColor))
        binding.tvWeekPreview.setTextColor(toUiColor(timeColor))
        binding.tvStepPreview.setTextColor(toUiColor(stepColor))
        binding.tvWeatherPreview.setTextColor(toUiColor(weatherColor))
        binding.viewPointerHour.setBackgroundColor(toUiColor(hourColor))
        binding.viewPointerMinute.setBackgroundColor(toUiColor(minuteColor))
        binding.viewPointerSecond.setBackgroundColor(toUiColor(secondColor))
        binding.viewCenterDot.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(toUiColor(dotColor))
            }

        binding.tvDatePreview.isVisible = binding.cbDate.isChecked
        binding.tvWeekPreview.isVisible = binding.cbWeek.isChecked
        binding.tvStepPreview.isVisible = binding.cbStep.isChecked
        binding.tvWeatherPreview.isVisible = binding.cbWeather.isChecked
        val pointers = binding.cbPointers.isChecked
        binding.viewPointerHour.isVisible = pointers
        binding.viewPointerMinute.isVisible = pointers
        binding.viewPointerSecond.isVisible = pointers
        binding.viewCenterDot.isVisible = pointers

        binding.flPreview.post {
            if (_binding == null) return@post
            layoutPreviewWidgets(width, height)
            applyPreviewCornerClip(width, corner)
        }
    }

    /**
     * Clips the preview container to a round-rect matching watch Corner.
     *
     * Formula: `radiusPreview = cornerWatchPx * previewWidth / watchWidth`
     * so a 233px corner on a 466px dial becomes a semicircle on the preview as well.
     *
     * Note: this only affects the **phone UI**. The bitmap sent to the watch is rounded again
     * in [WatchfaceImageUtils.scaledRoundedBitmap] using the watch-pixel corner.
     */
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

    /**
     * Positions preview overlays to mirror [syncToWatch] placement rules (scaled to preview).
     *
     * Defaults when X/Y are empty (same spirit as reference CustomWatchfaceFragment):
     * - Time X → horizontal center; Time Y → 60 (watch px)
     * - Date + Week → one row under time, 10px gap, centered as a group
     * - Step → X=100; Y = height - stepH - 70
     * - Weather → X = width - weatherW - 100; Y = height - weatherH - 40
     * - Pointers → dial center
     */
    private fun layoutPreviewWidgets(watchWidth: Int, watchHeight: Int) {
        val previewW = binding.flPreview.width
        val previewH = binding.flPreview.height
        if (previewW <= 0 || previewH <= 0) return
        val sx = previewW / watchWidth.toFloat()
        val sy = previewH / watchHeight.toFloat()

        val timeY = intOrNull(binding.etTimeY) ?: 60
        binding.tvTimePreview.measure(0, 0)
        val timeW = binding.tvTimePreview.measuredWidth
        val timeH = binding.tvTimePreview.measuredHeight
        val timeX =
            intOrNull(binding.etTimeX)?.let { (it * sx).toInt() }
                ?: ((previewW - timeW) / 2)
        place(binding.tvTimePreview, timeX, (timeY * sy).toInt())

        val rowY = (timeY * sy).toInt() + timeH + (10 * sy).toInt()
        binding.tvDatePreview.measure(0, 0)
        binding.tvWeekPreview.measure(0, 0)
        val dateW = if (binding.cbDate.isChecked) binding.tvDatePreview.measuredWidth else 0
        val weekW = if (binding.cbWeek.isChecked) binding.tvWeekPreview.measuredWidth else 0
        var rowX = (previewW - dateW - weekW) / 2
        if (binding.cbDate.isChecked) {
            place(binding.tvDatePreview, rowX, rowY)
            rowX += dateW
        }
        if (binding.cbWeek.isChecked) {
            place(binding.tvWeekPreview, rowX, rowY)
        }

        if (binding.cbStep.isChecked) {
            binding.tvStepPreview.measure(0, 0)
            val stepH = binding.tvStepPreview.measuredHeight
            val stepX = (intOr(binding.etStepX, 100) * sx).toInt()
            val stepY =
                intOrNull(binding.etStepY)?.let { (it * sy).toInt() }
                    ?: (previewH - stepH - (70 * sy).toInt())
            place(binding.tvStepPreview, stepX, stepY)
        }

        if (binding.cbWeather.isChecked) {
            binding.tvWeatherPreview.measure(0, 0)
            val weatherW = binding.tvWeatherPreview.measuredWidth
            val weatherH = binding.tvWeatherPreview.measuredHeight
            val weatherX =
                intOrNull(binding.etWeatherX)?.let { (it * sx).toInt() }
                    ?: (previewW - weatherW - (100 * sx).toInt())
            val weatherY =
                intOrNull(binding.etWeatherY)?.let { (it * sy).toInt() }
                    ?: (previewH - weatherH - (40 * sy).toInt())
            place(binding.tvWeatherPreview, weatherX, weatherY)
        }

        if (binding.cbPointers.isChecked) {
            val cx = previewW / 2
            val cy = previewH / 2
            placePivotBottom(binding.viewPointerHour, cx, cy)
            placePivotBottom(binding.viewPointerMinute, cx, cy)
            placePivotBottom(binding.viewPointerSecond, cx, cy)
            place(
                binding.viewCenterDot,
                cx - binding.viewCenterDot.layoutParams.width / 2,
                cy - binding.viewCenterDot.layoutParams.height / 2,
            )
        }
    }

    /** Absolute top-left placement inside [binding.flPreview] (preview pixels). */
    private fun place(view: View, x: Int, y: Int) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = x.coerceAtLeast(0)
        lp.topMargin = y.coerceAtLeast(0)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        view.layoutParams = lp
    }

    /**
     * Places a "hand" View so its bottom-center sits on the dial center, then sets pivot
     * at that tip so XML `android:rotation` rotates around the center like a real hand.
     */
    private fun placePivotBottom(view: View, centerX: Int, centerY: Int) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        lp.width = view.layoutParams.width
        lp.height = view.layoutParams.height
        lp.leftMargin = centerX - lp.width / 2
        lp.topMargin = centerY - lp.height
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        view.layoutParams = lp
        view.pivotX = lp.width / 2f
        view.pivotY = lp.height.toFloat()
    }

    /** Current Width / Height / Corner from the form (watch pixels). Used by ImagePicker crop. */
    private fun currentWatchSize(): Triple<Int, Int, Int> {
        val width = intOr(binding.etWidth, 466)
        val height = intOr(binding.etHeight, 466)
        val corner = intOr(binding.etCorner, width / 2)
        return Triple(width, height, corner)
    }

    /**
     * Gallery → crop UI → compress → forced output size matching the watch panel.
     *
     * [ImagePicker.fixedResultSize] width/height/corner must match the dial so the returned
     * bitmap is already panel-shaped; Sync still re-applies rounding for safety.
     *
     * `compress(2048)` = target max size in KB (library multiplies by 1024 internally).
     */
    private fun pickBackgroundFromAlbum() {
        val (width, height, corner) = currentWatchSize()
        ImagePicker.with(this)
            .crop()
            .galleryOnly()
            .compress(2048)
            .fixedResultSize(width, height, corner)
            .createIntent { intent ->
                pickImageResult.launch(intent)
            }
    }

    /** Same as album path but camera-only. Requires CAMERA permission (handled inside ImagePicker). */
    private fun pickBackgroundFromCamera() {
        val (width, height, corner) = currentWatchSize()
        ImagePicker.with(this)
            .crop()
            .cameraOnly()
            .compress(2048)
            .fixedResultSize(width, height, corner)
            .createIntent { intent ->
                pickImageResult.launch(intent)
            }
    }

    /** Decode picker Uri into [backgroundBitmap] and show it under the preview overlays. */
    private fun loadBackgroundFromUri(uri: Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                backgroundBitmap = bmp
                binding.imgBg.setImageBitmap(bmp)
                schedulePreviewRefresh()
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                e.message ?: e.javaClass.simpleName,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Build package + push to the connected watch.
     *
     * ### Field meanings (watch pixels unless noted)
     * | Field | Meaning |
     * |-------|---------|
     * | Width / Height | Dial resolution of [SifliCustomWatchface] |
     * | Corner | Round-rect radius for background (half min side ≈ circle) |
     * | Thumb W/H/Corner | Thumbnail asset size shown in watch's face list |
     * | Name | Watchface display name string inside the package |
     * | Time color | Color for Time, and also Date/Week when enabled |
     * | Time X/Y | Top-left of Time widget; empty X → centered |
     * | Step / Weather colors & XY | Per-widget paint and placement |
     * | Pointer / Dot colors | Analog hands + center pin |
     *
     * ### Callback notes
     * - [Callback.onZipCompleted]: local zip ready; BLE transfer may start next.
     * - [Callback.onProgress]: file-push bytes; may be called off main thread → post to UI.
     * - [Callback.onError]: SDK error code (see Watchface / Sifli docs); not HTTP status.
     *
     * Thumbnail is taken from the live preview FrameLayout so what the user sees is close to
     * what appears in the watch picker (still approximate fonts/icons).
     */
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

        val width = intOr(binding.etWidth, 466)
        val height = intOr(binding.etHeight, 466)
        val corner = intOr(binding.etCorner, width / 2).toFloat()
        val thumbW = intOr(binding.etThumbW, 264)
        val thumbH = intOr(binding.etThumbH, 264)
        val thumbCorner = intOr(binding.etThumbCorner, thumbW / 2).toFloat()
        val name = binding.etName.text?.toString()?.trim().orEmpty().ifBlank { "custom" }
        // Must be 0x00RRGGBB (see [parseWidgetColor]) — same as reference `new Step(0xff0000)`.
        val timeColor = parseWidgetColor(binding.etTimeColor.text?.toString(), 0xFFFFFF)

        // Ensure overlays are laid out before we screenshot the preview for the thumbnail.
        refreshPreview()
        setBusy(true, getString(R.string.cwf_making))
        try {
            val watchface = SifliCustomWatchface(width, height)
            watchface.setName(name)

            backgroundBitmap?.let { src ->
                // Scale + round in watch pixels. If no background, watch may show solid/default.
                watchface.setBackgroundImage(
                    WatchfaceImageUtils.scaledRoundedBitmap(src, width, height, corner),
                )
            }

            binding.flPreview.post {
                try {
                    val preview = WatchfaceImageUtils.frameLayoutToBitmap(binding.flPreview)
                    val thumbnail =
                        WatchfaceImageUtils.scaledRoundedBitmap(
                            preview,
                            thumbW,
                            thumbH,
                            thumbCorner,
                        )
                    watchface.setThumbnailImage(thumbnail)

                    // --- Time (always added) ---
                    val time = Time(timeColor)
                    val timeY = intOrNull(binding.etTimeY) ?: 60
                    val timeX =
                        intOrNull(binding.etTimeX)
                            ?: ((width - time.width) / 2)
                    time.x = timeX
                    time.y = timeY
                    watchface.addWidget(time)

                    // --- Date + Week: one centered row under Time ---
                    if (binding.cbDate.isChecked || binding.cbWeek.isChecked) {
                        val rowY = timeY + time.height + 10
                        var date: Date? = null
                        var week: Week? = null
                        if (binding.cbDate.isChecked) {
                            date = Date(timeColor).also { watchface.addWidget(it) }
                        }
                        if (binding.cbWeek.isChecked) {
                            week = Week(timeColor).also { watchface.addWidget(it) }
                        }
                        val rowWidth = (date?.width ?: 0) + (week?.width ?: 0)
                        var x = (width - rowWidth) / 2
                        date?.let {
                            it.x = x
                            it.y = rowY
                            x += it.width
                        }
                        week?.let {
                            it.x = x
                            it.y = rowY
                        }
                    }

                    // --- Step count widget ---
                    if (binding.cbStep.isChecked) {
                        val step =
                            Step(parseWidgetColor(binding.etStepColor.text?.toString(), 0xFF0000))
                        step.x = intOr(binding.etStepX, 100)
                        step.y =
                            intOrNull(binding.etStepY)
                                ?: (height - step.height - 70)
                        step.setIconAlign(selectedStepAlign())
                        watchface.addWidget(step)
                    }

                    // --- Weather (temperature + icon style WeatherTA) ---
                    if (binding.cbWeather.isChecked) {
                        val weather =
                            WeatherTA(
                                parseWidgetColor(binding.etWeatherColor.text?.toString(), 0xFFFFFF),
                            )
                        weather.x =
                            intOrNull(binding.etWeatherX)
                                ?: (width - weather.width - 100)
                        weather.y =
                            intOrNull(binding.etWeatherY)
                                ?: (height - weather.height - 40)
                        watchface.addWidget(weather)
                    }

                    // --- Analog pointers share one center Point ---
                    if (binding.cbPointers.isChecked) {
                        val center = Point(width / 2, height / 2)
                        HourPointer(
                            parseWidgetColor(binding.etHourColor.text?.toString(), 0x00FF00),
                        ).also {
                            it.center = center
                            watchface.addWidget(it)
                        }
                        MinutePointer(
                            parseWidgetColor(binding.etMinuteColor.text?.toString(), 0x0000FF),
                        ).also {
                            it.center = center
                            watchface.addWidget(it)
                        }
                        SecondPointer(
                            parseWidgetColor(binding.etSecondColor.text?.toString(), 0xA0FF55),
                        ).also {
                            it.center = center
                            watchface.addWidget(it)
                        }
                        Dot(parseWidgetColor(binding.etDotColor.text?.toString(), 0xFFFFFF)).also {
                            it.center = center
                            watchface.addWidget(it)
                        }
                    }

                    // Triggers WatchfaceSDK.setCustomWatchface → zip → Sifli file push.
                    watchface.syncToWatch(
                        mac,
                        object : Callback {
                            override fun onZipCompleted(zip: File?) {
                                mainHandler.post {
                                    setBusy(true, getString(R.string.cwf_syncing))
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
                                        getString(R.string.cwf_syncing_pct, pct)
                                }
                            }

                            override fun onSuccess() {
                                mainHandler.post {
                                    setBusy(false, getString(R.string.cwf_sync_ok))
                                    Toast.makeText(
                                        requireContext(),
                                        R.string.cwf_sync_ok,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }

                            override fun onError(code: Int) {
                                mainHandler.post {
                                    setBusy(false, getString(R.string.cwf_sync_fail, code))
                                }
                            }
                        },
                    )
                } catch (e: Exception) {
                    setBusy(
                        false,
                        e.message.orEmpty().ifBlank { e.javaClass.simpleName },
                    )
                }
            }
        } catch (e: Exception) {
            setBusy(false, e.message.orEmpty().ifBlank { e.javaClass.simpleName })
        }
    }

    /** Maps spinner index → SDK enum for the step icon relative to the number. */
    private fun selectedStepAlign(): IconData.Alignment =
        when (binding.spinnerStepAlign.selectedItemPosition) {
            1 -> IconData.Alignment.LEFT
            2 -> IconData.Alignment.TOP
            3 -> IconData.Alignment.BOTTOM
            else -> IconData.Alignment.RIGHT
        }

    /** Locks UI while packaging / transferring; clears progress when idle. */
    private fun setBusy(value: Boolean, status: String) {
        busy = value
        binding.btnSync.isEnabled = !value
        binding.btnAlbum.isEnabled = !value
        binding.btnCamera.isEnabled = !value
        binding.progress.isVisible = value
        if (!value) binding.progress.progress = 0
        binding.tvStatus.text = status
    }

    /** Parse int or [default] when blank / invalid. */
    private fun intOr(edit: TextInputEditText, default: Int): Int =
        edit.text?.toString()?.trim()?.toIntOrNull() ?: default

    /** Parse int or null when blank — used for "auto" placement fields. */
    private fun intOrNull(edit: TextInputEditText): Int? =
        edit.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /**
     * Parse UI / widget color text into **RGB only** (`0x00RRGGBB`).
     *
     * Sifli widget tint uses `if (getTintColor() >= 0) changeBitmapColor(...)`.
     * Default tint is `-1` (disabled). Any ARGB with alpha `0xFF` sets bit 31, so the
     * signed int is **negative** and tinting is skipped — colors appear unchanged.
     * Reference demo therefore passes literals like `0xff0000` / `0xffffff` (no alpha byte).
     *
     * Accepts `#RRGGBB`, `RRGGBB`, or 8-digit AARRGGBB (alpha discarded).
     */
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

    /** Alias used by preview / swatches — same RGB semantics as [parseWidgetColor]. */
    private fun parseColor(raw: String?, fallbackRgb: Int): Int = parseWidgetColor(raw, fallbackRgb)

    /** Android View APIs need opaque ARGB; OR in full alpha without changing RGB. */
    private fun toUiColor(rgbOrArgb: Int): Int = rgbOrArgb or 0xFF000000.toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * One row in the size spinner.
     * @property corner Round-rect radius in watch pixels for the full-size background.
     * @property thumbW / [thumbH] / [thumbCorner] Thumbnail asset metrics (often ~half dial).
     */
    private data class SizePreset(
        val label: String,
        val width: Int,
        val height: Int,
        val corner: Int,
        val thumbW: Int,
        val thumbH: Int,
        val thumbCorner: Int,
    )
}
