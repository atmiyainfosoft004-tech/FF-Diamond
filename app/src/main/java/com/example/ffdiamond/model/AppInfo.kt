package com.example.ffdiamond.model

import android.content.ComponentName
import android.os.UserHandle

/**
 * One launchable activity, for one user profile.
 *
 * The user profile is part of the identity, not a detail: the same component can exist in both the
 * personal and the work profile, and the two are different apps as far as the launcher is
 * concerned. [userSerial] is the stable, persistable form of [user] — a UserHandle cannot be
 * written to a database.
 */
data class AppInfo(
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
    val versionCode: Long,
    val isSystemApp: Boolean
) {
    /** Stable identity for cache keys, view tags and diffing. */
    val key: String = "${component.flattenToShortString()}|$userSerial"

    val packageName: String get() = component.packageName
}
