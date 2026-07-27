package com.huawo.nt.sdkdemo.ui.unbind

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
import com.huawo.nt.sdkdemo.databinding.DialogFlowSheetBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import com.huawo.nt.sdkdemo.ui.common.FlowStepAdapter
import kotlinx.coroutines.launch

class UnbindFlowDialogFragment : BottomSheetDialogFragment() {
    private var _binding: DialogFlowSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UnbindFlowViewModel by viewModels { ViewModelFactory() }
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
        binding.title.text = getString(R.string.unbind_watch)
        binding.subtitle.text = getString(R.string.unbind_subtitle)
        binding.stepList.layoutManager = LinearLayoutManager(requireContext())
        binding.stepList.adapter = adapter
        binding.btnClose.setOnClickListener {
            deliverResult(success = false, failed = true)
            dismiss()
        }
        binding.btnConfirm.setOnClickListener {
            deliverResult(success = true, failed = false)
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
                        if (state.finished) {
                            getString(R.string.confirm)
                        } else {
                            getString(R.string.unbinding_in_progress)
                        }
                }
            }
        }
    }

    private fun deliverResult(success: Boolean, failed: Boolean) {
        if (resultSent) return
        resultSent = true
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                KEY_SUCCESS to success,
                KEY_FAILED to failed,
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "unbind_flow_result"
        const val KEY_SUCCESS = "success"
        const val KEY_FAILED = "failed"
    }
}
