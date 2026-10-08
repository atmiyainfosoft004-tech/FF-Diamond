package com.example.ffdiamond.ads

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat
import com.onesignal.OneSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OneSignal is started after the first frame, off the main thread. Init + FCM token on
 * Application.onCreate after a data-clear blocks JobScheduler (ANR: no response to onStartJob).
 * Paid: permission on Gender. Organic (no ads flow): permission on Intro.
 */
object PushNotifications {

    const val APP_ID = "6bca334a-4350-497e-a2ba-aafad4159c04"
    private const val PREFS_NAME = "rbx_push_notifications"
    private const val KEY_ASKED_ONCE = "asked_notification_once"

    private val started = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val prompted = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread({
            runCatching {
                OneSignal.initWithContext(app, APP_ID)
                ready.set(true)
            }.onFailure { started.set(false) }
        }, "onesignal-init").apply {
            isDaemon = true
            start()
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasAskedOnce(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASKED_ONCE, false)
    }

    fun setAskedOnce(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED_ONCE, true)
            .apply()
    }

    /** Shows POST_NOTIFICATIONS on API 33+. Safe to call more than once; only the first prompts unless forced. */
    suspend fun promptIfNeeded(force: Boolean = false) {
        if (!force && !prompted.compareAndSet(false, true)) return
        runCatching {
            withTimeoutOrNull(8_000) {
                withContext(Dispatchers.IO) {
                    var waits = 0
                    while (!ready.get() && waits < 40) {
                        delay(100)
                        waits++
                    }
                    if (!ready.get()) return@withContext
                    OneSignal.Notifications.requestPermission(false)
                }
            }
        }
    }
}
