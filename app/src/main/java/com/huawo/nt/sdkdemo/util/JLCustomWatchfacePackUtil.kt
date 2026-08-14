package com.huawo.nt.sdkdemo.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.huawo.sdk.bluetoothsdk.wl.media.models.MediaFileInfo
import com.huawo.sdk.bluetoothsdk.wl.media.utils.MediaPacketBuilder
import com.huawo.sdk.bluetoothsdk.wl.models.V10CustomWatchfaceConfig
import com.huawo.sdk.bluetoothsdk.wl.models.WlWatchfaceWidgetWire
import com.qiwo.bmp_convert.BmpConvert
import com.qiwo.bmp_convert.ConvertParam
import com.qiwo.bmp_convert.ConvertResult
import com.qiwo.bmp_convert.OnConvertListener
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Builds only the JieLi media resources and A9 configuration; BLE packet construction, validation
 * and acknowledgement handling remain SDK-owned. Output names are protocol contracts: `pw` is the
 * thumbnail and `bg1..bgN` are full backgrounds.
 */
object JLCustomWatchfacePackUtil {
    private const val TAG = "JLCustomWfPack"

    data class Element(val wireType: Int, val x: Int, val y: Int)

    data class Request(
        val backgrounds: List<Bitmap>,
        val thumbnail: Bitmap,
        val displayMode: Int,
        val pointerStyle: Int,
        val textColorRgb: Int,
        val elements: List<Element>,
    )

    data class Result(
        val files: List<MediaFileInfo>,
        val config: V10CustomWatchfaceConfig,
    )

    /** Validates UI input before conversion so an invalid file or A9 element never reaches BLE. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun build(context: Context, request: Request): Result {
        require(request.backgrounds.isNotEmpty()) { "No custom watchface background selected" }
        require(request.backgrounds.all { it.width > 0 && it.height > 0 }) { "Background bitmap has invalid size" }
        require(request.thumbnail.width > 0 && request.thumbnail.height > 0) { "Thumbnail has invalid size" }
        require(request.displayMode in 1..3) { "Invalid display mode: ${request.displayMode}" }
        require(request.pointerStyle in 0..3) { "Invalid pointer style: ${request.pointerStyle}" }
        require(request.elements.all { it.wireType in 1..10 && it.x in 0..65535 && it.y in 0..65535 }) {
            "Invalid WL widget type or coordinate"
        }

        val outDir = File(context.cacheDir, "wl_v10_pack/${System.currentTimeMillis()}")
        check(outDir.mkdirs()) { "Cannot create WL pack directory: ${outDir.absolutePath}" }
        Log.i(TAG, "build start bg=${request.backgrounds.size} dir=${outDir.absolutePath}")

        val outputs = ArrayList<MediaFileInfo>(request.backgrounds.size + 1)
        outputs += convertBitmap(outDir, "thumb.png", "pw", request.thumbnail)
        request.backgrounds.forEachIndexed { index, bitmap ->
            outputs += convertBitmap(outDir, "bg_${index + 1}.png", "bg${index + 1}", bitmap)
        }

        val config = V10CustomWatchfaceConfig().apply {
            displayMode = request.displayMode
            bgCount = request.backgrounds.size
            pointerStyle = request.pointerStyle
            textColorRgb = request.textColorRgb
            elements = request.elements.map {
                check(WlWatchfaceWidgetWire.isValidWireByte(it.wireType) && it.wireType != 0) {
                    "Invalid WL widget wire type: ${it.wireType}"
                }
                V10CustomWatchfaceConfig.Element(it.wireType, it.x, it.y)
            }
        }
        Log.i(TAG, "build success files=${outputs.size} elements=${config.elementCount}")
        return Result(outputs, config)
    }

    /** Converts one phone bitmap to the fixed watch resource name expected by the media transfer. */
    private fun convertBitmap(
        outDir: File,
        sourceName: String,
        targetName: String,
        bitmap: Bitmap,
    ): MediaFileInfo {
        val source = File(outDir, sourceName)
        FileOutputStream(source).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Cannot write $sourceName" }
        }
        val target = File(outDir, targetName)
        val latch = CountDownLatch(1)
        var failed: String? = null
        val converter = BmpConvert()
        try {
            // TYPE_707N_ARGB is the watch resource format; do not change it without firmware agreement.
            converter.bitmapConvert(
                BmpConvert.TYPE_707N_ARGB,
                source.absolutePath,
                target.absolutePath,
                ConvertParam().setFormat(ConvertParam.FORMAT_AUTO),
                object : OnConvertListener {
                    override fun onStart(path: String?) = Unit

                    override fun onStop(result: Boolean, output: String?) {
                        if (!result) failed = output ?: "BmpConvert failed"
                        latch.countDown()
                    }

                    override fun onStop(result: ConvertResult?, output: String?) = Unit
                },
            )
            check(latch.await(30, TimeUnit.SECONDS)) { "BmpConvert timeout: $targetName" }
            check(failed == null) { "BmpConvert failed for $targetName: $failed" }
            check(target.isFile && target.length() > 0L) { "Converted file missing or empty: $targetName" }
            return MediaFileInfo.fromFile(
                target.absolutePath,
                targetName,
                MediaPacketBuilder.FILE_TYPE_JPG,
            ) ?: error("Cannot create media file: $targetName")
        } finally {
            runCatching { converter.release() }
        }
    }
}
