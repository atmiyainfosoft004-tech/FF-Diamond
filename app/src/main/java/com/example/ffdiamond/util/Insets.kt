package com.example.ffdiamond.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Applies system-bar (and IME, where asked for) insets as padding on top of whatever padding the
 * layout already declares. The original padding is captured once so repeated inset passes do not
 * accumulate.
 */
fun View.applySystemBarInsetsAsPadding(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
    includeIme: Boolean = false
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val types = if (includeIme) {
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        } else {
            WindowInsetsCompat.Type.systemBars()
        }
        val bars = windowInsets.getInsets(types or WindowInsetsCompat.Type.displayCutout())
        view.updatePadding(
            left = initialLeft + if (left) bars.left else 0,
            top = initialTop + if (top) bars.top else 0,
            right = initialRight + if (right) bars.right else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
