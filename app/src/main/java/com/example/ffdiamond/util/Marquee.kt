package com.example.ffdiamond.util

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

fun View.enableMarqueeLabels() {
    if (this is TextView && ellipsize == TextUtils.TruncateAt.MARQUEE) {
        isSelected = true
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).enableMarqueeLabels()
    }
}
