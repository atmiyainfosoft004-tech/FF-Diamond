package com.example.ffdiamond.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun all(): List<FolderEntity>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
