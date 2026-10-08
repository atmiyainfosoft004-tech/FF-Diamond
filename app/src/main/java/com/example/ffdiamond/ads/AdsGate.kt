package com.example.ffdiamond.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

enum class FullscreenResult {
    SHOWN,
    FAILED,
    SKIPPED
}

/**
 * Google + web both on:
 * - inter on, app open off → interstitial when [AdsConfig.interstitialCount] is due, else web
 * - inter off, app open on → web, then App Open when [AdsConfig.appOpenCount] is due
 * - both on → if interstitial count is due, interstitial only; otherwise web, then App Open
 *   when its count is due. Never interstitial + App Open on one click.
 *
 * Funnel Next/Done/CTA ([onNext]): when that mix chooses web, open [AdsConfig.webAdsCount]
 * Custom Tabs together (missing = 1, 0 = none). afterDefault / restore / home swipe stay 1 tab.
 */
object AdsGate {

    private val busy = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var waitingWeb = false
    private var waitingActivity: Activity? = null
    private var afterWeb: (() -> Unit)? = null
    private var webOpenedAt = 0L
    private var lastUrl: String? = null
    private var retriedWeb = false
    private val watchdog = Runnable { checkWebStillShowing() }
    private val pendingOpens = ArrayList<Runnable>()

    fun onResume(activity: Activity) {
        if (!waitingWeb) return
        val waiting = waitingActivity
        val stale = waiting == null || waiting.isDestroyed || waiting.isFinishing
        if (!stale && waiting !== activity) return
        if (SystemClock.elapsedRealtime() - webOpenedAt < 800L) return
        finishWait()
    }

    /** Drop a stuck wait without advancing the funnel. Used when Home/Recents restore a step. */
    fun abandon() {
        pendingOpens.forEach { handler.removeCallbacks(it) }
        pendingOpens.clear()
        handler.removeCallbacks(watchdog)
        waitingWeb = false
        waitingActivity = null
        afterWeb = null
        lastUrl = null
        retriedWeb = false
        busy.set(false)
    }

    fun release(activity: Activity) {
        if (waitingActivity === activity) abandon()
    }

    fun afterDefault(activity: Activity, proceed: () -> Unit) {
        if (!activityReady(activity) || !InstallSource.adsAllowed(activity)) {
            proceed()
            return
        }
        activity.window.decorView.post {
            if (!activityReady(activity)) {
                proceed()
                return@post
            }
            playFullscreen(activity, respectCount = false, proceed, webBurst = false)
        }
    }

    fun onNext(activity: Activity, proceed: () -> Unit) {
        if (AdsRepository.needsForceUpdate(activity) || !InstallSource.adsAllowed(activity)) {
            proceed()
            return
        }
        playFullscreen(activity, respectCount = true, proceed, webBurst = true)
    }

    /**
     * Home left/right swipe. One Custom Tab when [AdsConfig.homeSwipeCount] is due.
     * Does not use the funnel [AdsConfig.webAdsCount] burst.
     */
    fun onHomeSwipe(activity: Activity) {
        if (!activityReady(activity) || !InstallSource.adsAllowed(activity)) return
        val config = AdsRepository.config(activity)
        if (!config.homeSwipeEnabled) return
        if (!acquireBusy()) return
        val done = wrap {}
        if (!AdsSdk.consumeHomeSwipeTurn(config)) {
            done()
            return
        }
        openWebThen(activity, done, done, countOverride = 1)
    }

    /**
     * Counted interstitial on downloader / in-app buttons. No Custom Tabs after the launcher.
     */
    fun onInterOrWeb(activity: Activity, proceed: () -> Unit) {
        if (!activityReady(activity)) {
            proceed()
            return
        }
        val config = AdsRepository.config(activity)
        if (!config.adsEnabled || !InstallSource.adsAllowed(activity)) {
            proceed()
            return
        }
        if (!acquireBusy()) {
            proceed()
            return
        }
        val done = wrap(proceed)
        val interDue = config.interstitialEnabled &&
            AdsSdk.consumeInterstitialTurn(config, respectCount = true)
        if (interDue) {
            showInterstitialThen(
                activity,
                respectCount = false,
                alreadyCounted = true,
                done,
                webBurst = false
            )
        } else {
            done()
        }
    }

