package com.example.ffdiamond.widget

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import com.example.ffdiamond.R

/** Opens Google Search / Voice / Lens from the home search bar. */
object GoogleIntents {

    private const val GOOGLE = "com.google.android.googlequicksearchbox"

    fun openSearch(context: Context) {
        val launched = start(
            context,
            Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE)
                .putExtra(SearchManager.QUERY, ""),
            Intent("${GOOGLE}.GOOGLE_SEARCH").putExtra(SearchManager.QUERY, ""),
            context.packageManager.getLaunchIntentForPackage(GOOGLE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
        )
        if (!launched) {
            Toast.makeText(context, R.string.error_no_web_search, Toast.LENGTH_SHORT).show()
        }
    }

    fun directSearch(context: Context, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val encoded = Uri.encode(trimmed)
        val launched = start(
            context,
            Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE)
                .putExtra(SearchManager.QUERY, trimmed),
            Intent("${GOOGLE}.GOOGLE_SEARCH").putExtra(SearchManager.QUERY, trimmed),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")),
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, trimmed)
        )
        if (!launched) {
            Toast.makeText(context, R.string.error_no_web_search, Toast.LENGTH_SHORT).show()
        }
        return launched
    }

    fun voiceSearch(context: Context) {
        val launched = start(
            context,
            Intent(RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE),
            Intent(RecognizerIntent.ACTION_WEB_SEARCH),
            Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE)
                .putExtra(SearchManager.QUERY, "")
        )
        if (!launched) openSearch(context)
    }

    fun lens(context: Context) {
        val launched = start(
            context,
            Intent(Intent.ACTION_VIEW).setPackage(GOOGLE).setData(Uri.parse("google://lens")),
            Intent("com.google.android.googlequicksearchbox.LENS_ACTIVITY"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com"))
        )
        if (!launched) openSearch(context)
    }

    private fun start(context: Context, vararg intents: Intent?): Boolean {
        intents.filterNotNull().forEach { raw ->
            val intent = Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
