package com.huawo.nt.sdkdemo.data.repository

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.huawo.nt.sdkdemo.data.local.BoundDeviceStore
import com.huawo.nt.sdkdemo.data.model.BleActivity
import com.huawo.nt.sdkdemo.data.model.BleDevice
import com.huawo.nt.sdkdemo.data.model.BleDeviceInfo
import com.huawo.nt.sdkdemo.data.model.BleHeartrate
import com.huawo.nt.sdkdemo.data.model.BleSleep
import com.huawo.nt.sdkdemo.data.model.BoundDeviceRecord
import com.huawo.nt.sdkdemo.data.model.ConnectionEvent
import com.huawo.nt.sdkdemo.data.model.HealthDataCount
import com.huawo.nt.sdkdemo.data.model.ScanEvent
import com.huawo.nt.sdkdemo.data.model.SdkException
import com.huawo.nt.sdkdemo.util.awaitCreateBond
import com.huawo.nt.sdkdemo.util.awaitRemoveBond
import com.huawo.nt.sdkdemo.util.awaitVoid
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.sdk.bluetoothsdk.callback.ConnectCallback
import com.huawo.sdk.bluetoothsdk.callback.DisconnectCallback
import com.huawo.sdk.bluetoothsdk.core.callback.ScanCallback
import com.huawo.sdk.bluetoothsdk.core.model.Device
import com.huawo.sdk.bluetoothsdk.interfaces.callback.ActivityNumCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.ConnectionStateCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.DeviceInfoCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.HeartratesCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.SleepsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.SportsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ops.GetActivityNum
import com.huawo.sdk.bluetoothsdk.interfaces.ops.GetSports
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.ActivityNum
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DeviceInfo
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Gender
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Heartrate
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Sleep
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Sport
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Unit as MeasureUnit
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.UserInfo
import com.huawo.sdk.bluetoothsdk.interfaces.utils.LanguageUtils
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class BleRepository(private val application: Application) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val boundStore = BoundDeviceStore(application)

    @Volatile
    private var initialized = false

    @Volatile
    private var connectionListenerRegistered = false

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val connectionStateCallback =
        object : ConnectionStateCallback() {
            override fun onConnectionStateChange(connected: Boolean) {
                emitConnection(connected)
            }
        }

    fun init(maxMtu: Int = 247) {
        if (!initialized) {
            BluetoothSDK.init(application, maxMtu)
            initialized = true
            registerConnectionListener()
        }
    }

    fun destroy() {
        unregisterConnectionListener()
        if (initialized) {
            BluetoothSDK.destroy()
            initialized = false
        }
    }

    fun getVersion(): String = BluetoothSDK.getVersion()

    fun isConnected(): Boolean = BluetoothSDK.isConnected()

    fun isBind(): Boolean = BluetoothSDK.isBind()

    fun isBonded(): Boolean = BluetoothSDK.isBonded()

    fun setBind(bind: Boolean) {
        BluetoothSDK.setBind(bind)
    }

    fun stopScan() {
        BluetoothSDK.stopScan()
    }

    fun loadBoundDevice(): BoundDeviceRecord? = boundStore.load()

    fun saveBoundDevice(macAddress: String, name: String?, deviceInfo: BleDeviceInfo?) {
        boundStore.save(macAddress, name, deviceInfo)
    }

    fun clearBoundDevice() {
        boundStore.clear()
    }

    fun scanDevices(timeoutMs: Long = 10_000L): Flow<ScanEvent> = callbackFlow {
        if (!initialized) {
            close(SdkException(-1, "Call init() before scanDevices()"))
            return@callbackFlow
        }
        BluetoothSDK.scan(
            timeoutMs,
            object : ScanCallback() {
                override fun onStarted(success: Boolean) {
                    trySend(ScanEvent.Started(success))
                }

                override fun onResult(bleDevice: Device) {
                    trySend(ScanEvent.Result(bleDevice.toModel()))
                }

                override fun onFinished(resultList: List<Device>?) {
                    trySend(ScanEvent.Finished(resultList?.map { it.toModel() }.orEmpty()))
                    close()
                }
            },
        )
        awaitClose { BluetoothSDK.stopScan() }
    }

    suspend fun connect(macAddress: String, timeoutSeconds: Int = 30): BleDevice {
        require(macAddress.isNotBlank()) { "macAddress is required" }
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.connect(
                macAddress,
                timeoutSeconds * 1000L,
                object : ConnectCallback() {
                    override fun onSuccess(device: Device) {
                        mainHandler.post {
                            emitConnection(true)
                            if (cont.isActive) cont.resume(device.toModel())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "connect failed"))
                            }
                        }
                    }
                },
            )
        }
    }

    suspend fun disconnect() {
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.disconnect(
                object : DisconnectCallback() {
                    override fun onSuccess() {
                        mainHandler.post {
                            emitConnection(false)
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                },
            )
        }
    }

    suspend fun startBind() = awaitVoid("startBind failed") { BluetoothSDK.startBind(it) }

    suspend fun endBind() = awaitVoid("endBind failed") { BluetoothSDK.endBind(it) }

    suspend fun createBond() = awaitCreateBond("createBond failed") { BluetoothSDK.createBond(it) }

    suspend fun removeBond() = awaitRemoveBond("removeBond failed") { BluetoothSDK.removeBond(it) }

    suspend fun setDeviceTime(timeMs: Long = System.currentTimeMillis(), use24Hour: Boolean = true) {
        awaitVoid("setDeviceTime failed") {
            BluetoothSDK.setDeviceTimeAndStyle(Date(timeMs), if (use24Hour) 1 else 0, it)
        }
    }

    suspend fun setUserInfo(
        gender: Int = 0,
        age: Int = 28,
        height: Int = 175,
        weight: Int = 700,
    ) {
        val userInfo = UserInfo().apply {
            this.gender = Gender.valueOf(gender)
            this.age = age
            this.height = height
            this.weight = weight
        }
        awaitVoid("setUserInfo failed") { BluetoothSDK.setUserInfo(userInfo, it) }
    }

    suspend fun setUnit(metric: Boolean = true) {
        val unit = if (metric) MeasureUnit.Metric else MeasureUnit.British
        awaitVoid("setUnit failed") { BluetoothSDK.setUnit(unit, it) }
    }

    suspend fun setLanguage(languageCode: Int = 0) {
        val language = LanguageUtils.getLanguageCode(languageCode)
        awaitVoid("setLanguage failed") { BluetoothSDK.setLanguage(language, it) }
    }

    suspend fun getDeviceInfo(): BleDeviceInfo {
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.getDeviceInfo(
                object : DeviceInfoCallback() {
                    override fun onSuccess(deviceInfo: DeviceInfo) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(deviceInfo.toModel())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getDeviceInfo failed"))
                            }
                        }
                    }
                },
            )
        }
    }

    suspend fun getHealthDataCount(): HealthDataCount {
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.addDataTask(
                GetActivityNum(
                    object : ActivityNumCallback() {
                        override fun onSuccess(activityNum: ActivityNum) {
                            mainHandler.post {
                                if (cont.isActive) {
                                    cont.resume(
                                        HealthDataCount(
                                            activityCount = activityNum.sportNum,
                                            sleepCount = activityNum.sleepNum,
                                            heartrateCount = activityNum.heartrateNum,
                                            hrfCount = activityNum.hrvNum,
                                        ),
                                    )
                                }
                            }
                        }

                        override fun onFail(code: Int) {
                            mainHandler.post {
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        SdkException(code, "getHealthDataCount failed"),
                                    )
                                }
                            }
                        }
                    },
                ),
            )
        }
    }

    suspend fun getActivities(count: Int): List<BleActivity> {
        if (count <= 0) return emptyList()
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.addDataTask(
                GetSports(
                    count,
                    object : SportsCallback() {
                        override fun onSuccess(sportList: List<Sport>?) {
                            mainHandler.post {
                                if (cont.isActive) {
                                    cont.resume(sportList?.map { it.toModel() }.orEmpty())
                                }
                            }
                        }

                        override fun onFail(code: Int) {
                            mainHandler.post {
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        SdkException(code, "getActivities failed"),
                                    )
                                }
                            }
                        }
                    },
                ),
            )
        }
    }

    suspend fun getHeartrates(): List<BleHeartrate> {
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.getHeartrates(
                object : HeartratesCallback() {
                    override fun onSuccess(list: List<Heartrate>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(list?.map { it.toModel() }.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getHeartrates failed"))
                            }
                        }
                    }
                },
            )
        }
    }

    suspend fun getSleeps(): List<BleSleep> {
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.getSleeps(
                object : SleepsCallback() {
                    override fun onSuccess(list: List<Sleep>?) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resume(
                                    list?.mapIndexed { index, sleep -> sleep.toModel(index) }
                                        .orEmpty(),
                                )
                            }
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getSleeps failed"))
                            }
                        }
                    }
                },
            )
        }
    }

    suspend fun deleteSports() = awaitVoid("deleteSports failed") { BluetoothSDK.delSports(it) }

    suspend fun deleteHeartrates() =
        awaitVoid("deleteHeartrates failed") { BluetoothSDK.delHeartrates(it) }

    suspend fun deleteSleeps() = awaitVoid("deleteSleeps failed") { BluetoothSDK.delSleeps(it) }

    private fun registerConnectionListener() {
        if (!initialized || connectionListenerRegistered) return
        BluetoothSDK.addConnectionStateListener(connectionStateCallback)
        connectionListenerRegistered = true
    }

    private fun unregisterConnectionListener() {
        if (!connectionListenerRegistered) return
        try {
            BluetoothSDK.removeConnectionStateListener(connectionStateCallback)
        } catch (_: Exception) {
        }
        connectionListenerRegistered = false
    }

    private fun emitConnection(connected: Boolean) {
        val event =
            if (connected) {
                val device = BluetoothSDK.getConnectedDevice()
                ConnectionEvent.Connected(device?.name, device?.mac)
            } else {
                ConnectionEvent.Disconnected
            }
        _connectionEvents.tryEmit(event)
    }

    private fun Device.toModel() =
        BleDevice(name = name, macAddress = mac.orEmpty(), rssi = rssi)

    private fun DeviceInfo.toModel() =
        BleDeviceInfo(
            id = id,
            type = type,
            firmwareVersion = firmwareVersion,
            mac = mac,
            bindState = bindState,
            language = language?.value,
            battery = battery,
            displayingWatchfaceId = displayingWatchfaceId,
            watchfaceVersion = watchfaceVersion,
            protocolVersion = protocolVersion,
            mapUuid = mapUUID,
            mapAuthorized = isMapAuthorized,
        )

    private fun Sport.toModel() =
        BleActivity(
            index = index,
            timeMs = time,
            step = step.toInt(),
            calorie = calorie.toInt(),
            staticCalorie = staticCalorie.toInt(),
            distance = distance.toInt(),
            duration = duration.toInt(),
            avgBpm = heartAvg,
        )

    private fun Heartrate.toModel() = BleHeartrate(index = index, timeMs = time, bpm = bpm)

    private fun Sleep.toModel(index: Int) =
        BleSleep(
            index = index,
            timeMs = startTime,
            deep = deepDuration.toInt(),
            light = lightDuration.toInt(),
            awake = awakeDuration.toInt(),
            rem = remDuration.toInt(),
        )
}
