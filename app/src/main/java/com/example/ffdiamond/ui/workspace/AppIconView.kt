package com.example.ffdiamond.ui.workspace

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.util.Motion
import kotlinx.coroutines.Job

/** One app: a squircle icon with its label underneath. See [IconCellView] for the shared parts. */
class AppIconView(context: Context) : IconCellView(context) {

    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var placeholder: Bitmap? = null
    private var icon: Bitmap? = null

    /** Drives the cross-fade from placeholder to real icon, in 0..255. */
    private var iconAlpha = 255
    private var fade: ValueAnimator? = null

    var app: AppInfo? = null
        private set

    /** The in-flight icon load for the app currently bound, cancelled when the view is rebound. */
    var iconJob: Job? = null

    /**
     * Binds an app to this cell. Any icon load still running for the previous app is dropped —
     * recycled cells are the common case, and a late arrival would paint the wrong icon.
     *
     * [title] is passed separately rather than read off [app] because the user can rename an icon,
     * and that override lives with the layout row, not with the installed app.
     */
    fun bind(app: AppInfo, placeholder: Bitmap, title: String = app.label) {
        iconJob?.cancel()
        iconJob = null
        fade?.cancel()

        this.app = app
        this.placeholder = placeholder
        this.icon = null
        this.iconAlpha = 0

        setLabel(title)
        invalidate()
    }

    fun setIcon(bitmap: Bitmap, animate: Boolean) {
        icon = bitmap
        fade?.cancel()

        val fadeDuration = Motion.duration(context, Motion.DURATION_ICON_FADE)
        if (!animate || fadeDuration == 0L) {
            iconAlpha = 255
            invalidate()
            return
        }

        iconAlpha = 0
        fade = ValueAnimator.ofInt(0, 255).apply {
            duration = fadeDuration
            interpolator = Motion.SMALL
            addUpdateListener {
                iconAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    /** The icon as drawn, for the drag view to lift off the grid. Null until one has loaded. */
    fun currentIcon(): Bitmap? = icon ?: placeholder

    override fun drawIcon(canvas: Canvas) {
        val current = icon
        if (current == null || iconAlpha < 255) {
            iconPaint.alpha = 255
            placeholder?.let { canvas.drawBitmap(it, null, iconBounds, iconPaint) }
        }
        if (current != null) {
            iconPaint.alpha = iconAlpha
            canvas.drawBitmap(current, null, iconBounds, iconPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fade?.cancel()
        iconJob?.cancel()
        iconJob = null
    }
}
