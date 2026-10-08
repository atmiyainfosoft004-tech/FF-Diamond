package com.example.ffdiamond.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.example.ffdiamond.R
import java.lang.ref.WeakReference

/**
 * Blocks every activity with a full-screen "Update" sheet when the server min_version_code is
 * ahead of this install. [com.example.ffdiamond.LauncherActivity] is never finished.
 */
object ForceUpdateGate : Application.ActivityLifecycleCallbacks {

    private const val TAG = "force_update_overlay"
    private var resumed = WeakReference<Activity>(null)
    private val onConfig: (AdsConfig) -> Unit = { bind(resumed.get()) }

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        AdsRepository.addListener(onConfig)
        bind(resumed.get())
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        bind(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed.get() === activity) resumed = WeakReference(null)
    }

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed.get() === activity) resumed = WeakReference(null)
    }

    private fun bind(activity: Activity?) {
        try {
            if (activity == null || activity.isFinishing || activity.isDestroyed) return
            val decor = activity.window?.decorView as? ViewGroup ?: return
            val existing = decor.findViewWithTag<View>(TAG)
            val blocked = AdsRepository.needsForceUpdate(activity)
            if (!blocked) {
                existing?.let { removeOverlay(it) }
                return
            }
            if (existing != null) return
            val overlay = LayoutInflater.from(activity).inflate(R.layout.view_force_update, decor, false)
            overlay.tag = TAG
            overlay.isClickable = true
            overlay.isFocusable = true
            overlay.findViewById<View>(R.id.force_update_button).setOnClickListener {
                AdsRepository.openUpdate(activity)
            }
            if (activity is ComponentActivity) {
                val back = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = Unit
                }
                activity.onBackPressedDispatcher.addCallback(activity, back)
                overlay.setTag(R.id.force_update_button, back)
            }
            decor.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } catch (_: RuntimeException) {
        }
    }

    private fun removeOverlay(overlay: View) {
        (overlay.getTag(R.id.force_update_button) as? OnBackPressedCallback)?.remove()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }
}
