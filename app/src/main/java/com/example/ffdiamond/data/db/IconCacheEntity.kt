package com.example.ffdiamond.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * A rendered icon on disk, so a cold start can paint the grid without re-rasterising every app.
 *
 * [versionCode] is the invalidation key: an app that updates almost always changes its icon, and
 * comparing version codes is far cheaper than comparing bitmaps. Only the untinted icon is stored;
 * themed variants are cheap to derive and live in memory only, which keeps the table one row per
 * app rather than one row per app per theme.
 */
@Entity(tableName = "icon_cache", primaryKeys = ["component", "user_serial"])
data class IconCacheEntity(
    /** Flattened ComponentName, e.g. `com.example/.MainActivity`. */
    @ColumnInfo(name = "component") val component: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    @ColumnInfo(name = "version_code") val versionCode: Long,
    @ColumnInfo(name = "bitmap", typeAffinity = ColumnInfo.BLOB) val bitmap: ByteArray,
    @ColumnInfo(name = "label") val label: String
) {
    // Room data classes holding a ByteArray need these by hand; the generated ones compare the
    // array by reference, which would report two identical icons as different.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IconCacheEntity) return false
        return component == other.component &&
            userSerial == other.userSerial &&
            versionCode == other.versionCode &&
            label == other.label &&
            bitmap.contentEquals(other.bitmap)
    }

    override fun hashCode(): Int {
        var result = component.hashCode()
        result = 31 * result + userSerial.hashCode()
        result = 31 * result + versionCode.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + bitmap.contentHashCode()
        return result
    }
}
