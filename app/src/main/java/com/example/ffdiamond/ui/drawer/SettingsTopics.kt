package com.example.ffdiamond.ui.drawer

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.example.ffdiamond.R

/**
 * A small, static list of Settings screens. `SearchIndexablesProvider` is not a public API, so
 * search cannot query the real index; these topics are the substitute the spec asks for.
 */
data class SettingsTopic(
    val title: String,
    val intent: Intent
)

object SettingsTopics {

    fun all(context: Context): List<SettingsTopic> = listOf(
        topic(context, R.string.settings_topic_wifi, Settings.ACTION_WIFI_SETTINGS),
        topic(context, R.string.settings_topic_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
        topic(context, R.string.settings_topic_display, Settings.ACTION_DISPLAY_SETTINGS),
        topic(context, R.string.settings_topic_sound, Settings.ACTION_SOUND_SETTINGS),
        topic(context, R.string.settings_topic_notifications, "android.settings.NOTIFICATION_SETTINGS"),
        topic(context, R.string.settings_topic_apps, Settings.ACTION_APPLICATION_SETTINGS),
        topic(context, R.string.settings_topic_battery, Settings.ACTION_BATTERY_SAVER_SETTINGS),
        topic(context, R.string.settings_topic_wallpaper, Intent.ACTION_SET_WALLPAPER),
        topic(context, R.string.settings_topic_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
        topic(context, R.string.settings_topic_security, Settings.ACTION_SECURITY_SETTINGS),
        topic(context, R.string.settings_topic_about, Settings.ACTION_DEVICE_INFO_SETTINGS)
    )

    fun matching(context: Context, query: String): List<SettingsTopic> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return all(context).filter { it.title.contains(needle, ignoreCase = true) }
    }

    fun open(context: Context, topic: SettingsTopic) {
        val launched = runCatching {
            context.startActivity(topic.intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            Toast.makeText(context, R.string.error_no_settings_screen, Toast.LENGTH_SHORT).show()
        }
    }

    private fun topic(context: Context, title: Int, action: String) = SettingsTopic(
        title = context.getString(title),
        intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
