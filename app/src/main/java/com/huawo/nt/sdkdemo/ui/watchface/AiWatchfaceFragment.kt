package com.huawo.nt.sdkdemo.ui.watchface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.huawo.ai.AiCenter
import com.huawo.ai.event.AiEvent
import com.huawo.ai.handler.IErrorMessageProvider
import com.huawo.ai.handler.model.AiDeviceInfo
import com.huawo.ai.logger.ILog
import com.huawo.ai.service.AFlashVoiceRecorderService
import com.huawo.ai.utils.SpUtils
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentAiWatchfaceBinding
import com.huawo.nt.sdkdemo.util.AppLanguage
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.nt.sdkdemo.util.PcmPlayer
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.sdk.bluetoothsdk.HwPlatformType
import com.huawo.sdk.bluetoothsdk.interfaces.callback.DeviceInfoCallback
import com.huawo.sdk.bluetoothsdk.interfaces.callback.StringValueCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.AppStatus
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.DeviceInfo
import com.huawo.watchface.custom.SifliCustomWatchface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI Watchface demo tab (under [WatchfaceFragment] / AI Center entry).
 *
 * ## Recording paths (must support both — same as HaWoFit)
 *
 * Firmware reports [DeviceInfo.isRecordFromDevice]. Product apps (HaWoFit `MainActivity.initAIWatchFace`
 * / `WatchfaceEditV5Activity`) then call [AiCenter.setRecordFromDevice]:
 *
 * | Path | `recordFromDevice` | How audio is captured |
 * |------|--------------------|------------------------|
 * | **Watch mic** | `true` | Watch records; AiCenter pulls PCM via BLE (`getAiRecordData` / JieLi V2). No phone SCO. |
 * | **Phone / App** | `false` | Phone [AudioRecord]. If `speakOnWatch=true`, [BluetoothScoManager] opens **Bluetooth SCO** so the watch HFP mic is the Android input device. |
 *
 * [AFlashVoiceRecorderService] locks the source at `startRecording()`:
 * - JieLi + `recordFromDevice` → device JieLi path
 * - Non-JieLi + `recordFromDevice` + `speakOnWatch` → legacy device path
 * - Otherwise → App path (`speakOnWatch` → SCO + `VoiceRecorderService`; else phone mic)
 *
 * This demo exposes **Auto / Watch / Phone mic / Watch via SCO** so both product paths can be tested.
 * **Auto** mirrors HaWoFit: `JIELI || firmware.recordFromDevice` → watch; else phone path + [AiCenter.startRecordService].
 *
 * ## Platform
 * [AiDeviceInfo.setPlatformType] selects install/record handlers (SIFLI QJS vs JieLi WL, etc.).
 * Auto uses `protocolVersion >= 100` → [HwPlatformType.JIELI], else [HwPlatformType.SIFLI]
 * (same idea as deprecated `setProtocolVersion`).
 *
 * ## Typical watch-driven flow
 * ```
 * connect → setDeviceInfo + setRecordFromDevice + startWorking
 *   → watch opens AI Watchface → records (device or SCO)
 *   → cloud image → deviceAiImageCallback / devicePerviewImageCallback
 *   → install OTA → deviceOtaWatchfaceDone
 * ```
 *
 * ## Caveats
 * - SCO needs classic Bluetooth / HFP to the watch, not only BLE GATT.
 * - Phone / SCO paths need `RECORD_AUDIO` (+ `FOREGROUND_SERVICE_MICROPHONE` on API 34+).
 * - Watch-mic path does not need phone mic permission for watch-triggered AI.
 * - Geometry (W/H/R) must match the real panel; wrong values break packaging / OTA.
 */
class AiWatchfaceFragment : Fragment(), AiEvent, IErrorMessageProvider {

    private var _binding: FragmentAiWatchfaceBinding? = null
    private val binding get() = _binding!!

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logBuilder = StringBuilder()

    private var pcmPlayer: PcmPlayer? = null
    private var voiceRecorder: AFlashVoiceRecorderService? = null
    private var recording = false
    private var aiStarted = false

