package com.example.ffdiamond

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.ffdiamond.apps.AppRepository
import com.example.ffdiamond.data.HomeSettings
import com.example.ffdiamond.data.db.LauncherDatabase
import com.example.ffdiamond.home.HomeLayoutRepository
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.icons.IconFactory
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.widget.SearchWidgetProvider
import com.example.ffdiamond.widget.WidgetStore
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsRepository
import com.example.ffdiamond.ads.AdsSdk
import com.example.ffdiamond.ads.FacebookInstall
import com.example.ffdiamond.ads.FirebaseBoot
import com.example.ffdiamond.ads.ForceUpdateGate
import com.example.ffdiamond.ads.InstallSource
import com.example.ffdiamond.ads.PushNotifications
import com.example.ffdiamond.ads.WebAds
import com.example.ffdiamond.funnel.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns the objects that outlive any activity: the app list, the icon caches and the database.
 *
 * They are all lazy on purpose. This class is also constructed when the system binds the
 * notification listener, and enumerating every installed app at that moment would be pure waste —
 * the launcher asks for them when it opens.
 */
class LauncherApp : Application() {

    /** Survives configuration changes and activity teardown; cancelled only with the process. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLocale.hydrate(this)
        registerActivityLifecycleCallbacks(PortraitLock)
        FirebaseBoot.start(this)
        AdsRepository.start(this, scope)
        InstallSource.start(this)
        AdsBinder.register(this)
        ForceUpdateGate.register(this)
        val main = Handler(Looper.getMainLooper())
        main.post {
            if (InstallSource.adsAllowed(this)) {
                AdsSdk.start(this)
                WebAds.warmup(this)
            }
            FacebookInstall.start(this)
        }
        // After data-clear, GMS/FCM JobService ANRs if OneSignal inits in the same turn.
        main.postDelayed({ PushNotifications.start(this) }, 4_000)
    }

    /**
     * The grid-independent half of the geometry. Icon size is deliberately fixed to the default
     * grid so that switching to a 5-column layout re-lays out the workspace without invalidating a
     * single cached bitmap; screens call [DeviceProfile.withGrid] for the rest.
     */
    val deviceProfile: DeviceProfile by lazy { DeviceProfile.from(this, HomeSettings()) }

    private val database: LauncherDatabase by lazy { LauncherDatabase.get(this) }

    val iconCache: IconCache by lazy {
        val searchIconPx = resources.getDimensionPixelSize(R.dimen.search_card_icon)
        IconCache(
            context = this,
            dao = database.iconCacheDao(),
            factory = IconFactory(maxOf(deviceProfile.iconSizePx, searchIconPx)),
            scope = scope
        )
    }

    val appRepository: AppRepository by lazy { AppRepository(this, scope, iconCache) }

    val widgetStore: WidgetStore by lazy { WidgetStore(this) }

    val homeLayoutRepository: HomeLayoutRepository by lazy {
        HomeLayoutRepository(this, appRepository.loadedApps, database, widgetStore)
    }

    /**
     * Search-widget taps arrive as a broadcast, not as a HOME intent — the system rewrites those.
     * The live activity handles it; if none is up, [pendingOpenSearch] is consumed on next start.
     */
    var openSearchHandler: (() -> Unit)? = null
    @Volatile var pendingOpenSearch: Boolean = false

    fun requestOpenSearch() {
        val handler = openSearchHandler
        if (handler != null) {
            Handler(Looper.getMainLooper()).post(handler)
        } else {
            pendingOpenSearch = true
            startActivity(
                Intent(this, LauncherActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(SearchWidgetProvider.EXTRA_OPEN_SEARCH, true)
                }
            )
        }
    }

    companion object {
        fun from(context: Context): LauncherApp =
            context.applicationContext as LauncherApp
    }

    private object PortraitLock : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
