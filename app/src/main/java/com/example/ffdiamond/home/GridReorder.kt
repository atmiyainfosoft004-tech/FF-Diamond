package com.example.ffdiamond.home

/**
 * Where everything on a page ends up when an icon is dropped into an occupied cell.
 *
 * Kept as arithmetic over reading-order indices, with no views and no database, for two reasons.
 * The drag preview and the write that follows it have to agree exactly — if the icons slide one way
 * during the drag and the committed rows say something else, the grid visibly resettles on release
 * — so both ask this the same question and get the same answer. And a rule about where icons go is
 * the kind of thing that is much easier to be sure of from a test than from a screenshot.
 */
object GridReorder {

    /**
     * [moves] is only the items that have to shift, keyed by item id and valued by their new
     * reading-order index. The dragged item itself is not in it; it goes to [targetIndex].
     */
    data class Plan(val targetIndex: Int, val moves: Map<Long, Int>)

    /**
     * Plans a drop at [targetIndex], or returns null when the page has no room for one more.
     *
     * An occupied target does not push the whole row along. The nearest gap in either direction is
     * found first, and only the run of icons between the target and that gap moves, each by one
     * cell. That is what makes the reflow legible: the user sees a few icons step aside to open a
     * space, rather than the entire page reshuffling because of one drop near the top.
     *
     * @param occupants reading-order index to item id, *excluding* the item being dragged.
     */
    fun plan(
        occupants: Map<Int, Long>,
        capacity: Int,
        targetIndex: Int,
        blocked: Set<Int> = emptySet()
    ): Plan? {
        if (targetIndex < 0 || targetIndex >= capacity) return null
        if (targetIndex in blocked) return null
        if (targetIndex !in occupants) return Plan(targetIndex, emptyMap())

        val forward = (targetIndex + 1 until capacity).firstOrNull { candidate ->
            candidate !in occupants &&
                candidate !in blocked &&
                (targetIndex + 1 until candidate).none { it in blocked }
        }
        val backward = (targetIndex - 1 downTo 0).firstOrNull { candidate ->
            candidate !in occupants &&
                candidate !in blocked &&
                (candidate + 1 until targetIndex).none { it in blocked }
        }

        // Ties go forward, which reads as "the icons after this one step right" — the direction the
        // grid is written in, and the one the user is more likely to be predicting.
        val useForward = when {
            forward == null && backward == null -> return null
            forward == null -> false
            backward == null -> true
            else -> forward - targetIndex <= targetIndex - backward
        }

        val moves = HashMap<Long, Int>()
        if (useForward) {
            // Walked from the gap back towards the target so each item reads its neighbour's old
            // index, which is also the order the staggered animation should play in.
            for (index in forward!! - 1 downTo targetIndex) {
                occupants[index]?.let { moves[it] = index + 1 }
            }
        } else {
            for (index in backward!! + 1..targetIndex) {
                occupants[index]?.let { moves[it] = index - 1 }
            }
        }
        return Plan(targetIndex, moves)
    }

    fun indexOf(cellX: Int, cellY: Int, columns: Int): Int = cellY * columns + cellX

    fun cellOf(index: Int, columns: Int): Pair<Int, Int> = index % columns to index / columns
}
