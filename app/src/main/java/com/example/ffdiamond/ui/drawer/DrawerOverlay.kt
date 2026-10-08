package com.example.ffdiamond.ui.drawer

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.example.ffdiamond.R
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.workspace.PageIndicatorView
import com.example.ffdiamond.ui.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen overlay for the app drawer. It stays in the tree even when closed so a swipe can
 * drive [DrawerController.progress] without an inflate hitch; when progress is ~0 it refuses
 * touches so the home screen underneath keeps working.
 */
class DrawerOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var controller: DrawerController? = null

    var onOverflowClick: (() -> Unit)? = null
    var onPinToHome: ((AppInfo) -> Unit)? = null
    var onUninstall: ((AppInfo) -> Unit)? = null
    var onOpened: (() -> Unit)? = null

    private val chrome: DrawerChromeLayout
    private val pager: Workspace
    private val indicator: PageIndicatorView
    private val searchPanel: SearchPanel
    private val popup: AppPopup
    val backdrop: View

    private lateinit var binder: DrawerBinder
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var intercepting = false
    private var downX = 0f
    private var downY = 0f

    val isSearchOpen: Boolean get() = searchPanel.isOpen
    val isPopupOpen: Boolean get() = popup.isOpen

    init {
        LayoutInflater.from(context).inflate(R.layout.view_drawer_overlay, this, true)
        backdrop = findViewById(R.id.drawer_backdrop)
        chrome = findViewById(R.id.drawer_chrome)
        pager = findViewById<Workspace>(R.id.drawer_pager).apply {
            wallpaperParallax = false
            // The pager would otherwise lock this overlay out of intercept, which is why swipe-
            // down only worked from the empty chrome above the grid and not from the icons.
            interceptParent = false
            onVerticalSwipe = { originY, ev ->
                val current = controller
                if (current == null) {
                    false
                } else {
                    if (!current.isDragging) current.beginDrag(originY)
                    current.onTouch(ev)
                }
            }
        }
        indicator = findViewById(R.id.drawer_indicator)
        searchPanel = findViewById(R.id.search_panel)
        popup = findViewById(R.id.app_popup)

        pager.onScroll = { indicator.setPosition(it) }
        indicator.onPageClick = { pager.snapToPage(it, animate = true) }
        findViewById<View>(R.id.drawer_search_pill).setOnClickListener { openSearch() }
        findViewById<View>(R.id.drawer_overflow).setOnClickListener { onOverflowClick?.invoke() }

        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        clipChildren = true
        chrome.translationY = resources.displayMetrics.heightPixels.toFloat()
        chrome.visibility = View.INVISIBLE
    }

    fun attach(
        iconCache: IconCache,
        scope: CoroutineScope,
        appsProvider: () -> List<AppInfo>
    ) {
        binder = DrawerBinder(
            context = context,
            pager = pager,
            indicator = indicator,
            iconCache = iconCache,
            scope = scope,
            onAppLongPress = { app, view ->
                popup.show(view, app)
                true
            }
        )
        searchPanel.attach(iconCache, scope, appsProvider)
        popup.onPinToHome = { app -> onPinToHome?.invoke(app) }
        popup.onUninstall = { app -> onUninstall?.invoke(app) }
    }

    fun bind(apps: List<AppInfo>, profile: DeviceProfile, themed: Boolean) {
        chrome.applyProfile(profile)
        if (::binder.isInitialized) binder.bind(apps, profile, themed)
        searchPanel.setApps(apps, themed)
    }

    fun applySheet(progress: Float) {
        val height = height.coerceAtLeast(resources.displayMetrics.heightPixels).coerceAtLeast(1)
        chrome.translationY = (1f - progress) * height
        val open = progress > CLOSED_EPS
        val scrim = (SCRIM_ALPHA * progress).roundToInt().coerceIn(0, SCRIM_ALPHA)
        setBackgroundColor(Color.argb(scrim, 255, 255, 255))
        importantForAccessibility = if (open) {
            IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (!open) dismissTransient()
        // INVISIBLE keeps the measured size so the next swipe does not relayout, but stops the
        // chrome painting over the home dock when a settle lands a fraction of a pixel short.
        chrome.visibility = if (open && !isSearchOpen) View.VISIBLE else View.INVISIBLE
        clipChildren = !open
        if (progress > 0.999f) onOpened?.invoke()
    }

    fun blocksHomeGesture(): Boolean = isSearchOpen || isPopupOpen

    fun dismissTransient() {
        if (popup.isOpen) popup.dismiss(immediate = true)
        if (searchPanel.isOpen) searchPanel.close(immediate = true)
    }

    fun closeSearch(): Boolean {
        if (!searchPanel.isOpen) return false
        searchPanel.close()
        chrome.isVisible = true
        return true
    }

    fun clearSearchIfPending() {
        searchPanel.clearSearchIfPending()
    }

    fun closePopup(): Boolean {
        if (!popup.isOpen) return false
        popup.dismiss()
        return true
    }

    fun showSearch() {
        openSearch()
    }

    private fun openSearch() {
        val controller = controller ?: return
        if (!controller.isFullyOpen) controller.open()
        chrome.isVisible = false
        searchPanel.open()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val controller = controller ?: return false
        if (controller.isFullyClosed && !controller.isDragging) return false
        if (isSearchOpen || isPopupOpen) return super.dispatchTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!intercepting && !controller.isDragging) {
                    val dy = ev.y - downY
                    val dx = abs(ev.x - downX)
                    // Steal here, not only in onInterceptTouchEvent: FLAG_DISALLOW_INTERCEPT is
                    // checked inside super.dispatchTouchEvent, which is why a downward swipe on
                    // an icon never reached intercept while the empty chrome above the grid did.
                    if (dy > touchSlop && dy >= dx) {
                        intercepting = true
                        val cancel = MotionEvent.obtain(ev)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        controller.beginDrag(downY)
                        return onTouchEvent(ev)
                    }
                }
            }
        }
        // Only this overlay's own steal. controller.isDragging can be true because DragLayer
        // already took the gesture — feeding those events here called finishDrag on CANCEL.
        if (intercepting) return onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && (controller?.progress ?: 0f) > CLOSED_EPS) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isSearchOpen || isPopupOpen) return false
        val controller = controller ?: return false
        if (controller.progress < CLOSED_EPS) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                val dx = abs(ev.x - downX)
                if (!intercepting && dy > touchSlop && dy >= dx) {
                    intercepting = true
                    controller.beginDrag(downY)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // A CANCEL that is only cancelling the icon (parent steal) must not clear
                // intercepting before onTouchEvent runs for the same gesture.
                if (!controller.isDragging) intercepting = false
            }
        }
        return intercepting
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val controller = controller ?: return false
        if (intercepting || controller.isDragging) {
            val handled = controller.onTouch(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP ||
                ev.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                intercepting = false
            }
            return handled
        }
        return super.onTouchEvent(ev)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        controller?.applyProgress(controller?.progress ?: 0f)
        controller?.prepareBlur()
    }

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        const val CLOSED_EPS = 0.001f

        /** Light frost over the live wallpaper — not an app-drawn picture. */
        const val SCRIM_ALPHA = 50
    }
}
