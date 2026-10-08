package com.example.ffdiamond.funnel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * App-icon entry. Routes to the current funnel activity, or straight into the launcher.
 */
class FunnelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FunnelPreferences.isCompletedBlocking(this)) {
            FunnelNav.openApp(this)
            return
        }
        FunnelNav.open(this, FunnelPreferences.stepBlocking(this), showWeb = true)
        finish()
    }
}
