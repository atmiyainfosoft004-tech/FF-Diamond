package com.example.ffdiamond.util

import android.content.Context
import android.provider.Settings
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/**
 * The motion spec, in one place, so every animation in the launcher reads the same.
 *
 * Durations always go through [duration] rather than being used raw: when the user has turned
 * animations off in developer options, an animation that still runs for 400 ms feels broken, so
 * the scale of 0 is honoured by jumping straight to the end state.
 */
object Motion {

    /** Folder open/close, drawer settle, popups. */
    val STANDARD: Interpolator = PathInterpolator(0.22f, 0.9f, 0.24f, 1f)

    /** Icon press, chip select, and other small state changes. */
    val SMALL: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** The folder-merge circle, which is supposed to look eager rather than measured. */
    val OVERSHOOT: Interpolator = OvershootInterpolator(1.2f)

    const val DURATION_STANDARD = 400L
    const val DURATION_SMALL = 180L
    const val DURATION_CELL_REFLOW = 200L
    const val DURATION_REFLOW_STAGGER = 20L
    const val DURATION_ICON_FADE = 120L

    /** How long a hover has to hold before it means "merge these" rather than "move aside". */
    const val DURATION_MERGE_HOLD = 250L
    const val DURATION_MERGE_GROW = 300L

    /** How long a hover at the screen edge holds before the page turns under the drag. */
    const val DURATION_PAGE_FLIP_HOLD = 400L

    const val DURATION_FOLDER_MEMBER_STAGGER = 20L

    const val SPRING_STIFFNESS = 380f
    const val SPRING_DAMPING = 0.86f

    /** Drawer settles open above this progress, or on a fast upward fling. */
    const val DRAWER_SETTLE = 0.4f
    const val DRAWER_FLING_DP = 1000f

    fun animatorScale(context: Context): Float =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

    /** Returns 0 when the user has animations disabled, which callers treat as "jump to end". */
    fun duration(context: Context, base: Long): Long =
        (base * animatorScale(context)).toLong()
}
