package com.huawo.nt.sdkdemo.util

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zips multiple files for Sifli [SifliWatchSDK.syncZipFile].
 *
 * Production equivalents:
 * - Music: HaWoFit FileUtils.compressFilesToZip → type=4
 * - Album: QjsUtils convert to bin then compressFilesToZip → type=3
 *
 * Zip entries use each File.name (album: PNG_x.bin / PRE_x.bin).
 */
object MediaZipUtils {
    fun zipFiles(context: Context, files: List<File>, zipName: String): File {
        require(files.isNotEmpty()) { "No files to zip" }
        val dir = File(context.cacheDir, "media_zip").also { it.mkdirs() }
        val out = File(dir, zipName)
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            val buffer = ByteArray(8 * 1024)
            for (file in files) {
                if (!file.exists() || !file.isFile) continue
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        zos.putNextEntry(ZipEntry(file.name))
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
        require(out.exists() && out.length() > 0L) { "Zip empty: ${out.absolutePath}" }
        return out
    }
}