    /** Always open a web ad when web ads are on. Home / Recents restore uses this. */
    fun showWeb(activity: Activity, proceed: () -> Unit = {}) {
        if (!activityReady(activity)) {
            proceed()
            return
        }
        if (!AdsRepository.config(activity).webEnabled || !InstallSource.adsAllowed(activity)) {
            proceed()
            return
        }
        if (!acquireBusy()) {
            proceed()
            return
        }
        val done = wrap(proceed)
        openWebThen(activity, done, done, countOverride = 1)
    }

    private fun playFullscreen(
        activity: Activity,
        respectCount: Boolean,
        proceed: () -> Unit,
        webBurst: Boolean
    ) {
        if (!activityReady(activity)) {
            proceed()
            return
        }
        val config = AdsRepository.config(activity)
        if (!config.adsEnabled || !InstallSource.adsAllowed(activity)) {
            proceed()
            return
        }
        if (!acquireBusy()) {
            proceed()
            return
        }
        val done = wrap(proceed)
        fun web() = openWebThen(
            activity,
            done,
            done,
            countOverride = if (webBurst) null else 1
        )
        when {
            !config.googleEnabled && config.webEnabled -> web()
            config.googleEnabled && config.webEnabled -> when {
                config.interstitialEnabled && !config.appOpenEnabled ->
                    showInterstitialThen(
                        activity,
                        respectCount,
                        alreadyCounted = false,
                        done,
                        webBurst
                    )
                !config.interstitialEnabled && config.appOpenEnabled ->
                    chromeThenAppOpen(activity, respectCount, done, webBurst)
                config.interstitialEnabled && config.appOpenEnabled ->
                    showExclusive(activity, respectCount, done, webBurst)
                else -> web()
            }
            config.googleEnabled -> when {
                config.interstitialEnabled && !config.appOpenEnabled ->
                    showInterstitialThen(
                        activity,
                        respectCount,
                        alreadyCounted = false,
                        done,
                        webBurst
                    )
                !config.interstitialEnabled && config.appOpenEnabled ->
                    showAppOpenThen(activity, respectCount, alreadyCounted = false, done, webBurst)
                config.interstitialEnabled && config.appOpenEnabled ->
                    showExclusive(activity, respectCount, done, webBurst)
                else -> done()
            }
            config.webEnabled -> web()
            else -> done()
        }
    }

    private fun showExclusive(
        activity: Activity,
        respectCount: Boolean,
        done: () -> Unit,
        webBurst: Boolean
    ) {
        val config = AdsRepository.config(activity)
        val interDue = AdsSdk.consumeInterstitialTurn(config, respectCount)
        val web = config.webEnabled
        when {
            interDue -> showInterstitialThen(
                activity,
                respectCount = false,
                alreadyCounted = true,
                done,
                webBurst
            )
            web -> chromeThenAppOpen(activity, respectCount, done, webBurst)
            else -> showAppOpenThen(activity, respectCount, alreadyCounted = false, done, webBurst)
        }
    }

    private fun showInterstitialThen(
        activity: Activity,
        respectCount: Boolean,
        alreadyCounted: Boolean,
        done: () -> Unit,
        webBurst: Boolean
    ) {
        if (!alreadyCounted &&
            !AdsSdk.consumeInterstitialTurn(AdsRepository.config(activity), respectCount)
        ) {
            fallbackWeb(activity, done, webBurst)
            return
        }
        val loading = AdsLoading.show(activity)
        AdsSdk.showInterstitial(
            activity,
            onShown = { AdsLoading.hide(loading) }
        ) { result ->
            AdsLoading.hide(loading)
            when (result) {
                FullscreenResult.SHOWN -> done()
                FullscreenResult.SKIPPED -> fallbackWeb(activity, done, webBurst)
                FullscreenResult.FAILED -> fallbackWeb(activity, done, webBurst)
            }
        }
    }

    private fun chromeThenAppOpen(
        activity: Activity,
        respectCount: Boolean,
        done: () -> Unit,
        webBurst: Boolean
    ) {
        val afterChrome = {
            AdsSdk.showAppOpen(activity, respectCount) { done() }
        }
        if (AdsRepository.config(activity).webEnabled) {
            openWebThen(
                activity,
                afterChrome,
                afterChrome,
                countOverride = if (webBurst) null else 1
            )
        } else {
            afterChrome()
        }
    }

