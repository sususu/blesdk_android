package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.LocalMusicFile
import com.huawo.nt.sdkdemo.data.model.MusicTransferCallback
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the music push screen.
 *
 * - [status]: top status bar text
 * - [result]: bottom Result area (progress / success / failure detail)
 * - [sending]: transfer in progress (progress bar + button enablement)
 * - [bleConnected]: BLE GATT connected (required before push)
 * - [bonded] / [btConnected]: classic BT bond / connection (gates SPP)
 */
data class MusicTransferUiState(
    val status: String = "",
    val result: String = "",
    val sending: Boolean = false,
    val deviceName: String = "- -",
    val deviceMac: String = "- -",
    val bleConnected: Boolean = false,
    val btConnected: Boolean = false,
    val bonded: Boolean = false,
    val storageText: String = "- -",
    val progress: Int = 0,
    val progressText: String = "0%",
)

/**
 * Music file push ViewModel.
 *
 * List selection stays in Fragment/Adapter. This class handles:
 * 1. Refresh BLE / classic BT / watch music storage
 * 2. createBond (prep for SPP)
 * 3. Call [BleRepository.pushMusicFiles] and map channel callbacks to UI progress
 */
class MusicTransferViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private val _uiState =
        MutableStateFlow(MusicTransferUiState(status = str(R.string.feature_ready)))
    val uiState: StateFlow<MusicTransferUiState> = _uiState.asStateFlow()

    /** Active transfer channel name (SPP / Sifli) for status and Result text. */
    private var transferChannel: String = ""

    private fun ctx() = LocaleHelper.localizedContext(getApplication())
    private fun str(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx().getString(resId) else ctx().getString(resId, *args)

    /**
     * Refresh device display info.
     * Name/MAC prefer the current BLE device, else fall back to local bind record.
     */
    fun refreshDeviceInfo() {
        val connected = repository.isConnected()
        val bound = repository.loadBoundDevice()
        val name =
            when {
                connected ->
                    repository.currentConnectedName()?.ifBlank { null }
                        ?: bound?.name
                        ?: str(R.string.unknown_device)
                bound != null -> bound.name ?: str(R.string.unknown_device)
                else -> "- -"
            }
        val mac =
            when {
                connected ->
                    repository.currentConnectedMac()?.ifBlank { null }
                        ?: bound?.macAddress
                        ?: "- -"
                bound != null -> bound.macAddress
                else -> "- -"
            }
        _uiState.update {
            it.copy(
                bleConnected = connected,
                bonded = repository.isBonded(),
                deviceName = name,
                deviceMac = mac,
            )
        }
        refreshBtStatus()
    }

    /**
     * Query classic BT connection ([BluetoothSDK.getBTConnectionState]).
     * Unlike [bonded] (system pairing), bonded does not imply profile connected.
     */
    fun refreshBtStatus() {
        viewModelScope.launch {
            val connected =
                runCatching { repository.isClassicBtConnected() }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    btConnected = connected,
                    bonded = repository.isBonded(),
                )
            }
        }
    }

    /**
     * Query watch music available / total storage.
     * @param silent when true, do not change status/result (silent refresh of MB text after push)
     */
    fun queryStorage(silent: Boolean = false) {
        if (_uiState.value.sending) return
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(status = str(R.string.music_querying_storage)) }
            }
            try {
                val storage = repository.getMusicStorage()
                // SDK reports KB; card shows MB (same as tool page)
                val availableMb = storage.availableKb / 1024.0
                val totalMb = storage.totalKb / 1024.0
                val text = String.format("%.2fMB / %.2fMB", availableMb, totalMb)
                _uiState.update {
                    it.copy(
                        status = if (silent) it.status else str(R.string.feature_success),
                        storageText = text,
                        result =
                            if (silent) {
                                it.result
                            } else {
                                str(
                                    R.string.music_storage_result,
                                    storage.availableKb,
                                    (storage.totalKb - storage.availableKb).coerceAtLeast(0),
                                    storage.totalKb,
                                )
                            },
                    )
                }
            } catch (e: Exception) {
                if (!silent) fail(e.message.orEmpty())
            }
        }
    }

    /**
     * Start classic BT pairing ([BluetoothSDK.createBond]).
     * Usually required before SPP; watch [btConnected] after success.
     */
    fun createBond() {
        if (_uiState.value.sending) return
        if (!repository.isConnected()) {
            _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(status = str(R.string.music_pairing)) }
            try {
                repository.createBond()
                _uiState.update {
                    it.copy(
                        status = str(R.string.music_pair_ok),
                        bonded = repository.isBonded(),
                        result = str(R.string.music_pair_ok),
                    )
                }
                refreshBtStatus()
            } catch (e: Exception) {
                fail(e.message.orEmpty())
            }
        }
    }

    /**
     * Push selected music files.
     *
     * MediaStore [LocalMusicFile.path] may be empty/invalid on some devices;
     * filter to existing [File]s before Repository.
     * Channel selection is done in [BleRepository.pushMusicFiles] based on classic BT state.
     */
    fun pushSelected(files: List<LocalMusicFile>) {
        if (_uiState.value.sending) return
        if (files.isEmpty()) {
            _uiState.update { it.copy(status = str(R.string.music_no_files)) }
            return
        }
        if (!repository.isConnected()) {
            _uiState.update { it.copy(status = str(R.string.status_need_connect_first)) }
            return
        }
        val fileList =
            files.mapNotNull { item ->
                val f = File(item.path)
                f.takeIf { it.exists() && it.isFile }
            }
        if (fileList.isEmpty()) {
            _uiState.update { it.copy(status = str(R.string.music_no_valid_files)) }
            return
        }
        transferChannel = ""
        _uiState.update {
            it.copy(
                sending = true,
                progress = 0,
                progressText = "0%",
                status = str(R.string.music_pushing),
                result = "",
            )
        }
        repository.pushMusicFiles(
            fileList,
            object : MusicTransferCallback {
                override fun onChannel(channel: String) {
                    transferChannel = channel
                    _uiState.update {
                        it.copy(status = str(R.string.music_pushing_channel, channel))
                    }
                }

                override fun onReady() {
                    _uiState.update {
                        it.copy(status = str(R.string.music_ready, transferChannel))
                    }
                }

                override fun onProgress(progress: Float) {
                    // Repository progress is 0f..1f
                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                    _uiState.update {
                        it.copy(
                            progress = percent,
                            progressText = "$percent%",
                            status = str(R.string.music_progress, transferChannel, percent),
                            result = str(R.string.music_progress_result, transferChannel, percent),
                        )
                    }
                }

                override fun onSuccess() {
                    _uiState.update {
                        it.copy(
                            sending = false,
                            progress = 100,
                            progressText = "100%",
                            status = str(R.string.feature_success),
                            result = str(R.string.music_push_ok, transferChannel),
                        )
                    }
                    // Watch storage may change after push; silently refresh card text
                    queryStorage(silent = true)
                }

                override fun onFail(code: Int, message: String) {
                    fail("[$code] $message")
                }
            },
        )
    }

    /** Cancel in-flight SPP / Sifli transfer (Repository stops each path). */
    fun cancelTransfer() {
        repository.cancelMusicTransfer()
        _uiState.update {
            it.copy(
                sending = false,
                status = str(R.string.music_cancelled),
                result = str(R.string.music_cancelled),
            )
        }
    }

    private fun fail(message: String) {
        _uiState.update {
            it.copy(
                sending = false,
                status = str(R.string.feature_failed, message),
                result = message,
            )
        }
    }
}
