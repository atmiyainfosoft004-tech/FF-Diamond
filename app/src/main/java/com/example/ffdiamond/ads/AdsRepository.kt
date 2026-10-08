package com.example.ffdiamond.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

object AdsRepository {

    private const val PREFS = "ads_config"
    private const val KEY_JSON = "json"
    private const val TAG = "AdsRepository"

    @Volatile
    private var cached: AdsConfig? = null

    private val listeners = CopyOnWriteArrayList<(AdsConfig) -> Unit>()
    private val firestoreListen = AtomicReference<ListenerRegistration?>(null)

    fun config(context: Context): AdsConfig {
        cached?.let { return it }
        return load(context)
    }

    fun load(context: Context): AdsConfig {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JSON, null)?.takeIf { it.isNotBlank() } ?: "{}"
        val parsed = parseOrDefault(json)
        cached = parsed
        return parsed
    }

    fun start(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        load(app)
        scope.launch(Dispatchers.Main.immediate) {
            listenFirestore(app)
        }
    }

    fun addListener(listener: (AdsConfig) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (AdsConfig) -> Unit) {
        listeners.remove(listener)
    }

    fun applyFirestore(context: Context, snap: com.google.firebase.firestore.DocumentSnapshot) {
        val json = AdsFirestore.adsJson(snap)
        if (json == null) {
            Log.w(TAG, "ads_config empty keys=${snap.data?.keys}")
            return
        }
        applyJson(context.applicationContext, json)
    }

    fun needsForceUpdate(context: Context): Boolean {
        val min = config(context).minVersionCode
        if (min <= 0) return false
        return installedVersionCode(context) < min
    }

    fun openUpdate(context: Context) {
        val configured = config(context).updateUrl.trim()
        val https = configured.ifBlank {
            "https://play.google.com/store/apps/details?id=${context.packageName}"
        }
        val market = "market://details?id=${context.packageName}"
        val first = if (https.contains("play.google.com", ignoreCase = true)) market else https
        val fallback = if (first == market) https else market
        if (!launchView(context, first)) launchView(context, fallback)
    }

    private fun listenFirestore(context: Context) {
        firestoreListen.getAndSet(null)?.remove()
        val registration = AdsFirestore.document().addSnapshotListener { snap, error ->
            if (error != null) {
                Log.e(TAG, "Firestore listen failed", error)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                Log.w(TAG, "Firestore ${AdsFirestore.COLLECTION}/${AdsFirestore.DOCUMENT} missing")
                return@addSnapshotListener
            }
            applyFirestore(context, snap)
        }
        firestoreListen.set(registration)
    }

    private fun applyJson(context: Context, body: String) {
        val parsed = parseOrDefault(body)
        val previous = cached
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, body)
            .apply()
        cached = parsed
        AdsSdk.onRemoteConfig(context, previous, parsed)
        FacebookInstall.onConfig(context, parsed)
        listeners.forEach { listener -> runCatching { listener(parsed) } }
        Log.i(
            TAG,
            "applied ads_config flag=${parsed.adsFlag} google=${parsed.googleAds} " +
                "web=${parsed.webAds} links=${parsed.webLinks.size} count=${parsed.webAdsCount} " +
                "control=${parsed.installReferrerControl}"
        )
    }

    private fun parseOrDefault(json: String): AdsConfig =
        runCatching { AdsConfigParser.parse(json) }.getOrElse { AdsConfigParser.parse("{}") }

    fun installedVersionCode(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    private fun launchView(context: Context, uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
