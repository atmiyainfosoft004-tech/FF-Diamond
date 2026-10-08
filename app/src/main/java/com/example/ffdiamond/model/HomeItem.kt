package com.example.ffdiamond.model

/** Where an item lives. Folder members carry the folder's id alongside this. */
enum class Container { DESKTOP, DOCK, FOLDER, HIDDEN }

enum class HomeItemType { APP, FOLDER, WIDGET }

/**
 * A row of the layout table with its app resolved.
 *
 * The database stores a component name; the workspace needs a label, an icon and a UserHandle.
 * This is the joined form, and it is what the UI is given — a view should never have to ask
 * whether the app behind a cell still exists, because an item that could not be resolved is
 * removed during reconciliation rather than handed over as a hole.
 */
sealed interface HomeItem {
    val id: Long
    val container: Container
    val page: Int
    val cellX: Int
    val cellY: Int
    val spanX: Int
    val spanY: Int

    data class App(
        override val id: Long,
        override val container: Container,
        override val page: Int,
        override val cellX: Int,
        override val cellY: Int,
        override val spanX: Int = 1,
        override val spanY: Int = 1,
        val info: AppInfo,
        /** The user's rename, or the app's own label when they have not renamed it. */
        val title: String,
        val rank: Int = 0
    ) : HomeItem

    data class Folder(
        override val id: Long,
        override val container: Container,
        override val page: Int,
        override val cellX: Int,
        override val cellY: Int,
        override val spanX: Int = 1,
        override val spanY: Int = 1,
        val folderId: Long,
        val title: String,
        val colorSeed: Int,
        val members: List<App>
    ) : HomeItem

    data class Widget(
        override val id: Long,
        override val container: Container,
        override val page: Int,
        override val cellX: Int,
        override val cellY: Int,
        override val spanX: Int,
        override val spanY: Int,
        val appWidgetId: Int,
        val provider: String,
        val title: String
    ) : HomeItem
}

/**
 * The layout in the shape the workspace consumes it: already split by page, dock separate.
 *
 * [pages] is never empty — an empty home screen is still one page.
 */
data class HomeLayout(
    val pages: List<List<HomeItem>>,
    val dock: List<HomeItem>
) {
    val pageCount: Int get() = pages.size

    fun folder(folderId: Long): HomeItem.Folder? =
        (pages.flatten() + dock).filterIsInstance<HomeItem.Folder>()
            .firstOrNull { it.folderId == folderId }

    fun apps(): List<HomeItem.App> = (pages.flatten() + dock).flatMap { item ->
        when (item) {
            is HomeItem.App -> listOf(item)
            is HomeItem.Folder -> item.members
            is HomeItem.Widget -> emptyList()
        }
    }

    companion object {
        val EMPTY = HomeLayout(pages = listOf(emptyList()), dock = emptyList())
    }
}
