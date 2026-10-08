package com.example.ffdiamond.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Build
import android.view.View
import com.example.ffdiamond.model.AppInfo

/**
 * App shortcuts through `LauncherApps`. The launcher is allowed to read them only while it is the
 * default home app; anywhere else [list] returns empty rather than throwing.
 */
object AppShortcuts {

    fun list(context: Context, app: AppInfo, query: String = ""): List<ShortcutInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()

        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
        val shortcutQuery = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(flags)

        val shortcuts = runCatching {
            launcherApps.getShortcuts(shortcutQuery, app.user).orEmpty()
        }.getOrDefault(emptyList())

        val needle = query.trim()
        return if (needle.isEmpty()) {
            shortcuts.filter { it.isEnabled }
        } else {
            shortcuts.filter { shortcut ->
                shortcut.isEnabled &&
                    shortcut.shortLabel?.contains(needle, ignoreCase = true) == true
            }
        }
    }

    fun start(source: View, shortcut: ShortcutInfo) {
        val launcherApps = source.context.getSystemService(LauncherApps::class.java) ?: return
        val bounds = IntArray(2).let { location ->
            source.getLocationOnScreen(location)
            Rect(
                location[0],
                location[1],
                location[0] + source.width,
                location[1] + source.height
            )
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                launcherApps.startShortcut(
                    shortcut.`package`,
                    shortcut.id,
                    bounds,
                    null,
                    shortcut.userHandle
                )
            }
        }
    }
}
