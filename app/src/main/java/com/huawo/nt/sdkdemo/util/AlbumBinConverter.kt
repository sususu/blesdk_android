package com.huawo.nt.sdkdemo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.sifli.ezip.sifliEzipUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Album source image → watch ezip bins.
 *
 * Each photo produces a pair of files (names match firmware convention):
 * - `PNG_{id}.bin`: main image at user watch size (default 466×466)
 * - `PRE_{id}.bin`: preview thumbnail (~1/3 of main)
 *
 * Pipeline: sample-decode → centerCrop → PNG bytes → sifli `pngToEzip(rgb565a)`.
 * SPP sends these bins directly; Sifli zips them (type=3).
 */
object AlbumBinConverter {
    /** Demo default watch screen size in px. UI fields and Repository defaults use this. */
    const val DEFAULT_WIDTH = 466
    const val DEFAULT_HEIGHT = 466

    /**
     * @param index    Watch album slot ID; also used as `{id}` in output file names
     * @param mainBin  Main-image ezip
     * @param thumbBin Preview-image ezip
     */
    data class PhotoBins(
        val index: Int,
        val mainBin: File,
        val thumbBin: File,
    )

    /**
     * Convert one source image into main + preview ezip files.
     *
     * @param width  Target watch width; same as UI "watch size"
     * @param height Target watch height
     */
    fun convert(
        context: Context,
        index: Int,
        sourcePath: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
    ): PhotoBins {
        val outDir = File(context.cacheDir, "album_bin").also { it.mkdirs() }
        val mainBin = File(outDir, "PNG_$index.bin")
        val thumbBin = File(outDir, "PRE_$index.bin")

        // Sample toward target size first so large photos are not fully decoded
        val src = decodeSampled(sourcePath, width, height)
            ?: throw IllegalStateException("decode failed: $sourcePath")
        try {
            // Main: scale to cover, then center-crop to exact width/height
            val main = centerCrop(src, width, height)
            writeEzipBin(bitmapToPngBytes(main), mainBin)
            if (main !== src) main.recycle()

            // Preview: about 1/3 of screen size, same centerCrop
            val tw = maxOf(1, width / 3)
            val th = maxOf(1, height / 3)
            val thumb = centerCrop(src, tw, th)
            writeEzipBin(bitmapToPngBytes(thumb), thumbBin)
            if (thumb !== src) thumb.recycle()
        } finally {
            src.recycle()
        }
        return PhotoBins(index, mainBin, thumbBin)
    }

    /**
     * Flatten bins as main, preview pairs for transfer.
     * SPP / ZIP send in this order; firmware identifies files by name.
     */
    fun flatTransferFiles(bins: List<PhotoBins>): List<File> =
        bins.flatMap { listOf(it.mainBin, it.thumbBin) }

    /** PNG bytes → ezip(rgb565a) on disk; empty/null throws for caller fallback. */
    private fun writeEzipBin(pngBytes: ByteArray, out: File) {
        val ezip = sifliEzipUtil.pngToEzip(pngBytes, "rgb565a", 0, 1, 1)
            ?: throw IllegalStateException("pngToEzip returned null for ${out.name}")
        FileOutputStream(out).use { it.write(ezip) }
        if (!out.exists() || out.length() == 0L) {
            throw IllegalStateException("empty ezip: ${out.name}")
        }
    }

    /** Bounds-only pass to compute inSampleSize, then real decode. */
    private fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val opts =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            }
        return BitmapFactory.decodeFile(path, opts)
    }

    /**
     * Power-of-two sample size so decoded edges stay roughly ≥ 2× target,
     * keeping quality acceptable for the later centerCrop upscale.
     */
    private fun calcInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        while (srcH / inSampleSize > reqH * 2 && srcW / inSampleSize > reqW * 2) {
            inSampleSize *= 2
        }
        return maxOf(1, inSampleSize)
    }

    /**
     * Scale to cover the destination, then center-crop to [dstW]×[dstH].
     * Matches watch display behavior (fill then crop edges).
     */
    private fun centerCrop(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val srcW = src.width
        val srcH = src.height
        val scale = maxOf(dstW / srcW.toFloat(), dstH / srcH.toFloat())
        val matrix = Matrix().apply { postScale(scale, scale) }
        val scaled = Bitmap.createBitmap(src, 0, 0, srcW, srcH, matrix, true)
        val x = maxOf(0, (scaled.width - dstW) / 2)
        val y = maxOf(0, (scaled.height - dstH) / 2)
        val out =
            Bitmap.createBitmap(
                scaled,
                x,
                y,
                minOf(dstW, scaled.width - x),
                minOf(dstH, scaled.height - y),
            )
        if (out !== scaled) scaled.recycle()
        return out
    }

    /** Quality 20 intermediate PNG before ezip; final look is decided by ezip/firmware. */
    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 20, stream)
        return stream.toByteArray()
    }
}
