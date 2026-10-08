package com.example.ffdiamond.ui.drawer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.ffdiamond.R
import com.example.ffdiamond.apps.AppShortcuts
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.util.Motion

/**
 * Long-press menu on a drawer icon: shortcuts, App info, Add to Home, Uninstall.
 *
 * Grown out of the icon rather than popped in at a fixed corner, using the same 400 ms curve as
 * folders, so the menu reads as belonging to the app that was held.
 */
class AppPopup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onPinToHome: ((AppInfo) -> Unit)? = null
    var onUninstall: ((AppInfo) -> Unit)? = null

    private val scrim: View
    private val card: LinearLayout

    val isOpen: Boolean get() = isVisible

    init {
        LayoutInflater.from(context).inflate(R.layout.view_app_popup, this, true)
        scrim = findViewById(R.id.popup_scrim)
        card = findViewById(R.id.popup_card)
        isVisible = false
        scrim.setOnClickListener { dismiss() }
    }

    fun show(anchor: View, app: AppInfo) {
        card.removeAllViews()

        AppShortcuts.list(context, app).forEach { shortcut ->
            addRow(
                icon = {
                    runCatching {
                        context.getSystemService(android.content.pm.LauncherApps::class.java)
                            ?.getShortcutIconDrawable(shortcut, resources.displayMetrics.densityDpi)
                    }.getOrNull()
                },
                label = shortcut.shortLabel?.toString().orEmpty(),
                fallback = R.drawable.ic_apps_grid
            ) {
                AppShortcuts.start(anchor, shortcut)
                dismiss()
            }
        }

        addRow(R.drawable.ic_info, context.getString(R.string.popup_app_info)) {
            openAppInfo(app)
            dismiss()
        }
        addRow(R.drawable.ic_add_home, context.getString(R.string.popup_add_to_home)) {
            onPinToHome?.invoke(app)
            dismiss()
        }
        if (!app.isSystemApp) {
            addRow(R.drawable.ic_uninstall, context.getString(R.string.popup_uninstall)) {
                onUninstall?.invoke(app)
                dismiss()
            }
        }

        isVisible = true
        card.alpha = 0f
        card.scaleX = 0.4f
        card.scaleY = 0.4f
        post { expandFrom(anchor) }
    }

    fun dismiss(immediate: Boolean = false) {
        if (!isOpen) return
        val duration = if (immediate) 0L else Motion.duration(context, Motion.DURATION_STANDARD)
        if (duration == 0L) {
            hide()
            return
        }
        card.animate()
            .scaleX(0.4f)
            .scaleY(0.4f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(Motion.STANDARD)
            .withEndAction { hide() }
            .start()
    }

    private fun hide() {
        card.animate().cancel()
        isVisible = false
        card.scaleX = 1f
        card.scaleY = 1f
        card.alpha = 1f
        card.translationX = 0f
        card.translationY = 0f
        card.removeAllViews()
    }

    private fun expandFrom(anchor: View) {
        if (!isOpen || card.width == 0) {
            post { if (isOpen) expandFrom(anchor) }
            return
        }
        positionCard(anchor)
        card.pivotX = 0f
        card.pivotY = 0f

        val duration = Motion.duration(context, Motion.DURATION_STANDARD)
        if (duration == 0L) {
            card.alpha = 1f
            card.scaleX = 1f
            card.scaleY = 1f
            return
        }
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(Motion.STANDARD)
            .start()
    }

    private fun positionCard(anchor: View) {
        val overlay = IntArray(2)
        val from = IntArray(2)
        getLocationOnScreen(overlay)
        anchor.getLocationOnScreen(from)
        val x = (from[0] - overlay[0]).toFloat()
        val y = (from[1] - overlay[1] + anchor.height).toFloat()
        val maxX = (width - card.width).coerceAtLeast(0).toFloat()
        val maxY = (height - card.height).coerceAtLeast(0).toFloat()
        card.translationX = x.coerceIn(0f, maxX)
        card.translationY = y.coerceIn(0f, maxY)
    }

    private fun addRow(iconRes: Int, label: String, onClick: () -> Unit) {
        addRow(
            icon = { ContextCompat.getDrawable(context, iconRes) },
            label = label,
            fallback = iconRes,
            onClick = onClick
        )
    }

    private fun addRow(
        icon: () -> android.graphics.drawable.Drawable?,
        label: String,
        fallback: Int,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(context).inflate(R.layout.item_popup_row, card, false)
        row.findViewById<TextView>(R.id.row_label).text = label
        row.findViewById<ImageView>(R.id.row_icon).setImageDrawable(
            icon() ?: ContextCompat.getDrawable(context, fallback)
        )
        row.setOnClickListener { onClick() }
        card.addView(row)
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", app.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
