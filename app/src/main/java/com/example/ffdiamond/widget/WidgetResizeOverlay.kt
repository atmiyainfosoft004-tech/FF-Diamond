package com.example.ffdiamond.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.ffdiamond.R
import com.example.ffdiamond.model.HomeItem
import com.example.ffdiamond.ui.drag.homeItem
import com.example.ffdiamond.ui.workspace.CellLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Drawn over a widget after a long press: an outline, edge handles that snap to cells, a Remove
 * chip, and a drag on the body that moves the widget to a free slot.
 */
class WidgetResizeOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class Edit(
        val item: HomeItem.Widget,
        val page: CellLayout,
        val dbPage: Int,
        val minSpanX: Int,
        val minSpanY: Int
    )

    var onCommit: ((itemId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Unit)? =
        null
    var onRemove: ((HomeItem.Widget) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 2f
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ContextCompat.getColor(context, R.color.accent)
    }

    private val frame = Rect()
    private val frameF = RectF()

    private val remove = TextView(context).apply {
        text = context.getString(R.string.drop_remove)
        setTextColor(ContextCompat.getColor(context, R.color.white))
        textSize = 13f
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setBackgroundResource(R.drawable.bg_drop_target)
        elevation = dp(8).toFloat()
        isVisible = false
        setOnClickListener {
            val current = edit ?: return@setOnClickListener
            onRemove?.invoke(current.item)
            dismiss(commit = false)
        }
    }

    private var edit: Edit? = null
    private var cellX = 0
    private var cellY = 0
    private var spanX = 1
    private var spanY = 1
    private var handle: Handle? = null
    private var moving = false
    private var grabCellX = 0
    private var grabCellY = 0
    private var grabSpanX = 1
    private var grabSpanY = 1
    private var downX = 0f
    private var downY = 0f
    private var draggingHandle = false

    init {
        setWillNotDraw(false)
        isVisible = false
        isClickable = true
        addView(remove, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    val isEditing: Boolean get() = isVisible && edit != null

    fun show(session: Edit) {
        edit = session
        cellX = session.item.cellX
        cellY = session.item.cellY
        spanX = session.item.spanX
        spanY = session.item.spanY
        isVisible = true
        remove.isVisible = true
        layoutFrame()
        post {
            if (isEditing) {
                layoutFrame()
                invalidate()
            }
        }
        invalidate()
    }

    fun dismiss(commit: Boolean = true) {
        val current = edit
        if (commit && current != null) {
            onCommit?.invoke(current.item.id, current.dbPage, cellX, cellY, spanX, spanY)
        }
        edit = null
        handle = null
        moving = false
        isVisible = false
        remove.isVisible = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isEditing) layoutFrame()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isEditing) layoutFrame()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!isEditing) return
        frameF.set(frame)
        val radius = dp(12).toFloat()
        canvas.drawRoundRect(frameF, radius, radius, outline)
        Handle.entries.forEach { h ->
            canvas.drawCircle(h.centerX(frame), h.centerY(frame), HANDLE_RADIUS * density, handleFill)
            canvas.drawCircle(h.centerX(frame), h.centerY(frame), HANDLE_RADIUS * density, handleStroke)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEditing) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            handle = handleAt(ev.x, ev.y)
            moving = handle == null && frame.contains(ev.x.roundToInt(), ev.y.roundToInt())
            if (handle != null || moving) {
                downX = ev.x
                downY = ev.y
                grabCellX = cellX
                grabCellY = cellY
                grabSpanX = spanX
                grabSpanY = spanY
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditing) return super.onTouchEvent(event)
        val session = edit ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (session.page.cellWidth == 0 || session.page.cellHeight == 0) return true
                if (handle != null) {
                    draggingHandle = true
                    resize(session, event.x, event.y)
                } else if (moving) {
                    move(session, event.x, event.y)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dragged = draggingHandle || moving
                handle = null
                moving = false
                draggingHandle = false
                if (event.actionMasked == MotionEvent.ACTION_UP && !dragged &&
                    !frame.contains(event.x.roundToInt(), event.y.roundToInt())
                ) {
                    dismiss(commit = true)
                }
            }
        }
        return true
    }

    private fun resize(session: Edit, x: Float, y: Float) {
        val page = session.page
        var nextX = grabCellX
        var nextY = grabCellY
        var nextSpanX = grabSpanX
        var nextSpanY = grabSpanY
        val origin = Rect()
        page.spanRect(grabCellX, grabCellY, grabSpanX, grabSpanY, origin)
        mapPageRect(page, origin)

        when (handle) {
            Handle.E -> {
                val cells = ((x - origin.left) / page.cellWidth).roundToInt()
                nextSpanX = cells.coerceIn(session.minSpanX, page.columns - grabCellX)
            }
            Handle.W -> {
                val right = grabCellX + grabSpanX
                val cells = ((origin.right - x) / page.cellWidth).roundToInt()
                nextSpanX = cells.coerceIn(session.minSpanX, right)
                nextX = right - nextSpanX
            }
            Handle.S -> {
                val cells = ((y - origin.top) / page.cellHeight).roundToInt()
                nextSpanY = cells.coerceIn(session.minSpanY, page.rows - grabCellY)
            }
            Handle.N -> {
                val bottom = grabCellY + grabSpanY
                val cells = ((origin.bottom - y) / page.cellHeight).roundToInt()
                nextSpanY = cells.coerceIn(session.minSpanY, bottom)
                nextY = bottom - nextSpanY
            }
            null -> return
        }

        if (!page.canPlace(session.item.id, nextX, nextY, nextSpanX, nextSpanY)) return
        cellX = nextX
        cellY = nextY
        spanX = nextSpanX
        spanY = nextSpanY
        applyLive(session)
    }

    private fun move(session: Edit, x: Float, y: Float) {
        val page = session.page
        val dx = ((x - downX) / page.cellWidth).roundToInt()
        val dy = ((y - downY) / page.cellHeight).roundToInt()
        val nextX = (grabCellX + dx).coerceIn(0, page.columns - spanX)
        val nextY = (grabCellY + dy).coerceIn(0, page.rows - spanY)
        if (!page.canPlace(session.item.id, nextX, nextY, spanX, spanY)) return
        cellX = nextX
        cellY = nextY
        applyLive(session)
    }

    private fun applyLive(session: Edit) {
        val target = findWidget(session.page, session.item.id) ?: return
        val params = target.layoutParams as? CellLayout.LayoutParams ?: return
        params.cellX = cellX
        params.cellY = cellY
        params.spanX = spanX
        params.spanY = spanY
        val hostView = (target as? WidgetFrameView)?.let { frameView ->
            (0 until frameView.childCount)
                .map { frameView.getChildAt(it) }
                .filterIsInstance<AppWidgetHostView>()
                .firstOrNull()
        }
        if (hostView != null && session.page.cellWidth > 0 && session.page.cellHeight > 0) {
            val d = density.coerceAtLeast(0.1f)
            val wDp = ((spanX * session.page.cellWidth) / d).roundToInt().coerceAtLeast(1)
            val hDp = ((spanY * session.page.cellHeight) / d).roundToInt().coerceAtLeast(1)
            hostView.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
        }
        session.page.requestLayout()
        layoutFrame()
        invalidate()
    }

    private fun layoutFrame() {
        val session = edit ?: return
        session.page.spanRect(cellX, cellY, spanX, spanY, frame)
        mapPageRect(session.page, frame)
        remove.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val w = remove.measuredWidth
        val h = remove.measuredHeight
        val minX = dp(16)
        val maxX = (width - w - dp(16)).coerceAtLeast(minX)
        val left = (frame.centerX() - w / 2).coerceIn(minX, maxX)
        val minTop = dp(36)
        val top = if (frame.top - h - dp(8) >= minTop) {
            frame.top - h - dp(8)
        } else if (frame.bottom + h + dp(8) <= height) {
            frame.bottom + dp(8)
        } else {
            minTop
        }
        val lp = remove.layoutParams as? MarginLayoutParams
        if (lp != null) {
            lp.leftMargin = left
            lp.topMargin = top
        }
        remove.layout(left, top, left + w, top + h)
    }

    private fun mapPageRect(page: CellLayout, rect: Rect) {
        val pageLoc = IntArray(2)
        val overlayLoc = IntArray(2)
        page.getLocationInWindow(pageLoc)
        getLocationInWindow(overlayLoc)
        rect.offset(pageLoc[0] - overlayLoc[0], pageLoc[1] - overlayLoc[1])
    }

    private fun handleAt(x: Float, y: Float): Handle? {
        val hit = HANDLE_RADIUS * density + dp(8)
        return Handle.entries.firstOrNull { h ->
            abs(x - h.centerX(frame)) <= hit && abs(y - h.centerY(frame)) <= hit
        }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private enum class Handle {
        N, E, S, W;

        fun centerX(frame: Rect): Float = when (this) {
            E -> frame.right.toFloat()
            W -> frame.left.toFloat()
            N, S -> frame.centerX().toFloat()
        }

        fun centerY(frame: Rect): Float = when (this) {
            N -> frame.top.toFloat()
            S -> frame.bottom.toFloat()
            E, W -> frame.centerY().toFloat()
        }
    }

    private companion object {
        const val HANDLE_RADIUS = 10f
    }
}

private fun findWidget(page: CellLayout, id: Long): View? {
    for (i in 0 until page.childCount) {
        val child = page.getChildAt(i)
        if (child.homeItem()?.id == id) return child
    }
    return null
}
