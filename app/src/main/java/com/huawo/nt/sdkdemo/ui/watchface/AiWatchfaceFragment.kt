package com.huawo.nt.sdkdemo.ui.watchface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioFormat
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
import com.huawo.sdk.bluetoothsdk.interfaces.callback.StringValueCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.AppStatus
import com.huawo.watchface.custom.SifliCustomWatchface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI Watchface demo tab (third tab under [WatchfaceFragment]).
 *
 * ## What this screen demonstrates
 * End-to-end integration of the Huawo **AI SDK** (`com.huawo.ai.AiCenter`) for AI-generated
 * watchfaces:
 * 1. Configure device parameters (AFlash device id + screen / thumbnail geometry).
 * 2. Start `AiCenter` so BLE AI events from the watch are handled.
 * 3. Show generated **thumbnail** and **background** bitmaps when the cloud / pipeline returns them.
 * 4. Optional phone-side PCM record / play / share for debugging ASR / audio paths.
 *
 * ## Typical product flow (watch-driven)
 * ```
 * App connected → setDeviceInfo + startWorking
 *      → user opens AI Watchface on the watch
 *      → watch records voice (or phone records for debug)
 *      → AFlash cloud generates image
 *      → deviceAiImageCallback / devicePerviewImageCallback update UI
 *      → watch requests install → OTA progress → deviceOtaWatchfaceDone
 * ```
 *
 * ## Reference
 * Logic mirrors HUAWO-TOOL `AiTestActivity`. UI uses this demo's Material style
 * (same patterns as [CustomWatchfaceFragment]).
 *
 * ## Prerequisites / caveats
 * - BluetoothSDK must already be connected (bind/sync done on Home).
 * - AARs required: `ai-*.aar`, `QW_release_*.aar`, `jl_pack_resource_*.aar`, `BmpConvert_*.aar`,
 *   plus existing BluetoothSDK / qjs-watchface / Sifli stack.
 * - Runtime: `RECORD_AUDIO` (and declare `FOREGROUND_SERVICE_MICROPHONE` for AI voice service).
 * - Device ID is the AFlash / vendor id used for cloud auth — not the BLE MAC.
 * - Width / height / corner **must match the real watch panel**; wrong values crop or reject OTA.
 * - Call [AiCenter.destroy] when this fragment is fully destroyed (not only onDestroyView),
 *   because ViewPager may recreate the view while keeping the fragment instance briefly.
 *
 * Implements:
 * - [AiEvent] — watch / pipeline callbacks (images, OTA, enter/exit AI mode, record start/stop).
 * - [IErrorMessageProvider] — human-readable strings for AI / AFlash / ASR error codes.
 */
class AiWatchfaceFragment : Fragment(), AiEvent, IErrorMessageProvider {

    // ViewBinding is cleared in onDestroyView; always null-check before touching UI from async callbacks.
    private var _binding: FragmentAiWatchfaceBinding? = null
    private val binding get() = _binding!!

    /** All AI / BLE callbacks may arrive off the main thread — marshal UI updates here. */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /**
     * In-memory log buffer shown in the bottom panel.
     * Capped at ~12k chars (same idea as AiTestActivity) to avoid unbounded TextView growth.
     */
    private val logBuilder = StringBuilder()

    /** Plays `{workspace}/record.pcm` (16 kHz mono PCM16 from AI recorder). */
    private var pcmPlayer: PcmPlayer? = null

    /**
     * Phone-side AFlash recorder used only for **debug** (button "Record").
     * Production AI watchface recording usually happens on the watch; AiCenter still owns the
     * primary voice pipeline after [AiCenter.startWorking].
     */
    private var voiceRecorder: AFlashVoiceRecorderService? = null

    /** Whether [voiceRecorder] is currently capturing from the phone mic. */
    private var recording = false

    /**
     * `true` after a successful [startAiCenter] (init + setDeviceInfo + startWorking).
     * Play / share / phone-record require this so workspace and handlers exist.
     */
    private var aiStarted = false

    /**
     * Common watch panel presets (background size + thumbnail size).
     *
     * Fields: label, bgW, bgH, bgCornerRadius, thumbW, thumbH, thumbCornerRadius.
     * Pick values from the product spec / firmware; wrong geometry breaks packaging.
     */
    private val sizePresets =
        listOf(
            SizePreset("480 x 480", 480, 480, 240, 264, 264, 132),
            SizePreset("466 x 466", 466, 466, 233, 264, 264, 132),
            SizePreset("410 x 502", 410, 502, 108, 200, 244, 50),
        )

