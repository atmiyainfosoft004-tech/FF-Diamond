package com.example.ffdiamond.home

import com.example.ffdiamond.model.GridShape

/**
 * The seeded home screen is sparse: a search bar on row 0, a Google folder on the row above the
 * dock, four shortcut apps on the last row, and everything else left in the drawer.
 */
object SparseHome {

    const val HOME_APP_COUNT = 4
    const val MIN_GRID_PAGES = 1

    fun appsRow(rows: Int): Int = (rows - 1).coerceAtLeast(0)

    fun folderRow(rows: Int): Int = (rows - 2).coerceAtLeast(0)

    fun homeAppSlots(grid: GridShape): Int =
        HOME_APP_COUNT.coerceAtMost(grid.columns * grid.rows)

    fun nextSlot(occupancy: Occupancy, grid: GridShape): Pair<Int, Int>? {
        val y = appsRow(grid.rows)
        for (x in 0 until grid.columns) {
            if (occupancy.isFree(0, x, y)) return x to y
        }
        return null
    }
}
