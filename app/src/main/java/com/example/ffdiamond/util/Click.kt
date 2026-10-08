package com.example.ffdiamond.util

import android.os.SystemClock
import android.view.View

/**
 * Enforces a minimum interval between click events on a view to prevent
 * accidental rapid double-clicks or repeated ad/intent triggers.
 */
inline fun View.setOnSafeClickListener(
    throttleMs: Long = 1000L,
    crossinline onSafeClick: (View) -> Unit
) {
    setOnClickListener(object : View.OnClickListener {
        private var lastClickTime = 0L

        override fun onClick(v: View) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTime >= throttleMs) {
                lastClickTime = now
                onSafeClick(v)
            }
        }
    })
}
