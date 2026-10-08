package com.example.ffdiamond.guide

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import com.example.ffdiamond.databinding.DialogNotificationRationaleBinding

object NotificationRationaleDialog {

    fun show(
        context: Context,
        onEnable: () -> Unit,
        onLater: () -> Unit = {}
    ): Dialog? {
        val activity = context as? Activity
        if (activity != null && (activity.isFinishing || activity.isDestroyed)) return null

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogNotificationRationaleBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)

        binding.btnLater.setOnClickListener {
            runCatching { dialog.dismiss() }
            onLater()
        }

        binding.btnEnable.setOnClickListener {
            runCatching { dialog.dismiss() }
            onEnable()
        }

        runCatching { dialog.show() }
        return dialog
    }
}
