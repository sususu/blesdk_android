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
 * - Login: `POST {base}user/login` → cache `userId` + `token`
 * - Auth: `POST {base}auth/getAuthInfo?type=0001&deviceId={uuid}`
 * - Package: `POST {base}optionalMap/generateFile`
 *
 * Headers: `token` + `userId`. Success when response `code == 0`.
 */
object OfflineMapApi {
    private const val TAG = "OfflineMapApi"

    /** Native PiMap host (must end with `/`). */
    const val BASE_URL = "https://map-global.iipii.net/"

    /** Demo account; password is RSA-encrypted ciphertext expected by the server. */
    private const val LOGIN_PHONE = "919654481080"
    private const val LOGIN_PWD =
        "di0RwD38AEQglZEByPNErjdWyMTmN8QK/Z04gMIm09h7HKx/z8z0Zf0mwwmc8pC7tJ/UFPiTxrn7vcuTtn4EUKNaKqAJxj7KtF/NoFDlPvMXNhMBRTVMRLkvA6nzq9EZo6qLeED9/QCQWLMm3uEbWoVHPpuraI9o/6iR/VXCe00="

    private const val PREFS = "offline_map_api"
    private const val KEY_USER_ID = "userId"
    private const val KEY_TOKEN = "token"

    private const val LOGIN_PATH = "user/login"
    private const val AUTH_PATH = "auth/getAuthInfo"
    private const val GENERATE_PATH = "optionalMap/generateFile"

    @Volatile
    private var cachedUserId: String? = null

    @Volatile
    private var cachedToken: String? = null

    private val sessionLock = Any()

    fun fetchAuthCode(context: Context, mapUuid: String): String {
        val url =
            BASE_URL + AUTH_PATH +
                "?type=0001&deviceId=" + java.net.URLEncoder.encode(mapUuid, Charsets.UTF_8.name())
        val body =
            JSONObject()
                .put("type", "0001")
                .put("deviceId", mapUuid)
                .toString()
        val raw = httpPostJson(context, url, body)
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
        val raw = httpPostJson(context, BASE_URL + GENERATE_PATH, body.toString())
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

    /** Clears cached login session (memory + disk). */
    fun clearSession(context: Context) {
        synchronized(sessionLock) {
            cachedUserId = null
            cachedToken = null
            prefs(context).edit().clear().apply()
        }
    }

    private fun ensureSession(context: Context): Pair<String, String> {
        synchronized(sessionLock) {
            val memUser = cachedUserId
            val memToken = cachedToken
            if (!memUser.isNullOrBlank() && !memToken.isNullOrBlank()) {
                return memUser to memToken
            }
            val prefs = prefs(context)
            val storedUser = prefs.getString(KEY_USER_ID, null).orEmpty()
            val storedToken = prefs.getString(KEY_TOKEN, null).orEmpty()
            if (storedUser.isNotBlank() && storedToken.isNotBlank()) {
                cachedUserId = storedUser
                cachedToken = storedToken
                Log.i(TAG, "reuse cached session userId=$storedUser")
                return storedUser to storedToken
            }
            return loginAndCache(context)
        }
    }

    private fun loginAndCache(context: Context): Pair<String, String> {
        val body =
            JSONObject()
                .put("phone", LOGIN_PHONE)
                .put("pwd", LOGIN_PWD)
                .toString()
        val raw = httpPostJsonRaw(BASE_URL + LOGIN_PATH, body, userId = null, token = null)
        Log.i(TAG, "login response=$raw")
        val data = requireSuccessData(raw, "login")
        val userId = data.optString("userId").orEmpty()
        val token = data.optString("token").orEmpty()
        if (userId.isBlank() || token.isBlank()) {
            throw IllegalStateException("login userId/token empty")
        }
        cachedUserId = userId
        cachedToken = token
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_TOKEN, token)
            .apply()
        Log.i(TAG, "login ok, cached userId=$userId")
        return userId to token
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun requireSuccessData(raw: String, api: String): JSONObject {
        val root = JSONObject(raw)
        if (!isSuccessCode(root)) {
            val code = root.opt("code")
            val msg = root.optString("msg").orEmpty().ifBlank { raw }
            throw IllegalStateException("$api failed code=$code msg=$msg")
        }
        return root.optJSONObject("data")
            ?: throw IllegalStateException("$api data null")
    }

    /** Server may return `code` as int `0` or string `"0"`. */
    private fun isSuccessCode(root: JSONObject): Boolean {
        val code = root.opt("code") ?: return false
        return when (code) {
            is Number -> code.toInt() == 0
            else -> code.toString() == "0"
        }
    }

    private fun httpPostJson(context: Context, url: String, jsonBody: String): String {
        val (userId, token) = ensureSession(context)
        return httpPostJsonRaw(url, jsonBody, userId = userId, token = token)
    }

    private fun httpPostJsonRaw(
        url: String,
        jsonBody: String,
        userId: String?,
        token: String?,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrBlank()) setRequestProperty("token", token)
            if (!userId.isNullOrBlank()) setRequestProperty("userId", userId)
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
