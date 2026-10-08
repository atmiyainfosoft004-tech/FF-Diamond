package com.example.ffdiamond.ads

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * A [FrameLayout] wrapper for native ads that throttles rapid repeated clicks
 * and touch events to prevent multiple dialogs, activities, or browser tabs from opening
 * when an AdMob native ad is tapped rapidly or repeatedly.
 */
class SafeNativeAdContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var lastTouchDownTime = 0L
    private var isThrottledGesture = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val now = SystemClock.elapsedRealtime()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isThrottled = (now - lastTouchDownTime < THROTTLE_MS) ||
                    (now - AdsBinder.lastNativeClickTime < THROTTLE_MS)
                if (isThrottled) {
                    isThrottledGesture = true
                    return true
                }
                isThrottledGesture = false
                lastTouchDownTime = now
            }
            MotionEvent.ACTION_MOVE -> {
                if (isThrottledGesture) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isThrottledGesture) {
                    isThrottledGesture = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if ((now - lastTouchDownTime < THROTTLE_MS) ||
            (now - AdsBinder.lastNativeClickTime < THROTTLE_MS)
        ) {
            return false
        }
        lastTouchDownTime = now
        return super.performClick()
    }

    companion object {
        const val THROTTLE_MS = 1500L
    }
}
