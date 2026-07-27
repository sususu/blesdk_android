package com.huawo.nt.sdkdemo.data.local

import android.content.Context
import com.huawo.nt.sdkdemo.data.model.BleDeviceInfo
import com.huawo.nt.sdkdemo.data.model.BoundDeviceRecord
import org.json.JSONObject

/** App 侧绑定信息本地存储（与 SDK 的 setBind 标记无关）。 */
class BoundDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(macAddress: String, name: String?, deviceInfo: BleDeviceInfo?) {
        prefs.edit()
            .putString(KEY_MAC, macAddress)
            .putString(KEY_NAME, name.orEmpty())
            .putString(KEY_INFO, deviceInfo?.let { toJson(it) }.orEmpty())
            .apply()
    }

    fun load(): BoundDeviceRecord? {
        val mac = prefs.getString(KEY_MAC, null).orEmpty()
        if (mac.isEmpty()) return null
        val name = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
        val infoJson = prefs.getString(KEY_INFO, null)
        val info = infoJson?.takeIf { it.isNotBlank() }?.let { fromJson(it) }
        return BoundDeviceRecord(macAddress = mac, name = name, deviceInfo = info)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun toJson(info: BleDeviceInfo): String {
        return JSONObject().apply {
            info.id?.let { put("id", it) }
            info.type?.let { put("type", it) }
            info.firmwareVersion?.let { put("firmwareVersion", it) }
            info.mac?.let { put("mac", it) }
            info.bindState?.let { put("bindState", it) }
            info.language?.let { put("language", it) }
            info.battery?.let { put("battery", it) }
            info.displayingWatchfaceId?.let { put("displayingWatchfaceId", it) }
            info.watchfaceVersion?.let { put("watchfaceVersion", it) }
            info.protocolVersion?.let { put("protocolVersion", it) }
            info.mapUuid?.let { put("mapUuid", it) }
            put("mapAuthorized", info.mapAuthorized)
        }.toString()
    }

    private fun fromJson(json: String): BleDeviceInfo? {
        return try {
            val o = JSONObject(json)
            BleDeviceInfo(
                id = o.optString("id").takeIf { it.isNotEmpty() },
                type = o.optString("type").takeIf { it.isNotEmpty() },
                firmwareVersion = o.optString("firmwareVersion").takeIf { it.isNotEmpty() },
                mac = o.optString("mac").takeIf { it.isNotEmpty() },
                bindState = o.optIntOrNull("bindState"),
                language = o.optIntOrNull("language"),
                battery = o.optIntOrNull("battery"),
                displayingWatchfaceId = o.optString("displayingWatchfaceId").takeIf { it.isNotEmpty() },
                watchfaceVersion = o.optIntOrNull("watchfaceVersion"),
                protocolVersion = o.optIntOrNull("protocolVersion"),
                mapUuid = o.optString("mapUuid").takeIf { it.isNotEmpty() },
                mapAuthorized = o.optBoolean("mapAuthorized", false),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    companion object {
        private const val PREFS = "bound_device"
        private const val KEY_MAC = "macAddress"
        private const val KEY_NAME = "name"
        private const val KEY_INFO = "deviceInfoJson"
    }
}
