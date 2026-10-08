package com.example.ffdiamond.system

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Becoming the home app has three different paths depending on the OS version and the OEM, and
 * any of them can be missing on a given device, so they are tried in order.
 */
object DefaultLauncherRequest {

    /**
     * Runs the best available path. Returns false only when every path failed, which means the
     * caller should tell the user to do it by hand.
     *
     * @param useRole false skips straight to the settings path. The caller sets this once the role
     * dialog has already come back without making us the home app, so a device where that dialog
     * does nothing cannot trap the user in a loop.
     */
    fun request(
        activity: Activity,
        roleLauncher: ActivityResultLauncher<Intent>,
        settingsLauncher: ActivityResultLauncher<Intent>,
        useRole: Boolean = true
    ): Boolean {
        if (useRole) {
            roleIntent(activity)?.let { intent ->
                try {
                    roleLauncher.launch(intent)
                    return true
                } catch (_: ActivityNotFoundException) {
                    // Role exists but no UI handles it. Fall through.
                }
            }
        }

        return openHomeSettings(activity, settingsLauncher) ||
            clearPreferredHomeAndShowChooser(activity)
    }

    /**
     * Home-app picker in Settings — not the in-app role dialog. The funnel shows a tap-to-dismiss
     * guide first, then lands the user here so they pick this app from the system list.
     */
    fun openHomeSettings(
        activity: Activity,
        settingsLauncher: ActivityResultLauncher<Intent>
    ): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                settingsLauncher.launch(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }

    fun startHomeSettings(context: Context): Boolean {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(flags),
            Intent(Settings.ACTION_SETTINGS).addFlags(flags)
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }

    /**
     * API 29+ only, and only worth launching when the role is available and not already ours —
     * createRequestRoleIntent on a held role returns immediately with no UI.
     */
    fun roleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /**
     * Last resort: drop whatever preferred-home association exists and fire a HOME intent so the
     * system shows its own chooser. clearPackagePreferredActivities is a no-op for callers without
     * the platform permission, which is fine — the HOME intent still surfaces the picker on most
     * devices when no default is set.
     */
    private fun clearPreferredHomeAndShowChooser(context: Context): Boolean {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.clearPackagePreferredActivities(context.packageName)
        } catch (_: Exception) {
            // Expected on modern Android; the HOME intent below is the part that matters.
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun notificationListenerSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun usageAccessSettings(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    /** Where to send the user once POST_NOTIFICATIONS has been permanently denied. */
    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
