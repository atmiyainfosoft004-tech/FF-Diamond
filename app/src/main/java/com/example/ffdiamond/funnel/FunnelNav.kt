package com.example.ffdiamond.funnel

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.ffdiamond.LauncherActivity
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.ads.AdsSdk
import com.example.ffdiamond.ads.InstallSource
import com.example.ffdiamond.guide.DiamondHomeActivity
import com.example.ffdiamond.system.AccessChecks
import com.example.ffdiamond.util.overrideAppOpenTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object FunnelNav {

    const val EXTRA_GATE = "funnel_gate"
    const val EXTRA_AFTER_DEFAULT = "funnel_after_default"
    const val EXTRA_SHOW_WEB = "funnel_show_web"
    private var openingApp = false
    private val advancedAfterDefault = AtomicBoolean(false)
    private val debugFunnelReset = AtomicBoolean(false)
    @Volatile private var coverHome = false
    @Volatile private var homeCoverConsumed = false
    @Volatile private var pendingAfterDefault: FunnelStep? = null
    @Volatile private var suppressRestoreWebUntil = 0L
    private var lastResumeAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    fun open(
        context: Context,
        step: FunnelStep,
        afterDefault: Boolean = false,
        showWeb: Boolean = false
    ) {
        if (step != FunnelStep.SET_DEFAULT && step != FunnelStep.SET_DEFAULT_GATE) {
            pendingAfterDefault = null
        }
        if (step != FunnelStep.SET_DEFAULT_GATE) {
            CoroutineScope(Dispatchers.Main).launch {
                FunnelPreferences.saveStep(context.applicationContext, step)
            }
        }
        val intent = Intent(context, activityClass(step))
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_AFTER_DEFAULT, afterDefault)
            .putExtra(EXTRA_SHOW_WEB, showWeb)
        if (step == FunnelStep.SET_DEFAULT_GATE) {
            advancedAfterDefault.set(false)
            intent.putExtra(EXTRA_GATE, true)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startWithSlide(context, intent)
    }

    /**
     * New users cannot use Home until the funnel is done. Home / Recents reopen the saved
     * step and open web ads there.
     */
    fun resumeIncompleteFunnel(activity: Activity) {
        if (coverHome) return
        if (pendingAfterDefault != null) return
        if (FunnelPreferences.isCompletedBlocking(activity)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastResumeAt < 500L) return
        lastResumeAt = now
        AdsGate.abandon()
        if (InstallSource.isOrganic(activity)) {
            openOrganicEntry(activity)
            return
        }
        val showWeb = SystemClock.elapsedRealtime() >= suppressRestoreWebUntil
        open(activity, FunnelPreferences.stepBlocking(activity), showWeb = showWeb)
    }

    /** Splash just routed. Home must not immediately fire restore web ads (black screen / tab burst). */
    fun quietRouteFromSplash() {
        suppressRestoreWebUntil = SystemClock.elapsedRealtime() + 2_500L
    }

    fun next(activity: Activity, from: FunnelStep) {
        if (from == FunnelStep.SET_DEFAULT_GATE) {
            openApp(activity)
            return
        }
        if (from == FunnelStep.LANGUAGE && InstallSource.isOrganic(activity)) {
            openDownloader(activity)
            return
        }
        val next = from.next() ?: return openApp(activity)
        open(activity, next, afterDefault = false)
    }

    fun openIntro(activity: Activity) {
        open(activity, FunnelStep.INTRO)
    }

    /**
     * The system opened [LauncherActivity] because we became the default home.
     * Bring Set as Default back so the interstitial can play there, then Language.
     */
    fun coverHomeIfNeeded(activity: Activity) {
        if (!coverHome) return
        coverHome = false
        homeCoverConsumed = true
        bringSetDefault(activity)
    }

    /**
     * Settings granted HOME. Bring Set as Default to the front; that screen shows the
     * interstitial and only then opens Language.
     */
    fun continueAfterDefault(
        context: Context,
        from: FunnelStep,
        hideHome: Boolean = true
    ) {
        if (from == FunnelStep.SET_DEFAULT_GATE) {
            if (!advancedAfterDefault.compareAndSet(false, true)) return
            openAppFromContext(context.applicationContext)
            return
        }
        if (hideHome && !homeCoverConsumed) coverHome = true
        pendingAfterDefault = FunnelStep.SET_DEFAULT
        bringSetDefault(context)
        handler.postDelayed({ bringSetDefault(context.applicationContext) }, 400)
    }

    private fun bringSetDefault(context: Context) {
        val intent = Intent(context, SetDefaultActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            .putExtra(EXTRA_AFTER_DEFAULT, true)
        if (context !is SetDefaultActivity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val host = when {
            context is Activity && !context.isFinishing -> context
            else -> context.applicationContext
        }
        if (host !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { host.startActivity(intent) }
    }

    fun openApp(activity: Activity) {
        if (InstallSource.isOrganic(activity)) {
            openDownloader(activity)
            return
        }
        if (!beginOpen(activity)) return
        CoroutineScope(Dispatchers.Main).launch {
            FunnelPreferences.setCompleted(activity.applicationContext)
            AppLocale.commit()
            AdsSdk.resetFunnelWebShown(activity)
            startWithSlide(
                activity,
                Intent(activity, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(LauncherActivity.EXTRA_OPEN_DOWNLOADER, true)
            )
            activity.finish()
            openingApp = false
        }
    }

    fun openOrganicEntry(activity: Activity) {
        if (FunnelPreferences.isCompletedBlocking(activity)) {
            openDownloader(activity)
            return
        }
        val step = FunnelPreferences.stepBlocking(activity)
        if (step == FunnelStep.LANGUAGE) {
            open(activity, FunnelStep.LANGUAGE, showWeb = false)
        } else {
            open(activity, FunnelStep.INTRO, showWeb = false)
        }
    }

    fun openDownloader(activity: Activity) {
        if (!beginOpen(activity)) return
        CoroutineScope(Dispatchers.Main).launch {
            FunnelPreferences.setCompleted(activity.applicationContext)
            AppLocale.commit()
            AdsSdk.resetFunnelWebShown(activity)
            startWithSlide(
                activity,
                Intent(activity, DiamondHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            activity.finish()
            openingApp = false
        }
    }

    private fun openAppFromContext(context: Context) {
        if (InstallSource.isOrganic(context)) {
            if (!beginOpen(context)) return
            CoroutineScope(Dispatchers.Main).launch {
                FunnelPreferences.setCompleted(context.applicationContext)
                AppLocale.commit()
                AdsSdk.resetFunnelWebShown(context)
                runCatching {
                    startWithSlide(
                        context,
                        Intent(context, DiamondHomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
                openingApp = false
            }
            return
        }
        if (!beginOpen(context)) return
        CoroutineScope(Dispatchers.Main).launch {
            FunnelPreferences.setCompleted(context.applicationContext)
            AppLocale.commit()
            AdsSdk.resetFunnelWebShown(context)
            runCatching {
                startWithSlide(
                    context,
                    Intent(context, LauncherActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(LauncherActivity.EXTRA_OPEN_DOWNLOADER, true)
                )
            }
            openingApp = false
        }
    }

    /** Blocks a double-tap / double AdsGate callback during first open. */
    private fun beginOpen(context: Context): Boolean {
        if (openingApp) return false
        openingApp = true
        return true
    }

    /** Warm splash re-entry after a prior launch left the guard set. */
    fun clearOpenGuard() {
        openingApp = false
    }

    fun resetLaunchGuard() {
        handler.removeCallbacksAndMessages(null)
        openingApp = false
        advancedAfterDefault.set(false)
        coverHome = false
        homeCoverConsumed = false
        pendingAfterDefault = null
        lastResumeAt = 0L
        AdsGate.abandon()
    }

    /** Once per process so a locale-driven activity relaunch cannot reset DataStore again. */
    fun takeDebugFunnelReset(): Boolean = debugFunnelReset.compareAndSet(false, true)

    /** Home settings is open — if HOME launches, bring Set as Default back for the interstitial. */
    fun armCoverHome(next: FunnelStep = FunnelStep.SET_DEFAULT) {
        pendingAfterDefault = next
        coverHome = true
    }

    fun cancelCoverHome() {
        if (advancedAfterDefault.get()) return
        coverHome = false
        pendingAfterDefault = null
    }

    fun onStartApp(activity: Activity) {
        if (AccessChecks.isDefaultLauncher(activity)) {
            openApp(activity)
        } else {
            open(activity, FunnelStep.SET_DEFAULT_GATE)
        }
    }

    private fun startWithSlide(context: Context, intent: Intent) {
        val options = ActivityOptions.makeCustomAnimation(
            context,
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
        context.startActivity(intent, options.toBundle())
        if (context is Activity) context.overrideAppOpenTransition()
    }

    private fun activityClass(step: FunnelStep): Class<out Activity> = when (step) {
        FunnelStep.SPLASH -> SplashActivity::class.java
        FunnelStep.SET_DEFAULT, FunnelStep.SET_DEFAULT_GATE ->
            SetDefaultActivity::class.java
        FunnelStep.INTRO -> IntroActivity::class.java
        FunnelStep.LANGUAGE -> LanguageActivity::class.java
        FunnelStep.GENDER -> GenderActivity::class.java
        FunnelStep.AGE -> AgeActivity::class.java
        FunnelStep.CATEGORY -> FavoriteCharacterActivity::class.java
        FunnelStep.WATCH -> FavoritePetActivity::class.java
        FunnelStep.INTERESTS -> GameModeActivity::class.java
        FunnelStep.NO_WATERMARK -> FavoriteWeaponActivity::class.java
        FunnelStep.FAST_SPEED -> FavoriteVehicleActivity::class.java
        FunnelStep.MULTI_FORMAT -> FavoriteBundleActivity::class.java
        FunnelStep.GO_TO_APP -> FavoriteEmoteActivity::class.java
        FunnelStep.START_APP -> GetStartedActivity::class.java
    }
}
