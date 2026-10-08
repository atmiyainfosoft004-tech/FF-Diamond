package com.example.ffdiamond.funnel

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import androidx.lifecycle.lifecycleScope
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsReferrerCheck
import com.example.ffdiamond.ads.AdsRepository
import com.example.ffdiamond.ads.AdsSdk
import com.example.ffdiamond.ads.InstallSource
import com.example.ffdiamond.databinding.ActivitySplashBinding
import com.example.ffdiamond.system.AccessChecks
import com.example.ffdiamond.util.applySystemBarInsetsAsPadding
import kotlinx.coroutines.launch

/**
 * Loading screen: Firestore ads_config fetch, then gclid/fbclid referrer, then intro/funnel.
 */
class SplashActivity : FunnelScreenActivity() {
    override val step = FunnelStep.SPLASH
    override val persistStep = false
    override val skipFullscreenAds = true

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.clRoot.applySystemBarInsetsAsPadding()

        setupTransitions()
        binding.imgLogo.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.splash_logo_enter)
        )

        if (hasDebugInstallOverride()) {
            FunnelPreferences.resetProgressBlocking(this)
            FunnelNav.resetLaunchGuard()
            AdsSdk.resetClickCounts(this)
            AppLocale.cache(AppLocale.DEFAULT)
        } else if (FunnelPreferences.isCompletedBlocking(this)) {
            leaveSplashToApp()
            return
        }

        lifecycleScope.launch {
            AdsReferrerCheck.resolve(this@SplashActivity, intent)
            if (isFinishing) return@launch
            if (InstallSource.adsAllowed(this@SplashActivity)) {
                AdsSdk.start(this@SplashActivity)
            }
            FunnelNav.quietRouteFromSplash()
            val config = AdsRepository.config(this@SplashActivity)
            Log.i(
                TAG,
                "route kind=${InstallSource.kind(this@SplashActivity)} " +
                    "web=${config.webEnabled} links=${config.webLinks.size} " +
                    "count=${config.webAdsCount} google=${config.googleEnabled}"
            )
            routeAfterCheck()
        }
    }

    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun hasDebugInstallOverride(): Boolean {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return false
        }
        return !intent.getStringExtra(InstallSource.EXTRA_DEBUG_SOURCE).isNullOrBlank() ||
            !intent.getStringExtra(InstallSource.EXTRA_DEBUG_REFERRER).isNullOrBlank()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (FunnelPreferences.isCompletedBlocking(this)) leaveSplashToApp()
    }

    private fun leaveSplashToApp() {
        FunnelNav.clearOpenGuard()
        if (InstallSource.adsAllowed(this)) {
            AdsSdk.start(this)
        }
        if (!InstallSource.isOrganic(this) && !AccessChecks.isDefaultLauncher(this)) {
            FunnelNav.open(this, FunnelStep.SET_DEFAULT_GATE)
            finish()
            return
        }
        FunnelNav.openApp(this)
    }

    private fun routeAfterCheck() {
        if (FunnelPreferences.isCompletedBlocking(this)) {
            leaveSplashToApp()
            return
        }
        if (InstallSource.isOrganic(this) || InstallSource.kind(this) == null) {
            FunnelNav.open(this, FunnelStep.INTRO, showWeb = false)
            setupTransitions()
            finish()
            return
        }
        val saved = FunnelPreferences.stepBlocking(this)
        val next = if (saved == FunnelStep.SPLASH) FunnelStep.SET_DEFAULT else saved
        FunnelNav.open(this, next, showWeb = false)
        setupTransitions()
        finish()
    }

    companion object {
        private const val TAG = "SplashAds"
    }
}
