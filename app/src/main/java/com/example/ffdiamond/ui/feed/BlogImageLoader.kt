package com.example.ffdiamond.ui.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Small in-memory image cache so the Discover list does not refetch every scroll. */
class BlogImageLoader(private val scope: CoroutineScope) {

    private val jobs = HashMap<ImageView, Job>()
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun bind(view: ImageView, url: String) {
        jobs.remove(view)?.cancel()
        if (url.isBlank()) {
            view.setImageDrawable(null)
            view.setTag(R_ID, null)
            return
        }
        view.setTag(R_ID, url)
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        jobs[view] = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { download(url) } ?: return@launch
            cache.put(url, bitmap)
            if (view.getTag(R_ID) == url) view.setImageBitmap(bitmap)
        }
    }

    fun clear(view: ImageView) {
        jobs.remove(view)?.cancel()
        view.setTag(R_ID, null)
    }

    private fun download(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val R_ID = com.example.ffdiamond.R.id.feed_image_url
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtLeast(2 * 1024 * 1024)
    }
}
