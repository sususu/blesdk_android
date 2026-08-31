package com.huawo.nt.sdkdemo.data.repository

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.huawo.nt.sdkdemo.data.local.BoundDeviceStore
import com.huawo.nt.sdkdemo.data.model.BleActivity
import com.huawo.nt.sdkdemo.data.model.BleDevice
import com.huawo.nt.sdkdemo.data.model.BleDeviceInfo
import com.huawo.nt.sdkdemo.data.model.BleHeartrate
import com.huawo.nt.sdkdemo.data.model.BleHrv
import com.huawo.nt.sdkdemo.data.model.BleSleep
import com.huawo.nt.sdkdemo.data.model.BoundDeviceRecord
import com.huawo.nt.sdkdemo.data.model.ConnectionEvent
import com.huawo.nt.sdkdemo.data.model.HealthDataCount
import com.huawo.nt.sdkdemo.data.model.AlbumIdleStorage
import com.huawo.nt.sdkdemo.data.model.AlbumPhotoItem
import com.huawo.nt.sdkdemo.data.model.AlbumTransferCallback
import com.huawo.nt.sdkdemo.data.model.AgpsTransferCallback
import com.huawo.nt.sdkdemo.data.model.BleGpsStatus
import com.huawo.nt.sdkdemo.data.model.OtaTransferCallback
import com.huawo.nt.sdkdemo.data.model.MusicStorage
import com.huawo.nt.sdkdemo.data.model.MusicTransferCallback
import com.huawo.nt.sdkdemo.data.model.OnlineWatchfaceTransferCallback
import com.huawo.nt.sdkdemo.data.model.WlOnlineWatchfaceTransferCallback
import com.huawo.nt.sdkdemo.data.model.ScanEvent
import com.huawo.nt.sdkdemo.data.model.SdkException
import com.huawo.nt.sdkdemo.util.AlbumBinConverter
import com.huawo.nt.sdkdemo.util.MediaZipUtils
import com.huawo.nt.sdkdemo.util.awaitBoolValue
import com.huawo.nt.sdkdemo.util.awaitCreateBond
import com.huawo.nt.sdkdemo.util.awaitIntValue
import com.huawo.nt.sdkdemo.util.awaitRemoveBond
import com.huawo.nt.sdkdemo.util.awaitStringValue
import com.huawo.nt.sdkdemo.util.awaitVoid
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.sdk.bluetoothsdk.callback.ConnectCallback
import com.huawo.sdk.bluetoothsdk.callback.DisconnectCallback
import com.huawo.sdk.bluetoothsdk.core.callback.ScanCallback
import com.huawo.sdk.bluetoothsdk.core.model.Device
import com.huawo.sdk.bluetoothsdk.interfaces.callback.ActivityNumCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.AlarmsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.AvailableStorageCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.BoolValueCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.ConnectionStateCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.ContactsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.DeviceInfoCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.DrinkWaterReminderCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.GoalCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.GpsStatusCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.HeartratesCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.HrvsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.IntArrayCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.SedentaryReminderCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.SleepsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.SocialAppSwitchesCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.Spo2Callback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.SportsCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.StressCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.StringListCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.UpgradeStatusCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ota.OtaCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ota.OtaData
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.UpgradeStatus
import com.huawo.sdk.bluetoothsdk.wl.ota.WlOtaCallback
import com.huawo.sdk.bluetoothsdk.wl.ota.WlOtaManager
import com.huawo.sdk.bluetoothsdk.wl.media.MediaTransferConfig
import com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferCallback
import com.huawo.sdk.bluetoothsdk.wl.media.WlMediaTransferManager
import com.huawo.sdk.bluetoothsdk.wl.media.models.MediaFileInfo
import com.huawo.watchface.Callback as SifliCallback
import com.huawo.watchface.SifliWatchSDK
import com.huawo.watchface.WatchfaceSDK
import com.huawo.sdk.bluetoothsdk.interfaces.ops.GetActivityNum
import com.huawo.sdk.bluetoothsdk.interfaces.ops.GetSports
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.ActivityNum
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Alarm
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Contact
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DeviceInfo
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DrinkWaterReminder
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.EmergencyContact
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Gender
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Goal
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.GoalType
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.GpsStatus
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Heartrate
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Hrv
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.RepeatPeriodUnit
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SedentaryReminder
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Sleep
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialAppSwitch
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialMessage
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.SocialType
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Spo2
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Sport
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Stress
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.TimePoint
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.Unit as MeasureUnit
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.UserInfo
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.WashHandReminder
import com.huawo.sdk.bluetoothsdk.interfaces.utils.LanguageUtils
import com.huawo.sdk.bluetoothsdk.spp.SppFilesTransferTask
import java.io.File
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
    private companion object {
        const val TAG = "BleRepository"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val boundStore = BoundDeviceStore(application)

    @Volatile
    private var initialized = false

    @Volatile
    private var connectionListenerRegistered = false

    /**
     * When true, [com.huawo.nt.sdkdemo.ui.main.HomeViewModel] must not auto-reconnect.
     * Same role as HaWoFit `ServiceManager.isQJSOTAServiceRunning()` during Sifli DFU:
     * DFU opens its own GATT; SDK reconnect would fight it and fail the upgrade.
     */
    @Volatile
    var sifliOtaInProgress: Boolean = false
        private set

    fun setSifliOtaInProgress(inProgress: Boolean) {
        sifliOtaInProgress = inProgress
    }

    /** True while demo OTA flag is set or Sifli DFU service reports busy. */
    fun isSifliOtaBlockingReconnect(): Boolean {
        if (sifliOtaInProgress) return true
        return runCatching {
            com.sifli.siflidfu.SifliDFUService.isDfuBusy()
        }.getOrDefault(false)
    }

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    private val connectionStateCallback =
        object : ConnectionStateCallback() {
            override fun onConnectionStateChange(connected: Boolean) {
                emitConnection(connected)
            }
        }

    /**
     * One-time BLE + Sifli stack warm-up (called from Home when the app is ready).
     *
     * - [BluetoothSDK]: scan / connect / GATT ops (maxMtu typically 247).
     * - [SifliWatchSDK]: zip file push for album / music / AGPS / online watchface (typed syncZipFile).
     * - [WatchfaceSDK]: custom watchface package push (`setCustomWatchface`).
     *   Also initialized in [com.huawo.nt.sdkdemo.SdkDemoApp]; duplicated here so BLE-only
     *   entry points cannot forget it. Safe to call init more than once.
     */
    fun init(maxMtu: Int = 247) {
        if (!initialized) {
            BluetoothSDK.init(application, maxMtu)
            SifliWatchSDK.getInstance().init(application)
            WatchfaceSDK.getInstance().init(application)
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

    fun currentConnectedName(): String? = BluetoothSDK.getConnectedDevice()?.name

    fun currentConnectedMac(): String? = BluetoothSDK.getConnectedDevice()?.mac

    /**
     * Query watch GPS / AGPS status ([BluetoothSDK.getDeviceGpsStatus]).
     */
    suspend fun getDeviceGpsStatus(): BleGpsStatus =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getDeviceGpsStatus(
                object : GpsStatusCallback() {
                    override fun onSuccess(status: GpsStatus?) {
                        mainHandler.post {
                            if (!cont.isActive) return@post
                            if (status == null) {
                                cont.resumeWithException(
                                    SdkException(-1, "getDeviceGpsStatus returned null"),
                                )
                            } else {
                                cont.resume(status.toModel())
                            }
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getDeviceGpsStatus failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

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

    /**
     * Drop the SDK GATT session but keep local bind / reconnect metadata.
     * Used before Sifli DFU so [com.sifli.siflidfu.SifliDFUService] can open its own connection.
     */
    fun disconnectWithoutClean() {
        BluetoothSDK.disconnectWithoutClean()
        emitConnection(false)
    }

    suspend fun startBind() = awaitVoid("startBind failed") { BluetoothSDK.startBind(it) }

    suspend fun endBind() = awaitVoid("endBind failed") { BluetoothSDK.endBind(it) }

    suspend fun createBond() = awaitCreateBond("createBond failed") { BluetoothSDK.createBond(it) }

    /**
     * Remove classic BT bond for the currently connected device.
     * Prefer [removeBondByMac] after BLE disconnect (same as HaWoFit removePair).
     */
    suspend fun removeBond() = awaitRemoveBond("removeBond failed") { BluetoothSDK.removeBond(it) }

    /** Remove classic BT bond by MAC — works after BLE is already disconnected. */
    suspend fun removeBondByMac(mac: String) =
        awaitRemoveBond("removeBond($mac) failed") { BluetoothSDK.removeBond(mac, it) }

    fun isBonded(mac: String): Boolean = BluetoothSDK.isBonded(mac)

    /**
     * Ask the watch to turn classic Bluetooth (BT) radio on/off.
     * HaWoFit: `setBTSwitch(false)` before removeBond on unbind; `setBTSwitch(true)` before createBond on bind.
     */
    suspend fun setBTSwitch(on: Boolean) =
        awaitVoid("setBTSwitch($on) failed") { BluetoothSDK.setBTSwitch(on, it) }

    /**
     * Turn watch BT on with optional auto-connect (used when already bonded).
     * HaWoFit createPair: `turnOnBTSwitchWithOption(true)` if already bonded.
     */
    suspend fun turnOnBTSwitchWithOption(autoConnect: Boolean) =
        awaitVoid("turnOnBTSwitchWithOption failed") {
            BluetoothSDK.turnOnBTSwitchWithOption(autoConnect, it)
        }

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
                                            hrvCount = activityNum.hrvNum,
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

    /**
     * Fetch HRV / SpO2 / stress records via [BluetoothSDK.getHrvs].
     * SDK queries activity-num first; empty list when `hrvNum == 0`.
     */
    suspend fun getHrvs(): List<BleHrv> {
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.getHrvs(
                object : HrvsCallback() {
                    override fun onSuccess(hrvList: List<Hrv>?) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resume(hrvList?.map { it.toModel() }.orEmpty())
                            }
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getHrvs failed"))
                            }
                        }
                    }
                },
            )
        }
    }

    /** Fetch jieli step records through an individual request. */
    suspend fun getStepV2(): List<BleActivity> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getStepV2(
                object : SportsCallback() {
                    override fun onSuccess(sportList: List<Sport>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(sportList?.map { it.toModel() }.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getStepV2 failed"))
                            }
                        }
                    }
                },
            )
        }

    /** Fetch jieli heart-rate records through an individual request. */
    suspend fun getHeartRateV2(): List<BleHeartrate> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getHeartRateV2(
                object : HeartratesCallback() {
                    override fun onSuccess(heartrateList: List<Heartrate>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(heartrateList?.map { it.toModel() }.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getHeartRateV2 failed"))
                            }
                        }
                    }
                },
            )
        }

    /** Fetch jieli sleep records through an individual request. */
    suspend fun getSleepV2(): List<BleSleep> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getSleepV2(
                object : SleepsCallback() {
                    override fun onSuccess(sleepList: List<Sleep>?) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resume(
                                    sleepList?.mapIndexed { index, sleep -> sleep.toModel(index) }.orEmpty(),
                                )
                            }
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getSleepV2 failed"))
                            }
                        }
                    }
                },
            )
        }

    /** Fetch jieli HRV records through an individual request. */
    suspend fun getHrvV2(): List<BleHrv> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getHrvV2(
                object : HrvsCallback() {
                    override fun onSuccess(hrvList: List<Hrv>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(hrvList?.map { it.toModel() }.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getHrvV2 failed"))
                            }
                        }
                    }
                },
            )
        }

    /** Fetch jieli blood-oxygen records and keep only the metric returned by this API. */
    suspend fun getSpo2V2(): List<BleHrv> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getSpo2V2(
                object : Spo2Callback() {
                    override fun onSuccess(spo2List: List<Spo2>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(spo2List?.map { it.toModel() }.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getSpo2V2 failed"))
                            }
                        }
                    }
                },
            )
        }

    /** Fetch jieli stress records and keep only the metric returned by this API. */
    suspend fun getStressV2(): List<BleHrv> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getStressV2(
                object : StressCallback() {
                    override fun onSuccess(stressList: List<Stress>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(stressList?.map { it.toModel() }.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getStressV2 failed"))
                            }
                        }
                    }
                },
            )
        }

    suspend fun deleteSports() = awaitVoid("deleteSports failed") { BluetoothSDK.delSports(it) }

    suspend fun deleteHeartrates() =
        awaitVoid("deleteHeartrates failed") { BluetoothSDK.delHeartrates(it) }

    suspend fun deleteSleeps() = awaitVoid("deleteSleeps failed") { BluetoothSDK.delSleeps(it) }

    suspend fun deleteHrvs() = awaitVoid("deleteHrvs failed") { BluetoothSDK.delHrv(it) }

    suspend fun deleteBlood() = awaitVoid("deleteBlood failed") { BluetoothSDK.delBlood(it) }

    suspend fun deleteStress() = awaitVoid("deleteStress failed") { BluetoothSDK.delStress(it) }

    suspend fun deleteHrvsV2() = awaitVoid("deleteHrvsV2 failed") { BluetoothSDK.delHrvV2(it) }

    // region §8 Goals

    suspend fun getGoals(): Goal =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getGoals(
                object : GoalCallback() {
                    override fun onSuccess(goal: Goal) {
                        mainHandler.post { if (cont.isActive) cont.resume(goal) }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getGoals failed"))
                            }
                        }
                    }
                },
            )
        }

    suspend fun setGoal(type: GoalType, value: Int) =
        awaitVoid("setGoal failed") { BluetoothSDK.setGoal(type, value, it) }

    /** Demo: step 8000, calorie 400, distance 5, sleep 8h, duration 30min */
    suspend fun setDemoGoals() {
        setGoal(GoalType.Step, 80)
        setGoal(GoalType.Calorie, 400)
        setGoal(GoalType.Distance, 5)
        setGoal(GoalType.Sleep, 8)
        setGoal(GoalType.Duration, 30)
    }

    // endregion

    // region §11 Alarms & reminders

    suspend fun getAlarms(): List<Alarm> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getAlarms(
                object : AlarmsCallback() {
                    override fun onSuccess(list: List<Alarm>?) {
                        mainHandler.post { if (cont.isActive) cont.resume(list.orEmpty()) }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getAlarms failed"))
                            }
                        }
                    }
                },
            )
        }

    suspend fun addAlarm(alarm: Alarm) {
        awaitVoid("addAlarm failed") { cb ->
            BluetoothSDK.addAlarm(alarm, cb)
        }
    }

    /**
     * WL alarm creation uses the ID selected from the current device alarm list.
     * Unlike [addAlarm], this calls addAlarmV2 directly and does not query an ID in the SDK.
     */
    suspend fun addAlarmV2(alarm: Alarm) {
        require(alarm.id in 1..5) { "JL alarm ID must be in 1..5: ${alarm.id}" }
        val time = alarm.firstTimePoint
        Log.i(TAG, "addAlarmV2 enter id=${alarm.id} time=${time?.hour}:${time?.minute} snooze=${alarm.snooze}")
        try {
            awaitVoid("addAlarmV2 failed") { cb ->
                BluetoothSDK.addAlarmV2(alarm, cb)
            }
            Log.i(TAG, "addAlarmV2 success id=${alarm.id}")
        } catch (error: Exception) {
            Log.e(TAG, "addAlarmV2 failed id=${alarm.id}", error)
            throw error
        }
    }

    suspend fun delAlarmBy(id: Int) =
        awaitVoid("delAlarmBy failed") { BluetoothSDK.delAlarmBy(id, it) }

    suspend fun delAllAlarms() =
        awaitVoid("delAllAlarms failed") { BluetoothSDK.delAllAlarms(it) }

    fun createDemoAlarm(
        hour: Int = 7,
        minute: Int = 30,
        content: String = "Wake up",
    ): Alarm =
        Alarm().apply {
            isOn = true
            this.content = content
            repeatPeriodUnit = RepeatPeriodUnit.Week
            setMonday(true)
            setTuesday(true)
            setWednesday(true)
            setThursday(true)
            setFriday(true)
            timePointList = listOf(TimePoint(hour, minute))
        }

    suspend fun getSedentaryReminder(): SedentaryReminder =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getSedentaryReminder(
                object : SedentaryReminderCallback() {
                    override fun onSuccess(reminder: SedentaryReminder) {
                        mainHandler.post { if (cont.isActive) cont.resume(reminder) }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getSedentaryReminder failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

    suspend fun setSedentaryReminder(reminder: SedentaryReminder) =
        awaitVoid("setSedentaryReminder failed") {
            BluetoothSDK.setSedentaryReminder(reminder, it)
        }

    fun createDemoSedentaryReminder(): SedentaryReminder =
        SedentaryReminder().apply {
            isOn = true
            startTime = TimePoint(9, 0)
            endTime = TimePoint(18, 0)
            interval = 60 * 60
            setMonday(true)
            setTuesday(true)
            setWednesday(true)
            setThursday(true)
            setFriday(true)
        }

    suspend fun getDrinkWaterReminder(): DrinkWaterReminder =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getDrinkWaterReminder(
                object : DrinkWaterReminderCallback() {
                    override fun onSuccess(reminder: DrinkWaterReminder) {
                        mainHandler.post { if (cont.isActive) cont.resume(reminder) }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getDrinkWaterReminder failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

    suspend fun setDrinkWaterReminder(reminder: DrinkWaterReminder) =
        awaitVoid("setDrinkWaterReminder failed") {
            BluetoothSDK.setDrinkWaterReminder(reminder, it)
        }

    fun createDemoDrinkWaterReminder(): DrinkWaterReminder =
        DrinkWaterReminder().apply {
            isOn = true
            startTime = TimePoint(8, 0)
            endTime = TimePoint(20, 0)
            interval = 60 * 60
            setMonday(true)
            setTuesday(true)
            setWednesday(true)
            setThursday(true)
            setFriday(true)
            setSaturday(true)
            setSunday(true)
        }

    suspend fun setWashHandReminder(reminder: WashHandReminder) =
        awaitVoid("setWashHandReminder failed") {
            BluetoothSDK.setWashHandReminder(reminder, it)
        }

    fun createDemoWashHandReminder(): WashHandReminder =
        WashHandReminder().apply {
            isOn = true
            startTime = TimePoint(8, 0)
            endTime = TimePoint(22, 0)
            interval = 2 * 60 * 60
            setMonday(true)
            setTuesday(true)
            setWednesday(true)
            setThursday(true)
            setFriday(true)
            setSaturday(true)
            setSunday(true)
        }

    // endregion

    // region §12 Notification / call / contacts

    suspend fun getSocialAppSwitches(): List<SocialAppSwitch> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getSocialAppSwitches(
                object : SocialAppSwitchesCallback() {
                    override fun onSuccess(list: List<SocialAppSwitch>?) {
                        mainHandler.post { if (cont.isActive) cont.resume(list.orEmpty()) }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getSocialAppSwitches failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

    suspend fun setSocialAppSwitch(type: SocialType, enabled: Boolean) {
        val sw = SocialAppSwitch(type.value, enabled)
        awaitVoid("setSocialAppSwitch failed") { BluetoothSDK.setSocialAppSwitch(sw, it) }
    }

    suspend fun pushMessage(
        type: SocialType = SocialType.SMS,
        title: String,
        content: String,
    ) {
        val msg =
            SocialMessage().apply {
                this.type = type.value
                this.title = title
                this.content = content
                time = System.currentTimeMillis()
            }
        awaitVoid("pushMessage failed") { BluetoothSDK.pushMessage(msg, it) }
    }

    suspend fun incomingCall(name: String, number: String) =
        awaitVoid("incomingCall failed") { BluetoothSDK.incommingCall(name, number, it) }

    suspend fun hangUpCall(name: String, number: String) =
        awaitVoid("hangUpCall failed") { BluetoothSDK.hangUpCall(name, number, it) }

    suspend fun getContacts(): List<Contact> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getContacts(
                object : ContactsCallback() {
                    override fun onSuccess(list: List<Contact>?) {
                        mainHandler.post { if (cont.isActive) cont.resume(list.orEmpty()) }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(SdkException(code, "getContacts failed"))
                            }
                        }
                    }
                },
            )
        }

    suspend fun setContacts(contacts: List<Contact>) =
        awaitVoid("setContacts failed") { BluetoothSDK.setContacts(contacts, it) }

    suspend fun setEmergencyContact(name: String, phone: String) {
        val contact = EmergencyContact(phone, name)
        awaitVoid("setEmergencyContact failed") {
            BluetoothSDK.setEmergencyContact(contact, it)
        }
    }

    fun createDemoContacts(): List<Contact> =
        listOf(
            Contact("1", "Alice", "13800000001"),
            Contact("2", "Bob", "13800000002"),
            Contact("3", "Carol", "13800000003"),
        )

    // endregion

    // region §14 Music file push (SPP / Sifli)
    //
    // Channel strategy (aligned with HaWoFit MusicSelectActivity; Demo does not use WL):
    // 1) Classic BT connected → SPP: SppFilesTransferTask.sendMusicFiles
    // 2) Otherwise → Sifli ZIP: zip files then SifliWatchSDK.syncZipFile(..., type=4)
    // 3) SPP failure and not user cancel (code=26) → fallback to Sifli
    // Music and album are mutually exclusive; cannot run concurrently.

    /** In-progress SPP music task; stopSending on cancel. */
    @Volatile
    private var sppMusicTask: SppFilesTransferTask? = null

    /** Query watch music available/total storage, units in KB. */
    suspend fun getMusicStorage(): MusicStorage =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getDeviceMusicAvailableStorage(
                object : AvailableStorageCallback() {
                    override fun onSuccess(available: Int, total: Int) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(MusicStorage(available, total))
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getDeviceMusicAvailableStorage failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

    /**
     * Whether classic Bluetooth (BT Classic) is connected.
     * Unlike [isBonded]: bonded does not mean the profile is connected.
     * Music/album prefer SPP only when this returns true.
     */
    suspend fun isClassicBtConnected(): Boolean =
        awaitBoolValue("getBTConnectionState failed") {
            BluetoothSDK.getBTConnectionState(it)
        }

    fun isMusicTransferring(): Boolean =
        SppFilesTransferTask.isTransferring() ||
            SifliWatchSDK.getInstance().isWorking

    /**
     * Push music files to the watch.
     *
     * @param files Local readable files (caller verified existence)
     * @param callback Channel name, progress (0~1), success/failure all on main thread
     *
     * Channel: classic BT connected → SPP; otherwise Sifli ZIP (type=4).
     * SPP failure (except cancel code=26) falls back to Sifli.
     */
    fun pushMusicFiles(files: List<File>, callback: MusicTransferCallback) {
        if (files.isEmpty()) {
            callback.onFail(-1, "No music files")
            return
        }
        if (isMusicTransferring() || isAlbumTransferring()) {
            callback.onFail(-2, "Transfer already in progress")
            return
        }
        // Check classic BT connection first, then choose SPP or Sifli
        BluetoothSDK.getBTConnectionState(
            object : BoolValueCallback() {
                override fun onSuccess(btConnected: Boolean) {
                    mainHandler.post {
                        if (btConnected) {
                            pushMusicViaSpp(files, callback, allowFallback = true)
                        } else {
                            pushMusicViaSifli(files, callback)
                        }
                    }
                }

                override fun onFail(code: Int) {
                    // If BT state query fails, still try Sifli (depends on BLE + MAC)
                    mainHandler.post { pushMusicViaSifli(files, callback) }
                }
            },
        )
    }

    /** Cancel in-progress SPP or Sifli music transfer. */
    fun cancelMusicTransfer() {
        sppMusicTask?.stopSending()
        sppMusicTask = null
        if (SifliWatchSDK.getInstance().isWorking) {
            runCatching { SifliWatchSDK.getInstance().stop() }
        }
    }

    /**
     * Push music via classic BT SPP.
     * @param allowFallback Whether to fall back to Sifli on failure (user cancel code=26 does not fallback)
     */
    private fun pushMusicViaSpp(
        files: List<File>,
        callback: MusicTransferCallback,
        allowFallback: Boolean,
    ) {
        callback.onChannel("SPP")
        val task = SppFilesTransferTask()
        sppMusicTask = task
        task.sendMusicFiles(
            files,
            object : OtaCallback() {
                override fun onReady() {
                    mainHandler.post { callback.onReady() }
                }

                override fun onUpload(progress: Float) {
                    mainHandler.post { callback.onProgress(progress) }
                }

                override fun onSuccess() {
                    mainHandler.post {
                        sppMusicTask = null
                        callback.onSuccess()
                    }
                }

                override fun onFail(code: Int) {
                    mainHandler.post {
                        sppMusicTask = null
                        // 26: production cancel code; do not fallback to avoid opening a second channel
                        val cancelled = code == 26
                        if (allowFallback && !cancelled) {
                            pushMusicViaSifli(files, callback)
                        } else {
                            callback.onFail(code, "SPP push failed")
                        }
                    }
                }
            },
        )
    }

    /**
     * Push music via Sifli channel:
     * 1) Zip selected files (aligned with HaWoFit FileUtils.compressFilesToZip)
     * 2) [SifliWatchSDK.syncZipFile] type=4 = music; needByteAlign=true matches production
     */
    private fun pushMusicViaSifli(files: List<File>, callback: MusicTransferCallback) {
        val mac = connectedMacOrNull()
        if (mac.isNullOrBlank()) {
            callback.onFail(-5, "Device MAC unavailable for Sifli push")
            return
        }
        callback.onChannel("Sifli")
        Thread {
            try {
                val zip =
                    MediaZipUtils.zipFiles(
                        application,
                        files,
                        "music_${System.currentTimeMillis()}.zip",
                    )
                mainHandler.post {
                    callback.onReady()
                    SifliWatchSDK.getInstance().syncZipFile(
                        true,
                        mac,
                        zip.absolutePath,
                        4,
                        object : SifliCallback {
                            override fun onProgress(current: Long, total: Long) {
                                val progress =
                                    if (total > 0L) current.toFloat() / total.toFloat() else 0f
                                mainHandler.post { callback.onProgress(progress) }
                            }

                            override fun onSuccess() {
                                mainHandler.post { callback.onSuccess() }
                            }

                            override fun onError(code: Int) {
                                mainHandler.post {
                                    callback.onFail(code, "Sifli music push failed")
                                }
                            }

                            override fun onCancel() {
                                mainHandler.post {
                                    callback.onFail(14, "Sifli music push cancelled")
                                }
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback.onFail(-4, e.message ?: "Sifli music zip failed")
                }
            }
        }.start()
    }

    // endregion

    // region §14 Album file push (SPP / Sifli)
    //
    // Channel strategy similar to music (Demo does not use WL):
    // 1) Classic BT connected → convert to ezip bin then SPP sendAblumFiles
    // 2) Otherwise → convert to bin then zip, Sifli syncZipFile type=3
    // 3) SPP failure (not cancel) → fallback to Sifli
    // Slots: free indices from 1..50 excluding watch IDs and locally selected IDs

    @Volatile
    private var sppAlbumTask: SppFilesTransferTask? = null

    /** HaWoFit album max photo count. */
    private val albumMaxSlots = 50

    /** Album free storage in KB (idle only, no total). */
    suspend fun getAlbumIdleStorage(): AlbumIdleStorage {
        val available =
            awaitIntValue("getDeviceAlbumIdleStorage failed") {
                BluetoothSDK.getDeviceAlbumIdleStorage(it)
            }
        return AlbumIdleStorage(available)
    }

    suspend fun getAlbumFileIdList(): List<Int> =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getDeviceAlbumFileIdList(
                object : IntArrayCallback() {
                    override fun onSuccess(intArray: List<Int>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(intArray.orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getDeviceAlbumFileIdList failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

    /**
     * Allocate free album slot indices in 1..[albumMaxSlots] (typically 50).
     *
     * Excludes:
     * - IDs already on the watch ([getAlbumFileIdList])
     * - Caller-held [alreadySelected] (for append flows; current UI replaces the list, so usually empty)
     *
     * @return May be shorter than [count] when free slots are insufficient; empty if none free
     */
    suspend fun allocateAlbumIndices(
        count: Int,
        alreadySelected: Collection<Int> = emptyList(),
    ): List<Int> {
        val occupied =
            runCatching { getAlbumFileIdList() }.getOrDefault(emptyList()).toMutableSet()
        occupied.addAll(alreadySelected)
        val free = (1..albumMaxSlots).filter { it !in occupied }
        return free.take(count)
    }

    /** True if an SPP album task or Sifli SDK work is in progress. */
    fun isAlbumTransferring(): Boolean =
        SppFilesTransferTask.isTransferring() ||
            SifliWatchSDK.getInstance().isWorking

    /**
     * Push album photos to the watch.
     *
     * Channel policy:
     * 1. Classic BT connected → prefer [pushAlbumViaSpp] (send ezip bins)
     * 2. Otherwise / BT state query failed → [pushAlbumViaSifli] (ezip then ZIP, type=3)
     * 3. On SPP failure (except user cancel, code!=26), automatically fall back to Sifli
     *
     * @param width  Convert target width (UI "watch size")
     * @param height Convert target height
     */
    fun pushAlbumFiles(
        photos: List<AlbumPhotoItem>,
        callback: AlbumTransferCallback,
        width: Int = AlbumBinConverter.DEFAULT_WIDTH,
        height: Int = AlbumBinConverter.DEFAULT_HEIGHT,
    ) {
        if (photos.isEmpty()) {
            callback.onFail(-1, "No album files")
            return
        }
        // Mutual exclusion with music transfer (shared SPP / Sifli resources)
        if (isAlbumTransferring() || isMusicTransferring()) {
            callback.onFail(-2, "Transfer already in progress")
            return
        }
        BluetoothSDK.getBTConnectionState(
            object : BoolValueCallback() {
                override fun onSuccess(btConnected: Boolean) {
                    mainHandler.post {
                        if (btConnected) {
                            pushAlbumViaSpp(photos, callback, width, height, allowFallback = true)
                        } else {
                            pushAlbumViaSifli(photos, callback, width, height)
                        }
                    }
                }

                override fun onFail(code: Int) {
                    // Unknown classic-BT state → still try Sifli
                    mainHandler.post {
                        pushAlbumViaSifli(photos, callback, width, height)
                    }
                }
            },
        )
    }

    /** Stop SPP album task and/or Sifli sync (whichever is active). */
    fun cancelAlbumTransfer() {
        sppAlbumTask?.stopSending()
        sppAlbumTask = null
        if (SifliWatchSDK.getInstance().isWorking) {
            runCatching { SifliWatchSDK.getInstance().stop() }
        }
    }

    /**
     * Classic BT SPP push: convert on a worker thread via [AlbumBinConverter.convert],
     * then send on the main thread with [SppFilesTransferTask.sendAblumFiles].
     *
     * @param allowFallback if true, convert failure or SPP onFail (non-cancel) switches to Sifli
     */
    private fun pushAlbumViaSpp(
        photos: List<AlbumPhotoItem>,
        callback: AlbumTransferCallback,
        width: Int,
        height: Int,
        allowFallback: Boolean,
    ) {
        callback.onChannel("SPP")
        Thread {
            try {
                val bins =
                    photos.map { item ->
                        AlbumBinConverter.convert(
                            application,
                            item.index,
                            item.file.absolutePath,
                            width,
                            height,
                        )
                    }
                val files = AlbumBinConverter.flatTransferFiles(bins)
                mainHandler.post {
                    val task = SppFilesTransferTask()
                    sppAlbumTask = task
                    task.sendAblumFiles(
                        files,
                        object : OtaCallback() {
                            override fun onReady() {
                                mainHandler.post { callback.onReady() }
                            }

                            override fun onUpload(progress: Float) {
                                mainHandler.post { callback.onProgress(progress) }
                            }

                            override fun onSuccess() {
                                mainHandler.post {
                                    sppAlbumTask = null
                                    callback.onSuccess()
                                }
                            }

                            override fun onFail(code: Int) {
                                mainHandler.post {
                                    sppAlbumTask = null
                                    // code 26: user/upper-layer cancel — do not auto-switch channel
                                    val cancelled = code == 26
                                    if (allowFallback && !cancelled) {
                                        pushAlbumViaSifli(photos, callback, width, height)
                                    } else {
                                        callback.onFail(code, "SPP album push failed")
                                    }
                                }
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (allowFallback) {
                        pushAlbumViaSifli(photos, callback, width, height)
                    } else {
                        callback.onFail(-4, e.message ?: "SPP album convert failed")
                    }
                }
            }
        }.start()
    }

    /**
     * Sifli push: convert to ezip → zip → [SifliWatchSDK.syncZipFile] (type=3 = album).
     * Requires a valid device MAC (connected or locally bound).
     */
    private fun pushAlbumViaSifli(
        photos: List<AlbumPhotoItem>,
        callback: AlbumTransferCallback,
        width: Int,
        height: Int,
    ) {
        val mac = connectedMacOrNull()
        if (mac.isNullOrBlank()) {
            callback.onFail(-5, "Device MAC unavailable for Sifli push")
            return
        }
        callback.onChannel("Sifli")
        Thread {
            try {
                val bins =
                    photos.map { item ->
                        AlbumBinConverter.convert(
                            application,
                            item.index,
                            item.file.absolutePath,
                            width,
                            height,
                        )
                    }
                val zip =
                    MediaZipUtils.zipFiles(
                        application,
                        AlbumBinConverter.flatTransferFiles(bins),
                        "album_${System.currentTimeMillis()}.zip",
                    )
                mainHandler.post {
                    callback.onReady()
                    SifliWatchSDK.getInstance().syncZipFile(
                        true,
                        mac,
                        zip.absolutePath,
                        3, // Sifli file type: 3 = album
                        object : SifliCallback {
                            override fun onProgress(current: Long, total: Long) {
                                val progress =
                                    if (total > 0L) current.toFloat() / total.toFloat() else 0f
                                mainHandler.post { callback.onProgress(progress) }
                            }

                            override fun onSuccess() {
                                mainHandler.post { callback.onSuccess() }
                            }

                            override fun onError(code: Int) {
                                mainHandler.post {
                                    callback.onFail(code, "Sifli album push failed")
                                }
                            }

                            override fun onCancel() {
                                mainHandler.post {
                                    callback.onFail(14, "Sifli album push cancelled")
                                }
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback.onFail(-4, e.message ?: "Sifli album convert/zip failed")
                }
            }
        }.start()
    }

    /**
     * Device MAC required for Sifli push:
     * Prefer current BLE connected device, otherwise fall back to local bound record.
     */
    private fun connectedMacOrNull(): String? =
        BluetoothSDK.getConnectedDevice()?.mac?.takeIf { it.isNotBlank() }
            ?: boundStore.load()?.macAddress?.takeIf { it.isNotBlank() }

    // endregion

    // region §13 Online watchface (Sifli, type=5)
    //
    // Reference: HaWoFit DeviceSDKProxy.setOnlineWatchface (QJS / hasQJSFeature branch).
    // Demo intentionally implements ONLY the Sifli path:
    //   write/keep zip on disk → SifliWatchSDK.syncZipFile(needByteAlign=false, type=5)
    // Do NOT use BluetoothSDK.setOnlineWatchface (generic) or WL MediaTransfer here.

    /**
     * List watchface names already present on the Sifli watch.
     *
     * Used before install to decide “switch only” vs “download + push”.
     * Empty list / failure is handled by the ViewModel (fallback to download).
     */
    suspend fun getSifliWatchfaces(): List<String> {
        return suspendCancellableCoroutine { cont ->
            BluetoothSDK.getSifliWatchfaces(
                object : StringListCallback() {
                    override fun onSuccess(valueList: MutableList<String>?) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(valueList?.toList().orEmpty())
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getSifliWatchfaces failed"),
                                )
                            }
                        }
                    }
                },
            )
        }
    }

    /**
     * Activate an already-installed Sifli watchface by [name] (no file transfer).
     * Prefer this when the catalog face is detected on-device — much faster than re-push.
     */
    suspend fun switchSifliWatchfaceBy(name: String) {
        awaitVoid("switchSifliWatchfaceBy failed") {
            BluetoothSDK.switchSifliWatchfaceBy(name, it)
        }
    }

    /**
     * Push a verified online-watchface zip to the watch over Sifli BLE ZIP.
     *
     * ## syncZipFile arguments (must match HaWoFit QJS online install)
     * - `needByteAlign` = **false** (music uses true; watchface uses false)
     * - `type` = **5** (online watchface; 3=album/AGPS, 4=music)
     * - `mac` = connected BLE MAC (fallback: bound store)
     *
     * ## Preconditions
     * - [zipFile] exists and non-empty (caller already MD5-checked when server provided hash)
     * - Sifli SDK not busy (`isWorking` → code 190)
     * - BLE connected
     *
     * Callbacks are delivered on the main thread.
     */
    fun pushOnlineWatchfaceZip(zipFile: File, callback: OnlineWatchfaceTransferCallback) {
        if (!zipFile.exists() || zipFile.length() == 0L) {
            callback.onFail(-3, "Watchface zip missing or empty")
            return
        }
        if (SifliWatchSDK.getInstance().isWorking) {
            callback.onFail(190, "Sifli SDK is busy")
            return
        }
        val mac = connectedMacOrNull()
        if (mac.isNullOrBlank()) {
            callback.onFail(-5, "Device MAC unavailable for watchface push")
            return
        }
        if (!isConnected()) {
            callback.onFail(408, "BLE disconnected")
            return
        }
        mainHandler.post {
            callback.onReady()
            SifliWatchSDK.getInstance().syncZipFile(
                false,
                mac,
                zipFile.absolutePath,
                5,
                object : SifliCallback {
                    override fun onProgress(current: Long, total: Long) {
                        val progress =
                            if (total > 0L) current.toFloat() / total.toFloat() else 0f
                        mainHandler.post { callback.onProgress(progress) }
                    }

                    override fun onSuccess() {
                        mainHandler.post { callback.onSuccess() }
                    }

                    override fun onError(code: Int) {
                        mainHandler.post {
                            callback.onFail(code, "Sifli watchface push failed")
                        }
                    }

                    override fun onCancel() {
                        mainHandler.post {
                            callback.onFail(14, "Sifli watchface push cancelled")
                        }
                    }
                },
            )
        }
    }

    /**
     * jieli online watchface uses the A3 media channel with type 0x04. The SDK owns packet framing,
     * validation and retry behavior; this demo layer only validates caller input and bridges the
     * terminal device result back to the main thread through [callback].
     */
    fun pushWlOnlineWatchface(
        packageFile: File,
        watchfaceName: String,
        callback: WlOnlineWatchfaceTransferCallback,
    ) {
        if (!isConnected()) {
            Log.w(TAG, "pushWlOnlineWatchface rejected: BLE disconnected")
            callback.onFail(408, "BLE disconnected")
            return
        }
        if (watchfaceName.isBlank()) {
            Log.w(TAG, "pushWlOnlineWatchface rejected: blank name")
            callback.onFail(-6, "WL watchface name is blank")
            return
        }
        if (!packageFile.isFile || packageFile.length() <= 1L) {
            Log.w(TAG, "pushWlOnlineWatchface rejected: invalid file=${packageFile.absolutePath}")
            callback.onFail(-3, "WL watchface package missing or empty: ${packageFile.absolutePath}")
            return
        }
        val fileInfo =
            MediaFileInfo.fromFile(packageFile.absolutePath, watchfaceName, 0x00)
                ?: run {
                    callback.onFail(-3, "WL MediaFileInfo create failed: ${packageFile.absolutePath}")
                    return
                }
        Log.i(TAG, "pushWlOnlineWatchface start name=$watchfaceName bytes=${packageFile.length()}")
        WlMediaTransferManager.getInstance().transfer(
            MediaTransferConfig.MEDIA_TYPE_WATCH_ONLINE,
            listOf(fileInfo),
            object : WlMediaTransferCallback {
                override fun onReady() {
                    mainHandler.post {
                        Log.i(TAG, "pushWlOnlineWatchface ready name=$watchfaceName")
                        callback.onReady()
                    }
                }

                override fun onProgress(progress: Float) {
                    mainHandler.post { callback.onProgress(progress.coerceIn(0f, 1f)) }
                }

                override fun onSuccess() {
                    mainHandler.post {
                        Log.i(TAG, "pushWlOnlineWatchface success name=$watchfaceName")
                        callback.onSuccess()
                    }
                }

                override fun onFail(code: Int, message: String?) {
                    mainHandler.post {
                        val detail = message.orEmpty().ifBlank { "WL A3 transfer failed" }
                        Log.e(TAG, "pushWlOnlineWatchface failed code=$code name=$watchfaceName msg=$detail")
                        callback.onFail(code, detail)
                    }
                }
            },
        )
    }

    // endregion

    // region §15 AGPS zip push (Sifli, type=3)
    // Aligned with HaWoFit DeviceGpsUpgradeManager.pushZIP2Device(UpgradeType.AGPS)

    /**
     * Push a prepared AGPS zip to the watch via [SifliWatchSDK.syncZipFile] type=3.
     * Zip entries should already be under `music/gps/agps/` (see [com.huawo.nt.sdkdemo.util.AgpsXywBuilder]).
     */
    fun pushAgpsZip(zipFile: File, callback: AgpsTransferCallback) {
        if (!zipFile.exists() || zipFile.length() == 0L) {
            callback.onFail(-3, "AGPS zip missing or empty")
            return
        }
        if (SifliWatchSDK.getInstance().isWorking) {
            callback.onFail(190, "Sifli SDK is busy")
            return
        }
        val mac = connectedMacOrNull()
        if (mac.isNullOrBlank()) {
            callback.onFail(-5, "Device MAC unavailable for AGPS push")
            return
        }
        if (!isConnected()) {
            callback.onFail(408, "BLE disconnected")
            return
        }
        mainHandler.post {
            callback.onReady()
            SifliWatchSDK.getInstance().syncZipFile(
                true,
                mac,
                zipFile.absolutePath,
                3, // same type as DeviceGpsUpgradeManager UpgradeType.AGPS
                object : SifliCallback {
                    override fun onProgress(current: Long, total: Long) {
                        val progress =
                            if (total > 0L) current.toFloat() / total.toFloat() else 0f
                        mainHandler.post { callback.onProgress(progress) }
                    }

                    override fun onSuccess() {
                        mainHandler.post { callback.onSuccess() }
                    }

                    override fun onError(code: Int) {
                        mainHandler.post {
                            callback.onFail(code, "AGPS push failed")
                        }
                    }

                    override fun onCancel() {
                        mainHandler.post {
                            callback.onFail(10006, "AGPS push cancelled")
                        }
                    }
                },
            )
        }
    }

    fun cancelAgpsTransfer() {
        if (SifliWatchSDK.getInstance().isWorking) {
            runCatching { SifliWatchSDK.getInstance().stop() }
        }
    }

    // endregion

    // region §17 OTA firmware upgrade
    //
    // SDK wrappers used by the OTA page for pre-checks and alternate channels.
    // This demo's primary upgrade path is Sifli DFU (see OtaUpgradeViewModel +
    // SifliOtaHelper); [startOta] / [startWlOta] remain available for non-Sifli devices.

    /** True when the connected device speaks WL protocol (use [startWlOta]). */
    fun isWlProtocol(): Boolean = BluetoothSDK.isWlProtocol()

    /** Prefer BLE connected MAC; fall back to locally bound record. */
    fun connectedDeviceMac(): String? = connectedMacOrNull()

    suspend fun getFirmwareVersion(): String =
        awaitStringValue("getFirmwareVersion failed") { BluetoothSDK.getFirmwareVersion(it) }

    suspend fun getBattery(): Int =
        awaitIntValue("getBattery failed") { BluetoothSDK.getBattery(it) }

    /**
     * Query device upgrade state before starting OTA.
     * Only [UpgradeStatus.Normal] should proceed; Recovering / WaitOta / OTAing → wait.
     */
    suspend fun getDeviceUpgradeStatus(): UpgradeStatus =
        suspendCancellableCoroutine { cont ->
            BluetoothSDK.getDeviceUpgradeStatus(
                object : UpgradeStatusCallback() {
                    override fun onSuccess(status: UpgradeStatus) {
                        mainHandler.post {
                            if (cont.isActive) cont.resume(status)
                        }
                    }

                    override fun onFail(code: Int) {
                        mainHandler.post {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    SdkException(code, "getDeviceUpgradeStatus failed"),
                                )
                            }
                        }
                    }
                },
            )
        }

    /**
     * Generic (non-WL, non-Sifli) multi-file OTA.
     * [otaList] items must already include address header bytes if the bin format requires them.
     */
    fun startOta(otaList: List<OtaData>, callback: OtaTransferCallback) {
        if (otaList.isEmpty()) {
            callback.onFail(-3, "OTA data empty")
            return
        }
        BluetoothSDK.ota(
            otaList,
            object : OtaCallback() {
                override fun onReady() {
                    mainHandler.post { callback.onReady() }
                }

                override fun onUpload(progress: Float) {
                    mainHandler.post { callback.onProgress(progress) }
                }

                override fun onSuccess() {
                    mainHandler.post { callback.onSuccess() }
                }

                override fun onFail(code: Int) {
                    mainHandler.post { callback.onFail(code, "OTA failed") }
                }
            },
        )
    }

    /** WL protocol OTA via a local firmware file path. */
    fun startWlOta(filePath: String, callback: OtaTransferCallback) {
        BluetoothSDK.starWlOta(
            filePath,
            object : WlOtaCallback {
                override fun onReady() {
                    mainHandler.post { callback.onReady() }
                }

                override fun onProgress(progress: Float) {
                    mainHandler.post { callback.onProgress(progress) }
                }

                override fun onSuccess() {
                    mainHandler.post { callback.onSuccess() }
                }

                override fun onFail(code: Int, message: String?) {
                    mainHandler.post {
                        callback.onFail(code, message ?: "WL OTA failed")
                    }
                }
            },
        )
    }

    /** Clear WL OTA session (call when leaving the upgrade page as a safety net). */
    fun forceResetWlOta() {
        runCatching { WlOtaManager.forceReset() }
    }

    // endregion

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

    private fun GpsStatus.toModel() =
        BleGpsStatus(
            agpsValidStartTimeMs = agpsValidStartTime,
            agpsValidEndTimeMs = agpsValidEndTime,
            gpsClipType = gpsClipType,
            gpsFirmwareVersion = gpsFirmwareVersion,
            gpsFirmwareBuild = gpsFirmwareBuild,
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

    private fun Heartrate.toModel() =
        BleHeartrate(index = index, timeMs = time, bpm = bpm, resting = isResting)

    /**
     * Map SDK [Hrv]: `fatigue` → [BleHrv.hrv] (HRV), `stress` → stress, `spo2` → SpO2.
     */
    private fun Hrv.toModel() =
        BleHrv(
            index = index,
            timeMs = time,
            hrv = fatigue,
            stress = stress,
            spo2 = spo2,
        )

    private fun Spo2.toModel() = BleHrv(index = index, timeMs = time, spo2 = spo2)

    private fun Stress.toModel() = BleHrv(index = index, timeMs = time, stress = stress)

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
