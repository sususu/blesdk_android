package com.huawo.nt.sdkdemo.util

import android.content.Context
import android.util.Log
import com.huawo.nt.sdkdemo.data.model.OtaUpgradeInfo
import com.huawo.nt.sdkdemo.data.remote.OtaFirmwareApi
import com.sifli.siflidfu.DFUImagePath
import com.sifli.siflidfu.Protocol.IMAGE_ID_CTRL
import com.sifli.siflidfu.Protocol.IMAGE_ID_DYN
import com.sifli.siflidfu.Protocol.IMAGE_ID_HCPU
import com.sifli.siflidfu.Protocol.IMAGE_ID_LCPU
import com.sifli.siflidfu.Protocol.IMAGE_ID_NAND_LCPU_PATCH
import com.sifli.siflidfu.Protocol.IMAGE_ID_NAND_RES
import com.sifli.siflidfu.Protocol.IMAGE_ID_RES
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Prepare [DFUImagePath] list for Sifli NAND DFU.
 *
 * Aligned with HaWoFit `DeviceUpgradeManager.upgrade` QJS / Sifli branch.
 *
 * ## Mode selection (do not change)
 * - If `diff_ctrl*.bin` exists → **DIFF** (even when `ctrl*` also exists).
 *   Requires [OtaUpgradeInfo.resource] name/url/md5; append as [IMAGE_ID_NAND_RES].
 *   Skip outdyn / outroot.
 * - Else if `ctrl*.bin` exists → **FULL** (+ optional outdyn / outroot).
 *
 * Image order: `hcpu? → lcpu? → patch? → ctrl/diff_ctrl → (outdyn/outroot | resource)`
 */
object SifliOtaHelper {
    private const val TAG = "SifliOtaHelper"