    /**
     * Last [DeviceInfo.isRecordFromDevice] from firmware (HaWoFit stores this on bind/connect).
     * Used by [RecordMode.AUTO].
     */
    private var firmwareRecordFromDevice: Boolean? = null

    /** Last [DeviceInfo.getProtocolVersion]; used to infer JIELI vs SIFLI when platform = Auto. */
    private var firmwareProtocolVersion: Int? = null

    private val sizePresets =
        listOf(
            SizePreset("480 x 480", 480, 480, 240, 264, 264, 132),
            SizePreset("466 x 466", 466, 466, 233, 264, 264, 132),
            SizePreset("410 x 502", 410, 502, 108, 200, 244, 50),
        )

    private val recordModes = RecordMode.entries
    private val platformChoices = PlatformChoice.entries

    /**
     * Pending action after mic / FGS-mic / notification permission result.
     * Phone & SCO paths need [AiCenter.startRecordService] only when RECORD_AUDIO is granted.
     */
    private enum class PendingMicAction {
        NONE,
        START_RECORD_SERVICE,
        DEBUG_RECORD,
    }

    private var pendingMicAction = PendingMicAction.NONE

    private val requestMicPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val audioOk = result[Manifest.permission.RECORD_AUDIO] == true || hasRecordAudio()
            val action = pendingMicAction
            pendingMicAction = PendingMicAction.NONE
            if (action == PendingMicAction.NONE) return@registerForActivityResult
            if (!audioOk) {
                Toast.makeText(requireContext(), R.string.ai_wf_mic_denied, Toast.LENGTH_SHORT)
                    .show()
                appendLine(getString(R.string.ai_wf_mic_denied))
                return@registerForActivityResult
            }
            appendLine(getString(R.string.ai_wf_mic_granted))
            when (action) {
                PendingMicAction.START_RECORD_SERVICE -> startRecordServiceSafe()
                PendingMicAction.DEBUG_RECORD -> startDebugRecord()
                PendingMicAction.NONE -> Unit
            }
        }

    private val aiLog =
        object : ILog {
            override fun d(tag: String?, msg: String) = appendLog("D", tag, msg)

            override fun i(tag: String?, msg: String) = appendLog("I", tag, msg)

            override fun w(tag: String?, msg: String) = appendLog("W", tag, msg)

            override fun e(tag: String?, msg: String) = appendLog("E", tag, msg)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAiWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SpUtils.init(requireContext().applicationContext)
        setupSizeSpinner()
        setupRecordModeSpinner()
        setupPlatformSpinner()
        restoreFields()
        binding.tvFirmwareRecordHint.setText(R.string.ai_wf_fw_record_unknown)
        binding.tvStatus.setText(R.string.feature_ready)

        binding.btnApply.setOnClickListener { applyDeviceInfo() }
        binding.btnShare.setOnClickListener { sharePcm() }
        binding.btnPlay.setOnClickListener { playPcm() }
        binding.btnRecord.setOnClickListener { toggleDebugRecord() }

        fetchDeviceId()
        fetchDeviceInfoForRecordCapability()
        startAiCenter()
    }

    override fun onDestroyView() {
        stopDebugRecordInternal()
        pcmPlayer?.stop()
        pcmPlayer = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (aiStarted) {
            runCatching { AiCenter.getInstance().destroy() }
            aiStarted = false
        }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Spinners
    // -------------------------------------------------------------------------

    private fun setupSizeSpinner() {
        binding.spinnerSizePreset.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                sizePresets.map { it.label },
            )
        binding.spinnerSizePreset.setSelection(0)
        binding.spinnerSizePreset.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    applyPreset(sizePresets[position])
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun setupRecordModeSpinner() {
        binding.spinnerRecordMode.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                recordModes.map { getString(it.labelRes) },
            )
        binding.spinnerRecordMode.setSelection(RecordMode.AUTO.ordinal)
    }

    private fun setupPlatformSpinner() {
        binding.spinnerPlatform.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                platformChoices.map { getString(it.labelRes) },
            )
        binding.spinnerPlatform.setSelection(PlatformChoice.AUTO.ordinal)
    }

    private fun applyPreset(preset: SizePreset) {
        binding.etWidth.setText(preset.width.toString())
        binding.etHeight.setText(preset.height.toString())
        binding.etCorner.setText(preset.corner.toString())
        binding.etThumbW.setText(preset.thumbW.toString())
        binding.etThumbH.setText(preset.thumbH.toString())
        binding.etThumbCorner.setText(preset.thumbCorner.toString())
    }

    private fun restoreFields() {
        val id = SpUtils.getString("etID", "")
        if (id.isNotBlank()) binding.etDeviceId.setText(id)
        binding.etWidth.setText(SpUtils.getInt("etWidth", 480).toString())
        binding.etHeight.setText(SpUtils.getInt("etHeight", 480).toString())
        binding.etCorner.setText(SpUtils.getInt("etRadius", 240).toString())
        binding.etThumbW.setText(SpUtils.getInt("etThumbnailWidth", 264).toString())
        binding.etThumbH.setText(SpUtils.getInt("etThumbnailHeight", 264).toString())
        binding.etThumbCorner.setText(SpUtils.getInt("etThumbnailRadius", 132).toString())
    }

    private fun selectedRecordMode(): RecordMode =
        recordModes.getOrElse(binding.spinnerRecordMode.selectedItemPosition) { RecordMode.AUTO }

    private fun selectedPlatformChoice(): PlatformChoice =
        platformChoices.getOrElse(binding.spinnerPlatform.selectedItemPosition) { PlatformChoice.AUTO }

    // -------------------------------------------------------------------------
    // Firmware DeviceInfo → record capability + platform hint
    // -------------------------------------------------------------------------

    /**
     * Loads [BluetoothSDK.getDeviceInfo] to read `recordFromDevice` and `protocolVersion`.
     * HaWoFit persists these on connect/bind; the demo reads them live for Auto mode.
     */
    private fun fetchDeviceInfoForRecordCapability() {
        BluetoothSDK.getDeviceInfo(
            object : DeviceInfoCallback() {
                override fun onSuccess(deviceInfo: DeviceInfo) {
                    mainHandler.post {
                        if (_binding == null) return@post
                        firmwareRecordFromDevice = deviceInfo.isRecordFromDevice
                        firmwareProtocolVersion = deviceInfo.protocolVersion
                        val inferred = inferPlatform(deviceInfo.protocolVersion)
                        binding.tvFirmwareRecordHint.text =
                            getString(
                                R.string.ai_wf_fw_record_hint,
                                deviceInfo.isRecordFromDevice.toString(),
                                deviceInfo.protocolVersion,
                                inferred.name,
                            )
                        appendLine(
                            "DeviceInfo recordFromDevice=${deviceInfo.isRecordFromDevice} " +
                                "protocol=${deviceInfo.protocolVersion} inferredPlatform=$inferred",
                        )
                        // Re-apply Auto path once firmware flags are known.
                        if (aiStarted && selectedRecordMode() == RecordMode.AUTO) {
                            applyRecordingPath(logResult = true)
                        }
                    }
                }

                override fun onFail(code: Int) {
                    mainHandler.post {
                        appendLine("getDeviceInfo failed: $code (Auto mode falls back to watch if JIELI forced)")
                    }
                }
            },
        )
    }

    private fun fetchDeviceId() {
        binding.tvStatus.setText(R.string.ai_wf_fetching_id)
        BluetoothSDK.getDeviceID(
            object : StringValueCallback() {
                override fun onSuccess(value: String?) {
                    mainHandler.post {
                        if (_binding == null) return@post
                        if (!value.isNullOrBlank()) {
                            binding.etDeviceId.setText(value)
                            SpUtils.putString("etID", value)
                        }
                        binding.tvStatus.setText(R.string.feature_ready)
                    }
                }

                override fun onFail(code: Int) {
                    mainHandler.post {
                        if (_binding == null) return@post
                        binding.tvStatus.text = getString(R.string.feature_failed, code.toString())
                        appendLine(getString(R.string.ai_wf_fetch_id_fail, code))
                    }
                }
            },
        )
    }

    // -------------------------------------------------------------------------
    // Resolve platform + recordFromDevice (HaWoFit-compatible)
    // -------------------------------------------------------------------------

    /**
     * Infer platform from protocol version (AI SDK historically used ≥100 for WL/JieLi).
     * Prefer an explicit spinner choice in demos when the product is known.
     */
    private fun inferPlatform(protocolVersion: Int?): HwPlatformType =
        if ((protocolVersion ?: 0) >= 100) HwPlatformType.JIELI else HwPlatformType.SIFLI

    private fun resolvePlatform(): HwPlatformType =
        when (selectedPlatformChoice()) {
            PlatformChoice.AUTO -> inferPlatform(firmwareProtocolVersion)
            PlatformChoice.SIFLI -> HwPlatformType.SIFLI
            PlatformChoice.JIELI -> HwPlatformType.JIELI
            PlatformChoice.REALTEK -> HwPlatformType.REALTEK
        }

    /**
     * Whether AiCenter should use watch-side recording.
     *
     * Auto (HaWoFit):
     * `recordFromDevice = (platform == JIELI) || firmware.recordFromDevice`
     * JieLi products always use the device path in production init.
     */
    private fun resolveRecordFromDevice(platform: HwPlatformType): Boolean =
        when (selectedRecordMode()) {
            RecordMode.AUTO ->
                platform == HwPlatformType.JIELI || (firmwareRecordFromDevice == true)
            RecordMode.WATCH_DEVICE -> true
            RecordMode.PHONE_MIC, RecordMode.WATCH_SCO -> false
        }

    /**
     * For the **debug Record button** only: whether [AFlashVoiceRecorderService] should open SCO.
     *
     * - `true` → [BluetoothScoManager.startSco] + foreground [VoiceRecorderService] (watch HFP mic).
     * - `false` → phone built-in mic ([VoiceRecorder] directly).
     *
     * Production watch-triggered AI uses AiCenter's internal handler (`speakOnWatch=true` when App path).
     */
    private fun speakOnWatchForDebugRecord(): Boolean =
        when (selectedRecordMode()) {
            RecordMode.WATCH_SCO -> true
            RecordMode.PHONE_MIC -> false
            RecordMode.WATCH_DEVICE -> false
            RecordMode.AUTO -> !resolveRecordFromDevice(resolvePlatform())
        }

    /**
     * Push recording-path flags into AiCenter (call after init / when spinner changes via Apply).
     *
     * If App path (`recordFromDevice=false`): request mic (+ FGS mic / notifications as needed),
     * then [AiCenter.startRecordService] (same idea as HaWoFit when app is in foreground).
     */
    private fun applyRecordingPath(logResult: Boolean) {
        val platform = resolvePlatform()
        val fromDevice = resolveRecordFromDevice(platform)
        AiCenter.getInstance().setRecordFromDevice(fromDevice)
        if (!fromDevice) {
            ensureMicThen(PendingMicAction.START_RECORD_SERVICE)
        }
        if (logResult) {
            val speak = speakOnWatchForDebugRecord()
            val line =
                getString(
                    R.string.ai_wf_applied_path,
                    fromDevice.toString(),
                    platform.name,
                    speak.toString(),
                )
            appendLine(line)
            binding.tvStatus.text = line
        }
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Permissions needed before starting the mic foreground recorder (App / SCO path).
     * - Always: [Manifest.permission.RECORD_AUDIO]
     * - API 33+: [Manifest.permission.POST_NOTIFICATIONS] (FGS notification)
     * - API 34+: [Manifest.permission.FOREGROUND_SERVICE_MICROPHONE] (HaWoFit MainActivity)
     */
    private fun micPermissionsToRequest(): Array<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list += Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        }
        return list
            .filter {
                ContextCompat.checkSelfPermission(requireContext(), it) !=
                    PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    /**
     * Request mic-related permissions if missing, then run [action].
     * Watch-mic path should not call this for production AI; App / SCO must.
     */
    private fun ensureMicThen(action: PendingMicAction) {
        val missing = micPermissionsToRequest()
        val fgsMicMissing =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
                ) != PackageManager.PERMISSION_GRANTED
        // Block App/SCO until RECORD_AUDIO (and API 34+ FGS mic) are granted — same as HaWoFit.
        val mustRequest = !hasRecordAudio() || fgsMicMissing
        if (mustRequest && missing.isNotEmpty()) {
            appendLine(getString(R.string.ai_wf_requesting_mic))
            pendingMicAction = action
            requestMicPermissions.launch(missing)
            return
        }
        when (action) {
            PendingMicAction.START_RECORD_SERVICE -> startRecordServiceSafe()
            PendingMicAction.DEBUG_RECORD -> startDebugRecord()
            PendingMicAction.NONE -> Unit
        }
        // Non-blocking: still ask for notification permission if missing (API 33+).
        val softOnly =
            missing
                .filter { it == Manifest.permission.POST_NOTIFICATIONS }
                .toTypedArray()
        if (softOnly.isNotEmpty()) {
            pendingMicAction = PendingMicAction.NONE
            requestMicPermissions.launch(softOnly)
        }
    }

    private fun startRecordServiceSafe() {
        if (!hasRecordAudio()) {
            appendLine(getString(R.string.ai_wf_err_no_mic))
            return
        }
        AiCenter.getInstance().startRecordService()
        appendLine(getString(R.string.ai_wf_record_service_started))
    }

    // -------------------------------------------------------------------------
    // AiCenter lifecycle
    // -------------------------------------------------------------------------

    /**
     * Init order (compatible with HaWoFit + both record paths):
     * 1. Foreground app status
     * 2. [AiCenter.init]`(ctx, false)` — do not auto-start recorder until path is known
     * 3. Event / error / log / auth callback
     * 4. Key/secret
     * 5. [applyRecordingPath] — setRecordFromDevice + optional startRecordService
     * 6. [setDeviceInfo] with explicit [HwPlatformType]
     * 7. [startWorking]
     */
    private fun startAiCenter() {
        val device = BluetoothSDK.getConnectedDevice()
        if (device == null) {
            binding.tvStatus.setText(R.string.ai_wf_need_connect)
            appendLine(getString(R.string.ai_wf_need_connect))
            return
        }

        BluetoothSDK.setAppStatus(AppStatus.Foreground)
        AiCenter.getInstance().init(requireContext().applicationContext, false)
        AiCenter.getInstance().setAiEvent(this)
        AiCenter.getInstance().setErrorMessageProvider(this)
        AiCenter.getInstance().setLog(aiLog)
        AiCenter.getInstance().setCallback(
            object : AiCenter.Callback {
                override fun authorizeCompleted() {
                    appendLine("authorizeCompleted")
                }

                override fun authorizeError(code: Int, msg: String?) {
                    appendLine("authorizeError code=$code msg=$msg")
                }
            },
        )
        AiCenter.getInstance().setKeyAndSecret(AI_DEMO_KEY, AI_DEMO_SECRET)
        applyRecordingPath(logResult = true)
        AiCenter.getInstance().setDeviceInfo(buildDeviceInfo())
        AiCenter.getInstance().startWorking()
        aiStarted = true
        appendLine(getString(R.string.ai_wf_started))
        binding.tvStatus.setText(R.string.ai_wf_working)
    }

    private fun applyDeviceInfo() {
        if (!aiStarted) {
            startAiCenter()
            if (!aiStarted) return
        } else {
            AiCenter.getInstance().startWorking()
        }
        applyRecordingPath(logResult = true)
        val info = buildDeviceInfo(persist = true)
        AiCenter.getInstance().setDeviceInfo(info)
        appendLine(getString(R.string.ai_wf_device_info, info.toString()))
        Toast.makeText(requireContext(), R.string.ai_wf_info_applied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Build [AiDeviceInfo]. Always sets [AiDeviceInfo.setPlatformType] explicitly so install
     * handlers do not depend on deprecated protocol-only inference alone.
     */
    private fun buildDeviceInfo(persist: Boolean = false): AiDeviceInfo {
        val id = binding.etDeviceId.text?.toString()?.trim().orEmpty()
        val width = binding.etWidth.text?.toString()?.toIntOrNull() ?: 480
        val height = binding.etHeight.text?.toString()?.toIntOrNull() ?: 480
        val corner = binding.etCorner.text?.toString()?.toIntOrNull() ?: 240
        val thumbW = binding.etThumbW.text?.toString()?.toIntOrNull() ?: 264
        val thumbH = binding.etThumbH.text?.toString()?.toIntOrNull() ?: 264
        val thumbCorner = binding.etThumbCorner.text?.toString()?.toIntOrNull() ?: 132
        val platform = resolvePlatform()

        if (persist) {
            SpUtils.putString("etID", id)
            SpUtils.putInt("etWidth", width)
            SpUtils.putInt("etHeight", height)
            SpUtils.putInt("etRadius", corner)
            SpUtils.putInt("etThumbnailWidth", thumbW)
            SpUtils.putInt("etThumbnailHeight", thumbH)
            SpUtils.putInt("etThumbnailRadius", thumbCorner)
        }

        val connected = BluetoothSDK.getConnectedDevice()
        return AiDeviceInfo().apply {
            setMac(connected?.mac.orEmpty())
            setType(connected?.name.orEmpty())
            setId(id)
            setWidth(width)
            setHeight(height)
            setCornerRadius(corner)
            setThumbnailWidth(thumbW)
            setThumbnailHeight(thumbH)
            setThumbnailCornerRadius(thumbCorner)
            setCurrentLocale(aiLocale())
            setPlatformType(platform)
        }
    }

    private fun aiLocale(): String =
        when (LocaleHelper.getLanguage(requireContext())) {
            AppLanguage.CHINESE -> "zh"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.SYSTEM ->
                if (Locale.getDefault().language.startsWith("zh")) "zh" else "en"
        }

    // -------------------------------------------------------------------------
    // Debug Record button — phone mic OR watch-via-SCO
    // -------------------------------------------------------------------------

    private fun toggleDebugRecord() {
        if (recording) {
            stopDebugRecordInternal()
        } else {
            val mode = selectedRecordMode()
            if (mode == RecordMode.WATCH_DEVICE ||
                (mode == RecordMode.AUTO && resolveRecordFromDevice(resolvePlatform()))
            ) {
                Toast.makeText(requireContext(), R.string.ai_wf_record_watch_hint, Toast.LENGTH_LONG)
                    .show()
                appendLine(getString(R.string.ai_wf_record_watch_hint))
            }
            ensureMicThenDebugRecord()
        }
    }

    private fun ensureMicThenDebugRecord() {
        ensureMicThen(PendingMicAction.DEBUG_RECORD)
    }

    /**
     * Manual App-side capture for demos.
     *
     * Temporarily forces `recordFromDevice=false` so [AFlashVoiceRecorderService] takes the App
     * branch (same idea as HaWoFit `enterAiWatchfaceAppRecordMode`), then starts recording with
     * `speakOnWatch` = [speakOnWatchForDebugRecord]:
     * - SCO path: opens BT SCO, records watch HFP mic through the phone
     * - Phone path: built-in mic only
     */
    private fun startDebugRecord() {
        if (!aiStarted) {
            Toast.makeText(requireContext(), R.string.ai_wf_need_start, Toast.LENGTH_SHORT).show()
            return
        }
        val speakOnWatch = speakOnWatchForDebugRecord()
        if (speakOnWatch) {
            appendLine(getString(R.string.ai_wf_sco_need_bt))
        }

        // Force App capture for this debug session (restore on Apply / next applyRecordingPath).
        AiCenter.getInstance().setRecordFromDevice(false)
        startRecordServiceSafe()

        voiceRecorder =
            AFlashVoiceRecorderService(
                speakOnWatch,
                object : AFlashVoiceRecorderService.Callback {
                    override fun onTextUpdated(result: String?) {
                        appendLine("ASR: $result")
                    }

                    override fun onFailed(code: Int, errorMsg: String?) {
                        appendLine("Record failed: $code|$errorMsg")
                        mainHandler.post {
                            if (_binding == null) return@post
                            recording = false
                            binding.btnRecord.setText(R.string.ai_wf_record)
                            applyRecordingPath(logResult = false)
                        }
                    }
                },
            )
        voiceRecorder?.startRecording(false, -1)
        recording = true
        binding.btnRecord.setText(R.string.ai_wf_stop_record)
        appendLine(
            if (speakOnWatch) {
                getString(R.string.ai_wf_recording_sco)
            } else {
                getString(R.string.ai_wf_recording_phone)
            },
        )
    }

    private fun stopDebugRecordInternal() {
        voiceRecorder?.stopRecording()
        voiceRecorder = null
        if (recording) {
            recording = false
            _binding?.btnRecord?.setText(R.string.ai_wf_record)
            appendLine(getString(R.string.ai_wf_record_stopped))
            // Restore Auto/Watch/Phone path selected in the spinner.
            if (aiStarted) applyRecordingPath(logResult = false)
        }
    }

    private fun playPcm() {
        val path = recordPcmPath() ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.ai_wf_pcm_missing, Toast.LENGTH_SHORT).show()
            return
        }
        if (pcmPlayer == null) pcmPlayer = PcmPlayer()
        pcmPlayer?.playPcm(
            path,
            sampleRate = 16_000,
            channelConfig = AudioFormat.CHANNEL_OUT_MONO,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
        )
        appendLine(getString(R.string.ai_wf_playing))
    }

    private fun sharePcm() {
        val path = recordPcmPath() ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.ai_wf_pcm_missing_path, path),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val uri =
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(Intent.createChooser(intent, getString(R.string.ai_wf_share_title)))
    }

    private fun recordPcmPath(): String? {
        if (!aiStarted) {
            Toast.makeText(requireContext(), R.string.ai_wf_need_start, Toast.LENGTH_SHORT).show()
            return null
        }
        return AiCenter.getInstance().getWorkspace() + "/record.pcm"
    }

    // -------------------------------------------------------------------------
    // AiEvent
    // -------------------------------------------------------------------------

    override fun devicePerviewImageCallback(bitmap: Bitmap?, code: Int, msg: String?) {
        mainHandler.post {
            if (_binding == null) return@post
            if (code != 0) {
                Toast.makeText(requireContext(), msg ?: "preview error $code", Toast.LENGTH_LONG)
                    .show()
                appendLine("preview fail: $code $msg")
                return@post
            }
            binding.imgThumbnail.setImageBitmap(bitmap)
            appendLine(getString(R.string.ai_wf_preview_ok))
        }
    }

    override fun deviceAiImageCallback(image: Bitmap?, code: Int, message: String?) {
        mainHandler.post {
            if (_binding == null) return@post
            if (code != 0) {
                Toast.makeText(
                    requireContext(),
                    message ?: "image error $code",
                    Toast.LENGTH_LONG,
                ).show()
                appendLine("image fail: $code $message")
                return@post
            }
            binding.imgBackground.setImageBitmap(image)
            appendLine(getString(R.string.ai_wf_image_ok))
        }
    }

    override fun deviceOtaWatchfaceProgressUpdated(progress: Float) {
        mainHandler.post {
            if (_binding == null) return@post
            binding.tvStatus.text =
                getString(R.string.ai_wf_ota_progress, (progress * 100).toInt())
        }
    }

    override fun deviceOtaWatchfaceDone(
        watchface: SifliCustomWatchface?,
        code: Int,
        errorMsg: String?,
    ) {
        mainHandler.post {
            if (_binding == null) return@post
            if (code == 0) {
                binding.tvStatus.setText(R.string.ai_wf_ota_ok)
                appendLine(getString(R.string.ai_wf_ota_ok))
            } else {
                binding.tvStatus.text = getString(R.string.feature_failed, "$code $errorMsg")
                appendLine("OTA fail: $code $errorMsg")
            }
        }
    }

    override fun deviceEnteredAiWatchface() {
        appendLine("deviceEnteredAiWatchface")
    }

    override fun deviceExitedAiWatchface() {
        appendLine("deviceExitedAiWatchface")
    }

    /**
     * Watch started recording.
     * [type]: `0` = AI watchface, `1` = AI Q&A.
     * Actual PCM source is whatever [AiCenter.isRecordFromDevice] / SCO decided at start.
     */
    override fun deviceStartRecording(type: Int) {
        val fromDevice = AiCenter.getInstance().isRecordFromDevice
        appendLine(
            "deviceStartRecording type=$type recordFromDevice=$fromDevice " +
                "(watch mic vs phone/SCO — see Record mode)",
        )
    }

    override fun deviceStopRecording(type: Int) {
        appendLine(
            "deviceStopRecording type=$type recordFromDevice=${AiCenter.getInstance().isRecordFromDevice}",
        )
    }

    override fun messageForCode(code: Int): String {
        val ctx = context ?: return "$code"
        val mapped =
            when (code) {
                140001 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140001) }.getOrNull()
                140008 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140008) }.getOrNull()
                140011 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140011) }.getOrNull()
                140013 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140013) }.getOrNull()
                140900 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140900) }.getOrNull()
                140901 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140901) }.getOrNull()
                140903 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140903) }.getOrNull()
                140908 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140908) }.getOrNull()
                140910 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_140910) }.getOrNull()
                144002 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_144002) }.getOrNull()
                144003 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_144003) }.getOrNull()
                144004 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_144004) }.getOrNull()
                144006 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_144006) }.getOrNull()
                144103 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_144103) }.getOrNull()
                170008 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_170008) }.getOrNull()
                170806 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_170806) }.getOrNull()
                170807 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_170807) }.getOrNull()
                240005 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240005) }.getOrNull()
                240011 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240011) }.getOrNull()
                240040 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240040) }.getOrNull()
                240052 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240052) }.getOrNull()
                240063 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240063) }.getOrNull()
                240068 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240068) }.getOrNull()
                240070 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_240070) }.getOrNull()
                41010105 ->
                    runCatching { ctx.getString(com.huawo.ai.R.string.error_41010105) }.getOrNull()
                999999 -> runCatching { ctx.getString(com.huawo.ai.R.string.error_999999) }.getOrNull()
                80041 -> ctx.getString(R.string.ai_wf_err_no_mic)
                10416, 10516 -> ctx.getString(R.string.ai_wf_err_daily_limit)
                80321, 80031 -> ctx.getString(R.string.ai_wf_err_asr_empty)
                else ->
                    runCatching { ctx.getString(com.huawo.ai.R.string.error_default) }.getOrNull()
            }
        return "$code: ${mapped ?: "Failed"}"
    }

    private fun appendLog(level: String, tag: String?, msg: String) {
        mainHandler.post {
            appendLine("${dateFormat.format(Date())} $level 【$tag】 $msg")
        }
    }

    private fun appendLine(line: String) {
        val b = _binding ?: return
        logBuilder.append(line).append('\n')
        if (logBuilder.length > 12_000) {
            logBuilder.delete(0, logBuilder.length - 12_000)
        }
        b.tvLog.text = logBuilder.toString()
        b.logScroll.post { b.logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private data class SizePreset(
        val label: String,
        val width: Int,
        val height: Int,
        val corner: Int,
        val thumbW: Int,
        val thumbH: Int,
        val thumbCorner: Int,
    )

    /**
     * UI record-mode choices. Maps to [AiCenter.setRecordFromDevice] + debug `speakOnWatch`.
     */
    private enum class RecordMode(val labelRes: Int) {
        /** Same rule as HaWoFit: JieLi or firmware flag → watch; else phone/SCO App path. */
        AUTO(R.string.ai_wf_record_mode_auto),

        /** Force watch microphone / BLE audio pull. */
        WATCH_DEVICE(R.string.ai_wf_record_mode_watch),

        /** Force App AudioRecord on the phone handset mic (no SCO). */
        PHONE_MIC(R.string.ai_wf_record_mode_phone),

        /** Force App path with Bluetooth SCO (watch HFP mic → phone). */
        WATCH_SCO(R.string.ai_wf_record_mode_sco),
    }

    private enum class PlatformChoice(val labelRes: Int) {
        AUTO(R.string.ai_wf_platform_auto),
        SIFLI(R.string.ai_wf_platform_sifli),
        JIELI(R.string.ai_wf_platform_jieli),
        REALTEK(R.string.ai_wf_platform_realtek),
    }

    companion object {
        /** Demo credentials (same as HaWoFit). Replace with vendor-issued key/secret for production. */
        private const val AI_DEMO_KEY = "1f34e8ae-4bfc-4464-8fb0-1282200d7bac"
        private const val AI_DEMO_SECRET = "b66e29bb1125d8c5d27139f467822c16"
    }
}
