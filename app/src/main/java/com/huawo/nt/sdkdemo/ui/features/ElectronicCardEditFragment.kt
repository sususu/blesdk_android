package com.huawo.nt.sdkdemo.ui.features

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.huawo.imagepicker.ImagePicker
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentElectronicCardEditBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.util.QrCodeCodec
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Create or update one electronic card: name plus a QR payload of at most 159 UTF-8 bytes. */
class ElectronicCardEditFragment : Fragment() {
    private var _binding: FragmentElectronicCardEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ElectronicCardEditViewModel by viewModels { ViewModelFactory() }
    private var qrJob: Job? = null
    private var renderedQr: String? = null

    private val scanLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val text = result.contents
            if (!text.isNullOrEmpty()) acceptQr(text)
        }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScan() else toast(R.string.ecard_camera_denied)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            when {
                result.resultCode == android.app.Activity.RESULT_OK && data?.data != null ->
                    decodeImage(data.data!!)
                result.resultCode == ImagePicker.RESULT_ERROR ->
                    toastText(ImagePicker.getError(data))
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentElectronicCardEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cardId = arguments?.getInt(ARG_ID, -1)?.takeIf { it > 0 }
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.toolbar.inflateMenu(R.menu.menu_electronic_card_edit)
        binding.toolbar.menu.findItem(R.id.action_delete_card)?.isVisible = cardId != null
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_delete_card) {
                confirmDelete()
                true
            } else {
                false
            }
        }
        binding.qrContainer.setOnClickListener { if (!viewModel.uiState.value.busy) chooseQrSource() }
        binding.nameRow.setOnClickListener { if (!viewModel.uiState.value.busy) editName() }
        binding.btnSave.setOnClickListener { viewModel.save() }
        viewModel.start(cardId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch {
                    viewModel.close.collect {
                        if (isAdded) parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }

    private fun render(state: ElectronicCardEditViewModel.State) {
        val named = state.name.isNotBlank()
        binding.nameText.text = if (named) state.name else getString(R.string.ecard_name_hint)
        binding.nameText.setTextColor(
            resources.getColor(if (named) R.color.home_text else R.color.home_secondary, requireContext().theme),
        )
        binding.btnSave.isEnabled = !state.busy && named && !state.value.isNullOrEmpty()
        binding.toolbar.menu.findItem(R.id.action_delete_card)?.isEnabled = !state.busy
        binding.statusProgress.isVisible = state.busy
        binding.statusText.isVisible = state.message.isNotBlank()
        binding.statusText.text = state.message
        renderQr(state.value)
    }

    private fun renderQr(value: String?) {
        if (value == renderedQr) return
        renderedQr = value
        qrJob?.cancel()
        if (value.isNullOrEmpty()) {
            binding.qrImage.setImageDrawable(null)
            binding.qrImage.isVisible = false
            binding.qrPlaceholder.isVisible = true
            return
        }
        binding.qrPlaceholder.isVisible = false
        binding.qrImage.isVisible = true
        val size = (160 * resources.displayMetrics.density).toInt()
        qrJob =
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.Default) { QrCodeCodec.encode(value, size) }
                if (_binding != null && viewModel.uiState.value.value == value) {
                    binding.qrImage.setImageBitmap(bitmap)
                }
            }
    }

    private fun chooseQrSource() {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(
                arrayOf(getString(R.string.ecard_scan), getString(R.string.ecard_pick_image)),
            ) { _, which ->
                if (which == 0) startScan() else pickGallery()
            }
            .show()
    }

    private fun startScan() {
        val granted =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) launchScan() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchScan() {
        val options =
            ScanOptions()
                .setCaptureActivity(QrCaptureActivity::class.java)
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.ecard_scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        scanLauncher.launch(options)
    }

    private fun pickGallery() {
        ImagePicker.with(this).galleryOnly().createIntent { pickImage.launch(it) }
    }

    private fun decodeImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text =
                try {
                    withContext(Dispatchers.IO) { QrCodeCodec.decode(requireContext().applicationContext, uri) }
                } catch (_: Exception) {
                    null
                }
            if (!isAdded) return@launch
            if (text.isNullOrEmpty()) toast(R.string.ecard_code_not_found) else acceptQr(text)
        }
    }

    private fun acceptQr(text: String) {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_VALUE_BYTES) {
            toast(R.string.ecard_code_unsupported)
            return
        }
        viewModel.setValue(text)
    }

    private fun editName() {
        val input =
            EditText(requireContext()).apply {
                setText(viewModel.uiState.value.name)
                setSelection(text.length)
                filters = arrayOf(Utf8MaxBytesFilter(MAX_NAME_BYTES))
                setSingleLine(true)
            }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container =
            FrameLayout(requireContext()).apply {
                setPadding(pad, pad / 2, pad, 0)
                addView(
                    input,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ecard_edit_name)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ -> viewModel.setName(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        if (viewModel.uiState.value.id == null || viewModel.uiState.value.busy) return
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.ecard_delete_confirm)
            .setPositiveButton(R.string.ecard_delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    private fun toastText(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        qrJob?.cancel()
        qrJob = null
        renderedQr = null
        _binding = null
        super.onDestroyView()
    }

    private class Utf8MaxBytesFilter(private val maxBytes: Int) : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int,
        ): CharSequence? {
            val prefix = dest.subSequence(0, dstart).toString()
            val suffix = dest.subSequence(dend, dest.length).toString()
            val incoming = source.subSequence(start, end).toString()
            if ((prefix + incoming + suffix).toByteArray(Charsets.UTF_8).size <= maxBytes) return null
            val room = maxBytes - (prefix + suffix).toByteArray(Charsets.UTF_8).size
            if (room <= 0) return ""
            var count = 0
            var bytes = 0
            while (count < incoming.length) {
                val codePoint = incoming.codePointAt(count)
                val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
                if (bytes + size > room) break
                bytes += size
                count += Character.charCount(codePoint)
            }
            return if (count == 0) "" else incoming.substring(0, count)
        }
    }

    companion object {
        private const val ARG_ID = "id"
        private const val MAX_NAME_BYTES = 40
        private const val MAX_VALUE_BYTES = 159

        fun newInstance(id: Int? = null) =
            ElectronicCardEditFragment().apply {
                if (id != null && id > 0) arguments = Bundle().apply { putInt(ARG_ID, id) }
            }
    }
}
