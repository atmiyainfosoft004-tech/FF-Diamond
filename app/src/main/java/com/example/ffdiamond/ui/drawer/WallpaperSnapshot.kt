package com.example.ffdiamond.ui.drawer

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import androidx.core.graphics.createBitmap
import kotlin.math.max

/**
 * The user's wallpaper as a bitmap, so the drawer can blur *it* rather than the home icons.
 *
 * `windowShowWallpaper` draws the wallpaper in a layer behind this window. PixelCopy of the
 * window therefore captures icons on a transparent field and leaves the wallpaper untouched.
 * The wallpaper service's own screenshot, or a display screenshot taken while our views are
 * hidden, is the path that actually contains the picture.
 */
object WallpaperSnapshot {

    private const val TAG = "AtmiyaBlur"

    @SuppressLint("MissingPermission", "PrivateApi")
    fun capture(view: View, displayScreenshot: Boolean = false): Bitmap? {
        HiddenApis.exempt()
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "capture skipped, view ${width}x$height")
            return null
        }
        val manager = WallpaperManager.getInstance(view.context)
        drawableOf(manager)?.let {
            Log.i(TAG, "captured via drawable")
            return drawCovering(it, width, height)
        }
        bitmapOf(manager)?.let {
            Log.i(TAG, "captured via getBitmap")
            return scaleCovering(it, width, height)
        }
        fileOf(manager)?.let {
            Log.i(TAG, "captured via wallpaper file")
            return scaleCovering(it, width, height)
        }
        screenshotService("wallpaper", "android.app.IWallpaperManager\$Stub")?.let {
            Log.i(TAG, "captured via IWallpaperManager.screenshotWallpaper")
            return scaleCovering(it, width, height)
        }
        screenshotService("window", "android.view.IWindowManager\$Stub")?.let {
            Log.i(TAG, "captured via IWindowManager.screenshotWallpaper")
            return scaleCovering(it, width, height)
        }
        if (displayScreenshot) {
            surfaceScreenshot(width, height)?.let {
                Log.i(TAG, "captured via SurfaceControl.screenshot")
                return scaleCovering(it, width, height)
            }
        }
        Log.w(TAG, "all wallpaper capture paths returned null")
        return null
    }

    @SuppressLint("MissingPermission")
    private fun drawableOf(manager: WallpaperManager): Drawable? =
        runCatching { manager.peekFastDrawable() }.getOrNull()
            ?: runCatching { manager.fastDrawable }.getOrNull()
            ?: runCatching { manager.drawable }.getOrNull()

    @SuppressLint("PrivateApi")
    private fun bitmapOf(manager: WallpaperManager): Bitmap? {
        val methods = arrayOf("getBitmap", "peekBitmapCached")
        for (name in methods) {
            val bitmap = runCatching {
                manager.javaClass.getDeclaredMethod(name).apply { isAccessible = true }
                    .invoke(manager) as? Bitmap
            }.getOrNull()
            if (bitmap != null && !bitmap.isRecycled) return bitmap
        }
        return (drawableOf(manager) as? BitmapDrawable)?.bitmap?.takeUnless { it.isRecycled }
    }

    @SuppressLint("MissingPermission")
    private fun fileOf(manager: WallpaperManager): Bitmap? {
        val fd = runCatching {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return null
        return fd.use { decode(it) }
    }

    private fun decode(fd: ParcelFileDescriptor): Bitmap? =
        BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)

    @SuppressLint("PrivateApi")
    private fun screenshotService(service: String, stubClass: String): Bitmap? = runCatching {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, service) as? IBinder ?: return@runCatching null
        val stub = Class.forName(stubClass)
        val manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: return@runCatching null
        val screenshot = manager.javaClass.methods.firstOrNull { method ->
            method.name == "screenshotWallpaper" && method.parameterTypes.isEmpty()
        } ?: return@runCatching null
        screenshot.invoke(manager) as? Bitmap
    }.onFailure { Log.w(TAG, "screenshotService($service) failed", it) }
        .getOrNull()
        ?.takeUnless { it.isRecycled }

    @SuppressLint("PrivateApi")
    private fun surfaceScreenshot(width: Int, height: Int): Bitmap? {
        val crop = Rect(0, 0, width, height)
        val clazz = runCatching { Class.forName("android.view.SurfaceControl") }.getOrNull()
            ?: return null
        val attempts = listOf(
            {
                val m = clazz.getDeclaredMethod(
                    "screenshot",
                    Rect::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                m.isAccessible = true
                m.invoke(null, crop, width, height, 0) as? Bitmap
            },
            {
                val m = clazz.getDeclaredMethod(
                    "screenshot",
                    Rect::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                m.isAccessible = true
                m.invoke(null, crop, width, height, false, 0) as? Bitmap
            }
        )
        for (attempt in attempts) {
            val bitmap = runCatching { attempt() }
                .onFailure { Log.w(TAG, "SurfaceControl.screenshot variant failed", it) }
                .getOrNull()
            if (bitmap != null && !bitmap.isRecycled) return bitmap
        }
        return null
    }

    private fun drawCovering(drawable: Drawable, width: Int, height: Int): Bitmap {
        (drawable as? BitmapDrawable)?.bitmap?.takeUnless { it.isRecycled }?.let { bitmap ->
            return scaleCovering(bitmap, width, height)
        }
        val bitmap = createBitmap(width, height)
        val dw = drawable.intrinsicWidth.coerceAtLeast(1)
        val dh = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = max(width / dw.toFloat(), height / dh.toFloat())
        val scaledW = (dw * scale).toInt().coerceAtLeast(1)
        val scaledH = (dh * scale).toInt().coerceAtLeast(1)
        val left = (width - scaledW) / 2
        val top = (height - scaledH) / 2
        drawable.setBounds(left, top, left + scaledW, top + scaledH)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun scaleCovering(source: Bitmap, width: Int, height: Int): Bitmap {
        val src = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        } else {
            source
        }
        if (src.width == width && src.height == height) return src
        val out = createBitmap(width, height)
        val scale = max(width / src.width.toFloat(), height / src.height.toFloat())
        val scaledW = src.width * scale
        val scaledH = src.height * scale
        val left = (width - scaledW) / 2f
        val top = (height - scaledH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(out).drawBitmap(
            src,
            null,
            RectF(left, top, left + scaledW, top + scaledH),
            paint
        )
        return out
    }
}
