package com.example.ffdiamond.ui.workspace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.ffdiamond.ui.DeviceProfile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The row of markers under the workspace.
 *
 * Three kinds of marker, one per page, in page order: a list glyph for the content panel at page
 * -1, a house glyph for the main home page, and a dot for every page after that.
 *
 * It is driven by the workspace's fractional scroll position rather than by a settled page index,
 * so the active marker grows and fades as the finger moves. Each marker keeps a fixed slot and
 * expands in place: at the halfway point of a swipe both neighbouring dots are 11 dp wide, and
 * since their centres are 17 dp apart they still clear each other by 6 dp, which is why this looks
 * continuous without any of the markers ever colliding.
 */
class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pillRect = RectF()

    private var spacingPx = 0f
    private var dotSizePx = 0f
    private var activePillWidthPx = 0f
    private var glyphSizePx = 0f

    private var pageCount = 0
    private var position = 0f

    var onPageClick: ((Int) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    /**
     * The list and home glyphs only mean something on the workspace, where page -1 is a content
     * panel and page 1 is home. A folder's pages are just pages, so it turns them off and gets
     * dots throughout.
     */
    var showGlyphs: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    fun applyProfile(profile: DeviceProfile) {
        spacingPx = profile.indicatorSpacingPx
        dotSizePx = profile.indicatorDotSizePx
        activePillWidthPx = profile.indicatorActivePillWidthPx
        glyphSizePx = profile.indicatorGlyphSizePx
        strokePaint.strokeWidth = glyphSizePx * STROKE_RATIO
        isClickable = true
        requestLayout()
        invalidate()
    }

    fun setPageCount(count: Int) {
        if (pageCount == count) return
        pageCount = count
        requestLayout()
        invalidate()
    }

    /** [fractionalPage] is the workspace scroll in page units, e.g. 1.37 while swiping. */
    fun setPosition(fractionalPage: Float) {
        if (position == fractionalPage) return
        position = fractionalPage
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minTouch = (32f * resources.displayMetrics.density).roundToInt()
        val height = (glyphSizePx * 2f).roundToInt().coerceAtLeast(minTouch)
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (pageCount <= 0 || spacingPx <= 0f) return

        val centerY = height / 2f
        val firstX = width / 2f - (pageCount - 1) * spacingPx / 2f

        for (index in 0 until pageCount) {
            // 1 on the active marker, 0 once a full page away, linear in between — this is the
            // whole reason the indicator reads as continuous.
            val weight = (1f - abs(index - position)).coerceIn(0f, 1f)
            val centerX = firstX + index * spacingPx
            val color = lerpAlpha(weight)

            when {
                !showGlyphs -> drawDot(canvas, centerX, centerY, color, weight)
                index == PAGE_FEED -> drawListGlyph(canvas, centerX, centerY, color)
                index == PAGE_HOME -> drawHomeGlyph(canvas, centerX, centerY, color, weight)
                index == pageCount - 1 && pageCount > PAGE_HOME ->
                    drawDownloadGlyph(canvas, centerX, centerY, color)
                else -> drawDot(canvas, centerX, centerY, color, weight)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageCount <= 0 || spacingPx <= 0f) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val firstX = width / 2f - (pageCount - 1) * spacingPx / 2f
            val index = ((event.x - firstX) / spacingPx).roundToInt().coerceIn(0, pageCount - 1)
            val centerX = firstX + index * spacingPx
            if (abs(event.x - centerX) <= spacingPx * 0.75f) {
                onPageClick?.invoke(index)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawDot(canvas: Canvas, centerX: Float, centerY: Float, color: Int, weight: Float) {
        fillPaint.color = color
        val w = dotSizePx + (activePillWidthPx - dotSizePx) * weight
        val radius = dotSizePx / 2f
        pillRect.set(centerX - w / 2f, centerY - radius, centerX + w / 2f, centerY + radius)
        canvas.drawRoundRect(pillRect, radius, radius, fillPaint)
    }

    /** Three stacked bars: the marker for the content panel at page -1. */
    private fun drawListGlyph(canvas: Canvas, centerX: Float, centerY: Float, color: Int) {
        strokePaint.color = color
        val half = glyphSizePx / 2f
        val gap = glyphSizePx / 3f
        for (row in -1..1) {
            val y = centerY + row * gap
            canvas.drawLine(centerX - half, y, centerX + half, y, strokePaint)
        }
    }

    /** Downward arrow for the video-downloader page on the right of home. */
    private fun drawDownloadGlyph(canvas: Canvas, centerX: Float, centerY: Float, color: Int) {
        strokePaint.color = color
        val half = glyphSizePx / 2f
        canvas.drawLine(centerX, centerY - half, centerX, centerY + half * 0.35f, strokePaint)
        canvas.drawLine(centerX, centerY + half * 0.35f, centerX - half * 0.45f, centerY - half * 0.1f, strokePaint)
        canvas.drawLine(centerX, centerY + half * 0.35f, centerX + half * 0.45f, centerY - half * 0.1f, strokePaint)
        canvas.drawLine(centerX - half, centerY + half, centerX + half, centerY + half, strokePaint)
    }

    /**
     * A house outline that fills in as it becomes active, matching the reference where page 1's
     * marker is a solid home glyph rather than a pill.
     */
    private fun drawHomeGlyph(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        color: Int,
        weight: Float
    ) {
        val half = glyphSizePx / 2f
        val roofY = centerY - half
        val eavesY = centerY - half * 0.1f
        val floorY = centerY + half * 0.75f

        strokePaint.color = color
        // Roof.
        canvas.drawLine(centerX - half, eavesY, centerX, roofY, strokePaint)
        canvas.drawLine(centerX, roofY, centerX + half, eavesY, strokePaint)
        // Walls and floor.
        val bodyHalf = half * 0.72f
        canvas.drawLine(centerX - bodyHalf, eavesY, centerX - bodyHalf, floorY, strokePaint)
        canvas.drawLine(centerX + bodyHalf, eavesY, centerX + bodyHalf, floorY, strokePaint)
        canvas.drawLine(centerX - bodyHalf, floorY, centerX + bodyHalf, floorY, strokePaint)

        // Fades in from nothing rather than from the inactive alpha, so an unselected home glyph is
        // a clean outline and the selected one reads as solid.
        if (weight > 0f) {
            fillPaint.color = Color.argb((ACTIVE_ALPHA * weight).roundToInt(), 255, 255, 255)
            pillRect.set(centerX - bodyHalf, eavesY, centerX + bodyHalf, floorY)
            canvas.drawRect(pillRect, fillPaint)
        }
    }

    private fun lerpAlpha(weight: Float): Int {
        val alpha = (INACTIVE_ALPHA + (ACTIVE_ALPHA - INACTIVE_ALPHA) * weight).roundToInt()
        return Color.argb(alpha, 255, 255, 255)
    }

    private companion object {
        const val PAGE_FEED = 0
        const val PAGE_HOME = 1
        const val ACTIVE_ALPHA = 255

        /** 40% white for a page you are not on, straight from the tokens. */
        const val INACTIVE_ALPHA = 102
        const val STROKE_RATIO = 0.14f
    }
}
