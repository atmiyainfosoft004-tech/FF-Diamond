package com.example.ffdiamond.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import com.example.ffdiamond.model.AppInfo

/**
 * Picks the apps a brand-new home screen starts with.
 *
 * The dock is chosen by *role* rather than by name: whichever app actually answers a dial intent
 * is the phone app, whatever the manufacturer called it and whichever language the device is in.
 * Anything that cannot be resolved falls back to the front of the alphabetical list, so the dock is
 * never short even on a stripped-down device or an emulator image with no dialer.
 */
object DefaultLayout {

    /**
     * Dock order: default Phone, Messages, this launcher, Camera.
     */
    fun dockApps(context: Context, apps: List<AppInfo>, slots: Int): List<AppInfo> {
        if (apps.isEmpty() || slots <= 0) return emptyList()

        val byPackage = apps.groupBy { it.packageName }
        val chosen = LinkedHashSet<AppInfo>()

        fun addPackage(packageName: String?) {
            if (chosen.size >= slots || packageName.isNullOrBlank()) return
            byPackage[packageName]?.firstOrNull()?.let(chosen::add)
        }

        addPackage(resolvePackage(context, Intent(Intent.ACTION_DIAL)))
        addPackage(
            resolvePackage(
                context,
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            )
        )
        apps.firstOrNull { it.packageName == context.packageName }?.let(chosen::add)
        addPackage(resolvePackage(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))

        if (chosen.size < slots) {
            apps.asSequence()
                .filterNot { it in chosen }
                .take(slots - chosen.size)
                .forEach(chosen::add)
        }

        return chosen.take(slots)
    }

    /** Apps that sit on the last home row, after the dock and the Google folder have taken theirs. */
    fun homeShortcuts(
        context: Context,
        apps: List<AppInfo>,
        dock: List<AppInfo>,
        google: List<AppInfo>,
        slots: Int
    ): List<AppInfo> {
        if (apps.isEmpty() || slots <= 0) return emptyList()
        val taken = (dock + google).mapTo(HashSet()) { it.key }
        val chosen = LinkedHashSet<AppInfo>()
        val byPackage = apps.groupBy { it.packageName }

        for (packageName in HOME_PREFERRED_PACKAGES) {
            if (chosen.size == slots) break
            byPackage[packageName]
                ?.firstOrNull { it.key !in taken }
                ?.let(chosen::add)
        }

        if (chosen.size < slots) {
            val settings = resolvePackage(context, Intent(Settings.ACTION_SETTINGS))
            byPackage[settings]
                ?.firstOrNull { it.key !in taken && it !in chosen }
                ?.let(chosen::add)
        }

        if (chosen.size < slots) {
            apps.asSequence()
                .filterNot { it.key in taken || it in chosen || GoogleProducts.isGoogleProduct(it.packageName) }
                .take(slots - chosen.size)
                .forEach(chosen::add)
        }

        return chosen.take(slots)
    }

    /** Installed Google products that belong in the Google folder, minus dock duplicates. */
    fun googleProducts(apps: List<AppInfo>, dock: List<AppInfo>): List<AppInfo> {
        val dockKeys = dock.mapTo(HashSet()) { it.key }
        return apps.filter { app ->
            app.key !in dockKeys && GoogleProducts.isGoogleProduct(app.packageName)
        }
    }

    private fun resolvePackage(context: Context, intent: Intent): String? {
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return null

        val packageName = resolved.activityInfo?.packageName ?: return null
        return packageName.takeUnless { it == ANDROID_RESOLVER_PACKAGE }
    }

    private val HOME_PREFERRED_PACKAGES = listOf(
        "com.android.settings",
        "com.whatsapp",
        "com.instagram.android",
        "org.telegram.messenger",
        "com.facebook.katana",
        "com.spotify.music",
        "com.sonyericsson.album",
        "com.sonymobile.album",
        "com.sec.android.gallery3d",
        "com.android.gallery3d",
        "com.android.deskclock",
        "com.samsung.android.oneconnect"
    )

    private const val ANDROID_RESOLVER_PACKAGE = "android"
}
