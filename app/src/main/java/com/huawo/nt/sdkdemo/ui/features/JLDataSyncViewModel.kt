package com.huawo.nt.sdkdemo.ui.features

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Individual SDK examples: reads never delete, and explicit deletes do not depend on a previous read. */
class JLDataSyncViewModel(
    application: Application,
    private val repository: BleRepository,
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "JLDataSyncViewModel"
    }

    private fun str(id: Int, vararg args: Any): String =
        LocaleHelper.localizedContext(getApplication()).getString(id, *args)

    private val _uiState = MutableStateFlow(FeatureUiState(status = str(R.string.feature_ready)))
    val uiState = _uiState.asStateFlow()
    private var targetMac: String? = null
    private var requestJob: Job? = null
    private var requestVersion = 0L

    /** Store navigation context only; entering the page does not reconnect or issue a data command. */
    fun setTargetMac(mac: String?) {
        targetMac = mac?.takeIf { it.isNotBlank() }
    }

    fun readStep() = readData("getStepV2", R.string.jl_data_step) { repository.getStepV2() }
    fun readHeartRate() = readData("getHeartRateV2", R.string.jl_data_heart) { repository.getHeartRateV2() }
    fun readSleep() = readData("getSleepV2", R.string.jl_data_sleep) { repository.getSleepV2() }
    fun readHrv() = readData("getHrvV2", R.string.jl_data_hrv) { repository.getHrvV2() }
    fun readSpo2() = readData("getSpo2V2", R.string.jl_data_spo2) { repository.getSpo2V2() }
    fun readStress() = readData("getStressV2", R.string.jl_data_stress) { repository.getStressV2() }
    fun readSkinTemperature() = readData("getSkinTemperature", R.string.jl_data_skin) { repository.getSkinTemperature() }
    fun readBreaths() = readData("getBreaths", R.string.jl_data_breaths) { repository.getBreaths() }
    fun readWorkout() = readData("getWorkoutV2", R.string.jl_data_workout) { repository.getWorkoutV2() }

    fun deleteStep() = deleteData("delSports", R.string.jl_data_step) { repository.deleteSports() }
    fun deleteHeartRate() = deleteData("delHeartrates", R.string.jl_data_heart) { repository.deleteHeartrates() }
    fun deleteSleep() = deleteData("delSleeps", R.string.jl_data_sleep) { repository.deleteSleeps() }
    fun deleteHrv() = deleteData("delHrvV2", R.string.jl_data_hrv) { repository.deleteHrvsV2() }
    fun deleteSpo2() = deleteData("delBlood", R.string.jl_data_spo2) { repository.deleteBlood() }
    fun deleteStress() = deleteData("delStress", R.string.jl_data_stress) { repository.deleteStress() }
    fun deleteSkinTemperature() = deleteData("delSkinTemperature", R.string.jl_data_skin) { repository.deleteSkinTemperature() }
    fun deleteBreaths() = deleteData("delBreaths", R.string.jl_data_breaths) { repository.deleteBreaths() }
    fun deleteWorkout() = deleteData("delWorkouts", R.string.jl_data_workout) { repository.deleteWorkouts() }

    private fun readData(api: String, typeRes: Int, block: suspend () -> List<Any>) =
        runAction(api, typeRes, R.string.jl_data_read_action) {
            val items = block()
            Log.i(TAG, "$api received count=${items.size}")
            val details = if (items.isEmpty()) str(R.string.jl_data_empty) else buildString {
                items.forEachIndexed { index, item ->
                    // Keep the repository models' millisecond timestamps and float Celsius values unchanged.
                    appendLine("[$index] $item")
                    Log.i(TAG, "$api [$index] $item")
                }
            }.trimEnd()
            str(R.string.jl_data_read_result, str(typeRes), items.size, details)
        }

    private fun deleteData(api: String, typeRes: Int, block: suspend () -> Unit) =
        runAction(api, typeRes, R.string.jl_data_delete_action) {
            block()
            // Acknowledge this delete only; do not read back or invent a remaining-record count.
            str(R.string.jl_data_delete_result, str(typeRes), api)
        }

    private fun runAction(api: String, typeRes: Int, actionRes: Int, block: suspend () -> String) {
        if (_uiState.value.busy) return
        val version = ++requestVersion
        val type = str(typeRes)
        val action = str(actionRes)
        val running = str(R.string.jl_data_running, action, type, api)
        _uiState.update { it.copy(busy = true, status = running, result = "") }
        requestJob = viewModelScope.launch {
            Log.i(TAG, "$api enter type=$type request=$version connected=${repository.isConnected()}")
            try {
                if (!repository.isConnected()) {
                    val mac = targetMac ?: error(str(R.string.error_no_device_to_sync))
                    Log.i(TAG, "$api reconnect mac=$mac request=$version")
                    _uiState.update { it.copy(status = str(R.string.jl_data_reconnecting, mac)) }
                    repository.connect(mac)
                }
                if (version != requestVersion) return@launch
                _uiState.update { it.copy(status = running) }
                val result = block()
                if (version != requestVersion) return@launch
                Log.i(TAG, "$api success type=$type request=$version")
                _uiState.update { it.copy(status = str(R.string.feature_success), result = result) }
            } catch (error: CancellationException) {
                Log.i(TAG, "$api canceled type=$type request=$version")
                throw error
            } catch (error: Exception) {
                if (version != requestVersion) return@launch
                Log.e(TAG, "$api failed type=$type request=$version", error)
                val message = str(R.string.jl_data_failed, action, type, api, error.message.orEmpty())
                _uiState.update { it.copy(status = message, result = message) }
            } finally {
                // A canceled old operation must not unlock or overwrite a newer request after page re-entry.
                if (version == requestVersion) {
                    requestJob = null
                    _uiState.update { it.copy(busy = false) }
                }
            }
        }.takeIf { it.isActive }
    }

    fun cancelRequest(reason: String) {
        val job = requestJob ?: return
        requestVersion++
        requestJob = null
        job.cancel()
        // Cancel only our wait; queued/sent SDK commands and the shared SDK must remain untouched.
        _uiState.update { it.copy(busy = false, status = str(R.string.jl_data_canceled), result = "") }
        Log.i(TAG, "cancelRequest reason=$reason request=$requestVersion")
    }

    override fun onCleared() {
        cancelRequest("viewModelCleared")
        super.onCleared()
    }
}
