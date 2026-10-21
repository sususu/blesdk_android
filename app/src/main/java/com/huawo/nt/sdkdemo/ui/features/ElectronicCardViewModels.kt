package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.sdk.bluetoothsdk.interfaces.ops.SetElectronicCard
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.ElectronicCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ElectronicCardListViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    data class Item(val id: Int, val name: String)

    data class State(
        val cards: List<Item> = emptyList(),
        val busy: Boolean = false,
        val loaded: Boolean = false,
        val message: String = "",
    )

    private val _uiState = MutableStateFlow(State())
    val uiState = _uiState.asStateFlow()
    private var version = 0L

    private fun str(id: Int, vararg args: Any) =
        LocaleHelper.localizedContext(getApplication()).getString(id, *args)

    /** Reload id and name from the watch. Card payloads are fetched on the edit page. */
    fun refresh() {
        if (_uiState.value.busy) return
        if (!repository.isConnected()) {
            _uiState.value = State(message = str(R.string.ecard_need_connection))
            return
        }
        val token = ++version
        _uiState.update { it.copy(busy = true, message = str(R.string.ecard_loading)) }
        viewModelScope.launch {
            try {
                val cards =
                    repository.getAllElectronicCards().map { card ->
                        Item(id = card.id, name = card.name.orEmpty())
                    }
                if (token != version) return@launch
                _uiState.value = State(cards = cards, loaded = true)
                Log.i(TAG, "getAllElectronicCard success count=${cards.size}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != version) return@launch
                Log.e(TAG, "getAllElectronicCard failed", error)
                _uiState.update {
                    it.copy(message = str(R.string.ecard_load_failed, error.message ?: error.javaClass.simpleName))
                }
            } finally {
                if (token == version) _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val TAG = "ElectronicCardList"
    }
}

class ElectronicCardEditViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    data class State(
        val id: Int? = null,
        val name: String = "",
        val value: String? = null,
        val busy: Boolean = false,
        val loaded: Boolean = false,
        val message: String = "",
    )

    private val _uiState = MutableStateFlow(State())
    val uiState = _uiState.asStateFlow()
    private val _close = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val close = _close.asSharedFlow()
    private var started = false

    private fun str(id: Int, vararg args: Any) =
        LocaleHelper.localizedContext(getApplication()).getString(id, *args)

    /** [cardId] above 0 opens an existing card. A new card starts with the default name. */
    fun start(cardId: Int?) {
        if (started) return
        started = true
        if (cardId == null) {
            _uiState.value = State(name = str(R.string.ecard_name_default), loaded = true)
            return
        }
        if (!repository.isConnected()) {
            _uiState.value = State(id = cardId, message = str(R.string.ecard_need_connection))
            return
        }
        _uiState.value = State(id = cardId, busy = true, message = str(R.string.ecard_loading_one))
        viewModelScope.launch {
            try {
                val card = repository.getAllElectronicCards().firstOrNull { it.id == cardId }
                val value = repository.getElectronicCardValue(cardId)
                if (!repository.isConnected()) {
                    _uiState.value = State(id = cardId, message = str(R.string.ecard_need_connection))
                    return@launch
                }
                _uiState.value =
                    State(
                        id = cardId,
                        name = card?.name?.takeIf { it.isNotBlank() } ?: str(R.string.ecard_name_default),
                        value = value.takeIf { it.isNotEmpty() },
                        loaded = true,
                    )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "load card id=$cardId failed", error)
                _uiState.value =
                    State(
                        id = cardId,
                        message = str(R.string.ecard_load_failed, error.message ?: error.javaClass.simpleName),
                    )
            }
        }
    }

    fun setName(name: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(name = name.trim()) }
    }

    fun setValue(value: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(value = value, message = "") }
    }

    fun save() {
        val current = _uiState.value
        if (current.busy) return
        val name = current.name.trim()
        val payload = current.value
        val cardId = current.id
        val invalid =
            when {
                !repository.isConnected() -> R.string.ecard_need_connection
                name.isEmpty() -> R.string.ecard_name_required
                name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> R.string.ecard_name_too_long
                payload.isNullOrEmpty() -> R.string.ecard_value_required
                payload.toByteArray(Charsets.UTF_8).size > MAX_VALUE_BYTES -> R.string.ecard_code_unsupported
                else -> null
            }
        if (invalid != null || payload.isNullOrEmpty()) {
            if (invalid != null) _uiState.update { it.copy(message = str(invalid)) }
            return
        }
        _uiState.update { it.copy(busy = true, message = str(R.string.ecard_saving)) }
        viewModelScope.launch {
            try {
                val param =
                    if (cardId != null) {
                        SetElectronicCard.SetElectronicCardParam.update(card(cardId, name, payload))
                    } else {
                        val existing = repository.getAllElectronicCards()
                        if (existing.size >= MAX_CARDS) {
                            _uiState.update { it.copy(busy = false, message = str(R.string.ecard_limit)) }
                            return@launch
                        }
                        val nextId = nextAvailableId(existing.map { it.id })
                        SetElectronicCard.SetElectronicCardParam.create(card(nextId, name, payload))
                    }
                repository.setElectronicCard(param)
                Log.i(TAG, "setElectronicCard success id=${cardId ?: "new"}")
                _close.emit(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "setElectronicCard failed", error)
                _uiState.update {
                    it.copy(
                        busy = false,
                        message = str(R.string.ecard_save_failed, error.message ?: error.javaClass.simpleName),
                    )
                }
            }
        }
    }

    fun delete() {
        val id = _uiState.value.id ?: return
        if (_uiState.value.busy) return
        if (!repository.isConnected()) {
            _uiState.update { it.copy(message = str(R.string.ecard_need_connection)) }
            return
        }
        _uiState.update { it.copy(busy = true, message = str(R.string.ecard_deleting)) }
        viewModelScope.launch {
            try {
                repository.setElectronicCard(SetElectronicCard.SetElectronicCardParam.delete(id))
                Log.i(TAG, "deleteElectronicCard success id=$id")
                _close.emit(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "deleteElectronicCard failed id=$id", error)
                _uiState.update {
                    it.copy(
                        busy = false,
                        message = str(R.string.ecard_delete_failed, error.message ?: error.javaClass.simpleName),
                    )
                }
            }
        }
    }

    private fun card(id: Int, name: String, value: String) =
        ElectronicCard().apply {
            this.id = id
            this.name = name
            this.value = value
        }

    private fun nextAvailableId(used: List<Int>): Int {
        val taken = used.toSet()
        for (candidate in 1 until 65536) {
            if (candidate !in taken) return candidate
        }
        return 1
    }

    private companion object {
        const val TAG = "ElectronicCardEdit"
        const val MAX_CARDS = 3
        const val MAX_NAME_BYTES = 40
        const val MAX_VALUE_BYTES = 159
    }
}
