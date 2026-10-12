package com.huawo.nt.sdkdemo.ui.watchface.studio

import android.content.Context
import android.graphics.Bitmap
import com.huawo.watchface.Callback
import com.huawo.watchface.SifliWatchSDK
import com.huawo.watchface.qjs.Watchface
import com.huawo.watchface.qjs.widgets.Widget
import java.io.File

/**
 * Snapshot of Studio settings needed for one push to the watch.
 *
 * Assembled on the main thread by [WatchFaceStudioFragment.syncToWatch]:
 * - [thumbnail]: preview FrameLayout capture, scaled to preset thumb size (rounded)
 * - [background]: static background (already scaled); ignored when [backgroundGif] is set
 * - [backgroundGif]: animated GIF file from Video→GIF; uses [GifWidget] as bottom layer
 * - [widgets]: **without** the background layer; this packager prepends it
 */
data class StudioWatchfaceConfig(
    val name: String,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap,
    val background: Bitmap? = null,
    val backgroundGif: File? = null,
    val widgets: List<Widget>,
)

/**
 * Zips raw QJS [Widget]s from Studio and syncs them via [SifliWatchSDK].
 *
 * ## Pipeline
 * 1. Insert background: GIF → [QjsComponentBuilders.backgroundGif], else static bitmap
 * 2. Build [Watchface]: name, size, thumbnail, widget list
 * 3. `copy()` + `setAOD(true)`, attach as AOD config on the main watchface
 * 4. [Watchface.makeZip] async → [Callback.onZipCompleted]
 * 5. [SifliWatchSDK.syncZipFile]; `type = 5` means watchface zip
 *
 * ## vs CustomWatchface
 * This path uses only `com.huawo.watchface.qjs.*` low-level widgets,
 * **not** `com.huawo.watchface.custom.widget.*`.
 *
 * Callback threads are SDK-defined; the Fragment must hop back to main for UI.
 */
object QjsWatchfacePackager {

    /**
     * @param mac Connected device Bluetooth MAC (from [BluetoothSDK])
     * @param callback Forwards zip + transfer events (zip done, progress, success, error)
     */
    fun push(
        context: Context,
        mac: String,
        config: StudioWatchfaceConfig,
        callback: Callback,
    ) {
        // Background must be first or it will cover time/data widgets
        val widgets = ArrayList<Widget>(config.widgets.size + 1)
        val gif = config.backgroundGif
        when {
            gif != null && gif.exists() -> {
                val preview = config.background
                    ?: com.huawo.watchface.qjs.utils.FileUtils.getFirstFrameBitmapOfGif(
                        context,
                        gif,
                    )
                if (preview != null) {
                    widgets +=
                        QjsComponentBuilders.backgroundGif(
                            gifFile = gif,
                            preview = preview,
                            width = config.width,
                            height = config.height,
                        )
                }
            }
            config.background != null -> {
                widgets +=
                    QjsComponentBuilders.background(
                        config.background,
                        config.width,
                        config.height,
                    )
            }
        }
        widgets += config.widgets

        val watchface = Watchface().apply {
            setName(config.name)
            setWidth(config.width)
            setHeight(config.height)
            setThumbnail(config.thumbnail)
            setWidgetList(widgets)
        }
        // AOD: same layout as main dial, marked as always-on then attached
        val aod = watchface.copy().apply { setAOD(true) }
        watchface.setAod(aod)

        watchface.makeZip(
            context,
            object : Callback {
                override fun onError(code: Int) {
                    callback.onError(code)
                }

                override fun onSuccess() {
                    // makeZip success is reported via onZipCompleted; leave empty to avoid double toasts
                }

                override fun onQjsSaved() {
                    callback.onQjsSaved()
                }

                override fun onZipCompleted(zip: File?) {
                    callback.onZipCompleted(zip)
                    if (zip == null || !zip.exists()) {
                        callback.onError(-1)
                        return
                    }
                    // type 5 = watchface zip (SifliWatchSDK convention)
                    SifliWatchSDK.getInstance().syncZipFile(
                        true,
                        mac,
                        zip.absolutePath,
                        5,
                        object : Callback {
                            override fun onError(code: Int) = callback.onError(code)

                            override fun onSuccess() = callback.onSuccess()

                            override fun onProgress(current: Long, total: Long) {
                                callback.onProgress(current, total)
                            }

                            override fun onCancel() = callback.onCancel()

                            override fun onManagerStatusChanged(status: Int) {
                                callback.onManagerStatusChanged(status)
                            }
                        },
                    )
                }

                override fun onProgress(current: Long, total: Long) {
                    callback.onProgress(current, total)
                }
            },
        )
    }
}
