package com.example.ffdiamond.util

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.example.ffdiamond.R

fun Activity.applyAppSlideTransitions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
    }
}

fun Activity.overrideAppOpenTransition() {
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
}

fun Activity.overrideAppCloseTransition() {
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
}

fun Activity.startActivityWithSlide(intent: Intent) {
    startActivity(intent)
    overrideAppOpenTransition()
}

fun Activity.finishWithSlide() {
    finish()
    overrideAppCloseTransition()
}
