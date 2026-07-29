package com.huawo.nt.sdkdemo.data.remote

import android.content.Context
import android.util.Log
import com.huawo.nt.sdkdemo.data.model.OnlineWatchface
import com.huawo.nt.sdkdemo.util.AppLanguage
import com.huawo.nt.sdkdemo.util.LocaleHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * HTTP helpers for the **online watchface** catalog and package download.
 *
 * ## Scope
 * This demo only covers the **Sifli / QJS** product path (same server APIs as HaWoFit).
 * WL / Realtek install transports are intentionally not implemented here.
 *
 * ## List API
 * - Method / path: `GET {BASE_URL}api/v1/products/{deviceType}/watchfaces`
 * - Query:
 *   - `customerCode` – fixed [CUSTOMER_CODE] (`Huawo`)
 *   - `locale` – app UI language mapped to server codes (`zh-Hans` / `en`)
 * - Path param `deviceType`: product code from [com.huawo.nt.sdkdemo.data.model.BleDeviceInfo.type]
 *   (bound device info). Wrong / empty type → empty list or HTTP error.
 *
 * ## Response envelope
 * Expects top-level fields (not nested under `data`):
 * ```
 * { "code": 0, "msg": null, "total": N, "rows": [ ... ] }
 * ```
 * Success when `ok == true` **or** `code == 0` (compatible with either style).
 *
 * ## File URLs
 * Server returns **relative** paths for `thumbnail` / `aodThumbnail` / `bin`.
 * Always resolve with [resolveFileUrl] → prepend [FILE_BASE_URL] unless already `http(s)`.
 *
 * ## Download + integrity
 * After downloading `bin`, verify [OnlineWatchface.binMd5] (case-insensitive hex).
 * On mismatch the local file is deleted and an exception is thrown — never push a corrupt zip.
 *
 * ## Headers
 * Server requires `appId` (packageName) and `appVersion` (versionName), same as OTA check API.
 */
object WatchfaceApi {
    private const val TAG = "WatchfaceApi"

    /** API host for list / metadata (test environment). */
    const val BASE_URL = "https://test.huawo-wear.com/"

    /**
     * CDN / static file root for thumbnails and watchface zip packages.
     * Note: list API uses [BASE_URL]; binary assets use this file base (also under test host).
     */
    const val FILE_BASE_URL = "https://test.huawo-wear.com/files/"

    /** Tenant / customer code required by the products API. */
    const val CUSTOMER_CODE = "Huawo"

    /**
     * Fetch the online watchface catalog for one product.
     *
     * @param context used for locale + `appId` / `appVersion` headers
     * @param deviceType product code (`BleDeviceInfo.type`); must be non-blank
     * @throws IllegalStateException on blank type, HTTP error, or non-success `code`
     */
    fun fetchOnlineWatchfaces(context: Context, deviceType: String): List<OnlineWatchface> {
        val type = deviceType.trim()
        if (type.isBlank()) {
            throw IllegalStateException("deviceType is blank")
        }
        val locale = apiLocale(context)
        // Encode path segment so product codes with special chars stay valid in the URL.
        val url =
            BASE_URL +
                "api/v1/products/${encodePath(type)}/watchfaces" +
                "?customerCode=$CUSTOMER_CODE&locale=${encodeQuery(locale)}"
        Log.i(TAG, "fetchOnlineWatchfaces url=$url")
        val response = httpGetJson(context, url)
        Log.i(TAG, "fetchOnlineWatchfaces response=${response.take(500)}")
        return parseWatchfaceList(response)
    }

