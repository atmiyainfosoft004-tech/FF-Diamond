package com.example.ffdiamond.ui.drag

import com.example.ffdiamond.ui.workspace.CellLayout

/**
 * What a drag needs from whatever is rendering the workspace.
 *
 * Narrow on purpose. The drag controller decides what a drop *means*; it should not also know that
 * the page at database index 0 is the workspace's second child because the first one is the content
 * panel, or how a new page comes into being. Keeping that behind an interface is also what lets the
 * controller be reasoned about without a workspace at all.
 */
interface WorkspaceHost {

    /** Grid pages only. The page -1 panel is not one of them. */
    fun pageCount(): Int

    fun pageAt(dbPage: Int): CellLayout?

    /** The page currently on screen, or -1 while the content panel is showing. */
    fun visiblePage(): Int

    fun goToPage(dbPage: Int)

    /**
     * A trailing empty page to drop onto, created if the last page already has something in it.
     * Returns its database index.
     */
    fun ensureTrailingEmptyPage(): Int

    /** Drops the page [ensureTrailingEmptyPage] added, if nothing was dropped on it after all. */
    fun discardTrailingEmptyPage()

    /**
     * Hides an item's view for the length of a drag. The drag view is carrying its likeness, and
     * two copies of the same icon on screen is the tell that a launcher is faking it.
     */
    fun setItemHidden(id: Long, hidden: Boolean)
}
