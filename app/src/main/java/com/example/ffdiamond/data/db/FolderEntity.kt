package com.example.ffdiamond.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A folder's own properties. Where it sits and what is in it are both [HomeItemEntity] rows.
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    /** Seeds the folder plate's tint, so a folder keeps its colour across renames. */
    @ColumnInfo(name = "color_seed")
    val colorSeed: Int
)
