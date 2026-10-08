package com.example.ffdiamond.apps

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import com.example.ffdiamond.LauncherActivity

/**
 * Opens the system uninstall confirmation for [packageName]. Never deletes silently.
 */
object AppUninstaller {

    fun start(context: Context, packageName: String, user: UserHandle) {
        if (startIntent(context, packageName, user)) return
        val sender = PendingIntent.getActivity(
            context,
            REQUEST,
            Intent(context, LauncherActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ).intentSender
        runCatching {
            context.packageManager.packageInstaller.uninstall(packageName, sender)
        }
    }

    private fun startIntent(context: Context, packageName: String, user: UserHandle): Boolean {
        val data = Uri.fromParts("package", packageName, null)
        val intents = listOf(
            Intent(Intent.ACTION_DELETE, data),
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, data)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (user != Process.myUserHandle()) {
                intent.putExtra(Intent.EXTRA_USER, user)
            }
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }

    private const val REQUEST = 17
}
