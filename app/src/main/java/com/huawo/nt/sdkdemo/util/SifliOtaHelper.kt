package com.huawo.nt.sdkdemo.util

import android.content.Context
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
 * ## Full-package flow
 * 1. Download `firmwares[0]` zip (MD5 as cache file name) into `cache/device/qjs`.
 * 2. Unzip next to the zip; collect absolute file paths.
 * 3. Map bin file name prefixes → IMAGE_ID:
 *    - `hcpu*.bin`  → [IMAGE_ID_HCPU]
 *    - `lcpu*.bin`  → [IMAGE_ID_LCPU]
 *    - `patch*.bin` → [IMAGE_ID_NAND_LCPU_PATCH]
 *    - `ctrl*.bin`  → [IMAGE_ID_CTRL]
 *    - `outdyn*.bin` / `outroot*.bin` → [IMAGE_ID_DYN] / [IMAGE_ID_RES]
 * 4. Return list for [com.sifli.siflidfu.SifliDFUService.startActionDFUNand].
 *
 * ## Diff-package flow
 * If `diff_ctrl*.bin` is present (checked before plain `ctrl`):
 * - Use that as CTRL image.
 * - Require [OtaUpgradeInfo.resource] (name / url / md5).
 * - Download resource into `cache/device/qjs_diff` and append as [IMAGE_ID_NAND_RES].
 * - Skip outdyn / outroot from the main zip.
 */
object SifliOtaHelper {
    /**
     * @param onProgress overall prepare progress 0..100
     *                   (main zip ≈ 0..85, optional resource ≈ 85..100)
     */
    fun prepareDfuImagePaths(
        context: Context,
        info: OtaUpgradeInfo,
        onProgress: ((Int) -> Unit)? = null,
    ): ArrayList<DFUImagePath> {
        // Sifli production path only uses the first firmware entry.
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

        val qjsDir = zipFile.parentFile ?: File(context.cacheDir, "device/qjs")
        val extracted = unzip(zipFile.absolutePath, qjsDir.absolutePath)

        val list = ArrayList<DFUImagePath>()
        var ctrlPath: DFUImagePath? = null
        var diffCtrlPath: DFUImagePath? = null
        var outdynPath: DFUImagePath? = null
        var outrootPath: DFUImagePath? = null

        for (absolutePath in extracted) {
            val name = File(absolutePath).name
            // Prefer diff_ctrl over ctrl (same IMAGE_ID_CTRL slot).
            if (name.startsWith("diff_ctrl") && name.endsWith(".bin")) {
                diffCtrlPath = DFUImagePath(absolutePath, null, IMAGE_ID_CTRL)
            } else if (name.startsWith("ctrl") && name.endsWith(".bin")) {
                ctrlPath = DFUImagePath(absolutePath, null, IMAGE_ID_CTRL)
            }
            if (name.startsWith("hcpu") && name.endsWith(".bin")) {
                list += DFUImagePath(absolutePath, null, IMAGE_ID_HCPU)
            }
            if (name.startsWith("lcpu") && name.endsWith(".bin")) {
                list += DFUImagePath(absolutePath, null, IMAGE_ID_LCPU)
            }
            if (name.startsWith("patch") && name.endsWith(".bin")) {
                list += DFUImagePath(absolutePath, null, IMAGE_ID_NAND_LCPU_PATCH)
            }
            if (name.startsWith("outdyn") && name.endsWith(".bin")) {
                outdynPath = DFUImagePath(absolutePath, null, IMAGE_ID_DYN)
            }
            if (name.startsWith("outroot") && name.endsWith(".bin")) {
                outrootPath = DFUImagePath(absolutePath, null, IMAGE_ID_RES)
            }
        }

        // Diff OTA: CTRL from diff_ctrl + separate NAND resource package.
        if (diffCtrlPath != null) {
            list += diffCtrlPath
            val resource = info.resource
            if (resource?.url.isNullOrBlank() || resource?.md5.isNullOrBlank() || resource?.name.isNullOrBlank()) {
                throw IllegalStateException("Diff OTA requires resource name/url/md5")
            }
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
            return list
        }

        // Full OTA: CTRL + optional outdyn / outroot.
        if (ctrlPath != null) {
            list += ctrlPath
            outdynPath?.let { list += it }
            outrootPath?.let { list += it }
            onProgress?.invoke(100)
            return list
        }

        throw IllegalStateException("Zip missing ctrl / diff_ctrl")
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
                    val out = File(destDir, entry.name)
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
