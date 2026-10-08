package com.example.ffdiamond.apps

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.system.AccessChecks

/**
 * The eight apps that fill the empty search screen.
 *
 * Usage access is the real ranking — most time in the foreground over the last week. When the
 * user skipped that permission in onboarding, install time is the fallback the spec asks for,
 * so search still has something to show.
 */
object SuggestedApps {

    fun load(context: Context, apps: List<AppInfo>, limit: Int = LIMIT): List<AppInfo> {
        if (apps.isEmpty() || limit <= 0) return emptyList()
        val ranked = if (AccessChecks.hasUsageAccess(context)) {
            byUsage(context, apps)
        } else {
            emptyList()
        }
        return (ranked + byInstallTime(context, apps).filterNot { candidate ->
            ranked.any { it.key == candidate.key }
        }).take(limit)
    }

    private fun byUsage(context: Context, apps: List<AppInfo>): List<AppInfo> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, now - WEEK_MS, now)
        }.getOrNull().orEmpty()
        if (stats.isEmpty()) return emptyList()

        val timeByPackage = HashMap<String, Long>()
        stats.forEach { stat ->
            timeByPackage[stat.packageName] =
                (timeByPackage[stat.packageName] ?: 0L) + stat.totalTimeInForeground
        }
        return apps
            .filter { it.packageName != context.packageName }
            .sortedByDescending { timeByPackage[it.packageName] ?: 0L }
            .filter { (timeByPackage[it.packageName] ?: 0L) > 0L }
    }

    private fun byInstallTime(context: Context, apps: List<AppInfo>): List<AppInfo> {
        val packageManager = context.packageManager
        return apps
            .filter { it.packageName != context.packageName }
            .sortedByDescending { app ->
                runCatching {
                    packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
                }.getOrDefault(0L)
            }
    }

    private const val LIMIT = 8
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
}
