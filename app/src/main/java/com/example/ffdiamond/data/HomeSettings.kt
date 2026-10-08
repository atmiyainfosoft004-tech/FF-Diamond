package com.example.ffdiamond.data

/** The parts of the home screen the user can change from the long-press sheet. */
data class HomeSettings(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    val showLabels: Boolean = true,
    val themedIcons: Boolean = false
) {
    companion object {
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6

        /** The three grid shapes offered in the sheet, as (columns, rows). */
        val GRID_OPTIONS = listOf(4 to 5, 4 to 6, 5 to 6)
    }
}
