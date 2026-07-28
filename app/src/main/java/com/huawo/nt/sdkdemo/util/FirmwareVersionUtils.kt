package com.huawo.nt.sdkdemo.util

import java.util.regex.Pattern

/**
 * Parses firmware strings shaped like `V…R…T…H…B{digits}…`
 * into major version (V) and build number (B).
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

    fun extractV(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return extract(str, 1).orEmpty()
    }

    fun extractR(str: String?): String? = extract(str, 2)

    fun extractT(str: String?): String? = extract(str, 3)

    fun extractH(str: String?): String? = extract(str, 4)

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
     * Whether [destVersion]/[destBuild] is newer than [currentVersion]/[currentBuild].
     * When major versions are semantically equal, only build is compared.
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
