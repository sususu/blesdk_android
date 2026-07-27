package com.huawo.nt.sdkdemo.ui.bind

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.BleDeviceInfo
import com.huawo.nt.sdkdemo.databinding.DialogFlowSheetBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.common.FlowStepAdapter
import kotlinx.coroutines.launch

class BindFlowDialogFragment : BottomSheetDialogFragment() {
    private var _binding: DialogFlowSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BindFlowViewModel by viewModels { ViewModelFactory() }
    private val adapter = FlowStepAdapter()
    private var resultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogFlowSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.title.text = getString(R.string.bind_watch)
        binding.subtitle.text = getString(R.string.bind_subtitle)
        binding.stepList.layoutManager = LinearLayoutManager(requireContext())
        binding.stepList.adapter = adapter
        binding.btnClose.setOnClickListener {
            deliverResult(success = false)
            dismiss()
        }
        binding.btnConfirm.setOnClickListener {
            deliverResult(success = true, info = viewModel.uiState.value.deviceInfo)
            dismiss()
        }
        viewModel.start()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submit(state.steps)
                    binding.errorText.isVisible = !state.error.isNullOrBlank()
                    binding.errorText.text = state.error
                    binding.btnClose.isVisible = state.failed
                    binding.btnConfirm.isEnabled = state.finished
                    binding.btnConfirm.text =
                        if (state.finished) getString(R.string.confirm) else getString(R.string.binding_in_progress)
                }
            }
        }
    }

    private fun deliverResult(success: Boolean, info: BleDeviceInfo? = null) {
        if (resultSent) return
        resultSent = true
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                KEY_SUCCESS to success,
                KEY_TYPE to info?.type,
                KEY_FW to info?.firmwareVersion,
                KEY_MAC to info?.mac,
                KEY_BATTERY to (info?.battery ?: -1),
                KEY_PROTO to (info?.protocolVersion ?: -1),
                KEY_ID to info?.id,
                KEY_BIND_STATE to (info?.bindState ?: -1),
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "bind_flow_result"
        const val KEY_SUCCESS = "success"
        const val KEY_TYPE = "type"
        const val KEY_FW = "fw"
        const val KEY_MAC = "mac"
        const val KEY_BATTERY = "battery"
        const val KEY_PROTO = "proto"
        const val KEY_ID = "id"
        const val KEY_BIND_STATE = "bindState"

        fun readDeviceInfo(bundle: Bundle): BleDeviceInfo? {
            if (!bundle.getBoolean(KEY_SUCCESS)) return null
            val hasAny =
                !bundle.getString(KEY_TYPE).isNullOrBlank() ||
                    !bundle.getString(KEY_FW).isNullOrBlank() ||
                    !bundle.getString(KEY_MAC).isNullOrBlank() ||
                    !bundle.getString(KEY_ID).isNullOrBlank() ||
                    bundle.getInt(KEY_BATTERY, -1) >= 0
            if (!hasAny) return null
            return BleDeviceInfo(
                id = bundle.getString(KEY_ID),
                type = bundle.getString(KEY_TYPE),
                firmwareVersion = bundle.getString(KEY_FW),
                mac = bundle.getString(KEY_MAC),
                battery = bundle.getInt(KEY_BATTERY, -1).takeIf { it >= 0 },
                protocolVersion = bundle.getInt(KEY_PROTO, -1).takeIf { it >= 0 },
                bindState = bundle.getInt(KEY_BIND_STATE, -1).takeIf { it >= 0 },
            )
        }
    }
}
