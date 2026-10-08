package com.example.ffdiamond.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.ffdiamond.R

/**
 * The 4 × 1 search bar on page 1. Built here rather than copied from anyone's branded widget:
 * a pill, a magnifier, a hint, and a tap that opens this launcher's own search.
 */
class SearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { push(context, appWidgetManager, it) }
    }

    companion object {
        const val ACTION_OPEN_SEARCH = "com.example.ffdiamond.action.OPEN_SEARCH"
        const val EXTRA_OPEN_SEARCH = "open_search"

        fun component(context: Context): ComponentName =
            ComponentName(context, SearchWidgetProvider::class.java)

        fun isOurs(provider: String, context: Context): Boolean =
            provider == component(context).flattenToShortString() ||
                provider == component(context).flattenToString()

        fun push(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_search)
            val click = Intent(context, SearchWidgetClickReceiver::class.java).apply {
                action = ACTION_OPEN_SEARCH
                putExtra(EXTRA_OPEN_SEARCH, true)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
            val pending = PendingIntent.getBroadcast(context, appWidgetId, click, flags)
            views.setOnClickPendingIntent(R.id.search_widget_root, pending)
            views.setOnClickPendingIntent(R.id.search_widget_pill, pending)
            views.setOnClickPendingIntent(R.id.search_widget_logo, pending)
            views.setOnClickPendingIntent(R.id.search_widget_hint, pending)
            views.setOnClickPendingIntent(R.id.search_widget_mic, pending)
            views.setOnClickPendingIntent(R.id.search_widget_lens, pending)
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
