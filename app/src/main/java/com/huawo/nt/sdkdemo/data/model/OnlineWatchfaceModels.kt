package com.huawo.nt.sdkdemo.data.model

/**
 * One online watchface row from `GET api/v1/products/{deviceType}/watchfaces`.
 *
 * ## Field meanings
 * - [id]: server primary key (string snowflake id)
 * - [name]: watchface package / display name; also used when switching an already-installed face
 *   via [com.huawo.sdk.bluetoothsdk.BluetoothSDK.switchSifliWatchfaceBy]
 * - [thumbnail]: preview image path (often GIF); resolve with [com.huawo.nt.sdkdemo.data.remote.WatchfaceApi.resolveFileUrl]
 * - [aodThumbnail]: always-on-display preview; may be null — hide AOD UI when blank
 * - [bin]: relative path of the install zip; required for download/push
 * - [binMd5]: expected MD5 of the zip bytes; verify after download before Sifli push
 * - [byteSizeKb]: server `byteSize` — unit is **KB**, not bytes
 *
 * ## Notes
 * Relative media/bin paths are **not** absolute URLs; always prepend the file base URL.
 * Do not push to the watch without a successful MD5 check when [binMd5] is present.
 */
data class OnlineWatchface(
    val id: String,
    val name: String,
    val thumbnail: String? = null,
    val aodThumbnail: String? = null,
    val bin: String? = null,
    val binMd5: String? = null,
    val byteSizeKb: Long = 0L,
)

/**
 * Progress callbacks for Sifli online-watchface ZIP push (delivered on the **main thread**).
 *
 * Used by [com.huawo.nt.sdkdemo.data.repository.BleRepository.pushOnlineWatchfaceZip]:
 * - [onReady]: syncZipFile has been invoked (transfer starting)
 * - [onProgress]: 0f..1f from `SifliWatchSDK.syncZipFile` (type=5 = online watchface)
 * - [onSuccess] / [onFail]: terminal states; do not continue UI lock after either
 *
 * Common fail codes (informational): `190` Sifli busy, `408` BLE disconnected,
 * `14` cancelled, `-3` missing file, `-5` MAC unavailable.
 */
interface OnlineWatchfaceTransferCallback {
    fun onReady()
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFail(code: Int, message: String)
}

/**
 * jieli  online-watchface transfer callbacks.
 * [onReady] only means that the SDK accepted the transfer; [onSuccess] is emitted after the
 * watch completes the terminal result, so callers must keep their install UI locked until then.
 */
interface WlOnlineWatchfaceTransferCallback {
    fun onReady()
    fun onProgress(progress: Float)
    fun onSuccess()
    fun onFail(code: Int, message: String)
}