    /**
     * @param onProgress overall prepare progress 0..100
     *                   (main zip ≈ 0..85, optional resource ≈ 85..100)
     */
    fun prepareDfuImagePaths(
        context: Context,
        info: OtaUpgradeInfo,
        onProgress: ((Int) -> Unit)? = null,
    ): ArrayList<DFUImagePath> {
        val firmware =
            info.firmwares.firstOrNull()
                ?: throw IllegalStateException("No firmware package in upgrade info")
        if (firmware.url.isBlank() || firmware.md5.isNullOrBlank()) {
            throw IllegalStateException("Firmware url/md5 missing")
        }

        val zipFile =
            OtaFirmwareApi.downloadToCache(
                context = context,
                url = firmware.url,
                fileName = firmware.md5,
                expectMd5 = firmware.md5,
                subDir = "device/qjs",
            ) { pct -> onProgress?.invoke((pct * 0.85f).toInt().coerceIn(0, 85)) }

        // Per-package extract dir avoids leftover bins from other packages.
        val extractDir = File(zipFile.parentFile ?: context.cacheDir, "extract_${firmware.md5}")
        if (extractDir.exists()) {
            extractDir.deleteRecursively()
        }
        extractDir.mkdirs()
        val extracted = unzip(zipFile.absolutePath, extractDir.absolutePath)
        Log.i(TAG, "unzipped ${extracted.size} files into ${extractDir.absolutePath}")

        val list = ArrayList<DFUImagePath>()
        var ctrlPath: DFUImagePath? = null
        var diffCtrlPath: DFUImagePath? = null
        var outdynPath: DFUImagePath? = null
        var outrootPath: DFUImagePath? = null

        for (absolutePath in extracted) {
            val name = File(absolutePath).name
            val lower = name.lowercase()
            // Prefer diff_ctrl over ctrl when scanning (same IMAGE_ID_CTRL slot).
            if (lower.startsWith("diff_ctrl") && lower.endsWith(".bin")) {
                diffCtrlPath = DFUImagePath(absolutePath, null, IMAGE_ID_CTRL)
                Log.i(TAG, "found diff_ctrl: $name")
            } else if (lower.startsWith("ctrl") && lower.endsWith(".bin")) {
                ctrlPath = DFUImagePath(absolutePath, null, IMAGE_ID_CTRL)
                Log.i(TAG, "found ctrl: $name")
            }
            if (lower.startsWith("hcpu") && lower.endsWith(".bin")) {
                list += DFUImagePath(absolutePath, null, IMAGE_ID_HCPU)
                Log.i(TAG, "found hcpu: $name")
            }
            if (lower.startsWith("lcpu") && lower.endsWith(".bin")) {
                list += DFUImagePath(absolutePath, null, IMAGE_ID_LCPU)
                Log.i(TAG, "found lcpu: $name")
            }
            if (lower.startsWith("patch") && lower.endsWith(".bin")) {
                list += DFUImagePath(absolutePath, null, IMAGE_ID_NAND_LCPU_PATCH)
                Log.i(TAG, "found patch: $name")
            }
            if (lower.startsWith("outdyn") && lower.endsWith(".bin")) {
                outdynPath = DFUImagePath(absolutePath, null, IMAGE_ID_DYN)
                Log.i(TAG, "found outdyn: $name")
            }
            if (lower.startsWith("outroot") && lower.endsWith(".bin")) {
                outrootPath = DFUImagePath(absolutePath, null, IMAGE_ID_RES)
                Log.i(TAG, "found outroot: $name")
            }
        }

        // Prefer DIFF whenever diff_ctrl exists (HaWoFit / production rule).
        if (diffCtrlPath != null) {
            list += diffCtrlPath
            val resource = info.resource
            if (resource?.url.isNullOrBlank() ||
                resource?.md5.isNullOrBlank() ||
                resource?.name.isNullOrBlank()
            ) {
                throw IllegalStateException("Diff OTA requires resource name/url/md5")
            }
            Log.i(
                TAG,
                "mode=DIFF resource=${resource.name} from=${resource.fromVersion} to=${resource.toVersion}",
            )
            val resFile =
                OtaFirmwareApi.downloadToCache(
                    context = context,
                    url = resource.url!!,
                    fileName = resource.name!!,
                    expectMd5 = resource.md5,
                    subDir = "device/qjs_diff",
                ) { pct -> onProgress?.invoke(85 + (pct * 0.15f).toInt().coerceIn(0, 15)) }
            list += DFUImagePath(resFile.absolutePath, null, IMAGE_ID_NAND_RES)
            onProgress?.invoke(100)
            logFinalList(list)
            return list
        }

        // Full OTA: CTRL + optional outdyn / outroot.
        if (ctrlPath != null) {
            list += ctrlPath
            outdynPath?.let { list += it }
            outrootPath?.let { list += it }
            Log.i(TAG, "mode=FULL ctrl=${File(ctrlPath.imagePath).name}")
            onProgress?.invoke(100)
            logFinalList(list)
            return list
        }

        throw IllegalStateException("Zip missing ctrl / diff_ctrl")
    }

    private fun logFinalList(list: List<DFUImagePath>) {
        list.forEachIndexed { index, path ->
            Log.i(TAG, "DFU[$index] id=${path.imageType} path=${path.imagePath}")
        }
    }

    /**
     * Unzip [zipFilePath] into [destDir].
     * Returns absolute paths of extracted **files** only (directories are created but omitted).
     */
    fun unzip(zipFilePath: String, destDir: String): ArrayList<String> {
        val files = ArrayList<String>()
        val dir = File(destDir)
        if (!dir.exists()) dir.mkdirs()
        FileInputStream(zipFilePath).use { fis ->
            ZipInputStream(fis).use { zis ->
                val buffer = ByteArray(1024)
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(destDir, entry.name).canonicalFile
                    if (!out.path.startsWith(dir.canonicalPath)) {
                        throw IllegalStateException("Zip entry outside target dir: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        files += out.absolutePath
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return files
    }
}
