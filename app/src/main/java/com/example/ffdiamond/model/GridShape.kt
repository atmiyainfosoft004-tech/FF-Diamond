package com.example.ffdiamond.model

/**
 * How many cells a workspace page has. Separate from `DeviceProfile` because the layout repository
 * needs to reason about occupancy without knowing anything about pixels.
 */
data class GridShape(val columns: Int, val rows: Int) {
    val cellsPerPage: Int get() = columns * rows

    companion object {
        /**
         * The dock is a fixed 1 × 4 whatever the desktop grid is, per the spec. Letting it follow
         * the page columns is wrong twice over: a five wide dock holds the same four apps with a
         * hole on the right, and the extra slot would drift the icons off the width they balance.
         */
        const val DOCK_COLUMNS = 4
    }
}
