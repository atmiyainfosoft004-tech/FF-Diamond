package com.example.ffdiamond.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class InstallKind {
    GOOGLE,
    FACEBOOK,
    ORGANIC;

    val paid: Boolean get() = this == GOOGLE || this == FACEBOOK
}

/**
 * Splash pe Firestore + Play Install Referrer decide the first-run path.
 * Google Ads: every listed GCL ID prefix must appear in the referrer.
 * Control != 1 or Firestore fail → organic, no ads.
 */
object InstallSource {

    const val EXTRA_DEBUG_SOURCE = "debug_install_source"
    const val EXTRA_DEBUG_REFERRER = "debug_install_referrer"
    const val TIMEOUT_MS = 3_200L

    private const val PREFS = "install_source"
    private const val KEY_KIND = "kind"
    private const val KEY_REFERRER = "referrer"
    private const val TAG = "InstallSource"

    private val started = AtomicBoolean(false)
    private val referrerWaiters = CopyOnWriteArrayList<(String) -> Unit>()
    private val waiters = CopyOnWriteArrayList<(InstallKind) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var cached: InstallKind? = null
    @Volatile private var pendingReferrer: String? = null

    fun kind(context: Context): InstallKind? {
        cached?.let { return it }
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_KIND, null)
        val parsed = saved?.let { runCatching { InstallKind.valueOf(it) }.getOrNull() }
        cached = parsed
        return parsed
    }

    fun adsAllowed(context: Context): Boolean = kind(context)?.paid == true

    fun isOrganic(context: Context): Boolean = kind(context) == InstallKind.ORGANIC

    fun start(context: Context) {
        val app = context.applicationContext
        val existing = kind(app)
        if (existing != null) markResolved(existing)
        if (!started.compareAndSet(false, true)) return
        fetchReferrer(app) { referrer ->
            pendingReferrer = referrer
            val waiting = referrerWaiters.toList()
            referrerWaiters.clear()
            waiting.forEach { waiter -> runCatching { waiter(referrer) } }
        }
    }

    fun clearForDebug(context: Context) {
        if (!isDebuggable(context)) return
        synchronized(this) {
            cached = null
            pendingReferrer = null
            started.set(false)
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    suspend fun awaitReferrer(context: Context, timeoutMs: Long = TIMEOUT_MS): String {
        start(context)
        pendingReferrer?.let { return it }
        val arrived = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                pendingReferrer?.let {
                    cont.resume(it)
                    return@suspendCancellableCoroutine
                }
                val waiter: (String) -> Unit = { value ->
                    if (cont.isActive) cont.resume(value)
                }
                referrerWaiters.add(waiter)
                cont.invokeOnCancellation { referrerWaiters.remove(waiter) }
            }
        }
        return arrived ?: pendingReferrer.orEmpty()
    }

    suspend fun await(context: Context, timeoutMs: Long = TIMEOUT_MS): InstallKind {
        start(context)
        kind(context)?.let { return it }
        val arrived = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val already = kind(context)
                if (already != null) {
                    cont.resume(already)
                    return@suspendCancellableCoroutine
                }
                val waiter: (InstallKind) -> Unit = { value ->
                    if (cont.isActive) cont.resume(value)
                }
                waiters.add(waiter)
                cont.invokeOnCancellation { waiters.remove(waiter) }
            }
        }
        return arrived ?: applyResolved(context.applicationContext, InstallKind.ORGANIC, "", force = false)
    }

    /**
     * Debug-only fake Play referrer. Still goes through Firestore + gclid/fbclid match.
     * `debug_install_source google` does **not** skip fetch.
     */
    internal fun debugReferrer(context: Context, intent: Intent?): String? {
        if (!isDebuggable(context)) return null
        val extra = intent?.getStringExtra(EXTRA_DEBUG_REFERRER)?.trim()
        if (!extra.isNullOrEmpty()) return AdsReferrerCheck.normalizeDebugReferrer(extra)
        return AdsReferrerCheck.syntheticReferrer(intent?.getStringExtra(EXTRA_DEBUG_SOURCE))
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun applyResolved(
        context: Context,
        kind: InstallKind,
        referrer: String,
        force: Boolean = false
    ): InstallKind {
        synchronized(this) {
            val existing = cached ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_KIND, null)
                ?.let { runCatching { InstallKind.valueOf(it) }.getOrNull() }
            if (existing != null && !force) {
                cached = existing
                markResolved(existing)
                return existing
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_KIND, kind.name)
                .putString(KEY_REFERRER, referrer)
                .apply()
            cached = kind
        }
        Log.i(TAG, "resolved $kind referrer=$referrer")
        markResolved(kind)
        if (kind.paid) {
            // Set Default creates AdView in the same main-thread turn as this return.
            // GMA 23+ crashes if loadAd runs before initialize() is called.
            AdsSdk.start(context)
            handler.post { WebAds.warmup(context) }
        }
        return kind
    }

    private fun markResolved(kind: InstallKind) {
        val pending = waiters.toList()
        waiters.clear()
        pending.forEach { waiter -> runCatching { waiter(kind) } }
    }

    private fun fetchReferrer(context: Context, onDone: (String) -> Unit) {
        val client = InstallReferrerClient.newBuilder(context).build()
        val done = AtomicBoolean(false)
        lateinit var timeout: Runnable
        fun complete(value: String) {
            if (!done.compareAndSet(false, true)) return
            handler.removeCallbacks(timeout)
            runCatching { client.endConnection() }
            onDone(value)
        }
        timeout = Runnable { complete("") }
        handler.postDelayed(timeout, TIMEOUT_MS)
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val referrer = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull().orEmpty()
                    } else {
                        ""
                    }
                    complete(referrer)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    complete("")
                }
            })
        } catch (_: Exception) {
            complete("")
        }
    }
}

object InstallReferrerParser {

    fun parseKeys(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun parseUri(uri: Uri, gclKeys: List<String>): InstallKind? {
        val parsed = parse(uri.toString(), gclKeys)
        return parsed.takeIf { it.paid }
    }

    fun parse(referrer: String, gclKeys: List<String> = emptyList()): InstallKind {
        val decoded = decode(referrer)
        if (decoded.isBlank()) return InstallKind.ORGANIC
        val raw = decoded.lowercase()
        if (hasParam(raw, "fbclid")) return InstallKind.FACEBOOK
        if (matchesGclId(decoded, gclKeys)) return InstallKind.GOOGLE
        val source = param(raw, "utm_source")
        if (isFacebookSource(source)) return InstallKind.FACEBOOK
        return InstallKind.ORGANIC
    }

    fun matchesGclId(referrer: String, gclKeys: List<String>): Boolean {
        if (gclKeys.isEmpty()) return false
        val decoded = decode(referrer)
        return gclKeys.all { key -> decoded.contains(key) }
    }

    private fun isFacebookSource(source: String): Boolean {
        if (source.isBlank()) return false
        return source.contains("facebook") ||
            source.contains("instagram") ||
            source.contains("fb.me") ||
            source == "fb" ||
            source == "an" ||
            source.contains("apps.facebook")
    }

    private fun hasParam(raw: String, key: String): Boolean =
        Regex("""(?:^|[?&])$key=""").containsMatchIn(raw)

    private fun param(raw: String, key: String): String {
        val match = Regex("""(?:^|[?&])$key=([^&]*)""").find(raw) ?: return ""
        return decode(match.groupValues[1])
    }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
