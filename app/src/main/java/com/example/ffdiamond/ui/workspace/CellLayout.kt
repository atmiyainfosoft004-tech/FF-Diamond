package com.example.ffdiamond.ui.workspace

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import com.example.ffdiamond.ui.drag.homeItem
import com.example.ffdiamond.util.Motion
import kotlin.math.floor

/**
 * A fixed grid of cells that children occupy by coordinate, not by order.
 *
 * This is not a RecyclerView or a GridLayout, and it cannot be. A home screen is positional: an
 * icon in the third column of the second row stays there whether or not anything occupies the two
 * cells before it, gaps are meaningful, and drag-and-drop (phase 5) needs to ask "what is at cell
 * (2, 1)?" and "where would this drop land?". Both questions are trivial arithmetic here and
 * awkward-to-impossible in a layout that assigns positions from a linear adapter.
 *
 * Cell size is derived from the container's own measured size rather than a fixed dp, so the same
 * class serves the workspace pages (columns x rows) and the dock (columns x 1).
 */
class CellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var columns: Int = 4
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var rows: Int = 6
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var cellWidth: Int = 0
        private set

    var cellHeight: Int = 0
        private set

    /** Children currently displaced by a drop preview, and the index each is pretending to be in. */
    private val previewed = HashMap<View, Int>()

    init {
        // Labels in the bottom row hang a couple of pixels past their cell on tall screens, and the
        // press animation scales icons about their centre. Both would be cut off with clipping on.
        clipChildren = false
        clipToPadding = false
        isMotionEventSplittingEnabled = false
    }

    val capacity: Int get() = columns * rows

    /** Reading-order index of the cell a child occupies, matching `GridReorder`'s numbering. */
    fun indexOf(child: View): Int = (child.layoutParams as LayoutParams).let {
        it.cellY * columns + it.cellX
    }

    /** The rectangle covering [spanX] × [spanY] cells, in this layout's coordinates. */
    fun spanRect(cellX: Int, cellY: Int, spanX: Int, spanY: Int, out: Rect) {
        val left = paddingLeft + slackX() + cellX * cellWidth
        val top = paddingTop + slackY() + cellY * cellHeight
        out.set(
            left,
            top,
            left + cellWidth * spanX,
            top + cellHeight * spanY
        )
    }

    /** The cell rectangle, in this layout's own coordinates, ignoring any preview translation. */
    fun cellRect(cellX: Int, cellY: Int, out: Rect) = spanRect(cellX, cellY, 1, 1, out)

    /** True when [spanX] × [spanY] at [cellX], [cellY] is free, ignoring the item with [excludeId]. */
    fun canPlace(excludeId: Long, cellX: Int, cellY: Int, spanX: Int, spanY: Int): Boolean {
        if (cellX < 0 || cellY < 0 || cellX + spanX > columns || cellY + spanY > rows) return false
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue
            val item = child.homeItem()
            if (item != null && item.id == excludeId) continue
            val params = child.layoutParams as? LayoutParams ?: continue
            val overlap = cellX < params.cellX + params.spanX &&
                cellX + spanX > params.cellX &&
                cellY < params.cellY + params.spanY &&
                cellY + spanY > params.cellY
            if (overlap) return false
        }
        return true
    }

    /**
     * Slides children out of the way to show where a drop would land, without touching their real
     * cells.
     *
     * Preview is translation only, deliberately. The database is the layout, so the icons cannot
     * actually move until the drop is committed and read back — and doing it this way means the
     * commit is invisible: the rebind puts each view in the cell it is already sitting in and
     * clears the translation in the same frame, so nothing moves twice.
     *
     * @param moves view to the reading-order index it should appear to occupy.
     */
    fun applyReorderPreview(moves: Map<View, Int>) {
        previewed.keys.toList().forEach { if (it !in moves) clearPreview(it) }

        val reflow = Motion.duration(context, Motion.DURATION_CELL_REFLOW)
        val stagger = Motion.duration(context, Motion.DURATION_REFLOW_STAGGER)

        moves.entries.forEachIndexed { order, (child, targetIndex) ->
            if (previewed[child] == targetIndex) return@forEachIndexed
            previewed[child] = targetIndex

            val params = child.layoutParams as LayoutParams
            val dx = ((targetIndex % columns) - params.cellX) * cellWidth
            val dy = ((targetIndex / columns) - params.cellY) * cellHeight
            child.animate()
                .translationX(dx.toFloat())
                .translationY(dy.toFloat())
                .setDuration(reflow)
                .setStartDelay(order * stagger)
                .setInterpolator(Motion.STANDARD)
                .start()
        }
    }

    fun clearReorderPreview() {
        previewed.keys.toList().forEach { clearPreview(it) }
    }

    private fun clearPreview(child: View) {
        previewed.remove(child)
        child.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(Motion.duration(context, Motion.DURATION_CELL_REFLOW))
            .setStartDelay(0)
            .setInterpolator(Motion.STANDARD)
            .start()
    }

    /** The first free cell in reading order, or null when the page is full. */
    fun firstEmptyCell(): Pair<Int, Int>? {
        val occupied = Array(rows) { BooleanArray(columns) }
        forEachCellChild { _, params ->
            for (y in params.cellY until minOf(params.cellY + params.spanY, rows)) {
                for (x in params.cellX until minOf(params.cellX + params.spanX, columns)) {
                    occupied[y][x] = true
                }
            }
        }
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                if (!occupied[y][x]) return x to y
            }
        }
        return null
    }

    fun childAt(cellX: Int, cellY: Int): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as LayoutParams
            if (cellX >= params.cellX && cellX < params.cellX + params.spanX &&
                cellY >= params.cellY && cellY < params.cellY + params.spanY
            ) {
                return child
            }
        }
        return null
    }

    /** Maps a touch inside this layout to the cell under it, clamped to the grid. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        if (cellWidth == 0 || cellHeight == 0) return 0 to 0
        val cellX = floor((x - paddingLeft - slackX()) / cellWidth).toInt().coerceIn(0, columns - 1)
        val cellY = floor((y - paddingTop - slackY()) / cellHeight).toInt().coerceIn(0, rows - 1)
        return cellX to cellY
    }

    fun addInCell(child: View, cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1) {
        addView(child, LayoutParams(cellX, cellY, spanX, spanY))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom
        cellWidth = if (columns > 0) usableWidth / columns else 0
        cellHeight = if (rows > 0) usableHeight / rows else 0

        forEachCellChild { child, params ->
            child.measure(
                MeasureSpec.makeMeasureSpec(cellWidth * params.spanX, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellHeight * params.spanY, MeasureSpec.EXACTLY)
            )
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val slackX = slackX()
        val slackY = slackY()

        forEachCellChild { child, params ->
            val left = paddingLeft + slackX + params.cellX * cellWidth
            val top = paddingTop + slackY + params.cellY * cellHeight
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    // Integer division in onMeasure leaves up to (columns - 1) pixels unused; spreading the
    // remainder as extra outer padding keeps the grid optically centred instead of drifting left.
    private fun slackX() = (width - paddingLeft - paddingRight - cellWidth * columns) / 2

    private fun slackY() = (height - paddingTop - paddingBottom - cellHeight * rows) / 2

    private inline fun forEachCellChild(action: (View, LayoutParams) -> Unit) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue
            action(child, child.layoutParams as LayoutParams)
        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams =
        LayoutParams(0, 0)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams =
        if (p is LayoutParams) p else LayoutParams(0, 0)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    class LayoutParams(
        var cellX: Int,
        var cellY: Int,
        var spanX: Int = 1,
        var spanY: Int = 1
    ) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
}