    private fun showAppOpenThen(
        activity: Activity,
        respectCount: Boolean,
        alreadyCounted: Boolean,
        done: () -> Unit,
        webBurst: Boolean
    ) {
        AdsSdk.showAppOpen(activity, respectCount && !alreadyCounted) { result ->
            when (result) {
                FullscreenResult.SHOWN -> done()
                FullscreenResult.SKIPPED -> fallbackWeb(activity, done, webBurst)
                FullscreenResult.FAILED -> fallbackWeb(activity, done, webBurst)
            }
        }
    }

    private fun fallbackWeb(activity: Activity, done: () -> Unit, webBurst: Boolean) {
        if (AdsRepository.config(activity).webEnabled) {
            openWebThen(
                activity,
                done,
                done,
                countOverride = if (webBurst) null else 1
            )
        } else {
            done()
        }
    }

    private fun openWebThen(
        activity: Activity,
        onClosed: () -> Unit,
        onFailed: () -> Unit,
        countOverride: Int? = null
    ) {
        val config = AdsRepository.config(activity)
        if (!config.webEnabled) {
            onFailed()
            return
        }
        val count = (countOverride ?: FunnelWebBudget.tabsAtOnce(config.webAdsCount))
            .coerceAtMost(15)
        if (count <= 0) {
            onFailed()
            return
        }
        val urls = List(count) { WebAds.randomLink(config) }.filterNotNull()
        if (urls.isEmpty()) {
            android.util.Log.w(
                "AdsGate",
                "web ads on but no http links (web_links=${config.webLinks.size})"
            )
            onFailed()
            return
        }
        waitingActivity = activity
        waitingWeb = true
        afterWeb = onClosed
        lastUrl = urls.last()
        retriedWeb = false
        webOpenedAt = SystemClock.elapsedRealtime()
        val opened = java.util.concurrent.atomic.AtomicInteger(0)
        urls.forEachIndexed { index, url ->
            val job = Runnable {
                if (activity.isFinishing || activity.isDestroyed || !waitingWeb) return@Runnable
                if (WebAds.open(activity, url, reuseSession = index == 0)) {
                    opened.incrementAndGet()
                    webOpenedAt = SystemClock.elapsedRealtime()
                }
                if (index != urls.lastIndex) return@Runnable
                if (opened.get() == 0) {
                    waitingWeb = false
                    waitingActivity = null
                    afterWeb = null
                    lastUrl = null
                    onFailed()
                    return@Runnable
                }
                handler.removeCallbacks(watchdog)
                handler.postDelayed(watchdog, 900L)
            }
            pendingOpens.add(job)
            handler.postDelayed(job, index * 90L)
        }
    }

    private fun checkWebStillShowing() {
        if (!waitingWeb) return
        val activity = waitingActivity
        if (activity == null || activity.isDestroyed || activity.isFinishing) {
            abandon()
            return
        }
        if (!activity.hasWindowFocus()) return
        val url = lastUrl
        if (!retriedWeb && url != null) {
            retriedWeb = true
            if (WebAds.open(activity, url)) {
                webOpenedAt = SystemClock.elapsedRealtime()
                handler.postDelayed(watchdog, 900L)
                return
            }
        }
        finishWait()
    }

    private fun finishWait() {
        pendingOpens.forEach { handler.removeCallbacks(it) }
        pendingOpens.clear()
        handler.removeCallbacks(watchdog)
        waitingWeb = false
        waitingActivity = null
        lastUrl = null
        retriedWeb = false
        val next = afterWeb
        afterWeb = null
        handler.post { next?.invoke() }
    }

    private fun acquireBusy(): Boolean {
        if (busy.compareAndSet(false, true)) return true
        val waiting = waitingActivity
        val stale = waiting == null || waiting.isDestroyed || waiting.isFinishing
        val stuckOnScreen = waitingWeb &&
            waiting != null &&
            !waiting.isDestroyed &&
            waiting.hasWindowFocus() &&
            SystemClock.elapsedRealtime() - webOpenedAt > 1200L
        if (!stale && !stuckOnScreen) return false
        abandon()
        return busy.compareAndSet(false, true)
    }

    private fun wrap(proceed: () -> Unit): () -> Unit = {
        handler.removeCallbacks(watchdog)
        busy.set(false)
        proceed()
    }

    private fun activityReady(activity: Activity): Boolean =
        !activity.isFinishing &&
            !activity.isDestroyed &&
            !AdsRepository.needsForceUpdate(activity)
}
