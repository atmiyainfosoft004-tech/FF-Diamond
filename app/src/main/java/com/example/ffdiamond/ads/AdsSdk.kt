package com.example.ffdiamond.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

object AdsSdk {

    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var appOpen: AppOpenAd? = null
    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var appOpenLoadedAt = 0L
    @Volatile private var interstitialLoadedAt = 0L
    private var interstitialClicks = 0
    private var appOpenClicks = 0
    private var homeSwipeClicks = 0

    private const val WEB_PREFS = "funnel_web_ads"

    /** Google expires App Open ads after 4 h; drop them a little earlier so a stale one is never shown. */
    private const val APP_OPEN_TTL_MS = 3 * 60 * 60 * 1000L + 50 * 60 * 1000L
    private const val INTERSTITIAL_TTL_MS = 60 * 60 * 1000L

    /** How long App Open waits for the screen to get focus (e.g. right after a Custom Tab closes). */
    private const val FOCUS_WAIT_MS = 2_500L
    private const val FOCUS_SETTLE_MS = 200L

    private fun freshAppOpen(): AppOpenAd? {
        val ad = appOpen ?: return null
        if (SystemClock.elapsedRealtime() - appOpenLoadedAt > APP_OPEN_TTL_MS) {
            appOpen = null
            return null
        }
        return ad
    }

    private fun freshInterstitial(): InterstitialAd? {
        val ad = interstitial ?: return null
        if (SystemClock.elapsedRealtime() - interstitialLoadedAt > INTERSTITIAL_TTL_MS) {
            interstitial = null
            return null
        }
        return ad
    }

    fun start(context: Context) {
        if (!InstallSource.adsAllowed(context)) return
        if (!started.compareAndSet(false, true)) {
            preload(context.applicationContext)
            return
        }
        val app = context.applicationContext
        MobileAds.initialize(app) {
            preload(app)
        }
    }

    /**
     * Config poll must not refresh live ads. Only load when Google ads turn on, or when a
     * unit / flag that was off turns on. Turning ads off drops the cached full-screen ads.
     */
    fun onRemoteConfig(context: Context, previous: AdsConfig?, next: AdsConfig) {
        if (!InstallSource.adsAllowed(context) || !next.googleEnabled || AdsRepository.needsForceUpdate(context)) {
            appOpen = null
            interstitial = null
            return
        }
        val same = previous != null &&
            previous.googleEnabled &&
            previous.interstitialEnabled == next.interstitialEnabled &&
            previous.appOpenEnabled == next.appOpenEnabled &&
            previous.interstitialUnit == next.interstitialUnit &&
            previous.appOpenUnit == next.appOpenUnit
        if (same) return
        preload(context.applicationContext)
    }

    fun preload(context: Context) {
        val config = AdsRepository.config(context)
        if (!InstallSource.adsAllowed(context) || !config.googleEnabled || AdsRepository.needsForceUpdate(context)) return
        if (config.interstitialEnabled) loadInterstitial(context, config)
        if (config.appOpenEnabled) loadAppOpen(context, config)
    }

    fun consumeInterstitialTurn(config: AdsConfig, respectCount: Boolean): Boolean {
        if (!config.interstitialEnabled || config.interstitialUnit.isBlank()) return false
        if (!respectCount) return true
        return shouldShowInterstitial(config)
    }

    fun consumeAppOpenTurn(config: AdsConfig, respectCount: Boolean): Boolean {
        if (!config.appOpenEnabled || config.appOpenUnit.isBlank()) return false
        if (!respectCount) return true
        return shouldShowAppOpen(config)
    }

    fun consumeHomeSwipeTurn(config: AdsConfig): Boolean {
        if (!config.homeSwipeEnabled) return false
        val every = config.homeSwipeCount
        if (every <= 0) return false
        homeSwipeClicks += 1
        return homeSwipeClicks % every == 0
    }

