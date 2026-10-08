package com.example.ffdiamond.ui.drawer

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.isVisible
import java.util.WeakHashMap

/**
 * Compositor blur of whatever sits *behind this window* — the wallpaper, on a launcher.
 *
 * `RenderEffect` on a view only blurs that view's own drawing. `PixelCopy` of the window skips
 * the wallpaper layer. The ViewRootImpl background-blur drawable is the same path SystemUI uses
 * for the shade: the surface compositor samples the wallpaper and frosts it into [view].
 *
 * The drawable must be set as the view's background directly — wrapping it hides it from
 * ViewRootImpl, which is what actually registers the blur region with SurfaceFlinger.
 *
 * Devices that disable cross-window blur (common on some Sony builds) make this a no-op; the
 * bitmap path on `drawer_blur` is the fallback.
 */
object WindowBlur {

    private val radiusSetters = WeakHashMap<Drawable, (Int) -> Unit>()

    @SuppressLint("PrivateApi")
    fun attach(view: View) {
        if (view.background != null && radiusSetters.containsKey(view.background)) return
        val root = view.rootView.parent ?: return
        val drawable = runCatching {
            root.javaClass.getMethod("createBackgroundBlurDrawable").invoke(root) as Drawable
        }.getOrNull() ?: return
        val method = runCatching {
            drawable.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
        }.getOrNull()
        runCatching {
            drawable.javaClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
                .invoke(drawable, 0f)
        }
        radiusSetters[drawable] = { radius ->
            runCatching { method?.invoke(drawable, radius) }
        }
        view.background = drawable
        view.isVisible = false
    }

    fun setRadius(view: View, radiusPx: Int) {
        if (view.background == null || !radiusSetters.containsKey(view.background)) {
            attach(view)
        }
        val radius = radiusPx.coerceAtLeast(0)
        view.background?.let { radiusSetters[it]?.invoke(radius) }
        view.isVisible = radius > 0 && view.background != null
    }
}
