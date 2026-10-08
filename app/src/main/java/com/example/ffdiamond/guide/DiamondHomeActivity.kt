package com.example.ffdiamond.guide

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ffdiamond.R
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.databinding.ActivityDiamondHomeBinding
import com.example.ffdiamond.funnel.AppLocale
import com.example.ffdiamond.util.applyAppSlideTransitions
import com.example.ffdiamond.util.overrideAppCloseTransition

/** Standalone FF Diamond home, used when the app is not the launcher (organic installs). */
class DiamondHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiamondHomeBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyAppSlideTransitions()
        binding = ActivityDiamondHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.app_name)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.flDiamondHomeHost.id, DiamondHomeFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // Lets a home-tile web ad (Custom Tab) finish and open its screen when the user comes back.
        AdsGate.onResume(this)
    }

    override fun onDestroy() {
        AdsGate.release(this)
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overrideAppCloseTransition()
    }
}
