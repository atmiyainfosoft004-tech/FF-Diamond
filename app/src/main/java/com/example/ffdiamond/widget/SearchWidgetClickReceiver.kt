package com.example.ffdiamond.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Home-search widget tap. A broadcast stays inside this app; a HOME activity intent would not. */
class SearchWidgetClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GoogleIntents.openSearch(context)
    }
}
