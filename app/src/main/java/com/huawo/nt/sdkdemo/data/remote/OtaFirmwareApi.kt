package com.huawo.nt.sdkdemo.data.remote

import android.content.Context
import android.util.Log
import com.huawo.nt.sdkdemo.data.model.OtaFirmwareItem
import com.huawo.nt.sdkdemo.data.model.OtaResourceItem
import com.huawo.nt.sdkdemo.data.model.OtaUpgradeInfo
import com.huawo.nt.sdkdemo.util.FirmwareVersionUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Server-side OTA helpers: check for updates + download firmware / resource files.
 *
 * ## Check-upgrade API
 *
 * - Method / path: `POST {BASE_URL}api/v1/devices/upgrades`
 * - JSON body fields:
 *   - `currentVersion`  – major version extracted from device FW (`V…`)
 *   - `currentBuild`    – build number extracted from device FW (`B…`)
 *   - `productCode`     – device type / product code
 *   - `customerCode`    – fixed [CUSTOMER_CODE]
 *   - `deviceId`        – device id from [BleDeviceInfo]
 * - Request headers (required by server):
 *   - `appId`      – application package name
 *   - `appVersion` – application `versionName`
 *
 * ## Response
 *
 * Expects `{ ok: true, data: { version, build, firmwares[], resource? } }`.
 * Relative file paths in `firmwares[].url` / `resource.url` are resolved with [FILE_BASE_URL].
 *
 * ## Download
 *
 * [downloadToCache] writes under app cache, optionally verifies MD5, and reports 0..100 progress.
 */
object OtaFirmwareApi {
    private const val TAG = "OtaFirmwareApi"

    const val BASE_URL = "https://test.huawo-wear.com/"
    const val FILE_BASE_URL = "https://test.huawo-wear.com/files/"
    const val CUSTOMER_CODE = "Huawo"

    private const val CHECK_PATH = "api/v1/devices/upgrades"

    /**
     * Query the upgrade server for a firmware package matching this device.
     *
     * @param context used only to read packageName / versionName for request headers
     * @param currentFirmwareRaw raw device firmware string (`V…R…T…H…B…`)
     * @param productCode device type / product code
     * @param deviceId device id from [com.huawo.nt.sdkdemo.data.model.BleDeviceInfo.id]
     */
    fun checkUpgrade(
        context: Context,
        currentFirmwareRaw: String,
        productCode: String,
        deviceId: String,
    ): OtaUpgradeInfo {
        val currentVersion = FirmwareVersionUtils.extractV(currentFirmwareRaw)
        val currentBuild = FirmwareVersionUtils.extractB(currentFirmwareRaw)
        if (currentVersion.isBlank()) {
            throw IllegalStateException("Cannot parse major version from firmware: $currentFirmwareRaw")
        }

        val url = BASE_URL + CHECK_PATH
        val body =
            JSONObject().apply {
                put("currentVersion", currentVersion)
                put("productCode", productCode)
                put("currentBuild", currentBuild ?: 0L)
                put("customerCode", CUSTOMER_CODE)
                put("deviceId", deviceId)
            }
        Log.i(TAG, "checkUpgrade url=$url")
        Log.i(TAG, "checkUpgrade params=$body")
        val response = httpPostJson(context, url, body.toString())
        Log.i(TAG, "checkUpgrade response=$response")
        return parseUpgradeResponse(response)
    }

    /**
     * Whether [info] is a usable newer package vs the watch's current firmware.
     * Requires non-empty [OtaUpgradeInfo.firmwares] and comparable V/B on both sides.
     */
    fun isNewerThan(info: OtaUpgradeInfo, currentFirmwareRaw: String): Boolean {
        if (info.firmwares.isEmpty()) return false
        val curV = FirmwareVersionUtils.extractV(currentFirmwareRaw)
        val curB = FirmwareVersionUtils.extractB(currentFirmwareRaw) ?: return false
        val destV = info.version ?: return false
        val destB = info.build ?: return false
        return FirmwareVersionUtils.canUpgrade(curV, curB, destV, destB)
    }

