package com.huawo.nt.sdkdemo.data.model

import java.io.File

data class BleDevice(
    val name: String? = null,
    val macAddress: String,
    val rssi: Int? = null,
)

data class BleDeviceInfo(
    val id: String? = null,
    val type: String? = null,
    val firmwareVersion: String? = null,
    val mac: String? = null,
    val bindState: Int? = null,
    val language: Int? = null,
    val battery: Int? = null,
    val displayingWatchfaceId: String? = null,
    val watchfaceVersion: Int? = null,
    val protocolVersion: Int? = null,
    val mapUuid: String? = null,
    val mapAuthorized: Boolean = false,
)

data class BoundDeviceRecord(
    val macAddress: String,
    val name: String? = null,
    val deviceInfo: BleDeviceInfo? = null,
)

data class HealthDataCount(
    val activityCount: Int = 0,
    val sleepCount: Int = 0,
    val heartrateCount: Int = 0,
    val hrfCount: Int = 0,
)

/** Watch music available storage (SDK callback, units in KB). */
data class MusicStorage(
    val availableKb: Int,
    val totalKb: Int,
)

/**
 * Music push progress callbacks (all delivered on the main thread).
 * [onChannel]: actual channel name used, e.g. `"SPP"` / `"Sifli"`.
 * [onProgress]: 0f~1f.
 */
interface MusicTransferCallback {
    fun onChannel(channel: String)
    fun onReady()
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFail(code: Int, message: String)
}

/** Watch album free storage (returned by device, units in KB). */
data class AlbumIdleStorage(
    val availableKb: Int,
)

/**
 * One album photo pending push.
 *
 * @param index Watch album slot ID (typically 1..50, allocated by [com.huawo.nt.sdkdemo.data.repository.BleRepository.allocateAlbumIndices])
 * @param file  Local source image copied to app cache; transcoding and preview read this file
 */
data class AlbumPhotoItem(
    val index: Int,
    val file: File,
)

/**
 * Album push progress callbacks.
 * Repository delivers on the main thread; [onProgress] progress range is 0f..1f.
 */
interface AlbumTransferCallback {
    /** Actual channel used: "SPP" or "Sifli". */
    fun onChannel(channel: String)
    fun onReady()
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFail(code: Int, message: String)
}

/** Device GPS / AGPS status from [com.huawo.sdk.bluetoothsdk.BluetoothSDK.getDeviceGpsStatus]. */
data class BleGpsStatus(
    val agpsValidStartTimeMs: Long = 0L,
    val agpsValidEndTimeMs: Long = 0L,
    val gpsClipType: String? = null,
    val gpsFirmwareVersion: String? = null,
    val gpsFirmwareBuild: Int = 0,
)

/**
 * AGPS zip push callbacks (main thread).
 * [onProgress] range is 0f..1f from [com.huawo.watchface.SifliWatchSDK.syncZipFile].
 */
interface AgpsTransferCallback {
    fun onReady()
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFail(code: Int, message: String)
}

data class BleActivity(
    val index: Int = 0,
    val timeMs: Long = 0,
    val step: Int = 0,
    val calorie: Int = 0,
    val staticCalorie: Int = 0,
    val distance: Int = 0,
    val duration: Int = 0,
    val avgBpm: Int = 0,
)

data class BleHeartrate(
    val index: Int = 0,
    val timeMs: Long = 0,
    val bpm: Int = 0,
)

data class BleSleep(
    val index: Int = 0,
    val timeMs: Long = 0,
    val deep: Int = 0,
    val light: Int = 0,
    val awake: Int = 0,
    val rem: Int = 0,
)

enum class DevicePhase {
    IDLE,
    CONNECTED,
    BOUND,
    SYNCING,
    UNBINDING,
}

enum class FlowStepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED,
}

data class FlowStep(
    val api: String,
    val description: String,
    val platformNote: String? = null,
    var status: FlowStepStatus = FlowStepStatus.PENDING,
    var detail: String? = null,
)

sealed class ConnectionEvent {
    data class Connected(val deviceName: String?, val macAddress: String?) : ConnectionEvent()
    data object Disconnected : ConnectionEvent()
}

sealed class ScanEvent {
    data class Started(val success: Boolean) : ScanEvent()
    data class Result(val device: BleDevice) : ScanEvent()
    data class Finished(val devices: List<BleDevice>) : ScanEvent()
}

class SdkException(val code: Int, message: String) : Exception("[$code] $message")
