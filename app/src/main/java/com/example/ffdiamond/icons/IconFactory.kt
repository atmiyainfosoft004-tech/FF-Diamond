package com.example.ffdiamond.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.roundToInt

/**
 * Turns whatever drawable an app happens to ship into a bitmap with the launcher's silhouette.
 *
 * Every icon comes out the same size and the same shape, which is the whole point: a grid where
 * some icons are circles, some are squares and some are rounded rects reads as untidy no matter
 * how good the individual icons are.
 *
 * Instances are cheap but not thread safe, so [render] is synchronized. Icon generation is a
 * couple of milliseconds, and serialising it also keeps a burst of parallel requests from
 * allocating a dozen full-size bitmaps at once.
 */
class IconFactory(val sizePx: Int) {

    private val squircle: Path = SquircleShape.pathFor(sizePx.toFloat())
    private val bounds = Rect()

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LEGACY_PLATE_COLOR }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PLACEHOLDER_COLOR
    }

    /**
     * @param themed draws the monochrome layer on a flat plate instead of the app's own artwork.
     * Falls back to the normal icon when the app ships no monochrome layer.
     */
    @Synchronized
    fun render(drawable: Drawable, themed: Boolean = false): Bitmap {
        val bitmap = createBitmap()
        val canvas = Canvas(bitmap)
        canvas.clipPath(squircle)

        val monochrome = if (themed) monochromeOf(drawable) else null
        when {
            monochrome != null -> drawThemed(canvas, monochrome)
            drawable is AdaptiveIconDrawable -> drawAdaptive(canvas, drawable)
            else -> drawLegacy(canvas, drawable)
        }
        return bitmap
    }

    /** Shown in the grid until the real icon has been generated. */
    @Synchronized
    fun renderPlaceholder(): Bitmap {
        val bitmap = createBitmap()
        Canvas(bitmap).drawPath(squircle, placeholderPaint)
        return bitmap
    }

    /**
     * Adaptive icons are authored on a 108 dp canvas of which only the middle 72 dp is ever
     * visible, so the layers are drawn 1.5x oversized and centred — the overhang is what the mask
     * eats, and what parallax would use if we ever animate the layers.
     *
     * The two layers are drawn by hand rather than calling `draw()` on the AdaptiveIconDrawable,
     * because that would apply the device's own mask first and we would then be clipping an
     * already-clipped icon.
     */
    private fun drawAdaptive(canvas: Canvas, drawable: AdaptiveIconDrawable) {
        val inset = (sizePx * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
        bounds.set(-inset, -inset, sizePx + inset, sizePx + inset)

        val background = drawable.background
        if (background == null) {
            // A missing background layer would leave the foreground floating on nothing.
            canvas.drawPath(squircle, platePaint)
        } else {
            background.bounds = bounds
            background.draw(canvas)
        }
        drawable.foreground?.let {
            it.bounds = bounds
            it.draw(canvas)
        }
    }

    /**
     * Pre-adaptive icons have no separate layers and no safe zone, so they get centred on a
     * neutral plate at 92% and clipped to the same silhouette. Without the plate, clipping a
     * legacy icon would slice the corners straight off its artwork.
     */
    private fun drawLegacy(canvas: Canvas, drawable: Drawable) {
        canvas.drawPath(squircle, platePaint)

        val content = (sizePx * LEGACY_CONTENT_SCALE).roundToInt()
        val offset = (sizePx - content) / 2
        bounds.set(offset, offset, offset + content, offset + content)
        drawable.bounds = bounds
        drawable.draw(canvas)
    }

    private fun drawThemed(canvas: Canvas, monochrome: Drawable) {
        canvas.drawColor(THEMED_BACKGROUND_COLOR)
        val inset = (sizePx * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
        bounds.set(-inset, -inset, sizePx + inset, sizePx + inset)
        monochrome.bounds = bounds
        monochrome.setTint(THEMED_FOREGROUND_COLOR)
        monochrome.draw(canvas)
    }

    /** Mutated so tinting the themed variant never touches the app's shared drawable instance. */
    private fun monochromeOf(drawable: Drawable): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (drawable !is AdaptiveIconDrawable) return null
        return drawable.monochrome?.mutate()
    }

    private fun createBitmap(): Bitmap =
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    private companion object {
        const val LEGACY_CONTENT_SCALE = 0.92f
        const val LEGACY_PLATE_COLOR = Color.WHITE
        const val PLACEHOLDER_COLOR = 0x24FFFFFF
        const val THEMED_BACKGROUND_COLOR = 0xFF1F2430.toInt()
        const val THEMED_FOREGROUND_COLOR = 0xFFDDE3F0.toInt()
    }
}
