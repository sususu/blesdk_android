package com.huawo.nt.sdkdemo.data.local

import android.content.Context
import com.huawo.sdk.bluetoothsdk.interfaces.ops.models.HealthAnalysisResult
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/**
 * 手表运动健康分析报告的本地记录。
 * 同一设备、同一自然日只保留一条，新报告覆盖当天记录，与 HaWoFit 的保存规则一致。
 */
class AiHealthReportStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun list(mac: String): List<AiHealthRecord> {
        if (mac.isBlank()) return emptyList()
        return synchronized(lock) {
            read().filter { it.mac.equals(mac, ignoreCase = true) }
                .sortedByDescending { it.timeMillis }
        }
    }

    fun upsert(mac: String, result: HealthAnalysisResult): AiHealthRecord {
        val now = System.currentTimeMillis()
        val incoming = result.toRecord(id = now, mac = mac, timeMillis = now)
        synchronized(lock) {
            val all = read()
            val today = dayKey(now)
            val existing = all.lastOrNull { it.mac.equals(mac, ignoreCase = true) && dayKey(it.timeMillis) == today }
            val saved = if (existing == null) {
                incoming
            } else {
                incoming.copy(id = existing.id)
            }
            val updated = buildList {
                if (existing == null) add(saved)
                all.forEach { record ->
                    if (record.id == saved.id) add(saved) else add(record)
                }
            }.take(MAX_RECORDS)
            write(updated)
            return saved
        }
    }

    private fun read(): List<AiHealthRecord> {
        val raw = prefs.getString(KEY_RECORDS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toRecord()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(records: List<AiHealthRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).commit()
    }

    private fun HealthAnalysisResult.toRecord(id: Long, mac: String, timeMillis: Long) =
        AiHealthRecord(
            id = id,
            mac = mac,
            timeMillis = timeMillis,
            step = getStep().orEmpty(),
            heartRate = getHeartRate().orEmpty(),
            sleep = getSleep().orEmpty(),
            spo2 = getSpO2().orEmpty(),
            stress = getStress().orEmpty(),
            pai = getPAI().orEmpty(),
            weight = getWeight().orEmpty(),
            bp = getBp().orEmpty(),
            summary = getSummary().orEmpty(),
        )

    private fun AiHealthRecord.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("mac", mac)
            .put("timeMillis", timeMillis)
            .put("step", step)
            .put("heartRate", heartRate)
            .put("sleep", sleep)
            .put("spo2", spo2)
            .put("stress", stress)
            .put("pai", pai)
            .put("weight", weight)
            .put("bp", bp)
            .put("summary", summary)

    private fun JSONObject.toRecord(): AiHealthRecord? {
        val mac = optString("mac")
        val timeMillis = optLong("timeMillis")
        if (mac.isBlank() || timeMillis <= 0L) return null
        return AiHealthRecord(
            id = optLong("id").takeIf { it > 0L } ?: timeMillis,
            mac = mac,
            timeMillis = timeMillis,
            step = optString("step"),
            heartRate = optString("heartRate"),
            sleep = optString("sleep"),
            spo2 = optString("spo2"),
            stress = optString("stress"),
            pai = optString("pai"),
            weight = optString("weight"),
            bp = optString("bp"),
            summary = optString("summary"),
        )
    }

    private fun dayKey(timeMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private companion object {
        const val PREFS = "ai_health_reports"
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 100
    }
}

data class AiHealthRecord(
    val id: Long,
    val mac: String,
    val timeMillis: Long,
    val step: String,
    val heartRate: String,
    val sleep: String,
    val spo2: String,
    val stress: String,
    val pai: String,
    val weight: String,
    val bp: String,
    val summary: String,
)
