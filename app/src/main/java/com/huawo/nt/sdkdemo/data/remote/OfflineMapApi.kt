package com.huawo.nt.sdkdemo.data.remote

import android.content.Context
import android.util.Log
import com.huawo.nt.sdkdemo.data.model.PiMapInfo
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Calls the native PiMap HTTP APIs directly (same as server [PiMapService]),
 * not the HaWoFit proxy `api/v1/devices/pimap/...`.
 *
 * - Auth: `POST {base}auth/getAuthInfo?type=0001&deviceId={uuid}`
 * - Package: `POST {base}optionalMap/generateFile`
 *
 * Headers: `token` + `userId`. Success when response `code == 0`.
 *
 * RSA public key is only needed for password login; this demo uses a pre-issued token.
 */
object OfflineMapApi {
    private const val TAG = "OfflineMapApi"

    /** Native PiMap host (must end with `/`). */
    const val BASE_URL = "https://map-global.iipii.net/"

    /** RSA public key (password encrypt); unused while [USER_ID]/[TOKEN] are set. */
    const val RSA_PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCntVNj2x3Kw/0EndhE8+zOianIG0TdPtMrAwWPRwHLdNsd02lE4KsmkZW/9Ac1uno54vydj7D6/Bb0UAKJ5LaocX6nZ66DtYVKPS2Vr45F0shSnzJTGInk68EwrdWRWmZuNccVkDfntIKCjcbw5ha1lZ9EG66hBrJEJHR0Ru2/kwIDAQAB"

    const val USER_ID = "1d724a30909e419bbb049940901649e1"
    const val TOKEN =
        "8p39z03n0zwgkg6u2ckx5s70sjroqx132fat4z8f5pkyr18sr3trdep4q31f0217bp4mqwkema2huj8djare0t92zmuiil8gepgjjst17nenyx17j0xuzujcl8z4sf20"

    private const val AUTH_PATH = "auth/getAuthInfo"
    private const val GENERATE_PATH = "optionalMap/generateFile"

    fun fetchAuthCode(context: Context, mapUuid: String): String {
        val url =
            BASE_URL + AUTH_PATH +
                "?type=0001&deviceId=" + java.net.URLEncoder.encode(mapUuid, Charsets.UTF_8.name())
        val body =
            JSONObject()
                .put("type", "0001")
                .put("deviceId", mapUuid)
                .toString()
        val raw = httpPostJson(url, body)
        Log.i(TAG, "getAuthInfo response=$raw")
        val data = requireSuccessData(raw, "getAuthInfo")
        val code = data.optString("authCode").orEmpty()
        if (code.isBlank()) throw IllegalStateException("authCode empty")
        return code
    }

    fun requestOfflinePackage(
        context: Context,
        mapUuid: String,
        productCode: String,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
        centerLat: Double,
        centerLon: Double,
    ): PiMapInfo {
        val body =
            JSONObject().apply {
                put("type", 1)
                put("minLongitude", minLon)
                put("minLatitude", minLat)
                put("maxLongitude", maxLon)
                put("maxLatitude", maxLat)
                put("deviceUuid", mapUuid)
                put("manufacturer", "Huawo")
                put("model", productCode)
                put("reservedStorage", 52048)
                put("locationLat", centerLat)
                put("locationLon", centerLon)
            }
        Log.i(TAG, "generateFile params=$body")
        val raw = httpPostJson(BASE_URL + GENERATE_PATH, body.toString())
        Log.i(TAG, "generateFile response=$raw")
        val data = requireSuccessData(raw, "generateFile")
        val url = data.optString("downloadUrl").orEmpty()
        val md5 = data.optString("md5").orEmpty()
        if (url.isBlank() || md5.isBlank()) {
            throw IllegalStateException("downloadUrl/md5 missing")
        }
        // Same field swap as server PiMapService (API keys are historically inverted).
        val uncompressed =
            data.optLong("compressedSize").takeIf { data.has("compressedSize") }
        val compressed =
            data.optLong("uncompressedSize").takeIf { data.has("uncompressedSize") }
        return PiMapInfo(
            downloadUrl = resolveFileUrl(url),
            md5 = md5,
            uncompressedSize = uncompressed,
            compressedSize = compressed,
        )
    }

    fun resolveFileUrl(path: String): String =
        if (path.lowercase().startsWith("http")) {
            path
        } else {
            BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
        }

    fun downloadToCache(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "device/offlinemap").also { it.mkdirs() }
        val out = File(dir, "map.zip")
        if (out.exists()) out.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(out).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0) {
                            onProgress((readTotal.toDouble() / total).toFloat().coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            onProgress(1f)
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun md5Of(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireSuccessData(raw: String, api: String): JSONObject {
        val root = JSONObject(raw)
        val code = root.optInt("code", -1)
        if (code != 0) {
            val msg = root.optString("msg").orEmpty().ifBlank { raw }
            throw IllegalStateException("$api failed code=$code msg=$msg")
        }
        return root.optJSONObject("data")
            ?: throw IllegalStateException("$api data null")
    }

    private fun httpPostJson(url: String, jsonBody: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("token", TOKEN)
            setRequestProperty("userId", USER_ID)
        }
        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val stream =
                if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                    ?: conn.inputStream
            return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
