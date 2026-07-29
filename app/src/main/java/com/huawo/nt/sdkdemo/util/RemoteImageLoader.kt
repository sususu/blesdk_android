package com.huawo.nt.sdkdemo.util

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Minimal remote image loader for online watchface thumbnails (PNG / animated GIF).
 *
 * ## Why not Glide/Coil
 * This demo avoids extra image-loading dependencies. minSdk is 28, so [ImageDecoder]
 * can decode animated GIF into [AnimatedImageDrawable].
 *
 * ## Behavior
 * 1. Tag the [ImageView] with the URL to ignore stale async results after rebind.
 * 2. Hit in-memory path map → decode from disk cache file.
 * 3. Else download to `cacheDir/img_cache/` on a background pool, then post to main.
 *
 * ## Caveats
 * - Callers must pass an **absolute** URL ([WatchfaceApi.resolveFileUrl]).
 * - Failures are silent unless [placeholderRes] is set.
 * - Not a general-purpose loader (no size transform / memory LRU for bitmaps).
 */
object RemoteImageLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    /** URL → local cache file (survives process only via disk file; map is in-memory). */
    private val cache = ConcurrentHashMap<String, File>()

    /**
     * Load [url] into [imageView]. Empty URL clears or applies [placeholderRes].
     *
     * @param placeholderRes optional drawable while loading / on failure (0 = none)
     */
    fun load(imageView: ImageView, url: String?, placeholderRes: Int = 0) {
        val tagKey = url.orEmpty()
        imageView.tag = tagKey
        if (url.isNullOrBlank()) {
            if (placeholderRes != 0) imageView.setImageResource(placeholderRes) else imageView.setImageDrawable(null)
            return
        }
        if (placeholderRes != 0) imageView.setImageResource(placeholderRes)

        val cached = cache[url]
        if (cached != null && cached.exists()) {
            applyDrawable(imageView, tagKey, decodeDrawable(cached))
            return
        }

        executor.execute {
            try {
                val file = downloadTemp(imageView.context.cacheDir, url)
                cache[url] = file
                val drawable = decodeDrawable(file)
                mainHandler.post {
                    applyDrawable(imageView, tagKey, drawable)
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (imageView.tag == tagKey && placeholderRes != 0) {
                        imageView.setImageResource(placeholderRes)
                    }
                }
            }
        }
    }

    /** Apply only if the ImageView still expects [tagKey] (RecyclerView rebind safety). */
    private fun applyDrawable(imageView: ImageView, tagKey: String, drawable: Drawable?) {
        if (imageView.tag != tagKey || drawable == null) return
        imageView.setImageDrawable(drawable)
        if (drawable is AnimatedImageDrawable) {
            drawable.start()
        }
    }

    private fun decodeDrawable(file: File): Drawable {
        val source = ImageDecoder.createSource(file)
        return ImageDecoder.decodeDrawable(source)
    }

    /**
     * Download once per URL into a stable cache file name derived from hash + basename.
     * Reuses existing non-empty files to avoid re-fetching GIF previews while scrolling.
     */
    private fun downloadTemp(cacheDir: File, url: String): File {
        val dir = File(cacheDir, "img_cache").also { it.mkdirs() }
        val name = Integer.toHexString(url.hashCode()) + "_" + url.substringAfterLast('/').take(40)
        val out = File(dir, name)
        if (out.exists() && out.length() > 0L) return out
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 30_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            return out
        } finally {
            conn.disconnect()
        }
    }
}
