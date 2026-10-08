package com.example.ffdiamond.ui.drag

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.ffdiamond.R
import com.example.ffdiamond.ui.drawer.DrawerController
import kotlin.math.abs

/**
 * The root of the home screen, and the only view above the pages.
 *
 * A drag has to survive leaving the icon it started on, cross pages that are themselves scrolling,
 * and end somewhere with no relationship to where it began. None of that works if the gesture stays
 * with the child that received the long press, so once a drag is running this layer takes the touch
 * stream away from whatever was handling it — which is exactly what [onInterceptTouchEvent] is for,
 * and the reason this sits above the workspace rather than beside it.
 */
class DragLayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var controller: DragController? = null
    var drawer: DrawerController? = null
    var allowDrawerGesture: () -> Boolean = { true }

    /**
     * The last touch point, in this layer's coordinates.
     *
     * A long press arrives through `OnLongClickListener`, which is told that a view was held but
     * not where — and a drag that starts from the centre of the icon instead of from under the
     * finger jumps on the first frame. Recording every event on the way past is the cheapest way to
     * still have the point when the press fires.
     */
    var lastTouchX = 0f
        private set

    var lastTouchY = 0f
        private set

    private var insetTop = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var drawerSteal = false
    private var downX = 0f
    private var downY = 0f

    init {
        // The wallpaper shows through everything above it; this layer never paints.
        setWillNotDraw(true)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (bars.top != insetTop) {
                insetTop = bars.top
                findViewById<View>(R.id.drop_action_bar)?.updateLayoutParams<LayoutParams> {
                    topMargin = insetTop
                }
            }
            insets
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchX = ev.x
        lastTouchY = ev.y

        val drawer = drawer
        if (drawer != null &&
            allowDrawerGesture() &&
            !drawer.isFullyClosed &&
            !drawer.blocksTransient() &&
            controller?.isDragging != true
        ) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    drawerSteal = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!drawerSteal && !drawer.isDragging) {
                        val dy = ev.y - downY
                        val dx = abs(ev.x - downX)
                        if (dy > touchSlop && dy >= dx) {
                            drawerSteal = true
                            // Cancel the icon first, then start the drag. beginDrag-before-cancel
                            // made the overlay treat that CANCEL as "finger up" and spring the
                            // sheet back open — which is why swipe-down on the grid did nothing.
                            val cancel = MotionEvent.obtain(ev)
                            cancel.action = MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                            drawer.beginDrag(downY)
                            return drawer.onTouch(ev)
                        }
                    }
                }
            }
            if (drawerSteal || drawer.isDragging) {
                if (ev.actionMasked == MotionEvent.ACTION_UP ||
                    ev.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    drawerSteal = false
                }
                return drawer.onTouch(ev)
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && drawer?.isFullyClosed == false) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (controller?.isDragging == true) return true
        if (!allowDrawerGesture()) return false
        return drawer?.onInterceptTouch(ev) == true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val controller = controller
        if (controller?.isDragging == true) return controller.onDragTouch(ev)
        val drawer = drawer
        if (drawer != null && (drawer.isDragging || !drawer.isFullyClosed)) {
            return drawer.onTouch(ev)
        }
        return false
    }

    override fun performClick(): Boolean = super.performClick()
}
