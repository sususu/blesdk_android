package com.huawo.nt.sdkdemo.ui.watchface

import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentJlCustomWatchfaceBinding
import com.huawo.nt.sdkdemo.util.WatchfaceImageUtils
import com.huawo.nt.sdkdemo.util.JLCustomWatchfacePackUtil
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.sdk.bluetoothsdk.interfaces.callback.IntValueCallback
import com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferCallback
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** jieli  custom-watchface sample*/
class JLCustomWatchfaceFragment : Fragment() {
    private var bindingRef: FragmentJlCustomWatchfaceBinding? = null
    private val binding get() = bindingRef!!
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgrounds = ArrayList<Bitmap>()
    private var maxImages = 1
    private var busy = false
    private var previewRefreshScheduled = false

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val images = uris.mapNotNull { uri ->
            runCatching { requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        addBackgrounds(images)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { addBackgrounds(listOf(it)) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = FragmentJlCustomWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinners()
        setupTextColorPicker()
        setupPreviewListeners()
        binding.btnAlbum.setOnClickListener { if (!busy) pickImages.launch("image/*") }
        binding.btnCamera.setOnClickListener { if (!busy) takePhoto.launch(null) }
        binding.btnClear.setOnClickListener { if (!busy) { backgrounds.clear(); renderBackgrounds() } }
        binding.btnStorage.setOnClickListener { queryStorage() }
        binding.btnInstall.setOnClickListener { install() }
        renderBackgrounds()
        binding.flPreview.post { refreshPreview() }
    }

    private fun setupSpinners() {
        binding.spinnerDisplayMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("单张显示", "顺序切换", "随机切换"))
        binding.spinnerPointerStyle.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("无指针", "指针样式 1", "指针样式 2", "指针样式 3"))
        val components = listOf("Disabled", "Time", "Date", "Heart rate", "Step", "Distance", "Calorie", "Active time", "Weather", "Sleep", "Battery")
        listOf(binding.spinnerComponent1, binding.spinnerComponent2, binding.spinnerComponent3, binding.spinnerComponent4).forEach { spinner ->
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, components)
        }
        binding.spinnerComponent1.setSelection(1)
        binding.spinnerComponent2.setSelection(2)
        binding.spinnerComponent3.setSelection(3)
        binding.spinnerComponent4.setSelection(4)
    }

    /** Uses the same opaque RGB picker as the Sifli custom-watchface text color field. */
    private fun setupTextColorPicker() {
        binding.etTextColor.isFocusable = false
        binding.etTextColor.isClickable = true
        binding.etTextColor.setOnClickListener { showTextColorPicker() }
    }

    private fun showTextColorPicker() {
        val target = bindingRef?.etTextColor ?: return
        ColorPickerDialog.Builder(requireContext())
            .setTitle(getString(R.string.cwf_pick_color))
            .setPreferenceName("CwfColorPicker_${target.id}")
            .setPositiveButton(
                getString(R.string.confirm),
                ColorEnvelopeListener { envelope: ColorEnvelope, _: Boolean ->
                    // A9 accepts RGB only; store the same #RRGGBB value used by CustomWatchfaceFragment.
                    val rgb = 0xFFFFFF and envelope.color
                    bindingRef?.etTextColor?.setText(String.format("#%06X", rgb))
                    Log.d(TAG, "text color selected rgb=#${String.format("%06X", rgb)}")
                    schedulePreviewRefresh()
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

    /** Keeps the sample preview aligned with the V10 fields without changing the OTA payload. */
    private fun setupPreviewListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = schedulePreviewRefresh()
        }
        listOf(
            binding.etWidth,
            binding.etHeight,
            binding.etTextColor,
            binding.etComponent1X,
            binding.etComponent1Y,
            binding.etComponent2X,
            binding.etComponent2Y,
            binding.etComponent3X,
            binding.etComponent3Y,
            binding.etComponent4X,
            binding.etComponent4Y,
        ).forEach { it.addTextChangedListener(watcher) }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                schedulePreviewRefresh()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        listOf(
            binding.spinnerPointerStyle,
            binding.spinnerComponent1,
            binding.spinnerComponent2,
            binding.spinnerComponent3,
            binding.spinnerComponent4,
        ).forEach { it.onItemSelectedListener = listener }
    }

    /** Queries watch storage before accepting additional backgrounds; the estimate is UI-only. */
    private fun queryStorage() {
        if (!BluetoothSDK.isConnected()) return showStatus(getString(R.string.owf_need_connected))
        Log.i(TAG, "queryStorage enter")
        BluetoothSDK.getDeviceWatchfaceAvailableStorage(object : IntValueCallback() {
            override fun onSuccess(value: Int) {
                mainHandler.post {
                    // reserve 100 KB, then 215 KB for every full image, max eight.
                    maxImages = ((value - 100) / 215).coerceIn(1, 8)
                    binding.tvStorage.text = getString(R.string.jl_cwf_storage_result, value, maxImages)
                    Log.i(TAG, "queryStorage success kb=$value maxImages=$maxImages")
                    if (backgrounds.size > maxImages) {
                        backgrounds.subList(maxImages, backgrounds.size).clear()
                        renderBackgrounds()
                    }
                }
            }

            override fun onFail(code: Int) {
                mainHandler.post { showStatus("[${code}] ${getString(R.string.jl_cwf_storage_failed)}") }
            }
        })
    }

    /** Enforces the last storage-derived image limit locally before any resource conversion begins. */
    private fun addBackgrounds(images: List<Bitmap>) {
        if (images.isEmpty()) return
        val slots = (maxImages - backgrounds.size).coerceAtLeast(0)
        if (slots == 0) {
            Toast.makeText(requireContext(), getString(R.string.jl_cwf_image_limit, maxImages), Toast.LENGTH_SHORT).show()
            return
        }
        backgrounds += images.take(slots)
        if (images.size > slots) Toast.makeText(requireContext(), getString(R.string.jl_cwf_image_limit, maxImages), Toast.LENGTH_SHORT).show()
        renderBackgrounds()
    }

    private fun renderBackgrounds() {
        binding.tvImages.text = getString(R.string.jl_cwf_images_selected, backgrounds.size, maxImages)
        binding.imgPreview.setImageBitmap(backgrounds.lastOrNull())
        schedulePreviewRefresh()
    }

    private fun schedulePreviewRefresh() {
        val currentBinding = bindingRef ?: return
        if (previewRefreshScheduled) return
        previewRefreshScheduled = true
        currentBinding.flPreview.post {
            previewRefreshScheduled = false
            if (bindingRef === currentBinding) refreshPreview()
        }
    }

    /**
     * Renders an approximate phone-side preview. Widget text and pointer artwork are owned by
     * watch firmware; this view only mirrors the selected wire type, color and coordinates.
     */
    private fun refreshPreview() {
        val currentBinding = bindingRef ?: return
        val watchWidth = intValue(currentBinding.etWidth.text?.toString(), 480).coerceAtLeast(1)
        val watchHeight = intValue(currentBinding.etHeight.text?.toString(), 480).coerceAtLeast(1)
        updatePreviewAspect(watchWidth, watchHeight)

        val uiColor = (parseColor(currentBinding.etTextColor.text?.toString()) ?: 0xFFFFFF) or -0x1000000
        val previews = listOf(
            Triple(currentBinding.spinnerComponent1, currentBinding.tvComponent1Preview, currentBinding.etComponent1X to currentBinding.etComponent1Y),
            Triple(currentBinding.spinnerComponent2, currentBinding.tvComponent2Preview, currentBinding.etComponent2X to currentBinding.etComponent2Y),
            Triple(currentBinding.spinnerComponent3, currentBinding.tvComponent3Preview, currentBinding.etComponent3X to currentBinding.etComponent3Y),
            Triple(currentBinding.spinnerComponent4, currentBinding.tvComponent4Preview, currentBinding.etComponent4X to currentBinding.etComponent4Y),
        )
        previews.forEach { (spinner, textView, _) ->
            val type = spinner.selectedItemPosition
            textView.text = COMPONENT_PREVIEW_TEXT.getOrElse(type) { "" }
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (type == 1) 20f else 12f)
            textView.setTextColor(uiColor)
            textView.isVisible = type != 0
        }
        renderPointerPreview(currentBinding.spinnerPointerStyle.selectedItemPosition)

        currentBinding.flPreview.post {
            if (bindingRef !== currentBinding) return@post
            val previewWidth = currentBinding.flPreview.width
            val previewHeight = currentBinding.flPreview.height
            if (previewWidth <= 0 || previewHeight <= 0) return@post
            val scaleX = previewWidth / watchWidth.toFloat()
            val scaleY = previewHeight / watchHeight.toFloat()
            previews.forEach { (_, textView, point) ->
                if (!textView.isVisible) return@forEach
                val x = intValue(point.first.text?.toString(), 0)
                val y = intValue(point.second.text?.toString(), 0)
                placePreview(textView, (x * scaleX).toInt(), (y * scaleY).toInt())
            }
            applyPreviewClip()
            Log.d(TAG, "preview refreshed size=${watchWidth}x$watchHeight widgets=${previews.count { it.second.isVisible }} pointer=${currentBinding.spinnerPointerStyle.selectedItemPosition}")
        }
    }

    private fun updatePreviewAspect(watchWidth: Int, watchHeight: Int) {
        val density = resources.displayMetrics.density
        val base = (220 * density).toInt()
        binding.flPreview.layoutParams = binding.flPreview.layoutParams.apply {
            width = base
            height = (base * watchHeight.toFloat() / watchWidth).toInt().coerceIn((80 * density).toInt(), (320 * density).toInt())
        }
    }

    private fun applyPreviewClip() {
        val preview = binding.flPreview
        preview.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(view.width, view.height) / 2f)
            }
        }
        preview.clipToOutline = true
    }

    private fun renderPointerPreview(style: Int) {
        val drawableRes = when (style) {
            1 -> R.drawable.wl02_pointer_style1
            2 -> R.drawable.wl02_pointer_style2
            3 -> R.drawable.wl02_pointer_style3
            else -> 0
        }
        binding.imgPointerPreview.isVisible = drawableRes != 0
        if (drawableRes == 0) {
            binding.imgPointerPreview.setImageDrawable(null)
        } else {
            binding.imgPointerPreview.setImageResource(drawableRes)
        }
    }

    private fun placePreview(view: View, x: Int, y: Int) {
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = x.coerceAtLeast(0)
        params.topMargin = y.coerceAtLeast(0)
        view.layoutParams = params
    }

    /**
     * Validates form values, converts media off the main thread, then lets BluetoothSDK run the
     * custom media phase followed by the terminal A9 configuration phase.
     */
    private fun install() {
        if (busy) return
        Log.i(TAG, "install enter backgrounds=${backgrounds.size} maxImages=$maxImages")
        if (!BluetoothSDK.isConnected()) return showStatus(getString(R.string.owf_need_connected))
        if (backgrounds.isEmpty()) return showStatus(getString(R.string.jl_cwf_need_background))
        val width = intValue(binding.etWidth.text?.toString(), 0)
        val height = intValue(binding.etHeight.text?.toString(), 0)
        val thumbW = intValue(binding.etThumbW.text?.toString(), 0)
        val thumbH = intValue(binding.etThumbH.text?.toString(), 0)
        if (width <= 0 || height <= 0 || thumbW <= 0 || thumbH <= 0) return showStatus(getString(R.string.jl_cwf_invalid_size))
        val elements = runCatching { selectedElements() }.getOrElse { return showStatus(it.message ?: getString(R.string.jl_cwf_invalid_widgets)) }
        val textColor = parseColor(binding.etTextColor.text?.toString()) ?: return showStatus(getString(R.string.jl_cwf_invalid_color))
        val source = backgrounds.toList()
        val scaled = source.map { WatchfaceImageUtils.scaledRoundedBitmap(it, width, height, width / 2f) }
        val thumbnail = WatchfaceImageUtils.scaledRoundedBitmap(source.last(), thumbW, thumbH, thumbW / 2f)
        val displayMode = binding.spinnerDisplayMode.selectedItemPosition + 1
        val pointerStyle = binding.spinnerPointerStyle.selectedItemPosition
        setBusy(true, getString(R.string.owf_installing))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pack = withContext(Dispatchers.IO) {
                    JLCustomWatchfacePackUtil.build(
                        requireContext().applicationContext,
                        JLCustomWatchfacePackUtil.Request(
                            scaled,
                            thumbnail,
                            displayMode,
                            pointerStyle,
                            textColor,
                            elements,
                        ),
                    )
                }
                Log.i(TAG, "install start files=${pack.files.size} elements=${pack.config.elementCount}")
                // Do not report success after media upload alone: SDK success follows the A9 ACK.
                BluetoothSDK.installWlCustomWatchfaceV10(pack.files, pack.config, object : WlMediaTransferCallback {
                    override fun onReady() {
                        mainHandler.post { showStatus(getString(R.string.owf_installing)) }
                    }

                    override fun onProgress(progress: Float) {
                        mainHandler.post { showStatus(getString(R.string.owf_installing_pct, (progress * 100).toInt().coerceIn(0, 100))) }
                    }

                    override fun onSuccess() {
                        mainHandler.post {
                            Log.i(TAG, "install success")
                            setBusy(false, getString(R.string.owf_install_ok))
                        }
                    }

                    override fun onFail(code: Int, message: String?) {
                        mainHandler.post {
                            val detail = message.orEmpty()
                            Log.e(TAG, "install failed code=$code msg=$detail")
                            setBusy(false, "[$code] $detail")
                        }
                    }
                })
            } catch (error: Exception) {
                Log.e(TAG, "install pack failed", error)
                setBusy(false, error.message ?: getString(R.string.owf_install_failed))
            }
        }
    }

    /** Converts the four UI rows into A9 wire types and absolute watch-canvas coordinates. */
    private fun selectedElements(): List<JLCustomWatchfacePackUtil.Element> {
        val rows = listOf(
            binding.spinnerComponent1 to (binding.etComponent1X.text?.toString() to binding.etComponent1Y.text?.toString()),
            binding.spinnerComponent2 to (binding.etComponent2X.text?.toString() to binding.etComponent2Y.text?.toString()),
            binding.spinnerComponent3 to (binding.etComponent3X.text?.toString() to binding.etComponent3Y.text?.toString()),
            binding.spinnerComponent4 to (binding.etComponent4X.text?.toString() to binding.etComponent4Y.text?.toString()),
        )
        val used = HashSet<Int>()
        return rows.mapNotNull { (spinner, point) ->
            val type = spinner.selectedItemPosition
            if (type == 0) return@mapNotNull null
            require(used.add(type)) { getString(R.string.jl_cwf_duplicate_widget) }
            val x = intValue(point.first, -1)
            val y = intValue(point.second, -1)
            require(x in 0..65535 && y in 0..65535) { getString(R.string.jl_cwf_invalid_widgets) }
            JLCustomWatchfacePackUtil.Element(type, x, y)
        }
    }

    /** Publishes busy state to the parent so the host cannot switch tabs during an active install. */
    private fun setBusy(value: Boolean, message: String) {
        busy = value
        binding.progress.isVisible = value
        binding.btnInstall.isEnabled = !value
        binding.btnAlbum.isEnabled = !value
        binding.btnCamera.isEnabled = !value
        parentFragment?.childFragmentManager?.setFragmentResult(JLWatchfaceFragment.TRANSFER_RESULT, Bundle().apply { putBoolean(JLWatchfaceFragment.TRANSFER_BUSY, value) })
        showStatus(message)
    }

    private fun showStatus(message: String) { binding.tvStatus.text = message }
    private fun intValue(value: String?, fallback: Int) = value?.toIntOrNull() ?: fallback
    private fun parseColor(value: String?): Int? = runCatching { android.graphics.Color.parseColor(value?.trim().orEmpty()) and 0xFFFFFF }.getOrNull()

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "JLCustomWatchface"
        val COMPONENT_PREVIEW_TEXT = listOf("", "12:00", "08/13", "72 bpm", "8,888", "6.2 km", "386 kcal", "45 min", "25 C", "7 h 30 m", "85%")
    }
}
