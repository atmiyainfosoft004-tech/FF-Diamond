package com.example.ffdiamond.funnel

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocale {

    const val DEFAULT = "en"

    @Volatile
    private var cachedCode: String? = null

    fun apply(code: String) {
        cache(code)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalize(code))
        )
    }

    /** Persist AppCompat locales without touching the language picker (that recreates + reloads ads). */
    fun commit() {
        val code = cachedCode ?: return
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val current = if (tags.isBlank()) {
            ""
        } else {
            normalize(tags.substringBefore(",").substringBefore("-"))
        }
        if (current == code) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    fun cache(code: String) {
        cachedCode = normalize(code)
    }

    fun cached(): String? = cachedCode

    fun hydrate(context: Context) {
        if (cachedCode != null) return
        val saved = FunnelPreferences.savedLanguageBlocking(context)
        if (saved.isNotBlank()) cachedCode = normalize(saved)
    }

    /**
     * Apply the cached language to this activity without [apply] / recreate.
     * Call from [Activity.attachBaseContext] via [wrap].
     */
    fun attach(activity: Activity, newBase: Context): Context = wrap(newBase)

    fun wrap(context: Context, code: String = cachedCode ?: DEFAULT): Context {
        val locale = localeFor(code)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun restore(context: Context) {
        val saved = FunnelPreferences.savedLanguageBlocking(context)
        if (saved.isBlank()) return
        if (isApplied(saved)) return
        apply(saved)
    }

    fun currentCode(): String {
        val fromCache = cachedCode
        if (!fromCache.isNullOrBlank()) return fromCache
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.isBlank()) return ""
        return normalize(tags.substringBefore(",").substringBefore("-"))
    }

    fun isApplied(code: String): Boolean {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.isBlank()) return false
        return normalize(tags.substringBefore(",").substringBefore("-")) == normalize(code)
    }

    fun indexOf(saved: String, codes: Array<String>): Int {
        val i = codes.indexOf(normalize(saved))
        return if (i >= 0) i else 0
    }

    fun normalize(saved: String): String {
        val key = saved.trim().lowercase()
        if (key.isEmpty()) return DEFAULT
        return when (key) {
            "en", "english", "gb", "uk", "us", "usa", "ca", "canada", "np", "nepal" -> "en"
            "hi", "hindi", "in", "india" -> "hi"
            "gu", "gujarati" -> "gu"
            "ur", "urdu" -> "ur"
            "fr", "french", "francais", "français" -> "fr"
            "ar", "arabic" -> "ar"
            "es", "spanish", "espanol", "español" -> "es"
            "bn", "bengali" -> "bn"
            else -> if (key.length in 2..5) key else DEFAULT
        }
    }

    private fun localeFor(code: String): Locale = Locale.forLanguageTag(normalize(code))
}
