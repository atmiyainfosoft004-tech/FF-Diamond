package com.example.ffdiamond.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing sitting somewhere on the home screen.
 *
 * A single table holds apps, folders and (later) widgets rather than one table each, because the
 * launcher's questions are always positional — "what is on page 2?", "what is in the dock?" — and
 * those become joins the moment the rows are split up. [type] says which of the nullable columns
 * are meaningful.
 *
 * Items inside a folder are also rows here, with `container = FOLDER` and a [folderId]; their
 * [page], [cellX] and [cellY] are unused and [rank] gives their order instead.
 */
@Entity(
    tableName = "home_items",
    indices = [
        Index(value = ["container", "page"]),
        Index(value = ["folder_id"]),
        Index(value = ["component", "user_serial"])
    ]
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** One of [com.example.ffdiamond.model.HomeItemType]. */
    @ColumnInfo(name = "type")
    val type: String,

    /** Zero-based workspace page. Ignored for dock and folder members. */
    @ColumnInfo(name = "page")
    val page: Int = 0,

    @ColumnInfo(name = "cell_x")
    val cellX: Int = 0,

    @ColumnInfo(name = "cell_y")
    val cellY: Int = 0,

    @ColumnInfo(name = "span_x")
    val spanX: Int = 1,

    @ColumnInfo(name = "span_y")
    val spanY: Int = 1,

    /** One of [com.example.ffdiamond.model.Container]. */
    @ColumnInfo(name = "container")
    val container: String,

    /** Flattened ComponentName. Null for folders. Widget rows store the provider. */
    @ColumnInfo(name = "component")
    val component: String? = null,

    /** [android.appwidget.AppWidgetHost] id. Null for everything that is not a widget. */
    @ColumnInfo(name = "app_widget_id")
    val appWidgetId: Int? = null,

    @ColumnInfo(name = "user_serial")
    val userSerial: Long = 0,

    /** The folder this row *is* (for a FOLDER row) or the folder it lives in (for a member). */
    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,

    /** A title the user typed, overriding the app's own label. Null means "use the app's". */
    @ColumnInfo(name = "title")
    val title: String? = null,

    /** Order within a folder. Meaningless outside one. */
    @ColumnInfo(name = "rank")
    val rank: Int = 0
)
