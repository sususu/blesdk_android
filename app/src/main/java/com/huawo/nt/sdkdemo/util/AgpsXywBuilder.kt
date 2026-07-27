package com.huawo.nt.sdkdemo.util

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Build 7-day XYW (芯与物) AGPS zip, aligned with console [GpsService] xyw path:
 *
 * 1. Download 5 `.pgl` from `starcourse.rx-networks.cn`
 * 2. Append 16-byte trailer: `AGPS` + start(4 LE) + end(4 LE) + reserved(4)
 * 3. Zip entries under `music/gps/agps/{filename}` → `agps_xyw.zip`
 */
object AgpsXywBuilder {
    private const val BASE_URL = "http://starcourse.rx-networks.cn/IYMx9qGm7H/"

    /** Same file names / constellation mapping as server GpsService (7-day). */
    private val SOURCE_FILES =
        listOf(
            "f1e1G7.pgl", // GPS
            "f1e1C7.pgl", // BDS
            "f1e1J7.pgl", // QZSS
            "f1e1E7.pgl", // GLO (server naming)
            "f1e1R7.pgl", // GAL (server naming)
        )

    private const val ZIP_ENTRY_PREFIX = "music/gps/agps/"

    data class Result(
        val zipFile: File,
        val validStartTimeMs: Long,
        val validEndTimeMs: Long,
    )

    interface Listener {
        fun onLog(message: String)

        fun onProgress(progress: Int)
    }

    /**
     * Download + process + zip on the calling thread (run on IO).
     * [Listener.onProgress] range is 0..100 for this build phase only.
     */
    fun build(context: Context, listener: Listener? = null): Result {
        val root =
            File(context.cacheDir, "agps_xyw/${System.currentTimeMillis()}").also {
                it.mkdirs()
            }
        val rawDir = File(root, "raw").also { it.mkdirs() }
        val processedDir = File(root, "processed").also { it.mkdirs() }

        var validStartMs = 0L
        var validEndMs = 0L
        val processedFiles = ArrayList<File>(SOURCE_FILES.size)

        SOURCE_FILES.forEachIndexed { index, name ->
            val url = "$BASE_URL$name?t=${System.currentTimeMillis()}"
            val rawFile = File(rawDir, name)
            listener?.onLog("下载 $name …")
            downloadFile(url, rawFile)
            listener?.onProgress(((index + 1) * 70) / SOURCE_FILES.size)

            val outFile = File(processedDir, name)
            listener?.onLog("处理有效时间 $name …")
            val (startSec, endSec) = appendValidTimeTrailer(rawFile, outFile)
            // Same merge as GpsService: latest start, earliest end
            val startMs = startSec * 1000L
            val endMs = endSec * 1000L
            if (validStartMs <= 0L || startMs > validStartMs) {
                validStartMs = startMs
            }
            if (validEndMs <= 0L || endMs < validEndMs) {
                validEndMs = endMs
            }
            processedFiles.add(outFile)
            listener?.onProgress(70 + ((index + 1) * 20) / SOURCE_FILES.size)
        }

        listener?.onLog("打包 agps_xyw.zip …")
        val zipFile = File(root, "agps_xyw.zip")
        zipWithAgpsPaths(processedFiles, zipFile)
        listener?.onProgress(100)
        listener?.onLog(
            "ZIP 完成: ${zipFile.name} (${zipFile.length()} bytes), " +
                "valid=$validStartMs..$validEndMs",
        )
        return Result(zipFile, validStartMs, validEndMs)
    }

    private fun downloadFile(fileUrl: String, savePath: File) {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val conn =
                    (URL(fileUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code for $fileUrl")
                    }
                    conn.inputStream.use { input ->
                        FileOutputStream(savePath).use { output ->
                            input.copyTo(output, 8 * 1024)
                        }
                    }
                    if (!savePath.exists() || savePath.length() == 0L) {
                        throw IllegalStateException("Downloaded empty: ${savePath.name}")
                    }
                    return
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt == 2) throw e
            }
        }
        throw lastError ?: IllegalStateException("Download failed: $fileUrl")
    }

    /**
     * Parse EE info after 29-byte header, append 16-byte AGPS trailer.
     * @return start/end time in **seconds** (UTC), same as server.
     */
    private fun appendValidTimeTrailer(source: File, dest: File): Pair<Long, Long> {
        val bytes = source.readBytes()
        require(bytes.isNotEmpty()) { "Empty AGPS file: ${source.name}" }

        // header 29 + EE VERSION 1 → CONNSTELLATION at offset 30
        var start = 29 + 1
        val constel = bytesToInt(bytes, start, 1)
        start += 1
        val gnssStart = bytesToLongBigEnd(bytes, start, 4)
        start += 4
        val numberOfBlock = bytesToInt(bytes, start, 1)
        start += 1
        val durationInHour = bytesToInt(bytes, start, 1)

        val startTime = gnssToUtc(gnssStart, constel)
        val endTime = startTime + numberOfBlock.toLong() * durationInHour.toLong() * 3600L

        val trailer = ByteArray(16)
        val flag = "AGPS".toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(flag, 0, trailer, 0, 4)
        System.arraycopy(longToBytesLe(startTime, 4), 0, trailer, 4, 4)
        System.arraycopy(longToBytesLe(endTime, 4), 0, trailer, 8, 4)

        FileOutputStream(dest).use { fos ->
            fos.write(bytes)
            fos.write(trailer)
        }
        return startTime to endTime
    }

    private fun zipWithAgpsPaths(files: List<File>, zipFile: File) {
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val buffer = ByteArray(8 * 1024)
            for (file in files) {
                if (!file.exists() || !file.isFile) continue
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        zos.putNextEntry(ZipEntry(ZIP_ENTRY_PREFIX + file.name))
                        while (true) {
                            val read = bis.read(buffer)
                            if (read < 0) break
                            zos.write(buffer, 0, read)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
        require(zipFile.exists() && zipFile.length() > 0L) {
            "AGPS zip empty: ${zipFile.absolutePath}"
        }
    }

    private fun gnssToUtc(gnss: Long, constel: Int): Long {
        val mslSystemGpsOffset = 315_964_800L
        val mslGpsBdsOffset = 820_108_800L
        val mslGpsGalOffset = 619_315_187L
        val mslGpsBeidouLeap = 14L
        val mslGpsGalileoLeap = 13L

        var result = gnss + (mslSystemGpsOffset - 18)
        when (constel) {
            3 -> result += mslGpsBdsOffset + mslGpsBeidouLeap // BDS
            2 -> result += mslGpsGalOffset + mslGpsGalileoLeap // GAL
        }
        return result
    }

    private fun bytesToInt(data: ByteArray, offset: Int, length: Int): Int {
        var result = 0
        for (i in 0 until length) {
            result += (data[offset + i].toInt() and 0xff) shl (i * 8)
        }
        return result
    }

    private fun bytesToLongBigEnd(data: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in length - 1 downTo 0) {
            result += (data[offset + i].toLong() and 0xffL) shl ((length - 1 - i) * 8)
        }
        return result
    }

    private fun longToBytesLe(value: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = (value shr (i * 8) and 0xffL).toByte()
        }
        return result
    }
}
