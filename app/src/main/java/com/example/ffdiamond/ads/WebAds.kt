package com.example.ffdiamond.ads

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

/**
 * Web ads always open as Chrome Custom Tabs (the in-app Chrome toolbar with X), never the
 * system browser chooser. This device often has no default browser, so ACTION_VIEW would
 * show a picker instead of Custom Chrome.
 */
object WebAds {

    private val PREFERRED = listOf(
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.sec.android.app.sbrowser"
    )

    @Volatile private var session: CustomTabsSession? = null
    @Volatile private var boundPkg: String? = null
    @Volatile private var lastClickUrl: String? = null

    private val connection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            client.warmup(0L)
            session = client.newSession(null)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            session = null
            boundPkg = null
        }
    }

    fun warmup(context: Context) {
        val app = context.applicationContext
        val pkg = providerPackage(app) ?: return
        if (boundPkg == pkg) return
        if (CustomTabsClient.bindCustomTabsService(app, pkg, connection)) {
            boundPkg = pkg
        }
    }

    fun randomLink(config: AdsConfig): String? {
        val fromWeb = config.webLinks.filter { it.startsWith("http") }
        if (fromWeb.isNotEmpty()) return pickClickLink(fromWeb)
        val fromNative = config.customNativeClickLinks()
        if (fromNative.isNotEmpty()) return pickClickLink(fromNative)
        val fromBanner = config.customBannerClickLinks()
        return pickClickLink(fromBanner)
    }

    /**
     * Fresh random http URL for a custom ad click. Skips the last opened URL when the pool
     * has more than one link so the same tab does not open twice in a row.
     */
    fun pickClickLink(candidates: List<String>): String? {
        val pool = candidates.filter { it.startsWith("http") }.distinct()
        if (pool.isEmpty()) return null
        val pick = if (pool.size == 1) {
            pool.first()
        } else {
            pool.filter { it != lastClickUrl }.randomOrNull() ?: pool.random()
        }
        lastClickUrl = pick
        return pick
    }

    fun openCustomNative(activity: Activity, config: AdsConfig): Boolean {
        val pool = config.customNativeClickLinks().ifEmpty {
            config.webLinks.filter { it.startsWith("http") }
        }
        val url = pickClickLink(pool) ?: return false
        return open(activity, url)
    }

    fun openCustomBanner(activity: Activity, config: AdsConfig): Boolean {
        val pool = config.customBannerClickLinks().ifEmpty {
            config.webLinks.filter { it.startsWith("http") }
        }
        val url = pickClickLink(pool) ?: return false
        return open(activity, url)
    }

    fun open(activity: Activity, url: String, reuseSession: Boolean = true): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        warmup(activity)
        val uri = Uri.parse(url)
        val pkg = providerPackage(activity) ?: return false
        val tabSession = if (reuseSession) session else null
        if (launchCustomTab(activity, uri, pkg, tabSession)) return true
        if (launchCustomTab(activity, uri, pkg, session = null)) return true
        for (fallback in PREFERRED) {
            if (fallback == pkg) continue
            if (!installed(activity, fallback)) continue
            if (!supportsCustomTabs(activity, fallback)) continue
            if (launchCustomTab(activity, uri, fallback, session = null)) return true
        }
        return false
    }

    fun providerPackage(context: Context): String? {
        for (pkg in PREFERRED) {
            if (installed(context, pkg) && supportsCustomTabs(context, pkg)) return pkg
        }
        return CustomTabsClient.getPackageName(context, PREFERRED)
            ?: CustomTabsClient.getPackageName(context, null)
    }

    private fun launchCustomTab(
        activity: Activity,
        uri: Uri,
        pkg: String,
        session: CustomTabsSession?
    ): Boolean {
        val tabs = CustomTabsIntent.Builder(session)
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setUrlBarHidingEnabled(false)
            .setInstantAppsEnabled(false)
            .build()
        tabs.intent.setPackage(pkg)
        tabs.intent.data = uri
        // NEW_TASK opens full Chrome. Custom Tabs must stay in this task.
        tabs.intent.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            tabs.launchUrl(activity, uri)
            true
        }.getOrDefault(false)
    }

    private fun supportsCustomTabs(context: Context, pkg: String): Boolean {
        val intent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(pkg)
        return context.packageManager.resolveService(intent, 0) != null
    }

    private fun installed(context: Context, pkg: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }
}