    /**
     * Turn a server path into an absolute download URL.
     *
     * - Already absolute (`http…` / `https…`) → returned unchanged
     * - Relative (`2026-06-03/.../x.zip`) → [FILE_BASE_URL] + path
     * - Blank / null → empty string (caller should hide the ImageView / skip download)
     */
    fun resolveFileUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return if (path.lowercase().startsWith("http")) path else FILE_BASE_URL + path
    }

    /**
     * Download a file into app cache and optionally verify MD5.
     *
     * @param url relative or absolute; always passed through [resolveFileUrl]
     * @param fileName local name under `cacheDir/[subDir]/` (prefer server zip name)
     * @param expectMd5 server `binMd5`; when non-blank, must match or download is discarded
     * @param subDir cache subdirectory (default `online_watchface`)
     * @param onProgress 0..100 for this file only (may jump if Content-Length is missing)
     * @return the verified local [File]
     */
    fun downloadToCache(
        context: Context,
        url: String,
        fileName: String,
        expectMd5: String?,
        subDir: String = "online_watchface",
        onProgress: ((Int) -> Unit)? = null,
    ): File {
        val dir = File(context.cacheDir, subDir).also { it.mkdirs() }
        val out = File(dir, fileName)
        downloadToFile(resolveFileUrl(url), out, onProgress)
        // Integrity check is mandatory for install packages when the server provides binMd5.
        if (!expectMd5.isNullOrBlank()) {
            val actual = md5Hex(out)
            if (!actual.equals(expectMd5, ignoreCase = true)) {
                out.delete()
                throw IllegalStateException("MD5 mismatch: expect=$expectMd5 actual=$actual")
            }
        }
        return out
    }

    /**
     * Parse list JSON into [OnlineWatchface] rows.
     *
     * Field notes:
     * - `byteSize` is **KB** (not bytes) — stored as [OnlineWatchface.byteSizeKb]
     * - `bin` is the zip package path; required before install
     * - `binMd5` is hex MD5 of that zip; required for safe install
     * - `thumbnail` / `aodThumbnail` may be GIF/PNG relative paths
     */
    fun parseWatchfaceList(json: String): List<OnlineWatchface> {
        val root = JSONObject(json)
        val ok = root.optBoolean("ok", root.optInt("code", -1) == 0)
        if (!ok) {
            val msg = root.optString("msg").ifBlank { root.optString("message") }
            throw IllegalStateException(msg.ifBlank { "watchface list failed: code=${root.optInt("code")}" })
        }
        val rows = root.optJSONArray("rows") ?: return emptyList()
        val list = ArrayList<OnlineWatchface>(rows.length())
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val name = o.optString("name").ifBlank { id }
            // Tolerate Number or String for byteSize (server / proxy variance).
            val byteSize =
                when (val b = o.opt("byteSize")) {
                    is Number -> b.toLong()
                    is String -> b.toLongOrNull() ?: 0L
                    else -> 0L
                }
            list +=
                OnlineWatchface(
                    id = id,
                    name = name,
                    thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                    aodThumbnail = o.optString("aodThumbnail").takeIf { it.isNotBlank() },
                    bin = o.optString("bin").takeIf { it.isNotBlank() },
                    binMd5 = o.optString("binMd5").takeIf { it.isNotBlank() },
                    byteSizeKb = byteSize,
                )
        }
        return list
    }

    /**
     * Map app language preference to the locale string the products API expects.
     * Production HaWoFit uses resource `language` (`zh-Hans` / `en`); we mirror that.
     */
    fun apiLocale(context: Context): String {
        return when (LocaleHelper.getLanguage(context)) {
            AppLanguage.CHINESE -> "zh-Hans"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.SYSTEM -> {
                val lang =
                    context.resources.configuration.locales[0]?.language.orEmpty()
                if (lang.startsWith("zh")) "zh-Hans" else "en"
            }
        }
    }

    /** Blocking GET JSON with app identity headers. Call from a background dispatcher. */
    private fun httpGetJson(context: Context, url: String): String {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                applyAppHeaders(context)
            }
        try {
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

    /** Attach `appId` / `appVersion` — required by Huawo backend gateways. */
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

    /**
     * Stream GET into [out]. Progress uses Content-Length when present;
     * otherwise only a final 100% callback is guaranteed.
     */
    private fun downloadToFile(url: String, out: File, onProgress: ((Int) -> Unit)?) {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                // Watchface zips can be large; allow a longer read timeout than the list API.
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
                            // Debounce identical percentages to reduce UI churn.
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

    /** Lowercase hex MD5 of the entire file (same digest style as HaWoFit DigestHelper). */
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

    /** Encode a single path segment; keep spaces as `%20` (not `+`). */
    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