    /**
     * Runtime mic permission for phone recording.
     * Note: AiCenter's internal VoiceRecorderService may also need this; grant before heavy AI use.
     */
    private val requestRecordAudio =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startPhoneRecord()
            else {
                Toast.makeText(requireContext(), R.string.ai_wf_mic_denied, Toast.LENGTH_SHORT)
                    .show()
            }
        }

    /**
     * Bridge AiCenter's [ILog] into the on-screen log panel.
     * Useful when diagnosing auth, ASR, image download, and OTA packaging issues.
     */
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
        // AiCenter / SpUtils expect an initialized SharedPreferences store ("AiCenterSP").
        SpUtils.init(requireContext().applicationContext)
        setupSizeSpinner()
        restoreFields()
        binding.tvStatus.setText(R.string.feature_ready)

        // Apply = push edited AiDeviceInfo into AiCenter (and start if not yet started).
        binding.btnApply.setOnClickListener { applyDeviceInfo() }
        // Share / Play operate on workspace/record.pcm written by the AI recorder pipeline.
        binding.btnShare.setOnClickListener { sharePcm() }
        binding.btnPlay.setOnClickListener { playPcm() }
        binding.btnRecord.setOnClickListener { toggleRecord() }

        // Prefer the id reported by the connected firmware; falls back to SpUtils cache on failure.
        fetchDeviceId()
        // Auto-start when the tab opens and BLE is connected (same as AiTestActivity onCreate).
        startAiCenter()
    }

    override fun onDestroyView() {
        // Stop phone debug recording and release player; do NOT destroy AiCenter here —
        // ViewPager2 may destroy the view while the fragment instance still lives.
        stopPhoneRecordInternal()
        pcmPlayer?.stop()
        pcmPlayer = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        // Full teardown: unregister BLE AI listeners, stop foreground recorder service, free workspace.
        if (aiStarted) {
            runCatching { AiCenter.getInstance().destroy() }
            aiStarted = false
        }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // UI helpers — size presets & persisted fields
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
                    // Overwrites the six numeric fields; user can still fine-tune afterward.
                    applyPreset(sizePresets[position])
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun applyPreset(preset: SizePreset) {
        binding.etWidth.setText(preset.width.toString())
        binding.etHeight.setText(preset.height.toString())
        binding.etCorner.setText(preset.corner.toString())
        binding.etThumbW.setText(preset.thumbW.toString())
        binding.etThumbH.setText(preset.thumbH.toString())
        binding.etThumbCorner.setText(preset.thumbCorner.toString())
    }

    /**
     * Restore last-used device id / sizes from AiCenter's [SpUtils] keys
     * (shared with HUAWO-TOOL AiTestActivity: etID, etWidth, …).
     */
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

    // -------------------------------------------------------------------------
    // Device ID from firmware
    // -------------------------------------------------------------------------

    /**
     * Reads the vendor device id via [BluetoothSDK.getDeviceID].
     *
     * This id is what AFlash / cloud auth expects in [AiDeviceInfo.setId].
     * If the call fails (not connected, unsupported opcode, etc.), keep the cached SpUtils value.
     */
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
    // AiCenter lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialize and start the AI SDK.
     *
     * Order (device-mic path used by this demo):
     * 1. [BluetoothSDK.setAppStatus] Foreground
     * 2. [AiCenter.init]`(ctx, false)` — do **not** auto-start phone recorder service
     * 3. setAiEvent / setErrorMessageProvider / setLog / setCallback
     * 4. [AiCenter.setKeyAndSecret]
     * 5. [AiCenter.setRecordFromDevice]`(true)` — always use watch mic (no RECORD_AUDIO / FGS)
     * 6. [AiCenter.startWorking]
     * 7. [AiCenter.setDeviceInfo] — required (missing → 80051)
     *
     * [AiCenter.setApplication] is done in [com.huawo.nt.sdkdemo.SdkDemoApp].
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
                /** Cloud / AFlash authorization succeeded — AI features can proceed. */
                override fun authorizeCompleted() {
                    appendLine("authorizeCompleted")
                }

                /** Auth failed (bad key, network, device id, etc.). Check logs and device id. */
                override fun authorizeError(code: Int, msg: String?) {
                    appendLine("authorizeError code=$code msg=$msg")
                }
            },
        )
        // Same demo key/secret as product demo apps (replace with vendor credentials in production).
        AiCenter.getInstance().setKeyAndSecret(AI_DEMO_KEY, AI_DEMO_SECRET)
        // Fixed: watch-side microphone — no phone RECORD_AUDIO / startRecordService.
        AiCenter.getInstance().setRecordFromDevice(true)
        AiCenter.getInstance().startWorking()
        AiCenter.getInstance().setDeviceInfo(buildDeviceInfo())
        aiStarted = true
        appendLine(getString(R.string.ai_wf_started))
        binding.tvStatus.setText(R.string.ai_wf_working)
    }

    /**
     * "Apply" button: persist UI fields and push a fresh [AiDeviceInfo] into AiCenter.
     *
     * Call this after changing resolution / device id / reconnecting.
     * If AI was never started (e.g. opened tab before connect), tries [startAiCenter] first.
     */
    private fun applyDeviceInfo() {
        if (!aiStarted) {
            startAiCenter()
            if (!aiStarted) return
        } else {
            // Re-register listeners in case stopWorking was called elsewhere.
            AiCenter.getInstance().startWorking()
        }
        val info = buildDeviceInfo(persist = true)
        AiCenter.getInstance().setDeviceInfo(info)
        appendLine(getString(R.string.ai_wf_device_info, info.toString()))
        binding.tvStatus.setText(R.string.ai_wf_info_applied)
        Toast.makeText(requireContext(), R.string.ai_wf_info_applied, Toast.LENGTH_SHORT).show()
    }

    /**
     * Build [AiDeviceInfo] from the form (and optionally persist to SpUtils).
     *
     * Field meanings:
     * - **mac / type**: from connected BLE device (identity / product model for cloud).
     * - **id**: AFlash device id (from getDeviceID or manual input).
     * - **width / height / cornerRadius**: full-screen background asset size & rounding.
     * - **thumbnail***: list/preview icon size & rounding (smaller than panel).
     * - **currentLocale**: `"zh"` / `"en"` — affects cloud prompt / TTS language.
     *
     * For some WL-protocol watches you may also need `setProtocolVersion(100+)` (not exposed here;
     * add if your product requires it — see AI SDK ReadMe).
     */
    private fun buildDeviceInfo(persist: Boolean = false): AiDeviceInfo {
        val id = binding.etDeviceId.text?.toString()?.trim().orEmpty()
        val width = binding.etWidth.text?.toString()?.toIntOrNull() ?: 480
        val height = binding.etHeight.text?.toString()?.toIntOrNull() ?: 480
        val corner = binding.etCorner.text?.toString()?.toIntOrNull() ?: 240
        val thumbW = binding.etThumbW.text?.toString()?.toIntOrNull() ?: 264
        val thumbH = binding.etThumbH.text?.toString()?.toIntOrNull() ?: 264
        val thumbCorner = binding.etThumbCorner.text?.toString()?.toIntOrNull() ?: 132

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
        }
    }

    /** Map demo app language setting → AI locale string expected by AiDeviceInfo. */
    private fun aiLocale(): String =
        when (LocaleHelper.getLanguage(requireContext())) {
            AppLanguage.CHINESE -> "zh"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.SYSTEM ->
                if (Locale.getDefault().language.startsWith("zh")) "zh" else "en"
        }

    // -------------------------------------------------------------------------
    // Phone debug recording / PCM play / share
    // -------------------------------------------------------------------------

    /** Toggle phone mic capture (debug path, not the watch mic). */
    private fun toggleRecord() {
        if (recording) {
            stopPhoneRecordInternal()
        } else {
            ensureMicThenRecord()
        }
    }

    private fun ensureMicThenRecord() {
        val granted =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) startPhoneRecord()
        else requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Start [AFlashVoiceRecorderService] from the **app** (phone mic).
     *
     * Constructor arg `speakOnWatch = false` → do not route playback to the watch speaker.
     * [AFlashVoiceRecorderService.startRecording] `(autoStop, language)`:
     * - autoStop=false: user stops via button.
     * - language=-1: use default / device locale.
     *
     * PCM is written under AiCenter workspace as `record.pcm` (see [recordPcmPath]).
     */
    private fun startPhoneRecord() {
        if (!aiStarted) {
            Toast.makeText(requireContext(), R.string.ai_wf_need_start, Toast.LENGTH_SHORT).show()
            return
        }
        voiceRecorder =
            AFlashVoiceRecorderService(
                false,
                object : AFlashVoiceRecorderService.Callback {
                    /** Partial / final ASR text from AFlash (debug visibility). */
                    override fun onTextUpdated(result: String?) {
                        appendLine("ASR: $result")
                    }

                    override fun onFailed(code: Int, errorMsg: String?) {
                        appendLine("Record failed: $code|$errorMsg")
                        mainHandler.post {
                            if (_binding == null) return@post
                            recording = false
                            binding.btnRecord.setText(R.string.ai_wf_record)
                        }
                    }
                },
            )
        voiceRecorder?.startRecording(false, -1)
        recording = true
        binding.btnRecord.setText(R.string.ai_wf_stop_record)
        appendLine(getString(R.string.ai_wf_recording))
    }

    private fun stopPhoneRecordInternal() {
        voiceRecorder?.stopRecording()
        voiceRecorder = null
        if (recording) {
            recording = false
            _binding?.btnRecord?.setText(R.string.ai_wf_record)
            appendLine(getString(R.string.ai_wf_record_stopped))
        }
    }

    /**
     * Play last `record.pcm`.
     * Format must match AI recorder output: **16 kHz, mono, PCM 16-bit**.
     * Wrong format sounds like noise or silence.
     */
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

    /**
     * Share `record.pcm` via system chooser using [FileProvider].
     * Paths must be covered by `res/xml/file_paths.xml` (files / cache / external-files).
     */
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

    /** Absolute path to AiCenter workspace `record.pcm`, or null if AI not started. */
    private fun recordPcmPath(): String? {
        if (!aiStarted) {
            Toast.makeText(requireContext(), R.string.ai_wf_need_start, Toast.LENGTH_SHORT).show()
            return null
        }
        return AiCenter.getInstance().getWorkspace() + "/record.pcm"
    }

    // -------------------------------------------------------------------------
    // AiEvent — watch / cloud pipeline callbacks
    // -------------------------------------------------------------------------

    /**
     * Preview / thumbnail bitmap ready (note: API name is historically misspelled "Perview").
     * [code] == 0 success; otherwise [msg] explains failure (download, crop, etc.).
     */
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

    /**
     * Full AI watchface **background** image ready (already scaled / rounded per AiDeviceInfo).
     * Shown in the large preview ImageView; later packaged for OTA when the watch requests install.
     */
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

    /**
     * Watchface install (QJS / Sifli OTA) progress.
     * [progress] is typically 0f..1f from AiCenter / watchface sync.
     */
    override fun deviceOtaWatchfaceProgressUpdated(progress: Float) {
        mainHandler.post {
            if (_binding == null) return@post
            val pct = (progress * 100).toInt()
            binding.tvStatus.text = getString(R.string.ai_wf_ota_progress, pct)
        }
    }

    /**
     * Install finished.
     * [code] == 0 success; non-zero → packaging, transfer, or device reject ([errorMsg]).
     */
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

    /** Watch UI entered AI Watchface mode. */
    override fun deviceEnteredAiWatchface() {
        appendLine("deviceEnteredAiWatchface")
    }

    /** Watch UI left AI Watchface mode. */
    override fun deviceExitedAiWatchface() {
        appendLine("deviceExitedAiWatchface")
    }

    /**
     * Watch started recording.
     * [type]: `0` = for watchface generation, `1` = for AI Q&A (see AiEvent docs).
     */
    override fun deviceStartRecording(type: Int) {
        appendLine("deviceStartRecording type=$type")
    }

    /** Watch stopped recording ([type] same as [deviceStartRecording]). */
    override fun deviceStopRecording(type: Int) {
        appendLine("deviceStopRecording type=$type")
    }

    // -------------------------------------------------------------------------
    // IErrorMessageProvider
    // -------------------------------------------------------------------------

    /**
     * Map numeric AI / AFlash / ASR error codes to display strings.
     *
     * Prefer strings from `com.huawo.ai.R.string.error_*` when present; fall back to demo strings
     * for a few common app-side codes (mic, daily limit, empty ASR).
     *
     * Returning a non-empty message lets AiCenter show something useful instead of a bare code.
     * See AI SDK ReadMe §6 for the common code table (80041 mic, 10516 daily watchface limit, …).
     */
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

    // -------------------------------------------------------------------------
    // On-screen log
    // -------------------------------------------------------------------------

    private fun appendLog(level: String, tag: String?, msg: String) {
        mainHandler.post {
            val time = dateFormat.format(Date())
            appendLine("$time $level 【$tag】 $msg")
        }
    }

    /** Append one line and auto-scroll; no-op if the view was destroyed. */
    private fun appendLine(line: String) {
        val b = _binding ?: return
        logBuilder.append(line).append('\n')
        if (logBuilder.length > 12_000) {
            logBuilder.delete(0, logBuilder.length - 12_000)
        }
        b.tvLog.text = logBuilder.toString()
        b.logScroll.post { b.logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    /**
     * One watch geometry preset.
     *
     * @property width / [height] / [corner] Background (full dial) size and corner radius in px.
     * @property thumbW / [thumbH] / [thumbCorner] Thumbnail (list icon) size and corner radius in px.
     */
    private data class SizePreset(
        val label: String,
        val width: Int,
        val height: Int,
        val corner: Int,
        val thumbW: Int,
        val thumbH: Int,
        val thumbCorner: Int,
    )

    companion object {
        /** Demo credentials (same as HaWoFit). Replace with vendor-issued key/secret for production. */
        private const val AI_DEMO_KEY = "1f34e8ae-4bfc-4464-8fb0-1282200d7bac"
        private const val AI_DEMO_SECRET = "b66e29bb1125d8c5d27139f467822c16"
    }
}
