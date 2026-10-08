package com.example.ffdiamond.ads

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object FirebaseBoot {

    const val TAG = "AtmiyaFirebase"

    fun start(context: Context) {
        val app = runCatching { FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance() }
            .onFailure { Log.e(TAG, "FirebaseApp init failed", it) }
            .getOrNull()
        if (app == null) {
            Log.e(TAG, "FirebaseApp is null — google-services.json / google-services plugin missing")
            return
        }
        val options = app.options
        Log.i(
            TAG,
            "ready project=${options.projectId} appId=${options.applicationId} gcm=${options.gcmSenderId}"
        )
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            Log.i(TAG, "Crashlytics collection on")
        }.onFailure { Log.e(TAG, "Crashlytics start failed", it) }

        val analytics = runCatching { FirebaseAnalytics.getInstance(context) }
            .onFailure { Log.e(TAG, "Analytics getInstance failed", it) }
            .getOrNull()
        if (analytics == null) return
        analytics.setAnalyticsCollectionEnabled(true)
        analytics.logEvent(
            "debug_boot",
            Bundle().apply {
                putString("package_name", context.packageName)
                putString("project_id", options.projectId)
            }
        )
        Log.i(
            TAG,
            "debug_boot sent. DebugView device needs: adb shell setprop debug.firebase.analytics.app ${context.packageName}"
        )
    }
}
