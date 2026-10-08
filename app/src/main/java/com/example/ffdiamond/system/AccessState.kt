package com.example.ffdiamond.system

import android.Manifest
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Everything onboarding shows a granted / not-granted state for. Read fresh on every onResume,
 * because all of it except POST_NOTIFICATIONS is granted outside the app.
 */
data class AccessState(
    val notificationsApplicable: Boolean,
    val notificationsGranted: Boolean,
    val notificationListenerEnabled: Boolean,
    val usageAccessGranted: Boolean,
    val isDefaultLauncher: Boolean
) {
    companion object {
        fun read(context: Context): AccessState = AccessState(
            notificationsApplicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            notificationsGranted = AccessChecks.hasNotificationPermission(context),
            notificationListenerEnabled = AccessChecks.isNotificationListenerEnabled(context),
            usageAccessGranted = AccessChecks.hasUsageAccess(context),
            isDefaultLauncher = AccessChecks.isDefaultLauncher(context)
        )
    }
}

object AccessChecks {

    /** Below API 33 the permission is granted at install time, so treat it as held. */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /**
     * PACKAGE_USAGE_STATS is an app-op, not a normal permission, so checkSelfPermission is not
     * enough. MODE_DEFAULT means "fall back to the permission check", which is why both run.
     */
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Asking the package manager who currently owns HOME is the only answer that stays correct
     * across OEM launchers, the role holder and the "always / just once" chooser.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                return true
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            home,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
