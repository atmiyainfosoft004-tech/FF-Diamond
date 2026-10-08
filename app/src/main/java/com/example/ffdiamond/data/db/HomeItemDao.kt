package com.example.ffdiamond.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {

    /**
     * The whole layout, as a flow. Small enough to hand over in one piece — a home screen is tens
     * of rows, not thousands — and reading it whole is what lets the repository reconcile it
     * against the installed apps in a single pass.
     */
    @Query("SELECT * FROM home_items")
    fun observeAll(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items")
    suspend fun all(): List<HomeItemEntity>

    @Query("SELECT COUNT(*) FROM home_items")
    suspend fun count(): Int

    @Insert
    suspend fun insert(items: List<HomeItemEntity>): List<Long>

    @Insert
    suspend fun insert(item: HomeItemEntity): Long

    @Update
    suspend fun update(items: List<HomeItemEntity>)

    @Delete
    suspend fun delete(items: List<HomeItemEntity>)

    @Query("DELETE FROM home_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        """
        UPDATE home_items
        SET container = :container, page = :page, cell_x = :cellX, cell_y = :cellY,
            folder_id = :folderId, rank = :rank
        WHERE id = :id
        """
    )
    suspend fun place(
        id: Long,
        container: String,
        page: Int,
        cellX: Int,
        cellY: Int,
        folderId: Long?,
        rank: Int
    )

    @Query("UPDATE home_items SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String?)

    @Query(
        """
        UPDATE home_items
        SET page = :page, cell_x = :cellX, cell_y = :cellY, span_x = :spanX, span_y = :spanY
        WHERE id = :id
        """
    )
    suspend fun placeSpan(
        id: Long,
        page: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int
    )

    @Query("SELECT * FROM home_items WHERE folder_id = :folderId AND container = 'FOLDER' ORDER BY rank")
    suspend fun membersOf(folderId: Long): List<HomeItemEntity>

    /**
     * Everything belonging to a folder: the folder's own row and every member row. Used when a
     * folder dissolves.
     */
    @Query("DELETE FROM home_items WHERE folder_id = :folderId")
    suspend fun deleteFolderRows(folderId: Long)

    /** One transaction so a rearrangement can never be half-written if the process dies. */
    @Transaction
    suspend fun applyMutations(
        inserted: List<HomeItemEntity>,
        updated: List<HomeItemEntity>,
        deletedIds: List<Long>
    ) {
        if (deletedIds.isNotEmpty()) deleteByIds(deletedIds)
        if (updated.isNotEmpty()) update(updated)
        if (inserted.isNotEmpty()) insert(inserted)
    }
}
