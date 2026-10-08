package com.example.ffdiamond.ui.workspace

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ffdiamond.R
import com.example.ffdiamond.ui.DeviceProfile

/**
 * Positions the three fixed pieces of the home screen: the paged workspace, the page indicator and
 * the dock.
 *
 * A FrameLayout with margins would have done the job only if the margins were known at inflation
 * time, and they are not — every offset is measured from the bottom of the content box, which
 * depends on the navigation bar inset the window reports at runtime. Doing the arithmetic in
 * onLayout means insets, rotation and grid changes all resolve through the same path.
 */
class HomeRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private lateinit var profile: DeviceProfile
    private var metrics: DeviceProfile.VerticalMetrics? = null

    private val workspace: Workspace by lazy { findViewById(R.id.workspace) }
    private val indicator: PageIndicatorView by lazy { findViewById(R.id.page_indicator) }
    private val dock: CellLayout by lazy { findViewById(R.id.dock) }

    private var insetLeft = 0
    private var insetTop = 0
    private var insetRight = 0
    private var insetBottom = 0

    init {
        clipChildren = false
        // The wallpaper shows through, so this view never paints; skipping it saves a full-screen
        // overdraw on every frame of a scroll.
        setWillNotDraw(true)

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
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (!::profile.isInitialized) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(width, height)
            return
        }

        val contentWidth = width - insetLeft - insetRight
        val contentHeight = height - insetTop - insetBottom

        val metrics = profile.verticalMetrics(contentHeight).also { this.metrics = it }

        // Full-window pager so Discover can paint edge-to-edge. Grid pages keep their icons in the
        // original band via padding; the dock and indicator sit on top of that same band.
        workspace.measure(exactly(width), exactly(height))
        dock.measure(exactly(contentWidth), exactly(metrics.dockHeightPx))
        indicator.measure(
            exactly(contentWidth),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val metrics = metrics ?: return

        val left = insetLeft
        val right = width - insetRight
        val contentBottom = height - insetBottom

        workspace.layout(0, 0, width, height)

        // The dock is anchored to the bottom of the content box and its height is exactly twice the
        // dock icon's offset from that edge, which lands the icon on the token's centre line.
        dock.layout(left, contentBottom - metrics.dockHeightPx, right, contentBottom)

        val indicatorCenter = contentBottom - metrics.indicatorCenterFromBottomPx
        val indicatorTop = indicatorCenter - indicator.measuredHeight / 2
        indicator.layout(left, indicatorTop, right, indicatorTop + indicator.measuredHeight)

        padGridPages(metrics)

        // Full-screen workspace would otherwise paint over the dock. Z is set every layout so a
        // later child reorder cannot bury the chrome again.
        val chromeZ = 16f * resources.displayMetrics.density
        indicator.translationZ = chromeZ
        dock.translationZ = chromeZ
    }

    private fun padGridPages(metrics: DeviceProfile.VerticalMetrics) {
        val top = insetTop + metrics.gridTopPaddingPx
        val bottom = insetBottom + metrics.gridBottomReservePx
        val side = profile.sidePaddingPx
        for (i in 0 until workspace.childCount) {
            val child = workspace.getChildAt(i) as? CellLayout ?: continue
            if (child.paddingTop != top || child.paddingBottom != bottom ||
                child.paddingStart != side
            ) {
                child.setPadding(side, top, side, bottom)
            }
        }
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
}
