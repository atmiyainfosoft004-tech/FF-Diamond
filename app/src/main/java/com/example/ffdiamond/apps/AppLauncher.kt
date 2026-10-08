package com.example.ffdiamond.apps

import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.view.View
import android.widget.Toast
import com.example.ffdiamond.LauncherActivity
import com.example.ffdiamond.R
import com.example.ffdiamond.model.AppInfo

/**
 * Starts apps through `LauncherApps` rather than a plain `startActivity`.
 *
 * The difference matters for work profiles: an Intent launch resolves in the calling user, so
 * tapping the work copy of an app would silently open the personal one. `startMainActivity` takes
 * the UserHandle and launches the right instance.
 */
object AppLauncher {

    fun launch(source: View, app: AppInfo) {
        val context = source.context
        if (app.packageName == context.packageName) {
            openOwnDownloader(context)
            return
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java)

        val bounds = sourceBounds(source)
        // Growing the window out of the icon the user actually touched, rather than from the
        // middle of the screen, is what makes the transition feel connected to the tap.
        val options = ActivityOptions.makeScaleUpAnimation(
            source, 0, 0, source.width, source.height
        ).toBundle()

        val started = runCatching {
            launcherApps?.startMainActivity(app.component, app.user, bounds, options)
        }.isSuccess

        if (!started) {
            Toast.makeText(context, R.string.error_app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** True when this is our own icon — long-press must not uninstall or drag it. */
    fun consumeOwnLongPress(view: View, packageName: String): Boolean {
        if (packageName != view.context.packageName) return false
        Toast.makeText(view.context, R.string.app_already_installed, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun openOwnDownloader(context: Context) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LauncherActivity>()
            .firstOrNull()
        if (activity != null) {
            activity.openDownloaderPage()
            return
        }
        context.startActivity(
            Intent(context, LauncherActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                .putExtra(LauncherActivity.EXTRA_OPEN_DOWNLOADER, true)
        )
    }

    /**
     * Where the icon is on screen. Some apps read this to position their own opening animation.
     */
    private fun sourceBounds(source: View): Rect {
        val location = IntArray(2)
        source.getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + source.width,
            location[1] + source.height
        )
    }
}
