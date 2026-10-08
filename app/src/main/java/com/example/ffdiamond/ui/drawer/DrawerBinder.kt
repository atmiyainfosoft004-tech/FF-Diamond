package com.example.ffdiamond.ui.drawer

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import com.example.ffdiamond.apps.AppLauncher
import com.example.ffdiamond.icons.IconCache
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.workspace.AppIconView
import com.example.ffdiamond.ui.workspace.CellLayout
import com.example.ffdiamond.ui.workspace.PageIndicatorView
import com.example.ffdiamond.ui.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Fills the drawer pager with every installed app, A–Z, 4 × 6 per page.
 *
 * The home grid size is ignored on purpose: the drawer is a directory, not an arranged workspace,
 * and the spec pins it at four columns regardless of what the user picked for home.
 */
class DrawerBinder(
    private val context: Context,
    private val pager: Workspace,
    private val indicator: PageIndicatorView,
    private val iconCache: IconCache,
    private val scope: CoroutineScope,
    private val onAppLongPress: (AppInfo, AppIconView) -> Boolean
) {

    private val views = ArrayList<AppIconView>()
    private var profile: DeviceProfile? = null
    private var themed = false
    private var apps: List<AppInfo> = emptyList()

    fun bind(apps: List<AppInfo>, profile: DeviceProfile, themed: Boolean) {
        this.apps = apps
        this.profile = profile.withGrid(COLUMNS, ROWS)
        this.themed = themed
        indicator.applyProfile(this.profile!!)
        indicator.showGlyphs = false

        val perPage = COLUMNS * ROWS
        val pageCount = ceil(apps.size / perPage.toFloat()).toInt().coerceAtLeast(1)
        ensurePages(pageCount)

        var viewIndex = 0
        apps.forEachIndexed { index, app ->
            val page = pager.getChildAt(index / perPage) as CellLayout
            val cell = index % perPage
            val view = viewAt(viewIndex++)
            bindApp(view, app)
            if (view.parent !== page) {
                (view.parent as? ViewGroup)?.removeView(view)
                page.addInCell(view, cell % COLUMNS, cell / COLUMNS)
            } else {
                val params = view.layoutParams as CellLayout.LayoutParams
                val cellX = cell % COLUMNS
                val cellY = cell / COLUMNS
                if (params.cellX != cellX || params.cellY != cellY) {
                    params.cellX = cellX
                    params.cellY = cellY
                    page.requestLayout()
                }
            }
        }

        while (viewIndex < views.size) {
            val extra = views.removeAt(views.lastIndex)
            (extra.parent as? ViewGroup)?.removeView(extra)
        }

        for (pageIndex in 0 until pager.childCount) {
            val page = pager.getChildAt(pageIndex) as CellLayout
            val start = pageIndex * perPage
            val keep = apps.drop(start).take(perPage).map { it.key }.toHashSet()
            for (childIndex in page.childCount - 1 downTo 0) {
                val child = page.getChildAt(childIndex) as? AppIconView ?: continue
                if (child.app?.key !in keep) page.removeViewAt(childIndex)
            }
        }

        indicator.setPageCount(pageCount)
        if (pager.currentPage >= pageCount) {
            pager.snapToPage(pageCount - 1, animate = false)
        }
        indicator.setPosition(pager.scrollProgress)
    }

    private fun ensurePages(count: Int) {
        val profile = profile ?: return
        while (pager.childCount > count) {
            pager.removeViewAt(pager.childCount - 1)
        }
        while (pager.childCount < count) {
            pager.addView(
                CellLayout(context).apply {
                    columns = COLUMNS
                    rows = ROWS
                    setPadding(profile.sidePaddingPx, 0, profile.sidePaddingPx, 0)
                }
            )
        }
        for (i in 0 until pager.childCount) {
            (pager.getChildAt(i) as CellLayout).apply {
                columns = COLUMNS
                rows = ROWS
                setPadding(profile.sidePaddingPx, 0, profile.sidePaddingPx, 0)
            }
        }
    }

    private fun viewAt(index: Int): AppIconView {
        while (views.size <= index) views += AppIconView(context)
        return views[index]
    }

    private fun bindApp(view: AppIconView, app: AppInfo) {
        val profile = profile ?: return
        view.applyProfile(profile)
        view.showLabel = true
        view.bind(app, iconCache.placeholderBitmap(), app.label)
        val cached = iconCache.peek(app, themed)
        if (cached != null) {
            view.setIcon(cached, animate = false)
        } else {
            view.iconJob = scope.launch {
                view.setIcon(iconCache.get(app, themed), animate = true)
            }
        }
        view.setOnClickListener { AppLauncher.launch(it, app) }
        view.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (AppLauncher.consumeOwnLongPress(it, app.packageName)) {
                true
            } else {
                onAppLongPress(app, view)
            }
        }
    }

    companion object {
        const val COLUMNS = 4
        const val ROWS = 6
    }
}
