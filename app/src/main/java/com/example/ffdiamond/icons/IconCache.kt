package com.example.ffdiamond.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.example.ffdiamond.data.db.IconCacheDao
import com.example.ffdiamond.data.db.IconCacheEntity
import com.example.ffdiamond.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Two levels of cache in front of an expensive rasterisation step.
 *
 * Memory serves the scroll, disk serves the cold start, and only a genuine miss — a new app, an
 * updated app, or a first ever run — pays for decoding the app's drawable and masking it. All of
 * that happens off the main thread; callers get a placeholder to show in the meantime.
 */
class IconCache(
    context: Context,
    private val dao: IconCacheDao,
    private val factory: IconFactory,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val densityDpi = appContext.resources.displayMetrics.densityDpi

    private val memory = object : LruCache<String, Bitmap>(memoryBudgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    /** Requests already running, so twenty views scrolling past the same app rasterise it once. */
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap>>()

    private val placeholder: Bitmap by lazy { factory.renderPlaceholder() }

    /**
     * The neutral squircle shown while a real icon renders. Every cell shares this one bitmap,
     * which is safe because cells draw it into their own destination rect rather than wrapping it
     * in a Drawable and mutating its bounds.
     */
    fun placeholderBitmap(): Bitmap = placeholder

    /** Non-blocking lookup for bind time: a hit can be shown immediately with no fade. */
    fun peek(app: AppInfo, themed: Boolean = false): Bitmap? = memory[key(app, themed)]

    /**
     * Runs on the shared application scope rather than the caller's, so a view that scrolls away
     * mid-render cancels its own await without throwing away work the next view will need.
     */
    suspend fun get(app: AppInfo, themed: Boolean = false): Bitmap {
        val key = key(app, themed)
        memory[key]?.let { return it }

        val request = inFlight.computeIfAbsent(key) { pending ->
            scope.async(Dispatchers.Default) {
                try {
                    load(app, themed).also { memory.put(pending, it) }
                } finally {
                    inFlight.remove(pending)
                }
            }
        }
        return request.await()
    }

    /** Called when a package is installed, updated or removed so its icons are re-read. */
    suspend fun invalidatePackage(packageName: String, userSerial: Long) {
        val prefix = "$packageName/"
        synchronized(memory) {
            memory.snapshot().keys
                .filter { it.startsWith(prefix) && it.contains("|$userSerial|") }
                .forEach { memory.remove(it) }
        }
        withContext(Dispatchers.IO) { dao.deletePackage("$prefix%", userSerial) }
    }

    private suspend fun load(app: AppInfo, themed: Boolean): Bitmap {
        // Themed icons are derived, never stored, so they skip the disk round trip entirely.
        if (!themed) {
            readFromDisk(app)?.let { return it }
        }

        val drawable = loadDrawable(app) ?: appContext.packageManager.defaultActivityIcon
        val bitmap = applyUserBadge(factory.render(drawable, themed), app.user)

        if (!themed) {
            writeToDisk(app, bitmap)
        }
        return bitmap
    }

    /**
     * Work and clone profile apps get the system's badge stamped on afterwards rather than asking
     * LauncherApps for a pre-badged drawable. A badged drawable is a wrapper, not an
     * AdaptiveIconDrawable, so masking it would send every work app down the legacy path and give
     * it a different silhouette from its personal-profile twin.
     */
    private fun applyUserBadge(icon: Bitmap, user: UserHandle): Bitmap {
        if (user == Process.myUserHandle()) return icon

        val badged = appContext.packageManager.getUserBadgedIcon(
            icon.toDrawable(appContext.resources),
            user
        )
        val output = createBitmap(icon.width, icon.height)
        badged.setBounds(0, 0, icon.width, icon.height)
        badged.draw(Canvas(output))
        return output
    }

    private suspend fun readFromDisk(app: AppInfo): Bitmap? = withContext(Dispatchers.IO) {
        val row = runCatching {
            dao.find(app.component.flattenToShortString(), app.userSerial)
        }.getOrNull() ?: return@withContext null

        if (row.versionCode != app.versionCode) return@withContext null

        val bitmap = BitmapFactory.decodeByteArray(row.bitmap, 0, row.bitmap.size)
        // A stored icon from a different display density is the wrong size; re-render instead of
        // letting the grid upscale it.
        if (bitmap == null || bitmap.width != factory.sizePx) null else bitmap
    }

    private suspend fun writeToDisk(app: AppInfo, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            dao.put(
                IconCacheEntity(
                    component = app.component.flattenToShortString(),
                    userSerial = app.userSerial,
                    versionCode = app.versionCode,
                    bitmap = compress(bitmap),
                    label = app.label
                )
            )
        }
    }

    /**
     * Resolving through LauncherApps rather than PackageManager because it is the only route that
     * takes a UserHandle — PackageManager would silently answer for the personal profile.
     */
    private fun loadDrawable(app: AppInfo): Drawable? = runCatching {
        launcherApps
            ?.getActivityList(app.packageName, app.user)
            ?.firstOrNull { it.componentName == app.component }
            ?.getIcon(densityDpi)
    }.getOrNull()

    private fun compress(bitmap: Bitmap): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.PNG
        }
        return ByteArrayOutputStream(bitmap.allocationByteCount / 4).use { stream ->
            bitmap.compress(format, 100, stream)
            stream.toByteArray()
        }
    }

    private fun key(app: AppInfo, themed: Boolean) = "${app.key}|${if (themed) "t" else "n"}"

    private companion object {
        /** An eighth of the heap: enough for several screens of icons, small enough to be polite. */
        fun memoryBudgetKb(): Int =
            (Runtime.getRuntime().maxMemory() / 1024 / 8).coerceIn(4 * 1024, 32 * 1024).toInt()
    }
}
