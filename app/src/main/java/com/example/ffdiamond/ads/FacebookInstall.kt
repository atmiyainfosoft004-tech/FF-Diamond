package com.example.ffdiamond.ads

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Facebook install / app-open events. The App ID is not baked into the APK — it arrives on
 * [AdsConfig.facebookAppId] from the remote JSON (or the last cached copy).
 */
object FacebookInstall {

    const val TAG = "AtmiyaFacebook"

    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        apply(context.applicationContext, AdsRepository.config(context))
    }

    fun onConfig(context: Context, config: AdsConfig) {
        apply(context.applicationContext, config)
    }

    private fun apply(appContext: Context, config: AdsConfig) {
        val appId = config.facebookAppId
        if (appId.isBlank()) return
        val app = appContext as? Application ?: return
        handler.post {
            runCatching { initialize(app, appId, config.facebookClientToken) }
                .onFailure { Log.e(TAG, "Facebook SDK init failed", it) }
        }
    }

    private fun initialize(app: Application, appId: String, clientToken: String) {
        if (!started.compareAndSet(false, true)) return
        try {
            FacebookSdk.setApplicationId(appId)
            if (clientToken.isNotBlank()) {
                FacebookSdk.setClientToken(clientToken)
            }
            FacebookSdk.setAutoInitEnabled(true)
            FacebookSdk.setAutoLogAppEventsEnabled(true)
            FacebookSdk.setAdvertiserIDCollectionEnabled(true)
            @Suppress("DEPRECATION")
            FacebookSdk.sdkInitialize(app)
            if (!FacebookSdk.isInitialized()) {
                FacebookSdk.fullyInitialize()
            }
            AppEventsLogger.activateApp(app)
            Log.i(TAG, "install tracking ready appId=$appId")
        } catch (error: Throwable) {
            started.set(false)
            throw error
        }
    }
}
