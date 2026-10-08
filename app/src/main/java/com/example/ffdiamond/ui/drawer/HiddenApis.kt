package com.example.ffdiamond.ui.drawer

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log

/**
 * One-shot exemption so [WallpaperSnapshot] can call the wallpaper-screenshot hidden APIs.
 * Without this, Android 9+ throws on `IWallpaperManager.screenshotWallpaper` and
 * `SurfaceControl.screenshot`, which are the only ways a third-party launcher can read the
 * picture behind a `windowShowWallpaper` window.
 */
internal object HiddenApis {

    private const val TAG = "AtmiyaBlur"
    private var attempted = false

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun exempt() {
        if (attempted || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        attempted = true
        runCatching {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            val runtime = getRuntime.invoke(null)
            val setExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setExemptions.invoke(runtime, arrayOf("L"))
            Log.i(TAG, "hidden api exemptions set")
        }.onFailure { Log.w(TAG, "hidden api exemptions failed", it) }
    }
}
