package com.huawo.nt.sdkdemo.ui.watchface

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.huawo.ai.AiCenter
import com.huawo.ai.event.AiEvent
import com.huawo.ai.handler.IErrorMessageProvider
import com.huawo.ai.handler.model.AiDeviceInfo
import com.huawo.ai.logger.ILog
import com.huawo.ai.utils.SpUtils
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.FragmentJlAiWatchfaceBinding
import com.huawo.sdk.bluetoothsdk.BluetoothSDK
import com.huawo.sdk.bluetoothsdk.HwPlatformType
import com.huawo.sdk.bluetoothsdk.interfaces.callback.StringValueCallback
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.AppStatus
import com.huawo.watchface.custom.SifliCustomWatchface

/** JieLi AI watchface sample. Recording is always watch-driven and installation stays in AiCenter. */
class JLAiWatchfaceFragment : Fragment(), AiEvent, IErrorMessageProvider {
    private var bindingRef: FragmentJlAiWatchfaceBinding? = null
    private val binding get() = bindingRef!!
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logs = StringBuilder()
    private var started = false
    private var installing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = FragmentJlAiWatchfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SpUtils.init(requireContext().applicationContext)
        binding.btnApply.setOnClickListener { applyDeviceInfo() }
        fetchDeviceId()
        startAiCenter()
    }

    /** Initializes one explicit JIELI watch-recording session; it never infers another platform. */
    private fun startAiCenter() {
        if (BluetoothSDK.getConnectedDevice() == null) {
            status(getString(R.string.ai_wf_need_connect))
            return
        }
        BluetoothSDK.setAppStatus(AppStatus.Foreground)
        AiCenter.getInstance().init(requireContext().applicationContext)
        AiCenter.getInstance().setAiEvent(this)
        AiCenter.getInstance().setErrorMessageProvider(this)
        AiCenter.getInstance().setLog(object : ILog {
            override fun d(tag: String?, msg: String) { append("D $tag $msg") }
            override fun i(tag: String?, msg: String) { append("I $tag $msg") }
            override fun w(tag: String?, msg: String) { append("W $tag $msg") }
            override fun e(tag: String?, msg: String) { append("E $tag $msg") }
        })
        // Fixed sample configuration, not runtime platform routing.
        AiCenter.getInstance().setRecordFromDevice(true)
        AiCenter.getInstance().setDeviceInfo(buildDeviceInfo())
        AiCenter.getInstance().startWorking()
        started = true
        append("AiCenter started platform=JIELI recordFromDevice=true")
        status(getString(R.string.ai_wf_working))
    }

    private fun applyDeviceInfo() {
        if (!started) startAiCenter()
        if (!started) return
        AiCenter.getInstance().setRecordFromDevice(true)
        AiCenter.getInstance().setDeviceInfo(buildDeviceInfo())
        AiCenter.getInstance().startWorking()
        append("device info applied platform=JIELI")
        status(getString(R.string.ai_wf_info_applied))
    }

    private fun fetchDeviceId() {
        BluetoothSDK.getDeviceID(object : StringValueCallback() {
            override fun onSuccess(value: String?) {
                mainHandler.post { if (!value.isNullOrBlank()) binding.etDeviceId.setText(value) }
            }
            override fun onFail(code: Int) {
                append("getDeviceID failed code=$code")
            }
        })
    }

    /** Converts the form dimensions into the AI SDK contract and pins its platform to JIELI. */
    private fun buildDeviceInfo(): AiDeviceInfo {
        val connected = BluetoothSDK.getConnectedDevice()
        return AiDeviceInfo().apply {
            setId(binding.etDeviceId.text?.toString()?.trim().orEmpty())
            setMac(connected?.mac.orEmpty())
            setType(connected?.name.orEmpty())
            setWidth(number(binding.etWidth.text?.toString(), 480))
            setHeight(number(binding.etHeight.text?.toString(), 480))
            setCornerRadius(number(binding.etCorner.text?.toString(), 240))
            setThumbnailWidth(number(binding.etThumbW.text?.toString(), 264))
            setThumbnailHeight(number(binding.etThumbH.text?.toString(), 264))
            setThumbnailCornerRadius(number(binding.etThumbCorner.text?.toString(), 132))
            setCurrentLocale(if (resources.configuration.locales[0]?.language == "zh") "zh" else "en")
            setPlatformType(HwPlatformType.JIELI)
        }
    }

    override fun devicePerviewImageCallback(bitmap: Bitmap?, code: Int, msg: String?) {
        mainHandler.post {
            if (code == 0 && bitmap != null) binding.imgThumbnail.setImageBitmap(bitmap)
            else append("preview failed code=$code msg=$msg")
        }
    }

    override fun deviceAiImageCallback(image: Bitmap?, code: Int, msg: String?) {
        mainHandler.post {
            if (code == 0 && image != null) binding.imgBackground.setImageBitmap(image)
            else append("image failed code=$code msg=$msg")
        }
    }

    override fun deviceStartRecording(type: Int) {
        append("watch recording started type=$type")
        status(getString(R.string.jl_ai_recording_from_watch))
    }

    override fun deviceStopRecording(type: Int) {
        append("watch recording stopped type=$type; SDK requests AIRECORD V2")
        status(getString(R.string.jl_ai_waiting_record))
    }

    /** AiCenter starts the SDK-owned WL AI installation; the parent tab is locked until completion. */
    override fun deviceStartedOtaWatchface() {
        installing = true
        publishTransferBusy(true)
        append("WL AI install started by AiCenter")
    }

    override fun deviceOtaWatchfaceProgressUpdated(progress: Float) {
        status(getString(R.string.ai_wf_ota_progress, (progress * 100).toInt().coerceIn(0, 100)))
    }

    /** Always releases the parent lock and exposes the returned device code on the terminal path. */
    override fun deviceOtaWatchfaceDone(watchface: SifliCustomWatchface?, code: Int, errorMsg: String?) {
        installing = false
        publishTransferBusy(false)
        if (code == 0) {
            append("WL AI install success")
            status(getString(R.string.ai_wf_ota_ok))
        } else {
            append("WL AI install failed code=$code msg=$errorMsg")
            status("[$code] ${errorMsg.orEmpty()}")
        }
    }

    override fun deviceRequestWatchface(type: Int) {
        // AiCenter selects WlAiWatchfaceInstallHandler from the explicit JIELI platform type.
        append("watch requested AI install type=$type")
    }

    override fun messageForCode(code: Int): String = "$code: ${getString(R.string.owf_install_failed)}"

    private fun publishTransferBusy(busy: Boolean) {
        parentFragment?.childFragmentManager?.setFragmentResult(
            JLWatchfaceFragment.TRANSFER_RESULT,
            Bundle().apply { putBoolean(JLWatchfaceFragment.TRANSFER_BUSY, busy) },
        )
    }

    private fun status(value: String) = mainHandler.post { bindingRef?.tvStatus?.text = value }
    private fun append(value: String) = mainHandler.post {
        Log.i(TAG, value)
        logs.append(value).append('\n')
        if (logs.length > 8000) logs.delete(0, logs.length - 8000)
        bindingRef?.tvLog?.text = logs
    }
    private fun number(value: String?, fallback: Int) = value?.toIntOrNull()?.takeIf { it > 0 } ?: fallback

    override fun onDestroy() {
        if (started) {
            if (installing) publishTransferBusy(false)
            runCatching { AiCenter.getInstance().destroy() }
            started = false
        }
        super.onDestroy()
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    private companion object { const val TAG = "JLAiWatchface" }
}
