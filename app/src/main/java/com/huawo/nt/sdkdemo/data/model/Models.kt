package com.huawo.nt.sdkdemo.data.model

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
