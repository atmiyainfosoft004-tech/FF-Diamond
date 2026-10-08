package com.example.ffdiamond.service

import android.service.notification.NotificationListenerService

/**
 * Declared in Phase 1 purely so the app shows up in Settings > Notification access and the
 * optional "Notification badges" toggle is something the user can actually grant. Badge counting
 * is wired into the icon pipeline in a later phase; until then this listens and does nothing.
 */
class BadgeNotificationListenerService : NotificationListenerService()
