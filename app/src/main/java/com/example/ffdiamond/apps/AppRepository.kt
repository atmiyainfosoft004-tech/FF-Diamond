package com.example.ffdiamond.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * The list of launchable apps, kept current without a restart.
 *
 * Built on `LauncherApps` rather than `PackageManager` for two reasons the launcher cannot do
 * without: it enumerates per user profile, so work-profile apps appear (badged) alongside personal
 * ones, and it delivers install / uninstall / update callbacks directly instead of making us
 * listen for broadcasts and guess what changed.
 */
class AppRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val iconCache: IconCache
) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * [apps], but withheld until the first enumeration has actually finished.
     *
     * The difference matters to anything that treats "not in this list" as "uninstalled". The
     * initial empty value of [apps] is "not asked yet", and reading it as "nothing is installed"
     * would let the layout repository delete the entire home screen on every cold start.
     */
    val loadedApps: Flow<List<AppInfo>> =
        combine(apps, loaded) { list, isLoaded -> list.takeIf { isLoaded } }.filterNotNull()

    private val collator: Collator = Collator.getInstance().apply {
        strength = Collator.PRIMARY
    }

    /**
     * Collator rather than plain string ordering, so accented and non-Latin labels sort the way
     * the user's locale expects. Ties fall back to something stable, otherwise two activities of
     * the same app could swap places between reloads.
     */
    private val order = Comparator<AppInfo> { a, b ->
        val byLabel = collator.compare(a.label, b.label)
        if (byLabel != 0) {
            byLabel
        } else {
            compareValuesBy(a, b, { it.userSerial }, { it.component.flattenToShortString() })
        }
    }

    private var started = false

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) =
            refreshPackage(packageName, user)

        override fun onPackageRemoved(packageName: String, user: UserHandle) =
            refreshPackage(packageName, user)

        override fun onPackageChanged(packageName: String, user: UserHandle) =
            refreshPackage(packageName, user)

        /** External storage came back; the packages on it are launchable again. */
        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = packageNames.forEach { refreshPackage(it, user) }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = packageNames.forEach { refreshPackage(it, user) }

        /** A suspended app stays installed but stops being launchable, so it leaves the grid. */
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { refreshPackage(it, user) }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach { refreshPackage(it, user) }
    }

    /**
     * Idempotent, and never matched by a `stop()`: the launcher is the home app, so it tracks
     * packages for as long as its process is alive. Unregistering when the activity pauses would
     * mean missing every install that happens while the user is inside another app — which is,
     * of course, when installs happen.
     */
    fun start() {
        if (started) return
        started = true
        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch { reload() }
    }

    private suspend fun reload() {
        val loadedApps = withContext(Dispatchers.IO) {
            profiles().flatMap { user -> activitiesFor(null, user) }.sortedWith(order)
        }
        _apps.value = loadedApps
        _loaded.value = true
    }

    private fun refreshPackage(packageName: String, user: UserHandle) {
        scope.launch {
            val serial = serialFor(user)
            val replacement = withContext(Dispatchers.IO) { activitiesFor(packageName, user) }

            // An update can change the icon, and an uninstall must not leave one behind.
            iconCache.invalidatePackage(packageName, serial)

            _apps.value = _apps.value
                .filterNot { it.userSerial == serial && it.packageName == packageName }
                .plus(replacement)
                .sortedWith(order)
        }
    }

    /** Personal profile plus any work or clone profiles, in the order the system reports them. */
    private fun profiles(): List<UserHandle> =
        userManager?.userProfiles ?: emptyList()

    private fun activitiesFor(packageName: String?, user: UserHandle): List<AppInfo> {
        val activities: List<LauncherActivityInfo> = runCatching {
            launcherApps?.getActivityList(packageName, user).orEmpty()
        }.getOrDefault(emptyList())

        val serial = serialFor(user)
        val versions = HashMap<String, Long>()
        val listed = activities.map { activity ->
            val pkg = activity.componentName.packageName
            AppInfo(
                component = activity.componentName,
                user = user,
                userSerial = serial,
                label = activity.label?.toString().orEmpty().ifEmpty { pkg },
                versionCode = versions.getOrPut(pkg) { versionCodeOf(pkg) },
                isSystemApp = activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }
        return withOwnLauncher(user, serial, listed, packageName)
    }

    /**
     * Default-home packages are often omitted from [LauncherApps.getActivityList]. The user still
     * needs our icon on Home and in the drawer.
     */
    private fun withOwnLauncher(
        user: UserHandle,
        serial: Long,
        listed: List<AppInfo>,
        filterPackage: String?
    ): List<AppInfo> {
        val own = appContext.packageName
        if (filterPackage != null && filterPackage != own) return listed
        if (listed.any { it.packageName == own }) return listed
        val launch = appContext.packageManager.getLaunchIntentForPackage(own) ?: return listed
        val component = launch.component ?: ComponentName(own, "${own}.funnel.SetDefaultActivity")
        val label = appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
        return listed + AppInfo(
            component = component,
            user = user,
            userSerial = serial,
            label = label,
            versionCode = versionCodeOf(own),
            isSystemApp = appContext.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        )
    }

    private fun serialFor(user: UserHandle): Long =
        userManager?.getSerialNumberForUser(user) ?: 0L

    /**
     * The icon cache keys off this, so an app that updates gets a fresh icon on the next cold
     * start rather than a stale one.
     *
     * Version codes are a property of the APK, so reading them through the current user's
     * PackageManager is correct for a work-profile copy of the same package too. When the package
     * is not visible from here the value falls back to 0, and updates are then picked up by the
     * package-changed callback, which invalidates the cache directly.
     */
    private fun versionCodeOf(packageName: String): Long = runCatching {
        val info = appContext.packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)
}
