package com.example.ffdiamond.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

object MediaThumbs {

    fun loadImage(url: String, referer: String? = null): Bitmap? = runCatching {
        val bytes = download(url, referer) ?: return@runCatching null
        decodeSampled(bytes, 720)
    }.getOrNull()

    fun frameFromUrl(url: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(
                url,
                hashMapOf("User-Agent" to USER_AGENT, "Accept" to "*/*")
            )
            scale(retriever.frameAt(1_000_000L) ?: retriever.frameAt(0L), 720)
        }.getOrNull().also { runCatching { retriever.release() } }
    }

    fun frameFromUri(context: Context, uri: Uri): Bitmap? {
        if (uri == Uri.EMPTY || uri.toString().isBlank()) return null
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            scale(retriever.frameAt(1_000_000L) ?: retriever.frameAt(0L), 480)
        }.getOrNull().also { runCatching { retriever.release() } }
    }

    private fun MediaMetadataRetriever.frameAt(timeUs: Long): Bitmap? =
        runCatching { getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()

    private fun download(url: String, referer: String?): ByteArray? {
        var current = url
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*")
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: return null
                current = resolve(current, loc) ?: return null
                conn.disconnect()
            } else {
                return try {
                    conn.inputStream.use { it.readBytes().takeIf { bytes -> bytes.size in 1..(8 * 1024 * 1024) } }
                } finally {
                    conn.disconnect()
                }
            }
        }
        return null
    }

    private fun resolve(base: String, rel: String): String? {
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        if (rel.startsWith("//")) return "https:$rel"
        return runCatching { URL(URL(base), rel).toString() }.getOrNull()
    }

    private fun decodeSampled(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth.takeIf { it > 0 } ?: return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val options = BitmapFactory.Options().apply {
            inSampleSize = (w / maxWidth).coerceAtLeast(1)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun scale(bitmap: Bitmap?, maxWidth: Int): Bitmap? {
        val src = bitmap ?: return null
        if (src.width <= maxWidth) return src
        val h = (src.height.toFloat() * maxWidth / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxWidth, h, true)
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