    fun resetFunnelWebShown(context: Context) {
        context.applicationContext
            .getSharedPreferences(WEB_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun resetClickCounts(context: Context? = null) {
        interstitialClicks = 0
        appOpenClicks = 0
        homeSwipeClicks = 0
        if (context != null) resetFunnelWebShown(context)
    }

    fun showAppOpen(
        activity: Activity,
        respectCount: Boolean = false,
        onDone: (FullscreenResult) -> Unit
    ) {
        val config = AdsRepository.config(activity)
        val done = once(onDone)
        if (!config.appOpenEnabled || config.appOpenUnit.isBlank()) {
            done(FullscreenResult.FAILED)
            return
        }
        if (respectCount && !shouldShowAppOpen(config)) {
            done(FullscreenResult.SKIPPED)
            return
        }
        val timeout = Runnable { done(FullscreenResult.FAILED) }
        handler.postDelayed(timeout, 8_000)
        fun complete(result: FullscreenResult) {
            handler.removeCallbacks(timeout)
            done(result)
        }
        val safeShown = {
            handler.removeCallbacks(timeout)
        }
        val ready = freshAppOpen()
        if (ready != null) {
            presentAppOpen(activity, ready, safeShown, ::complete)
            return
        }
        loadAppOpen(activity, config) {
            val loaded = appOpen
            if (loaded == null) complete(FullscreenResult.FAILED)
            else presentAppOpen(activity, loaded, safeShown, ::complete)
        }
    }

    fun showInterstitial(
        activity: Activity,
        onShown: () -> Unit,
        onDone: (FullscreenResult) -> Unit
    ) {
        val config = AdsRepository.config(activity)
        val done = once(onDone)
        if (!config.interstitialEnabled || config.interstitialUnit.isBlank()) {
            done(FullscreenResult.FAILED)
            return
        }
        val timeout = Runnable { done(FullscreenResult.FAILED) }
        handler.postDelayed(timeout, 8_000)
        fun complete(result: FullscreenResult) {
            handler.removeCallbacks(timeout)
            done(result)
        }
        val safeShown = {
            handler.removeCallbacks(timeout)
            onShown()
        }
        val ready = freshInterstitial()
        if (ready != null) {
            presentInterstitial(activity, ready, safeShown, ::complete)
            return
        }
        loadInterstitial(activity, config) {
            val loaded = interstitial
            if (loaded == null) complete(FullscreenResult.FAILED)
            else presentInterstitial(activity, loaded, safeShown, ::complete)
        }
    }

    private fun once(onDone: (FullscreenResult) -> Unit): (FullscreenResult) -> Unit {
        val finished = AtomicBoolean(false)
        return { result ->
            if (finished.compareAndSet(false, true)) onDone(result)
        }
    }

    private fun shouldShowInterstitial(config: AdsConfig): Boolean {
        val every = config.interstitialCount
        if (every <= 0) return false
        interstitialClicks += 1
        return interstitialClicks % every == 0
    }

    private fun shouldShowAppOpen(config: AdsConfig): Boolean {
        val every = config.appOpenCount
        if (every <= 0) return false
        appOpenClicks += 1
        return appOpenClicks % every == 0
    }

    private fun presentAppOpen(
        activity: Activity,
        ad: AppOpenAd,
        onShown: () -> Unit = {},
        onDone: (FullscreenResult) -> Unit
    ) {
        appOpen = null
        var finished = false
        fun finish(result: FullscreenResult) {
            if (finished) return
            finished = true
            onDone(result)
            preload(activity.applicationContext)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish(FullscreenResult.SHOWN)
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                finish(FullscreenResult.FAILED)
            override fun onAdShowedFullScreenContent() = onShown()
        }
        showWhenFocused(
            activity,
            show = { runCatching { ad.show(activity) }.onFailure { finish(FullscreenResult.FAILED) } },
            giveUp = {
                // Screen never became ready: keep the unused ad for the next turn instead of showing it blank.
                appOpen = ad
                finish(FullscreenResult.FAILED)
            }
        )
    }

    /**
     * Shows a full-screen ad only once [activity] actually has window focus. Calling show() while a
     * Custom Tab is still closing can leave the ad window empty (only the "Test Ad" label visible).
     */
    private fun showWhenFocused(activity: Activity, show: () -> Unit, giveUp: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + FOCUS_WAIT_MS
        if (!activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus()) {
            show()
            return
        }
        val check = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) {
                    giveUp()
                    return
                }
                if (activity.hasWindowFocus()) {
                    handler.postDelayed({
                        if (activity.isFinishing || activity.isDestroyed) giveUp() else show()
                    }, FOCUS_SETTLE_MS)
                    return
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    giveUp()
                    return
                }
                handler.postDelayed(this, 100L)
            }
        }
        handler.postDelayed(check, 100L)
    }

    private fun presentInterstitial(
        activity: Activity,
        ad: InterstitialAd,
        onShown: () -> Unit,
        onDone: (FullscreenResult) -> Unit
    ) {
        interstitial = null
        var finished = false
        fun finish(result: FullscreenResult) {
            if (finished) return
            finished = true
            onDone(result)
            preload(activity.applicationContext)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish(FullscreenResult.SHOWN)
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                finish(FullscreenResult.FAILED)
            override fun onAdShowedFullScreenContent() = onShown()
        }
        runCatching { ad.show(activity) }.onFailure { finish(FullscreenResult.FAILED) }
    }

    private fun loadAppOpen(context: Context, config: AdsConfig, onLoaded: (() -> Unit)? = null) {
        if (!config.appOpenEnabled || config.appOpenUnit.isBlank()) {
            onLoaded?.invoke()
            return
        }
        if (freshAppOpen() != null) {
            onLoaded?.invoke()
            return
        }
        AppOpenAd.load(
            context,
            config.appOpenUnit,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpen = ad
                    appOpenLoadedAt = SystemClock.elapsedRealtime()
                    AdImpressions.attach(context, ad, config.appOpenUnit)
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpen = null
                    onLoaded?.invoke()
                }
            }
        )
    }

    private fun loadInterstitial(
        context: Context,
        config: AdsConfig,
        onLoaded: (() -> Unit)? = null
    ) {
        if (!config.interstitialEnabled || config.interstitialUnit.isBlank()) {
            onLoaded?.invoke()
            return
        }
        if (freshInterstitial() != null) {
            onLoaded?.invoke()
            return
        }
        val unit = config.interstitialUnit.trim()
        if (unit.startsWith("/")) {
            AdManagerInterstitialAd.load(
                context,
                unit,
                AdManagerAdRequest.Builder().build(),
                object : AdManagerInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                        interstitial = ad
                        interstitialLoadedAt = SystemClock.elapsedRealtime()
                        AdImpressions.attach(context, ad, unit)
                        onLoaded?.invoke()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitial = null
                        onLoaded?.invoke()
                    }
                }
            )
            return
        }
        InterstitialAd.load(
            context,
            unit,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoadedAt = SystemClock.elapsedRealtime()
                    AdImpressions.attach(context, ad, unit)
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    onLoaded?.invoke()
                }
            }
        )
    }
}
