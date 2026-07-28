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
 * OTA check / download against Huawo test server.
 *
 * Check: `POST {BASE_URL}api/v1/devices/upgrades`
 * Relative firmware/resource URLs are resolved with [FILE_BASE_URL].
 */
object OtaFirmwareApi {
    private const val TAG = "OtaFirmwareApi"

    const val BASE_URL = "https://test.huawo-wear.com/"
    const val FILE_BASE_URL = "https://static.huawo-wear.com/files/"
    const val CUSTOMER_CODE = "Huawo"

    private const val CHECK_PATH = "api/v1/devices/upgrades"

    /**
     * @param currentFirmwareRaw raw device firmware string (V…R…T…H…B…)
     * @param productCode device type / product code
     * @param deviceId device id from [com.huawo.nt.sdkdemo.data.model.BleDeviceInfo.id]
     */
    fun checkUpgrade(
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
        val response = httpPostJson(url, body.toString())
        Log.i(TAG, "checkUpgrade response=$response")
        return parseUpgradeResponse(response)
    }

    /**
     * Whether [info] is a usable newer package vs current firmware.
     */
    fun isNewerThan(info: OtaUpgradeInfo, currentFirmwareRaw: String): Boolean {
        if (info.firmwares.isEmpty()) return false
        val curV = FirmwareVersionUtils.extractV(currentFirmwareRaw)
        val curB = FirmwareVersionUtils.extractB(currentFirmwareRaw) ?: return false
        val destV = info.version ?: return false
        val destB = info.build ?: return false
        return FirmwareVersionUtils.canUpgrade(curV, curB, destV, destB)
    }

    fun resolveFileUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return if (path.lowercase().startsWith("http")) path else FILE_BASE_URL + path
    }

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

    private fun httpPostJson(url: String, jsonBody: String): String {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
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
