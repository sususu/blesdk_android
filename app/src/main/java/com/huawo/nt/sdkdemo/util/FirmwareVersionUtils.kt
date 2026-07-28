package com.huawo.nt.sdkdemo.util

import java.util.regex.Pattern

/**
 * Parse / compare watch firmware version strings.
 *
 * Device firmware is typically shaped like:
 * `V{major}R{…}T{…}H{…}B{build}…`
 * e.g. `V1.0.0RxxxTxxxHxxxB123`
 *
 * Used by OTA check:
 * - Extract `V` / `B` for the server request body (`currentVersion` / `currentBuild`).
 * - Decide whether the server package is newer ([canUpgrade]).
 */
object FirmwareVersionUtils {
    private val PATTERN: Pattern =
        Pattern.compile("V(.+?)R(.+?)T(.+?)H(.+?)B(\\d+).*", Pattern.CASE_INSENSITIVE)

    fun extractVersion(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return try {
            extractV(str)
        } catch (_: Exception) {
            ""
        }
    }

    /** Major version segment after `V` (group 1). */
    fun extractV(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return extract(str, 1).orEmpty()
    }

    fun extractR(str: String?): String? = extract(str, 2)

    fun extractT(str: String?): String? = extract(str, 3)

    fun extractH(str: String?): String? = extract(str, 4)

    /** Build number after `B` (group 5), as Long. */
    fun extractB(str: String?): Long? {
        val s = extract(str, 5) ?: return null
        return s.toLongOrNull()
    }

    /**
     * Formats as `major(build)`, e.g. `1.0.0(123)`.
     * Returns empty string when the firmware string cannot be parsed.
     */
    fun formatDisplay(str: String?): String {
        if (str.isNullOrBlank()) return ""
        val major = extractVersion(str)
        val build = extractB(str)
        if (major.isBlank() || build == null) return ""
        return "$major($build)"
    }

    /**
     * Whether two dotted numeric versions are equal numerically
     * (e.g. `1.0.0` vs `1.0.00`). Missing trailing segments count as 0.
     */
    fun semanticVersionEquals(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() && b.isNullOrBlank()) return true
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a == b) return true
        return try {
            val pa = a.split(".")
            val pb = b.split(".")
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val va = if (i < pa.size) pa[i].trim().toInt() else 0
                val vb = if (i < pb.size) pb[i].trim().toInt() else 0
                if (va != vb) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Whether destination package [destVersion]/[destBuild] is newer than
     * [currentVersion]/[currentBuild].
     *
     * Rules:
     * - If major versions are semantically equal → compare build only.
     * - Otherwise compare major segments left-to-right numerically.
     */
    fun canUpgrade(
        currentVersion: String?,
        currentBuild: Long?,
        destVersion: String?,
        destBuild: Long?,
    ): Boolean {
        if (currentVersion.isNullOrBlank() || destVersion.isNullOrBlank()) return false
        if (currentBuild == null || destBuild == null) return false
        return try {
            if (semanticVersionEquals(currentVersion, destVersion)) {
                currentBuild < destBuild
            } else {
                val pa = currentVersion.split(".")
                val pb = destVersion.split(".")
                val n = maxOf(pa.size, pb.size)
                for (i in 0 until n) {
                    val vc = if (i < pa.size) pa[i].trim().toInt() else 0
                    val vd = if (i < pb.size) pb[i].trim().toInt() else 0
                    if (vc == vd) continue
                    return vc < vd
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extract(str: String?, group: Int): String? {
        if (str.isNullOrBlank()) return null
        return try {
            val matcher = PATTERN.matcher(str)
            if (matcher.matches()) matcher.group(group) else null
        } catch (_: Exception) {
            null
        }
    }
}
