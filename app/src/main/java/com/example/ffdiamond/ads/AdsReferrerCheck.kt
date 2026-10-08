package com.example.ffdiamond.ads

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Splash loading: Firestore `ads_config` pehle. Fetch ke baad hi Play referrer
 * me gclid / fbclid check. Match + control=1 → paid. Fetch fail → is session organic.
 */
object AdsReferrerCheck {

    private const val TAG = "AdsReferrerCheck"
    private const val FIRESTORE_TIMEOUT_MS = 12_000L

    var adsOn = false
        private set

    suspend fun resolve(context: Context, intent: Intent?): InstallKind {
        val app = context.applicationContext
        val debugReferrer = InstallSource.debugReferrer(app, intent)
        if (debugReferrer != null) InstallSource.clearForDebug(app)
        InstallSource.start(app)

        val existing = if (debugReferrer == null) InstallSource.kind(app) else null

        val snap = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) { AdsFirestore.fetch() }
        if (snap != null) {
            AdsRepository.applyFirestore(app, snap)
        } else if (existing == null) {
            adsOn = false
            Log.i(TAG, "firestore miss — no paid until ads_config loads")
            return InstallSource.applyResolved(app, InstallKind.ORGANIC, "", force = true)
        }

        val config = AdsRepository.config(app)
        if (!controlOn(config.installReferrerControl)) {
            adsOn = false
            Log.i(TAG, "ads off control=${config.installReferrerControl}")
            return InstallSource.applyResolved(app, InstallKind.ORGANIC, "", force = true)
        }

        if (existing != null) {
            adsOn = existing.paid
            Log.i(TAG, "reusing existing kind=$existing adsOn=$adsOn")
            if (existing.paid) {
                AdsSdk.start(app)
            }
            return existing
        }

        val referrer = debugReferrer ?: InstallSource.awaitReferrer(app)
        val kind = kindAfterFetch(config.installReferrerControl, referrer, config.gclId)
        adsOn = kind.paid
        Log.i(TAG, "adsOn=$adsOn kind=$kind gcl=${config.gclId} referrer=$referrer")
        return InstallSource.applyResolved(app, kind, referrer, force = true)
    }

    /** After Firestore is on disk: control, then fbclid / configured gclid prefixes. */
    internal fun kindAfterFetch(control: Int, referrer: String, gclId: String): InstallKind {
        if (!controlOn(control)) return InstallKind.ORGANIC
        return InstallReferrerParser.parse(referrer, InstallReferrerParser.parseKeys(gclId))
    }

    internal fun controlOn(control: Int): Boolean = control == 1

    internal fun controlOn(raw: String): Boolean = raw.toIntOrNull() == 1

    internal fun syntheticReferrer(source: String?): String? = when (source?.trim()?.lowercase()) {
        "google", "gclid" -> "gclid=debug&gbraid=debug"
        "facebook", "fb", "fbclid" -> "fbclid=debug"
        "organic" -> ""
        else -> null
    }

    /** adb/PowerShell `&` tod dete hain, isliye comma bhi `&` ki tarah. */
    internal fun normalizeDebugReferrer(raw: String): String =
        raw.replace(',', '&').trim()
}