    /** Absolute http(s) URL as-is; otherwise prepend [FILE_BASE_URL]. */
    fun resolveFileUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return if (path.lowercase().startsWith("http")) path else FILE_BASE_URL + path
    }

    /**
     * Download [url] into `cacheDir/[subDir]/[fileName]`, verify [expectMd5] when set.
     * [onProgress] reports 0..100 for this single file.
     */
    fun downloadToCache(
        context: Context,
        url: String,
        fileName: String,
        expectMd5: String?,
        subDir: String,
        onProgress: ((Int) -> Unit)? = null,
    ): File {
        val dir = File(context.cacheDir, subDir).also { it.mkdirs() }
        val out = File(dir, fileName)
        downloadToFile(resolveFileUrl(url), out, onProgress)
        if (!expectMd5.isNullOrBlank()) {
            val actual = md5Hex(out)
            if (!actual.equals(expectMd5, ignoreCase = true)) {
                out.delete()
                throw IllegalStateException("MD5 mismatch: expect=$expectMd5 actual=$actual")
            }
        }
        return out
    }

    /** Convenience wrapper: cache under `device/qjs`, file name prefers md5. */
    fun downloadFirmware(
        context: Context,
        item: OtaFirmwareItem,
        onProgress: ((Int) -> Unit)? = null,
    ): File {
        val name =
            item.md5?.takeIf { it.isNotBlank() }
                ?: "fw_${System.currentTimeMillis()}"
        return downloadToCache(
            context = context,
            url = item.url,
            fileName = name,
            expectMd5 = item.md5,
            subDir = "device/qjs",
            onProgress = onProgress,
        )
    }

    /** Parse Huawo envelope `{ ok|code, msg, data }`. */
    fun parseUpgradeResponse(json: String): OtaUpgradeInfo {
        val root = JSONObject(json)
        val ok = root.optBoolean("ok", root.optInt("code", -1) == 0)
        if (!ok) {
            val msg = root.optString("msg").ifBlank { root.optString("message") }
            throw IllegalStateException(msg.ifBlank { "check upgrade failed: code=${root.optInt("code")}" })
        }
        val data = root.optJSONObject("data") ?: return OtaUpgradeInfo()
        return parseUpgradeInfoObject(data)
    }

    /** Parse a bare upgrade-info object, optionally wrapped in `data` / `result`. */
    fun parseUpgradeInfo(json: String): OtaUpgradeInfo {
        val root = JSONObject(json)
        val obj =
            when {
                root.has("data") && root.opt("data") is JSONObject -> root.getJSONObject("data")
                root.has("result") && root.opt("result") is JSONObject ->
                    root.getJSONObject("result")
                else -> root
            }
        return parseUpgradeInfoObject(obj)
    }

    /**
     * Map JSON fields into [OtaUpgradeInfo].
     * Accepts several alternate array / field names for compatibility.
     */
    private fun parseUpgradeInfoObject(obj: JSONObject): OtaUpgradeInfo {
        val firmwares = ArrayList<OtaFirmwareItem>()
        val arr: JSONArray? =
            when {
                obj.has("firmwares") -> obj.optJSONArray("firmwares")
                obj.has("firmwareList") -> obj.optJSONArray("firmwareList")
                obj.has("files") -> obj.optJSONArray("files")
                else -> null
            }
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val url = f.optString("url").ifBlank { f.optString("downloadUrl") }
                if (url.isBlank()) continue
                firmwares +=
                    OtaFirmwareItem(
                        url = url,
                        md5 = f.optString("md5").takeIf { it.isNotBlank() }
                            ?: f.optString("fileMd5").takeIf { it.isNotBlank() },
                        name = f.optString("name").takeIf { it.isNotBlank() },
                        id = f.optString("id").takeIf { it.isNotBlank() },
                        type = f.optInt("type", 0x01),
                    )
            }
        }

        // Diff-mode optional resource package (required when zip has diff_ctrl*.bin).
        val resourceObj = obj.optJSONObject("resource")
        val resource =
            resourceObj?.let {
                OtaResourceItem(
                    name = it.optString("name").takeIf { n -> n.isNotBlank() },
                    url = it.optString("url").takeIf { u -> u.isNotBlank() },
                    md5 = it.optString("md5").takeIf { m -> m.isNotBlank() },
                    fromVersion = it.optString("fromVersion").takeIf { v -> v.isNotBlank() },
                    toVersion = it.optString("toVersion").takeIf { v -> v.isNotBlank() },
                )
            }?.takeIf {
                // Empty `{}` resource must not force diff mode.
                !it.url.isNullOrBlank() && !it.md5.isNullOrBlank() && !it.name.isNullOrBlank()
            }

        val buildValue =
            when (val b = obj.opt("build")) {
                is Number -> b.toLong()
                is String -> b.toLongOrNull()
                else -> null
            }

        return OtaUpgradeInfo(
            version = obj.optString("version").takeIf { it.isNotBlank() }
                ?: obj.optString("firmwareVersion").takeIf { it.isNotBlank() },
            build = buildValue,
            forceUpdate = obj.optBoolean("forceUpdate", false),
            updateContent = obj.optString("updateContent").takeIf { it.isNotBlank() }
                ?: obj.optString("content").takeIf { it.isNotBlank() }
                ?: obj.optString("desc").takeIf { it.isNotBlank() },
            firmwares = firmwares,
            resource = resource,
        )
    }

    /** POST JSON with app identity headers. */
    private fun httpPostJson(context: Context, url: String, jsonBody: String): String {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                applyAppHeaders(context)
            }
        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream =
                if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: conn.inputStream
                }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(200)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** Attach `appId` (packageName) and `appVersion` (versionName) headers. */
    private fun HttpURLConnection.applyAppHeaders(context: Context) {
        val appId = context.packageName
        val appVersion =
            runCatching {
                context.packageManager.getPackageInfo(appId, 0).versionName.orEmpty()
            }.getOrDefault("")
        setRequestProperty("appId", appId)
        setRequestProperty("appVersion", appVersion)
        Log.i(TAG, "request headers appId=$appId appVersion=$appVersion")
    }

    private fun downloadToFile(url: String, out: File, onProgress: ((Int) -> Unit)?) {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download HTTP $code url=$url")
            }
            val total = conn.contentLengthLong
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(out).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var written = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        if (total > 0L) {
                            val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress?.invoke(pct)
                            }
                        }
                    }
                }
            }
            onProgress?.invoke(100)
        } finally {
            conn.disconnect()
        }
    }

    fun md5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
