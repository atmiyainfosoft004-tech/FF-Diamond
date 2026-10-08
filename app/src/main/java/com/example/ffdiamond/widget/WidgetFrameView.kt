package com.example.ffdiamond.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Hosts an [android.appwidget.AppWidgetHostView] in a grid cell.
 *
 * Widgets eat their own touches, so a long-press to resize has to be detected here and the child
 * cancelled before it treats the lift as a tap.
 */
class WidgetFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onEdit: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressPosted = false
    private var stole = false

    private val longPress = Runnable {
        longPressPosted = false
        stole = true
        parent?.requestDisallowInterceptTouchEvent(true)
        val cancel = MotionEvent.obtain(
            0L, 0L, MotionEvent.ACTION_CANCEL, downX, downY, 0
        )
        for (i in 0 until childCount) {
            getChildAt(i).dispatchTouchEvent(cancel)
        }
        cancel.recycle()
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onEdit?.invoke()
    }

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stole = false
                downX = ev.x
                downY = ev.y
                postLongPress()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!stole && hypot(ev.x - downX, ev.y - downY) > touchSlop) {
                    cancelLongPressTimer()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressTimer()
                if (stole) {
                    stole = false
                    return true
                }
            }
        }
        return stole
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (stole) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                stole = false
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            cancelLongPressTimer()
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun postLongPress() {
        cancelLongPressTimer()
        longPressPosted = true
        handler.postDelayed(longPress, longPressMs)
    }

    private fun cancelLongPressTimer() {
        if (!longPressPosted) return
        handler.removeCallbacks(longPress)
        longPressPosted = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelLongPressTimer()
    }

    private val longPressMs: Long
        get() = ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(500L)
}
