package com.example.ffdiamond.ui.drawer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ffdiamond.R
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.workspace.PageIndicatorView
import com.example.ffdiamond.ui.workspace.Workspace
import kotlin.math.roundToInt

/**
 * Positions the four fixed pieces of the open drawer: overflow, the 4 × 6 pager, page dots and
 * the search pill.
 *
 * Token Y values were measured on the 840 dp frame including a 30 dp status bar and a 42 dp nav
 * bar. Mapping them from the content box (below the real insets) keeps the pill and dots above
 * the navigation bar on every device, and the extra height on a taller phone goes to the grid.
 */
class DrawerChromeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val overflow: ImageButton by lazy { findViewById(R.id.drawer_overflow) }
    private val pager: Workspace by lazy { findViewById(R.id.drawer_pager) }
    private val indicator: PageIndicatorView by lazy { findViewById(R.id.drawer_indicator) }
    private val banner: FrameLayout by lazy { findViewById(R.id.drawer_ad_banner) }
    private val searchPill: LinearLayout by lazy { findViewById(R.id.drawer_search_pill) }

    private var profile: DeviceProfile? = null
    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    init {
        clipChildren = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (bars.left != insetLeft || bars.top != insetTop ||
                bars.right != insetRight || bars.bottom != insetBottom
            ) {
                insetLeft = bars.left
                insetTop = bars.top
                insetRight = bars.right
                insetBottom = bars.bottom
                requestLayout()
            }
            insets
        }
    }

    fun applyProfile(profile: DeviceProfile) {
        this.profile = profile
        indicator.applyProfile(profile)
        indicator.showGlyphs = false
        indicator.elevation = 4f
        searchPill.elevation = 4f
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val profile = profile

        overflow.measure(exactly(overflow.layoutParams.width), exactly(overflow.layoutParams.height))
        searchPill.measure(
            exactly(searchPill.layoutParams.width.let { if (it > 0) it else px(PILL_WIDTH_DP) }),
            exactly(searchPill.layoutParams.height.let { if (it > 0) it else px(PILL_HEIGHT_DP) })
        )
        indicator.measure(
            exactly(width - insetLeft - insetRight),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        banner.measure(
            exactly(width - insetLeft - insetRight),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val gridHeight = if (profile == null) {
            height / 2
        } else {
            gridBottom(height) - gridTop()
        }.coerceAtLeast(0)
        pager.measure(exactly(width - insetLeft - insetRight), exactly(gridHeight))

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = insetLeft
        val right = width - insetRight
        val contentBottom = height - insetBottom - bannerSpace()

        val overflowTop = insetTop + px(OVERFLOW_TOP_DP)
        overflow.layout(
            right - overflow.measuredWidth - px(OVERFLOW_END_DP),
            overflowTop,
            right - px(OVERFLOW_END_DP),
            overflowTop + overflow.measuredHeight
        )

        val gridTop = gridTop()
        pager.layout(left, gridTop, right, gridTop + pager.measuredHeight)

        val dotsCenter = contentBottom - px(DOTS_FROM_CONTENT_BOTTOM_DP)
        val dotsTop = dotsCenter - indicator.measuredHeight / 2
        indicator.layout(left, dotsTop, right, dotsTop + indicator.measuredHeight)

        val pillTop = contentBottom - px(PILL_FROM_CONTENT_BOTTOM_DP)
        val pillLeft = (width - searchPill.measuredWidth) / 2
        searchPill.layout(
            pillLeft,
            pillTop,
            pillLeft + searchPill.measuredWidth,
            pillTop + searchPill.measuredHeight
        )

        val bannerTop = height - insetBottom - banner.measuredHeight
        banner.layout(left, bannerTop, right, bannerTop + banner.measuredHeight)
    }

    private fun gridTop(): Int = insetTop + px(GRID_TOP_DP)

    private fun gridBottom(height: Int): Int {
        val contentBottom = height - insetBottom - bannerSpace()
        val dotsCenter = contentBottom - px(DOTS_FROM_CONTENT_BOTTOM_DP)
        return dotsCenter - indicator.measuredHeight / 2 - px(GRID_DOTS_GAP_DP)
    }

    private fun bannerSpace(): Int {
        if (banner.visibility == View.GONE) return 0
        return banner.measuredHeight.coerceAtLeast(0)
    }

    private fun px(dp: Float): Int {
        val scale = profile?.scale ?: 1f
        return (dp * scale * resources.displayMetrics.density).roundToInt()
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private companion object {
        /** Icon top of drawer row 1 (96) minus the 30 dp status bar. */
        const val GRID_TOP_DP = 66f
        const val OVERFLOW_TOP_DP = 14f
        const val OVERFLOW_END_DP = 13f
        const val PILL_WIDTH_DP = 216f
        const val PILL_HEIGHT_DP = 44f

        /** Token content bottom (798) minus pill top (745). */
        const val PILL_FROM_CONTENT_BOTTOM_DP = 53f

        /** Token content bottom (798) minus dots centre (703). */
        const val DOTS_FROM_CONTENT_BOTTOM_DP = 95f
        const val GRID_DOTS_GAP_DP = 8f
    }
}
