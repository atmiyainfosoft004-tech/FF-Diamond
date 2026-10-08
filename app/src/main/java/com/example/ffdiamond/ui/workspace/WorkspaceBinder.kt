package com.example.ffdiamond.ui.workspace

import android.content.Context
import android.view.LayoutInflater
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import com.example.ffdiamond.R
import com.example.ffdiamond.apps.AppLauncher
import com.example.ffdiamond.data.HomeSettings
import com.example.ffdiamond.home.SparseHome
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.model.Container
import com.example.ffdiamond.model.GridShape
import com.example.ffdiamond.model.HomeItem
import com.example.ffdiamond.model.HomeLayout
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.drag.WorkspaceHost
import com.example.ffdiamond.ui.drag.homeItem
import com.example.ffdiamond.ui.drag.setHomeItem
import com.example.ffdiamond.widget.GoogleIntents
import com.example.ffdiamond.widget.SearchWidgetProvider
import com.example.ffdiamond.widget.WidgetFrameView
import com.example.ffdiamond.widget.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Renders a [HomeLayout] into the workspace pages and the dock.
 *
 * There is no placement logic here. Every item already carries the cell it belongs in, chosen by
 * the layout repository and written to the database, so this class only puts views where it is told.
 *
 * It reuses views across binds rather than rebuilding the page, and that is load-bearing rather
 * than an optimisation. A drop previews itself by sliding icons with `translationX` and then writes
 * the same arrangement to the database; when the write comes back, each view has to be re-laid out
 * into the cell it already appears to be in and have its translation cleared in the same frame. If
 * the views were thrown away and made again, that frame would be a flicker of icons jumping from
 * their old cells to their new ones — the exact thing the preview existed to avoid.
 */
