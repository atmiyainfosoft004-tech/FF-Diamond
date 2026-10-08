package com.example.ffdiamond.ui.workspace

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.util.Motion
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything a home-screen cell does apart from drawing its own icon: place a squircle-sized box in
 * the cell, put an ellipsized label under it on the right baseline, and scale on press.
 *
 * A single [View] draws both pieces rather than an ImageView stacked on a TextView, because the
 * tokens pin the label by its *baseline* — 14 dp below the bottom edge of the icon — and no
 * combination of gravity and padding on a TextView expresses that. Collapsing a cell into one view
 * also removes two thirds of the views on a home screen, which matters when a page holds thirty of
 * them and the workspace has to stay at 120 fps while scrolling.
 *
 * Apps and folders differ only in what fills [iconBounds], so that is the one thing subclasses
 * supply.
 */
abstract class IconCellView(context: Context) : View(context) {

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG)
    private val mergePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var pressAnimation: ValueAnimator? = null
    private var mergeProgress = 0f

    private var label: CharSequence = ""
    private var visibleLabel: CharSequence = ""
    private var labelX = 0f
    private var labelBaseline = 0f
    private var labelBaselineGapPx = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressPosted = false
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressPosted = false
        if (isPressed) {
            longPressFired = true
            performLongClick()
        }
    }

    protected val iconBounds = Rect()
    protected var iconSizePx = 0
        private set

    var showLabel: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            layoutContents()
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        isLongClickable = true
        textPaint.color = Color.WHITE
        textPaint.setShadowLayer(SHADOW_RADIUS_PX, 0f, SHADOW_DY_PX, SHADOW_COLOR)
    }

    open fun applyProfile(profile: DeviceProfile) {
        iconSizePx = profile.iconSizePx
        labelBaselineGapPx = profile.labelBaselineGapPx
        textPaint.textSize = profile.labelTextSizePx
        layoutContents()
        invalidate()
    }

    fun setLabel(text: CharSequence) {
        if (label == text) return
        label = text
        contentDescription = text
        layoutContents()
        invalidate()
    }

    /** The icon's rectangle in this view's own coordinates; drag and folder open both need it. */
    fun iconRect(out: Rect) = out.set(iconBounds)

    /**
     * The "release here and these two will merge" affordance, driven 0..1 by the drag controller.
     *
     * It is on the cell being hovered rather than on the dragged icon because the question it
     * answers is about the target: this is the thing that is going to swallow what you are holding.
     * A circle growing out from behind it reads as the folder opening up to accept the drop, and
     * shrinking the icon inside that circle sells the same idea from the other direction.
     */
    fun setMergeProgress(progress: Float) {
        // Not clamped at the top, so the overshoot the motion spec asks for actually shows.
        val clamped = progress.coerceAtLeast(0f)
        if (mergeProgress == clamped) return
        mergeProgress = clamped
        invalidate()
    }

    /** Rasterises just the icon, with no label, for the drag view to carry. */
    fun drawIconInto(canvas: Canvas) = drawIcon(canvas)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutContents()
    }

    /**
     * Places the icon and label inside whatever cell rectangle the parent handed us.
     *
     * The icon wants to sit at the vertical centre of the cell — which is exactly where the
     * reference frame puts it, since its row centres are 94 dp apart and its rows are 94 dp tall.
     * On a device whose rows come out taller than the reference, though, centring the icon would
     * push the label past the bottom edge and into the next row, because the icon grows with the
     * screen width while the row height is whatever vertical space happens to be left over. So the
     * icon centre is also capped at the highest point that still leaves room for the label.
     */
    private fun layoutContents() {
        val w = width
        val h = height
        if (w == 0 || h == 0 || iconSizePx == 0) return

        val labelBlock = if (showLabel) labelBaselineGapPx + textPaint.fontMetrics.descent else 0f
        val centerY = min(h / 2f, h - labelBlock - iconSizePx / 2f).coerceAtLeast(iconSizePx / 2f)

        val left = (w - iconSizePx) / 2
        val top = (centerY - iconSizePx / 2f).roundToInt()
        iconBounds.set(left, top, left + iconSizePx, top + iconSizePx)

        if (!showLabel) {
            visibleLabel = ""
            return
        }

        val available = (w - 2 * LABEL_SIDE_PADDING_PX).coerceAtLeast(0)
        visibleLabel = TextUtils.ellipsize(
            label,
            textPaint,
            available.toFloat(),
            TextUtils.TruncateAt.END
        )
        labelX = (w - textPaint.measureText(visibleLabel, 0, visibleLabel.length)) / 2f
        labelBaseline = iconBounds.bottom + labelBaselineGapPx.toFloat()
    }

    final override fun onDraw(canvas: Canvas) {
        val centerX = iconBounds.exactCenterX()
        val centerY = iconBounds.exactCenterY()

        if (mergeProgress > 0f) {
            mergePaint.alpha = (MERGE_CIRCLE_ALPHA * mergeProgress).toInt()
            val radius = iconSizePx * (MERGE_CIRCLE_START + MERGE_CIRCLE_GROWTH * mergeProgress)
            canvas.drawCircle(centerX, centerY, radius, mergePaint)
        }

        val iconScale = 1f - (1f - MERGE_ICON_SCALE) * mergeProgress
        if (iconScale < 1f) {
            val checkpoint = canvas.save()
            canvas.scale(iconScale, iconScale, centerX, centerY)
            drawIcon(canvas)
            canvas.restoreToCount(checkpoint)
        } else {
            drawIcon(canvas)
        }

        if (showLabel && visibleLabel.isNotEmpty()) {
            canvas.drawText(visibleLabel, 0, visibleLabel.length, labelX, labelBaseline, textPaint)
        }
    }

    /** Fills [iconBounds]. Called before the label, so the label always wins an overlap. */
    protected abstract fun drawIcon(canvas: Canvas)

    /**
     * The spec asks for a 300 ms long-press, which is shorter than the system default. Handling
     * the timeout here rather than waiting for [View]'s own CheckForLongPress is what makes that
     * number actually apply; a page swipe still wins because this never calls
     * `requestDisallowInterceptTouchEvent`, so the workspace can steal the gesture once it slops.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                moved = false
                downX = event.x
                downY = event.y
                isPressed = true
                postDelayed(longPressRunnable, LONG_PRESS_MS)
                longPressPosted = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    moved = true
                    cancelPendingLongPress()
                    isPressed = false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasLongPress = longPressFired
                val wasTap = !moved
                cancelPendingLongPress()
                isPressed = false
                // A swipe that never got stolen by the pager or the drawer still must not launch:
                // ACTION_UP without a CANCEL is what a short, sloppy flick looks like to this view.
                if (!wasLongPress && wasTap && isClickable) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                isPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cancelPendingLongPress() {
        if (!longPressPosted) return
        removeCallbacks(longPressRunnable)
        longPressPosted = false
    }

    /** The press feedback from the motion spec: scale to 0.92 and back, never a ripple. */
    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val target = if (pressed) PRESSED_SCALE else 1f
        if (scaleX == target) return

        pressAnimation?.cancel()
        val pressDuration = Motion.duration(context, Motion.DURATION_ICON_FADE)
        if (pressDuration == 0L) {
            scaleX = target
            scaleY = target
            return
        }
        pressAnimation = ValueAnimator.ofFloat(scaleX, target).apply {
            duration = pressDuration
            interpolator = Motion.SMALL
            addUpdateListener {
                val value = it.animatedValue as Float
                scaleX = value
                scaleY = value
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingLongPress()
        pressAnimation?.cancel()
    }

    private companion object {
        /** Specced, not the system default — the lift has to feel immediate. */
        const val LONG_PRESS_MS = 300L

        const val PRESSED_SCALE = 0.92f
        const val MERGE_ICON_SCALE = 0.9f
        const val MERGE_CIRCLE_ALPHA = 90f

        /** Starts just inside the icon and ends wider than it, so the icon is visibly swallowed. */
        const val MERGE_CIRCLE_START = 0.45f
        const val MERGE_CIRCLE_GROWTH = 0.3f

        const val LABEL_SIDE_PADDING_PX = 4
        const val SHADOW_RADIUS_PX = 3f
        const val SHADOW_DY_PX = 1f
        val SHADOW_COLOR = Color.argb(120, 0, 0, 0)
    }
}
