package com.example.ffdiamond.ads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import com.example.ffdiamond.R

object AdsLoading {

    fun show(activity: Activity): Dialog? {
        if (activity.isFinishing) return null
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_loading_ads)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        runCatching { dialog.show() }.onFailure { return null }
        return dialog
    }

    fun hide(dialog: Dialog?) {
        if (dialog == null) return
        runCatching { if (dialog.isShowing) dialog.dismiss() }
    }
}