class WorkspaceBinder(
    private val context: Context,
    private val workspace: Workspace,
    private val dock: CellLayout,
    private val indicator: PageIndicatorView,
    private val iconCache: IconCache,
    private val scope: CoroutineScope,
    private val onEmptySpaceLongPress: () -> Unit,
    private val onItemLongPress: (HomeItem, IconCellView, Container, Int) -> Boolean,
    private val onFolderClick: (HomeItem.Folder, FolderIconView) -> Unit,
    private val onWidgetEdit: (HomeItem.Widget, WidgetFrameView, Int) -> Unit,
    private val onSearchWidgetClick: () -> Unit,
    private val widgets: WidgetStore
) : WorkspaceHost {

    private var boundOnce = false
    private var profile: DeviceProfile? = null

    /** Views by item id, so a rebind can find the view an item already has. */
    private val views = HashMap<Long, View>()

    /** What each view was last bound with, so unchanged items are not re-bound and re-faded. */
    private val bound = HashMap<Long, Any>()

    /** Items whose view must stay invisible because the drag layer is carrying their likeness. */
    private val hidden = HashSet<Long>()

    private var sparePage: CellLayout? = null

    /** First bind opens the downloader page instead of home (Let's Start / open-app extra). */
    var landOnDownloader: Boolean = false

    fun bind(layout: HomeLayout, profile: DeviceProfile, settings: HomeSettings) {
        this.profile = profile
        indicator.applyProfile(profile)

        val live = HashSet<Long>()
        bindDock(layout, profile, settings, live)
        bindPages(layout, profile, settings, live)

        // Anything the layout no longer mentions was uninstalled, removed, or swallowed by a folder.
        views.keys.filterNot { it in live }.forEach { id ->
            views.remove(id)?.let { (it.parent as? ViewGroup)?.removeView(it) }
            bound.remove(id)
            hidden.remove(id)
        }
    }

    private fun bindDock(
        layout: HomeLayout,
        profile: DeviceProfile,
        settings: HomeSettings,
        live: MutableSet<Long>
    ) {
        dock.columns = GridShape.DOCK_COLUMNS
        dock.rows = 1
        dock.setPadding(profile.sidePaddingPx, 0, profile.sidePaddingPx, 0)
        dock.setOnLongClickListener { onEmptySpaceLongPress(); true }

        // No labels in the dock: the reference shows bare icons, and four names crammed under the
        // grid would compete with the page indicator right above them.
        layout.dock.forEach { item ->
            live += item.id
            place(dock, item, profile, showLabel = false, settings = settings, dbPage = 0)
        }
        prune(dock, live)
    }

    private fun bindPages(
        layout: HomeLayout,
        profile: DeviceProfile,
        settings: HomeSettings,
        live: MutableSet<Long>
    ) {
        val previousPage = workspace.currentPage
        sparePage = null

        ensurePageCount(layout.pages.size.coerceAtLeast(SparseHome.MIN_GRID_PAGES), profile)

        for (dbPage in 0 until pageCount()) {
            val page = pageAt(dbPage) ?: continue
            page.columns = profile.columns
            page.rows = profile.rows
            layout.pages.getOrNull(dbPage).orEmpty().forEach { item ->
                live += item.id
                place(page, item, profile, settings.showLabels, settings, dbPage)
            }
            prune(page, live)
        }
        bindHomeClock(layout, profile)

        indicator.setPageCount(workspace.childCount)
        indicator.onPageClick = { workspace.snapToPage(it, animate = true) }

        // First bind lands on home — unless we were asked to open the downloader immediately.
        val target = when {
            boundOnce -> previousPage
            landOnDownloader -> (workspace.childCount - 1).coerceAtLeast(0)
            else -> HOME_PAGE_INDEX
        }
        workspace.snapToPage(target, animate = false)
        indicator.setPosition(workspace.scrollProgress)
        boundOnce = true
    }

    private fun ensurePageCount(pages: Int, profile: DeviceProfile) {
        val special = TRAILING_SPECIAL
        while (workspace.childCount - FIRST_GRID_CHILD - special > pages) {
            workspace.removeViewAt(workspace.childCount - 1 - special)
        }
        while (workspace.childCount - FIRST_GRID_CHILD - special < pages) {
            workspace.addView(newPage(profile), workspace.childCount - special)
        }
    }

    private fun newPage(profile: DeviceProfile) = CellLayout(context).apply {
        columns = profile.columns
        rows = profile.rows
        setPadding(profile.sidePaddingPx, 0, profile.sidePaddingPx, 0)
        setOnLongClickListener { onEmptySpaceLongPress(); true }
    }

    /** Removes views that no longer belong to this container, leaving the rest alone. */
    private fun prune(cellLayout: CellLayout, live: Set<Long>) {
        for (i in cellLayout.childCount - 1 downTo 0) {
            val child = cellLayout.getChildAt(i)
            val id = child.homeItem()?.id
            if (child.tag == CLOCK_TAG) continue
            if (id == null || id !in live) cellLayout.removeViewAt(i)
        }
    }

    private fun place(
        cellLayout: CellLayout,
        item: HomeItem,
        profile: DeviceProfile,
        showLabel: Boolean,
        settings: HomeSettings,
        dbPage: Int
    ) {
        val view = views[item.id]?.takeIf { it.fits(item) } ?: create(item).also { views[item.id] = it }

        val signature = contentSignature(item, showLabel, settings.themedIcons)
        if (bound[item.id] != signature) {
            bindContent(view, item, profile, settings)
            bound[item.id] = signature
        }
        (view as? IconCellView)?.let {
            it.showLabel = showLabel
            it.applyProfile(profile)
        }
        view.setHomeItem(item)
        view.alpha = if (item.id in hidden) 0f else 1f
        wireGestures(view, item, cellLayout, dbPage)

        if (view.parent !== cellLayout) {
            (view.parent as? ViewGroup)?.removeView(view)
            cellLayout.addInCell(view, item.cellX, item.cellY, item.spanX, item.spanY)
        } else {
            val params = view.layoutParams as CellLayout.LayoutParams
            if (params.cellX != item.cellX || params.cellY != item.cellY ||
                params.spanX != item.spanX || params.spanY != item.spanY
            ) {
                params.cellX = item.cellX
                params.cellY = item.cellY
                params.spanX = item.spanX
                params.spanY = item.spanY
                cellLayout.requestLayout()
            }
        }
        // The preview that led here has done its job; the real cells now say the same thing.
        view.translationX = 0f
        view.translationY = 0f
    }

    private fun bindHomeClock(layout: HomeLayout, profile: DeviceProfile) {
        val page = pageAt(0) ?: return
        val items = layout.pages.getOrNull(0).orEmpty()
        val search = items.filterIsInstance<HomeItem.Widget>()
            .firstOrNull { SearchWidgetProvider.isOurs(it.provider, context) }
        val clockY = (search?.cellY ?: 0) + (search?.spanY ?: 1)
        val existing = (0 until page.childCount)
            .map { page.getChildAt(it) }
            .firstOrNull { it.tag == CLOCK_TAG }
        if (clockY >= profile.rows || rowTaken(items, clockY)) {
            existing?.let { page.removeView(it) }
            return
        }
        val clock = existing ?: LayoutInflater.from(context)
            .inflate(R.layout.view_home_clock, page, false)
            .also {
                it.tag = CLOCK_TAG
                it.isClickable = false
                it.isFocusable = false
            }
        if (clock.parent !== page) {
            (clock.parent as? ViewGroup)?.removeView(clock)
            page.addInCell(clock, 0, clockY, profile.columns.coerceAtLeast(1), 1)
        } else {
            val params = clock.layoutParams as CellLayout.LayoutParams
            params.cellX = 0
            params.cellY = clockY
            params.spanX = profile.columns.coerceAtLeast(1)
            params.spanY = 1
            page.requestLayout()
        }
    }

    private fun rowTaken(items: List<HomeItem>, y: Int): Boolean =
        items.any { y in it.cellY until (it.cellY + it.spanY) }

    private fun View.fits(item: HomeItem) = when (item) {
        is HomeItem.App -> this is AppIconView
        is HomeItem.Folder -> this is FolderIconView
        is HomeItem.Widget -> this is WidgetFrameView
    }

    /** Only the parts a rebind would have to redraw; the cell is handled separately. */
    private fun contentSignature(item: HomeItem, showLabel: Boolean, themed: Boolean): Any =
        when (item) {
            is HomeItem.App -> listOf(item.info.key, item.title, showLabel, themed)
            is HomeItem.Folder ->
                listOf(item.folderId, item.title, themed, item.members.map { it.info.key })
            is HomeItem.Widget -> listOf(item.appWidgetId, item.spanX, item.spanY)
        }

    private fun create(item: HomeItem): View = when (item) {
        is HomeItem.App -> AppIconView(context)
        is HomeItem.Folder -> FolderIconView(context)
        is HomeItem.Widget -> WidgetFrameView(context)
    }

    private fun bindContent(
        view: View,
        item: HomeItem,
        profile: DeviceProfile,
        settings: HomeSettings
    ) {
        when (item) {
            is HomeItem.App -> bindApp(view as AppIconView, item, settings.themedIcons)
            is HomeItem.Folder -> bindFolder(view as FolderIconView, item, settings.themedIcons)
            is HomeItem.Widget -> bindWidget(view as WidgetFrameView, item)
        }
        (view as? IconCellView)?.applyProfile(profile)
    }

    private fun bindWidget(frame: WidgetFrameView, item: HomeItem.Widget) {
        frame.removeAllViews()
        if (SearchWidgetProvider.isOurs(item.provider, context)) {
            val pill = LayoutInflater.from(context).inflate(R.layout.widget_search, frame, false)
            pill.setOnClickListener { onSearchWidgetClick() }
            pill.findViewById<View>(R.id.search_widget_pill)?.setOnClickListener { onSearchWidgetClick() }
            pill.findViewById<View>(R.id.search_widget_mic)?.setOnClickListener {
                GoogleIntents.voiceSearch(context)
            }
            pill.findViewById<View>(R.id.search_widget_lens)?.setOnClickListener {
                GoogleIntents.lens(context)
            }
            frame.addView(
                pill,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return
        }
        val info = widgets.info(item.appWidgetId)
        val hostView = try {
            widgets.createView(context, item.appWidgetId, info)
        } catch (_: RuntimeException) {
            return
        }
        val page = frame.parent as? CellLayout
        val cellW = page?.cellWidth?.takeIf { it > 0 } ?: (context.resources.displayMetrics.widthPixels / 4)
        val cellH = page?.cellHeight?.takeIf { it > 0 } ?: cellW
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        val wDp = ((item.spanX * cellW) / density).roundToInt().coerceAtLeast(1)
        val hDp = ((item.spanY * cellH) / density).roundToInt().coerceAtLeast(1)
        val opts = widgets.optionsFor(item.spanX, item.spanY, cellW, cellH)
        widgets.applyOptions(item.appWidgetId, opts)
        hostView.updateAppWidgetSize(opts, wDp, hDp, wDp, hDp)

        frame.addView(
            hostView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun bindApp(view: AppIconView, item: HomeItem.App, themed: Boolean) {
        view.bind(item.info, iconCache.placeholderBitmap(), item.title)

        // A memory hit paints immediately; only a genuine miss shows the placeholder and fades,
        // so a warm launcher never flickers grey squares on rotation or a settings change.
        val cached = iconCache.peek(item.info, themed)
        if (cached != null) {
            view.setIcon(cached, animate = false)
        } else {
            view.iconJob = scope.launch {
                view.setIcon(iconCache.get(item.info, themed), animate = true)
            }
        }
    }

    private fun bindFolder(view: FolderIconView, item: HomeItem.Folder, themed: Boolean) {
        view.folderId = item.folderId
        view.setLabel(item.title)

        val preview = item.members.take(FolderIconView.GRID * FolderIconView.GRID)
        view.setMembers(preview.mapNotNull { iconCache.peek(it.info, themed) })
        scope.launch {
            view.setMembers(preview.map { iconCache.get(it.info, themed) })
        }
    }

    private fun wireGestures(
        view: View,
        item: HomeItem,
        cellLayout: CellLayout,
        dbPage: Int
    ) {
        val container = if (cellLayout === dock) Container.DOCK else Container.DESKTOP

        when (item) {
            is HomeItem.App -> {
                view.setOnClickListener { AppLauncher.launch(it, item.info) }
                view.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (AppLauncher.consumeOwnLongPress(it, item.info.packageName)) {
                        true
                    } else {
                        onItemLongPress(item, view as IconCellView, container, dbPage)
                    }
                }
            }
            is HomeItem.Folder -> {
                view.setOnClickListener { onFolderClick(item, view as FolderIconView) }
                view.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onItemLongPress(item, view as IconCellView, container, dbPage)
                }
            }
            is HomeItem.Widget -> {
                view.setOnClickListener(null)
                (view as WidgetFrameView).onEdit = { onWidgetEdit(item, view, dbPage) }
            }
        }
    }

    // ------------------------------------------------------------ WorkspaceHost

    override fun pageCount(): Int =
        (workspace.childCount - FIRST_GRID_CHILD - TRAILING_SPECIAL).coerceAtLeast(0)

    override fun ensureTrailingEmptyPage(): Int {
        sparePage?.let { return pageCount() - 1 }
        val last = pageAt(pageCount() - 1)
        if (last != null && last.childCount == 0) return pageCount() - 1

        val profile = profile ?: return pageCount() - 1
        val page = newPage(profile)
        workspace.addView(page, workspace.childCount - TRAILING_SPECIAL)
        sparePage = page
        indicator.setPageCount(workspace.childCount)
        return pageCount() - 1
    }

    override fun pageAt(dbPage: Int): CellLayout? =
        workspace.pageAt(dbPage + FIRST_GRID_CHILD) as? CellLayout

    override fun visiblePage(): Int = workspace.currentPage - FIRST_GRID_CHILD

    override fun goToPage(dbPage: Int) =
        workspace.snapToPage(dbPage + FIRST_GRID_CHILD, animate = true)

    override fun discardTrailingEmptyPage() {
        val spare = sparePage ?: return
        sparePage = null
        if (spare.childCount > 0) return
        if (pageCount() <= SparseHome.MIN_GRID_PAGES) return

        val onIt = workspace.currentPage == workspace.indexOfChild(spare)
        workspace.removeView(spare)
        indicator.setPageCount(workspace.childCount)
        if (onIt) workspace.snapToPage(HOME_PAGE_INDEX, animate = true)
    }

    override fun setItemHidden(id: Long, hidden: Boolean) {
        if (hidden) this.hidden += id else this.hidden -= id
        views[id]?.alpha = if (hidden) 0f else 1f
    }

    private companion object {
        /** Child 0 is Discover; grid pages follow; the last child is the downloader page. */
        const val FIRST_GRID_CHILD = 1
        const val TRAILING_SPECIAL = 1
        const val HOME_PAGE_INDEX = 1
        private const val CLOCK_TAG = "home_clock"
    }
}
