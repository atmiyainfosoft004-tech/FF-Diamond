package com.example.ffdiamond.ui

import android.content.Context
import android.util.TypedValue
import com.example.ffdiamond.data.HomeSettings
import kotlin.math.roundToInt

/**
 * Turns the design tokens into pixels for the device actually running the app.
 *
 * Every token in `design/design-tokens.json` was measured on a 360 x 840 dp frame, so they are
 * treated as ratios of that baseline rather than absolute dp — a 412 dp phone gets proportionally
 * larger icons instead of the same icons with wider gaps.
 *
 * The scale deliberately comes from `smallestScreenWidthDp` rather than the current width: it must
 * not change on rotation or in multi-window, or every cached icon bitmap would be the wrong size.
 *
 * Vertical geometry works the other way round. The reference frame's fixed insets (a 30 dp status
 * bar and a 42 dp navigation bar) leave a 768 dp content box, and inside that box the grid sits
 * 64 dp from the top with 169 dp reserved below it for the page indicator and dock. Feeding the
 * device's real content height through the same reserves reproduces the reference row spacing of
 * 94 dp exactly on a 360 x 840 screen, and stretches or squeezes only the row height anywhere else.
 */
class DeviceProfile private constructor(
    val scale: Float,
    val columns: Int,
    val rows: Int,
    val iconSizePx: Int,
    val labelTextSizePx: Float,
    val labelBaselineGapPx: Int,
    val sidePaddingPx: Int,
    val gridTopPaddingPx: Int,
    val gridBottomReservePx: Int,
    val indicatorCenterFromBottomPx: Int,
    val dockHeightPx: Int,
    val indicatorSpacingPx: Float,
    val indicatorDotSizePx: Float,
    val indicatorActivePillWidthPx: Float,
    val indicatorGlyphSizePx: Float,
    private val referenceContentHeightPx: Float
) {

    /** Icons per page on the workspace. The dock is separate and keeps its own fixed width. */
    val cellsPerPage: Int get() = columns * rows

    /**
     * The four vertical reserves, adjusted for the height actually available.
     *
     * At or above the reference content height they are used as measured, and every extra pixel
     * goes to the rows, which is what should happen on a taller phone. Below it — split screen, a
     * freeform window, a small display — they shrink in proportion instead of holding their size
     * and starving the grid of the space it needs to draw anything at all.
     */
    fun verticalMetrics(contentHeightPx: Int): VerticalMetrics {
        val shrink = (contentHeightPx / referenceContentHeightPx).coerceIn(MIN_SHRINK, 1f)
        return VerticalMetrics(
            gridTopPaddingPx = (gridTopPaddingPx * shrink).roundToInt(),
            gridBottomReservePx = (gridBottomReservePx * shrink).roundToInt(),
            indicatorCenterFromBottomPx = (indicatorCenterFromBottomPx * shrink).roundToInt(),
            dockHeightPx = (dockHeightPx * shrink).roundToInt()
        )
    }

    data class VerticalMetrics(
        val gridTopPaddingPx: Int,
        val gridBottomReservePx: Int,
        val indicatorCenterFromBottomPx: Int,
        val dockHeightPx: Int
    )

    fun withGrid(columns: Int, rows: Int): DeviceProfile =
        if (columns == this.columns && rows == this.rows) {
            this
        } else {
            DeviceProfile(
                scale, columns, rows, iconSizePx, labelTextSizePx, labelBaselineGapPx,
                sidePaddingPx, gridTopPaddingPx, gridBottomReservePx,
                indicatorCenterFromBottomPx, dockHeightPx, indicatorSpacingPx,
                indicatorDotSizePx, indicatorActivePillWidthPx, indicatorGlyphSizePx,
                referenceContentHeightPx
            )
        }

    companion object {
        const val BASE_WIDTH_DP = 360f

        private const val ICON_SIZE_DP = 56f
        private const val LABEL_TEXT_SIZE_SP = 12f
        private const val LABEL_BASELINE_GAP_DP = 14f
        private const val SIDE_PADDING_DP = 13.5f

        private const val GRID_TOP_PADDING_DP = 64f
        private const val GRID_BOTTOM_RESERVE_DP = 169f
        private const val INDICATOR_CENTER_FROM_BOTTOM_DP = 129.5f

        /** The 840 dp frame less its 30 dp status bar and 42 dp navigation bar. */
        private const val REFERENCE_CONTENT_HEIGHT_DP = 768f

        /** Below this the reserves stop shrinking; the grid is unusable long before it anyway. */
        private const val MIN_SHRINK = 0.35f

        /** Twice the dock icon's 56 dp offset from the bottom, so the icon lands centred in it. */
        private const val DOCK_HEIGHT_DP = 112f

        private const val INDICATOR_SPACING_DP = 17f
        private const val INDICATOR_DOT_SIZE_DP = 6f
        private const val INDICATOR_ACTIVE_PILL_WIDTH_DP = 16f
        private const val INDICATOR_GLYPH_SIZE_DP = 13f

        fun from(context: Context, settings: HomeSettings): DeviceProfile {
            val resources = context.resources
            val metrics = resources.displayMetrics
            val scale = resources.configuration.smallestScreenWidthDp / BASE_WIDTH_DP

            fun px(dp: Float): Float = dp * scale * metrics.density

            return DeviceProfile(
                scale = scale,
                columns = settings.columns,
                rows = settings.rows,
                iconSizePx = px(ICON_SIZE_DP).roundToInt(),
                // applyDimension rather than scaledDensity, so the non-linear font scaling
                // introduced for large accessibility font sizes is respected.
                labelTextSizePx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    LABEL_TEXT_SIZE_SP * scale,
                    metrics
                ),
                labelBaselineGapPx = px(LABEL_BASELINE_GAP_DP).roundToInt(),
                sidePaddingPx = px(SIDE_PADDING_DP).roundToInt(),
                gridTopPaddingPx = px(GRID_TOP_PADDING_DP).roundToInt(),
                gridBottomReservePx = px(GRID_BOTTOM_RESERVE_DP).roundToInt(),
                indicatorCenterFromBottomPx = px(INDICATOR_CENTER_FROM_BOTTOM_DP).roundToInt(),
                dockHeightPx = px(DOCK_HEIGHT_DP).roundToInt(),
                indicatorSpacingPx = px(INDICATOR_SPACING_DP),
                indicatorDotSizePx = px(INDICATOR_DOT_SIZE_DP),
                indicatorActivePillWidthPx = px(INDICATOR_ACTIVE_PILL_WIDTH_DP),
                indicatorGlyphSizePx = px(INDICATOR_GLYPH_SIZE_DP),
                referenceContentHeightPx = px(REFERENCE_CONTENT_HEIGHT_DP)
            )
        }
    }
}
