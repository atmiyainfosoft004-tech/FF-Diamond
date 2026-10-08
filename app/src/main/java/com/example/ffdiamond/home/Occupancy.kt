package com.example.ffdiamond.home

import com.example.ffdiamond.model.GridShape

/**
 * Which cells are taken, across as many pages as the layout needs.
 *
 * Used for both seeding and reconciliation, which is the point: an app being placed for the first
 * time and an app arriving because it was just installed should land in exactly the same cell, and
 * they do because both go through [firstFree].
 */
class Occupancy(private val grid: GridShape) {

    private val pages = mutableListOf<Array<BooleanArray>>()

    val pageCount: Int get() = pages.size

    fun ensurePage(index: Int) {
        while (pages.size <= index) {
            pages.add(Array(grid.rows) { BooleanArray(grid.columns) })
        }
    }

    fun inBounds(cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1): Boolean =
        cellX >= 0 && cellY >= 0 &&
            cellX + spanX <= grid.columns &&
            cellY + spanY <= grid.rows

    fun isFree(page: Int, cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1): Boolean {
        if (page < 0 || !inBounds(cellX, cellY, spanX, spanY)) return false
        ensurePage(page)
        val cells = pages[page]
        for (y in cellY until cellY + spanY) {
            for (x in cellX until cellX + spanX) {
                if (cells[y][x]) return false
            }
        }
        return true
    }

    fun occupy(page: Int, cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1) {
        ensurePage(page)
        val cells = pages[page]
        for (y in cellY until minOf(cellY + spanY, grid.rows)) {
            for (x in cellX until minOf(cellX + spanX, grid.columns)) {
                cells[y][x] = true
            }
        }
    }

    /**
     * The first free slot in reading order, opening a new page if every existing one is full.
     * Marks the slot taken before returning it, so callers can place items in a simple loop.
     */
    fun firstFree(spanX: Int = 1, spanY: Int = 1): Slot {
        ensurePage(0)
        var page = 0
        while (true) {
            ensurePage(page)
            for (y in 0..grid.rows - spanY) {
                for (x in 0..grid.columns - spanX) {
                    if (isFree(page, x, y, spanX, spanY)) {
                        occupy(page, x, y, spanX, spanY)
                        return Slot(page, x, y)
                    }
                }
            }
            page++
        }
    }

    data class Slot(val page: Int, val cellX: Int, val cellY: Int)
}
