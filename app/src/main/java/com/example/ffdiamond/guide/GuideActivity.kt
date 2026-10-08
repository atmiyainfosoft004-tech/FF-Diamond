package com.example.ffdiamond.guide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.databinding.ViewScreenHeaderBinding
import com.example.ffdiamond.funnel.AppLocale
import com.example.ffdiamond.util.applyAppSlideTransitions
import com.example.ffdiamond.util.overrideAppCloseTransition
import com.example.ffdiamond.util.setOnSafeClickListener
import com.example.ffdiamond.util.startActivityWithSlide

/**
 * Shared base for the FF Diamond tool screens. Keeps the same ad wiring the old calculator
 * screens used: interstitial-or-web before opening the next screen, AdsGate resume hook and
 * ad slot release on destroy.
 */
abstract class GuideActivity : AppCompatActivity() {

    private var isNavigating = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyAppSlideTransitions()
    }

    protected fun bindHeader(header: ViewScreenHeaderBinding, @StringRes titleRes: Int) {
        header.txtScreenTitle.setText(titleRes)
        header.btnBack.setOnSafeClickListener { finish() }
    }

    protected fun openWithAd(intent: Intent) {
        if (isNavigating) return
        isNavigating = true
        AdsGate.onInterOrWeb(this) {
            if (isFinishing || isDestroyed) {
                isNavigating = false
                return@onInterOrWeb
            }
            startActivityWithSlide(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
        AdsGate.onResume(this)
    }

    override fun finish() {
        super.finish()
        overrideAppCloseTransition()
    }

    override fun onDestroy() {
        isNavigating = false
        AdsGate.release(this)
        AdsBinder.release(this)
        super.onDestroy()
    }
}
