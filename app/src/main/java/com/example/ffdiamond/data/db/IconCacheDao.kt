package com.example.ffdiamond.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface IconCacheDao {

    @Query(
        "SELECT * FROM icon_cache WHERE component = :component AND user_serial = :userSerial LIMIT 1"
    )
    suspend fun find(component: String, userSerial: Long): IconCacheEntity?

    @Upsert
    suspend fun put(entity: IconCacheEntity)

    /**
     * Drops every activity of one package. Components are stored flattened as `package/class`,
     * so the package is a prefix match.
     */
    @Query("DELETE FROM icon_cache WHERE user_serial = :userSerial AND component LIKE :prefix")
    suspend fun deletePackage(prefix: String, userSerial: Long)

    @Query("DELETE FROM icon_cache")
    suspend fun clear()
}
