package com.example.ffdiamond.ui.drawer

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.ffdiamond.util.Motion
import kotlin.math.abs

/**
 * Owns the drawer progress value `0..1` and everything that interpolates off it.
 *
 * Dragging writes the value directly. Releasing hands it to a spring aimed at 0 or 1. Grabbing
 * again cancels the spring and takes the same value over — that is what makes reversing mid-flight
 * never stutter.
 */
class DrawerController(
    private val overlay: DrawerOverlay,
    private val homeRoot: View,
    private val workspace: View,
    private val dock: View,
    private val indicator: View,
    private val blurView: ImageView,
    private val window: Window
) {

    private val density = overlay.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(overlay.context).scaledTouchSlop
    private val holder = FloatValueHolder(0f)
    private val spring = SpringAnimation(holder).apply {
        spring = SpringForce().apply {
            stiffness = Motion.SPRING_STIFFNESS
            dampingRatio = Motion.SPRING_DAMPING
        }
        addUpdateListener { _, value, _ -> applyProgress(value) }
        addEndListener { _, _, value, _ -> applyProgress(value.coerceIn(0f, 1f)) }
    }

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastDragY = 0f
    private var startProgress = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var interceptCandidate = false
    private var blurPrepared = false
    private var blurSnapshot: Bitmap? = null
    private var blurBehindEnabled = false
    private var hideCapturePosted = false

    var progress: Float = 0f
        private set

    /** Finger currently driving progress, as opposed to the spring coasting. */
    var isDragging: Boolean = false
        private set

    val isOpen: Boolean get() = progress > OPEN_EPS
    val isFullyOpen: Boolean get() = progress > 0.999f
    val isFullyClosed: Boolean get() = progress < CLOSED_EPS

    fun blocksTransient(): Boolean = overlay.blocksHomeGesture()

    fun applyProgress(value: Float) {
        val p = value.coerceIn(0f, 1f)
        progress = p
        holder.value = p
        overlay.applySheet(p)
        applyHome(p)
        applyBlur(p)
    }

    fun open() = animateTo(1f)

    /** Re-apply workspace/dock fade after something else (home chrome, resume) touched those views. */
    fun syncHomeLayer() = applyHome(progress)

    /** Cache the wallpaper still so the first swipe already has something to blur. */
    fun prepareBlur() {
        if (!isFullyClosed) return
        if (blurSnapshot != null) return
        blurView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        WallpaperSnapshot.capture(blurView)?.let {
            blurSnapshot = it
            return
        }
        if (overlay.isAttachedToWindow) WindowBlur.attach(overlay.backdrop)
        scheduleHideCapture()
    }

    /**
     * Last resort: hide our window content for two frames so a display screenshot contains only
     * the wallpaper layer, then restore. Cached so it runs once.
     */
    private fun scheduleHideCapture() {
        if (hideCapturePosted || blurView.width == 0) return
        hideCapturePosted = true
        val views = arrayOf(homeRoot, overlay)
        val alphas = views.map { it.alpha }
        views.forEach { it.alpha = 0f }
        blurView.post {
            blurView.post {
                blurSnapshot = WallpaperSnapshot.capture(blurView, displayScreenshot = true)
                views.forEachIndexed { i, view -> view.alpha = alphas[i] }
            }
        }
    }

    fun close() {
        overlay.dismissTransient()
        animateTo(0f)
    }

    /**
     * True when a vertical swipe should become a drawer drag. Fully open still waits for MOVE so
     * an icon tap is not stolen, but a downward swipe from the grid is intercepted here — the
     * overlay cannot see those moves once the pager has the touch target.
     */
    fun onInterceptTouch(ev: MotionEvent): Boolean {
        if (overlay.blocksHomeGesture()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                interceptCandidate = true
                pointerId = ev.getPointerId(0)
                downX = ev.x
                downY = ev.y
                obtainTracker().addMovement(ev)
                if (spring.isRunning) {
                    beginDrag(ev.y)
                    return true
                }
                if (!isFullyClosed && !isFullyOpen) {
                    beginDrag(ev.y)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!interceptCandidate) return isDragging
                obtainTracker().addMovement(ev)
                val dx = abs(ev.x - downX)
                val dy = ev.y - downY
                if (isFullyClosed) {
                    if (-dy > touchSlop && -dy >= dx) {
                        beginDrag(downY)
                        return true
                    }
                } else if (abs(dy) > touchSlop && abs(dy) >= dx) {
                    beginDrag(downY)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                interceptCandidate = false
                if (isDragging) {
                    finishDrag()
                    return true
                }
                releaseTracker()
            }
        }
        return isDragging
    }

    fun onTouch(ev: MotionEvent): Boolean {
        obtainTracker().addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = ev.getPointerId(0)
                beginDrag(ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerId == MotionEvent.INVALID_POINTER_ID) {
                    pointerId = ev.getPointerId(0)
                }
                if (isDragging) dragTo(ev.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        return true
    }

    /**
     * @param y finger Y used as the drag origin. Pass the ACTION_DOWN Y when intercepting after
     * slop, not the current MOVE Y — otherwise the travelled distance is thrown away and a swipe
     * that started on the app grid never pulls the sheet far enough to close.
     */
    fun beginDrag(y: Float) {
        spring.cancel()
        isDragging = true
        startProgress = progress
        downY = y
        lastDragY = y
        interceptCandidate = false
    }

    private fun dragTo(y: Float) {
        lastDragY = y
        // A swipe from the middle of the grid only has ~half the screen left. Mapping against the
        // full height meant those swipes never crossed the settle point, so the drawer sprang back.
        val range = (overlay.height * DRAG_RANGE).coerceAtLeast(1f)
        applyProgress((startProgress - (y - downY) / range).coerceIn(0f, 1f))
    }

    private fun finishDrag() {
        if (!isDragging) {
            releaseTracker()
            return
        }
        isDragging = false
        interceptCandidate = false
        val tracker = obtainTracker()
        tracker.computeCurrentVelocity(VELOCITY_UNITS)
        val id = if (pointerId == MotionEvent.INVALID_POINTER_ID) 0 else pointerId
        val velocityDp = tracker.getYVelocity(id) / density
        val travelDp = (lastDragY - downY) / density
        releaseTracker()
        pointerId = MotionEvent.INVALID_POINTER_ID
        settle(velocityDp, travelDp)
    }

    fun settle(velocityDpPerSec: Float = 0f, travelDp: Float = 0f) {
        val open = when {
            velocityDpPerSec < -Motion.DRAWER_FLING_DP -> true
            velocityDpPerSec > Motion.DRAWER_FLING_DP -> false
            travelDp > CLOSE_TRAVEL_DP -> false
            travelDp < -CLOSE_TRAVEL_DP -> true
            else -> progress > Motion.DRAWER_SETTLE
        }
        if (!open) overlay.dismissTransient()
        animateTo(if (open) 1f else 0f)
    }

    private fun animateTo(target: Float) {
        spring.cancel()
        val scale = Motion.animatorScale(overlay.context)
        if (scale == 0f || overlay.height == 0) {
            applyProgress(target)
            return
        }
        holder.value = progress
        spring.animateToFinalPosition(target)
    }

    private fun applyHome(p: Float) {
        val scale = 1f - (1f - HOME_SCALE) * p
        workspace.pivotX = workspace.width / 2f
        workspace.pivotY = workspace.height / 2f
        workspace.scaleX = scale
        workspace.scaleY = scale
        workspace.alpha = 1f - p
        dock.translationY = dock.height * p
        dock.alpha = 1f - p
        indicator.alpha = 1f - p
        if (p < CLOSED_EPS) {
            workspace.scaleX = 1f
            workspace.scaleY = 1f
            workspace.alpha = 1f
            dock.translationY = 0f
            dock.alpha = 1f
            dock.visibility = View.VISIBLE
            indicator.alpha = 1f
            indicator.visibility = View.VISIBLE
        } else {
            // Keep the dock out of the nav-bar strip while the drawer owns the screen. Alpha alone
            // is not enough: home chrome can restore alpha=1 after Custom Tabs and flash icons
            // under the navigation bar while translationY is still pushed down.
            dock.visibility = View.INVISIBLE
            indicator.visibility = View.INVISIBLE
        }
    }

    private fun applyBlur(p: Float) {
        val radius = BLUR_DP * density * p
        if (p > CLOSED_EPS) {
            if (!blurPrepared) {
                blurPrepared = true
                ensureWallpaper()
                WindowBlur.attach(overlay.backdrop)
            }
            if (blurSnapshot != null) {
                if (blurView.drawable == null) blurView.setImageBitmap(blurSnapshot)
                blurView.isVisible = true
                blurView.alpha = 1f
            }
            applyWallpaperRenderBlur(radius)
            WindowBlur.setRadius(overlay.backdrop, radius.toInt())
            applyWindowBlur(radius)
        } else {
            blurPrepared = false
            blurView.alpha = 0f
            applyWallpaperRenderBlur(0f)
            WindowBlur.setRadius(overlay.backdrop, 0)
            applyWindowBlur(0f)
            blurView.isVisible = false
        }
    }

    private fun ensureWallpaper() {
        if (blurSnapshot == null) {
            blurSnapshot = WallpaperSnapshot.capture(blurView)
            if (blurSnapshot == null) scheduleHideCapture()
        }
        blurSnapshot?.let { blurView.setImageBitmap(it) }
    }

    /**
     * Blurs the wallpaper ImageView. That view holds the wallpaper bitmap only — never a copy of
     * the home icons — so the grid can fade out on top of a frosted picture.
     */
    private fun applyWallpaperRenderBlur(radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        blurView.setRenderEffect(
            if (radius > 0.4f) {
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
    }

    /**
     * Blurs the wallpaper *window* behind this activity. Used when the wallpaper is live and has
     * no bitmap, and as a second pass even when the ImageView path is working.
     */
    private fun applyWindowBlur(radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val px = radius.toInt().coerceAtLeast(0)
        val attrs = window.attributes
        if (px > 0) {
            if (!blurBehindEnabled) {
                blurBehindEnabled = true
                attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }
            attrs.blurBehindRadius = px
            window.attributes = attrs
            runCatching { window.setBackgroundBlurRadius(px) }
        } else if (blurBehindEnabled) {
            blurBehindEnabled = false
            attrs.blurBehindRadius = 0
            attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            window.attributes = attrs
            runCatching { window.setBackgroundBlurRadius(0) }
        }
    }

    private fun obtainTracker(): VelocityTracker =
        velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }

    private fun releaseTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private companion object {
        const val CLOSED_EPS = 0.001f
        const val OPEN_EPS = 0.02f
        const val HOME_SCALE = 0.92f
        /** Spec is 40; the One UI reference frosts the wallpaper until it is unreadable. */
        const val BLUR_DP = 60f
        /** Fraction of overlay height that maps to a full 0..1 drag. */
        const val DRAG_RANGE = 0.45f
        /** A short, clear downward swipe on the app list is enough to close. */
        const val CLOSE_TRAVEL_DP = 48f
        const val VELOCITY_UNITS = 1000
    }
}
