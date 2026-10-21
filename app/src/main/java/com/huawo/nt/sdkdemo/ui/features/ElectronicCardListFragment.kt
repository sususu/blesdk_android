package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentElectronicCardListBinding
import com.huawo.nt.sdkdemo.databinding.ItemElectronicCardBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/** Lists watch electronic cards. At most three cards; the add action hides once that limit is reached. */
class ElectronicCardListFragment : Fragment() {
    private var _binding: FragmentElectronicCardListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ElectronicCardListViewModel by viewModels { ViewModelFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentElectronicCardListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.toolbar.inflateMenu(R.menu.menu_electronic_card)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_refresh_cards) {
                viewModel.refresh()
                true
            } else {
                false
            }
        }
        binding.btnAdd.setOnClickListener {
            if (viewModel.uiState.value.cards.size >= MAX_CARDS) return@setOnClickListener
            openEdit(null)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    private fun render(state: ElectronicCardListViewModel.State) {
        val container = binding.cardList
        container.removeAllViews()
        state.cards.forEachIndexed { index, card ->
            if (index > 0) {
                container.addView(
                    View(requireContext()).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                (1 * resources.displayMetrics.density).toInt().coerceAtLeast(1),
                            )
                        setBackgroundColor(resources.getColor(R.color.home_separator, requireContext().theme))
                    },
                )
            }
            val row = ItemElectronicCardBinding.inflate(layoutInflater, container, false)
            row.cardName.text =
                card.name.ifBlank { getString(R.string.ecard_name_default) }
            row.cardId.text = getString(R.string.ecard_item_id, card.id)
            row.root.setOnClickListener {
                if (!viewModel.uiState.value.busy && card.id > 0) openEdit(card.id)
            }
            container.addView(row.root)
        }
        binding.emptyText.isVisible = state.loaded && state.cards.isEmpty()
        binding.btnAdd.isVisible = state.cards.size < MAX_CARDS
        binding.btnAdd.isEnabled = !state.busy && state.cards.size < MAX_CARDS
        binding.toolbar.menu.findItem(R.id.action_refresh_cards)?.isEnabled = !state.busy
        binding.statusProgress.isVisible = state.busy
        binding.statusText.isVisible = state.message.isNotBlank()
        binding.statusText.text = state.message
    }

    private fun openEdit(id: Int?) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ElectronicCardEditFragment.newInstance(id))
            .addToBackStack("ecard_edit")
            .commit()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MAX_CARDS = 3
    }
}
