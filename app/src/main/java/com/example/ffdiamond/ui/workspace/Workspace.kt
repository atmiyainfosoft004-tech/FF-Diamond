package com.example.ffdiamond.ui.workspace

import android.app.WallpaperManager
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import com.example.ffdiamond.util.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The horizontally paged home screen.
 *
 * Rolled by hand on an [OverScroller] rather than built on ViewPager2, because the launcher needs
 * the scroll *position*, continuously, not just the settled page:
 *
 *  - the wallpaper has to slide under the pages in lockstep, which means pushing a fresh offset to
 *    [WallpaperManager] on every frame of the drag;
 *  - the page indicator morphs between markers as the finger moves;
 *  - page -1 (child index 0) is a content panel rather than a grid, and phases 5 and 6 will need to
 *    reach into the scroller to auto-advance pages during a drag and to run the drawer transition
 *    off the same gesture.
 *
 * ViewPager2 wraps a RecyclerView and hides all of that behind an adapter.
 */
class Workspace @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val scroller = OverScroller(context, Motion.STANDARD)
    private val configuration = ViewConfiguration.get(context)
    private val touchSlop = configuration.scaledPagingTouchSlop
    private val closeSlop = configuration.scaledTouchSlop
    private val minFlingVelocity = configuration.scaledMinimumFlingVelocity
    private val maxFlingVelocity = configuration.scaledMaximumFlingVelocity
    private val wallpaperManager: WallpaperManager? = WallpaperManager.getInstance(context)

    private var velocityTracker: VelocityTracker? = null
    private var isDragging = false
    private var activePointerId = INVALID_POINTER
    private var lastMotionX = 0f
    private var downX = 0f
    private var downY = 0f

    var currentPage: Int = 0
        private set

    /** Fires on every frame of a scroll with the fractional page position, e.g. 1.37. */
    var onScroll: ((Float) -> Unit)? = null

    /** Fires when the scroll settles on a page, including programmatic snaps. */
    var onPageChanged: ((Int) -> Unit)? = null

    /**
     * Only the home screen's pager pans the wallpaper. A folder reuses this class for its own pages
     * and must not, or opening a folder would slide the wallpaper out from under the whole screen.
     */
    var wallpaperParallax: Boolean = true

    /**
     * When false, a vertical swipe is left for an ancestor (the app drawer) to intercept. The home
     * pager keeps this true so a page turn is not stolen by the swipe-up-to-open gesture.
     */
    var interceptParent: Boolean = true

    /**
     * Drawer pager only. Vertical-down on the app grid is a close gesture; the icons would
     * otherwise eat the stream and the sheet would never move.
     */
    var onVerticalSwipe: ((originY: Float, ev: MotionEvent) -> Boolean)? = null

    private var closingVertically = false

    private val maxScrollX: Int get() = ((childCount - 1) * width).coerceAtLeast(0)

    /** Where the fractional page sits right now; the source of truth for the indicator. */
    val scrollProgress: Float
        get() = if (width == 0) currentPage.toFloat() else scrollX.toFloat() / width

    init {
        // Discover is an opaque full-screen page; clipping keeps it from painting over the dock.
        clipChildren = true
        // Two fingers on two different pages would otherwise each start their own gesture.
        isMotionEventSplittingEnabled = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)

        for (i in 0 until childCount) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var left = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(left, 0, left + width, height)
            left += width
        }
        // A rotation, a grid change, or a bind that snapped before the first measure can leave
        // scrollX on the wrong page. Sync only when idle so a live swipe is not yanked back.
        if (!isDragging && scroller.isFinished && width > 0) {
            val destination = currentPage * width
            if (scrollX != destination) scrollTo(destination, 0)
        }
        updateWallpaperSteps()
        updateWallpaperOffset()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The window token only exists once attached, and the wallpaper API needs it.
        updateWallpaperSteps()
        updateWallpaperOffset()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        updateWallpaperOffset()
        onScroll?.invoke(scrollProgress)
    }

    /**
     * Keeps the live wallpaper's horizontal pan in step with the pages. Static wallpapers get the
     * same treatment for free: the system crops a wider bitmap using the offset we publish.
     */
    private fun updateWallpaperOffset() {
        if (!wallpaperParallax) return
        val token = windowToken ?: return
        val max = maxScrollX
        val offset = if (max > 0) (scrollX.toFloat() / max).coerceIn(0f, 1f) else 0f
        runCatching { wallpaperManager?.setWallpaperOffsets(token, offset, 0f) }
    }

    /**
     * Tells the wallpaper how far one page is in wallpaper-space, so a live wallpaper that snaps to
     * discrete positions lands on a page boundary instead of somewhere in between.
     */
    private fun updateWallpaperSteps() {
        if (!wallpaperParallax) return
        val token = windowToken ?: return
        val pages = childCount
        val step = if (pages > 1) 1f / (pages - 1) else 1f
        runCatching { wallpaperManager?.setWallpaperOffsetSteps(step, 0f) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        if (action == MotionEvent.ACTION_MOVE && (isDragging || closingVertically)) return true

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastMotionX = ev.x
                activePointerId = ev.getPointerId(0)
                closingVertically = false
                // Grabbing a page mid-settle should stop it dead under the finger rather than
                // letting it keep coasting to a page the user no longer wants.
                isDragging = !scroller.isFinished
                if (isDragging) {
                    scroller.abortAnimation()
                    if (interceptParent) parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false
                val x = ev.getX(pointerIndex)
                val y = ev.getY(pointerIndex)
                val dx = abs(x - downX)
                val signedDy = y - downY
                val dy = abs(signedDy)
                if (!interceptParent && onVerticalSwipe != null &&
                    signedDy > closeSlop && signedDy >= dx
                ) {
                    closingVertically = true
                    return true
                }
                // Horizontal-dominant only, so a vertical swipe still reaches the drawer gesture
                // and a long press on an icon is never stolen.
                if (dx > touchSlop && dx > dy) {
                    isDragging = true
                    lastMotionX = x
                    if (interceptParent) parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                closingVertically = false
                activePointerId = INVALID_POINTER
                releaseVelocityTracker()
            }
        }

        obtainVelocityTracker().addMovement(ev)
        return isDragging || closingVertically
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        obtainVelocityTracker().addMovement(ev)

        if (closingVertically) {
            onVerticalSwipe?.invoke(downY, ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                closingVertically = false
                endGesture()
            }
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.abortAnimation()
                activePointerId = ev.getPointerId(0)
                downX = ev.x
                downY = ev.y
                lastMotionX = ev.x
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                val x = ev.getX(pointerIndex)
                if (!isDragging) {
                    if (abs(x - downX) > touchSlop && abs(x - downX) > abs(ev.getY(pointerIndex) - downY)) {
                        isDragging = true
                        lastMotionX = x
                        if (interceptParent) parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (isDragging) {
                    val delta = (lastMotionX - x).roundToInt()
                    lastMotionX = x
                    scrollBy(clampScrollDelta(delta), 0)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val tracker = obtainVelocityTracker()
                    tracker.computeCurrentVelocity(VELOCITY_UNITS, maxFlingVelocity.toFloat())
                    val velocity = tracker.getXVelocity(activePointerId).toInt()
                    settle(velocity)
                } else {
                    // A tap that never became a drag landed on empty space between icons. Routing
                    // it through performClick keeps accessibility services able to activate the
                    // workspace the same way a sighted user would.
                    performClick()
                }
                endGesture()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) settle(0)
                endGesture()
            }

            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun endGesture() {
        isDragging = false
        activePointerId = INVALID_POINTER
        releaseVelocityTracker()
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        if (ev.getPointerId(pointerIndex) != activePointerId) return
        // The finger that started the drag lifted; hand the drag to another one already down
        // instead of ending it, or the page would jump.
        val newIndex = if (pointerIndex == 0) 1 else 0
        lastMotionX = ev.getX(newIndex)
        activePointerId = ev.getPointerId(newIndex)
        velocityTracker?.clear()
    }

    private fun clampScrollDelta(delta: Int): Int {
        val target = (scrollX + delta).coerceIn(0, maxScrollX)
        return target - scrollX
    }

    /** Decides which page a released drag belongs to, then animates the rest of the way. */
    private fun settle(velocity: Int) {
        val pageWidth = width
        if (pageWidth == 0) return

        val nearest = (scrollX.toFloat() / pageWidth).roundToInt()
        val target = when {
            // A deliberate flick wins over position: a short, fast swipe should turn the page even
            // though the finger never travelled half its width.
            velocity > minFlingVelocity -> (scrollX / pageWidth)
            velocity < -minFlingVelocity -> (scrollX / pageWidth) + 1
            else -> nearest
        }
        snapToPage(target, animate = true)
    }

    fun snapToPage(page: Int, animate: Boolean) {
        val target = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val destination = target * width
        currentPage = target

        val full = Motion.duration(context, Motion.DURATION_STANDARD)
        val delta = destination - scrollX

        if (!animate || full == 0L || delta == 0) {
            scroller.abortAnimation()
            if (delta != 0) scrollTo(destination, 0)
            onPageChanged?.invoke(target)
            // scrollTo is a no-op when already there (or width is still 0), so tell chrome anyway.
            onScroll?.invoke(if (width == 0) target.toFloat() else scrollX.toFloat() / width)
            return
        }

        // Scale the duration with the distance left so a nearly-complete swipe finishes crisply
        // instead of dawdling through a full-length animation.
        val fraction = abs(delta).toFloat() / width.coerceAtLeast(1)
        val duration = (full * fraction).toLong()
            .coerceIn(minOf(MIN_SNAP_DURATION, full), full)

        scroller.startScroll(scrollX, 0, delta, 0, duration.toInt())
        postInvalidateOnAnimation()
        onPageChanged?.invoke(target)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            postInvalidateOnAnimation()
        }
    }

    private fun obtainVelocityTracker(): VelocityTracker =
        velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    /** Pages are laid out side by side, so the first grid page is child index 1, not 0. */
    fun pageAt(index: Int): View? = getChildAt(index)

    private companion object {
        const val INVALID_POINTER = -1
        const val MIN_SNAP_DURATION = 120L

        /** Pixels per second, the unit the fling thresholds from ViewConfiguration are given in. */
        const val VELOCITY_UNITS = 1000
    }
}
