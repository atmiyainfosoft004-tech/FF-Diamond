package com.example.ffdiamond.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import com.example.ffdiamond.data.db.HomeItemEntity
import com.example.ffdiamond.model.Container
import com.example.ffdiamond.model.HomeItemType
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One [AppWidgetHost] for the process. The host id is a constant: changing it would orphan every
 * widget the user has already placed.
 */
class WidgetStore(context: Context) {

    private val appContext = context.applicationContext
    val host: AppWidgetHost = AppWidgetHost(appContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    fun createView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        val resolved = info ?: manager.getAppWidgetInfo(appWidgetId)
        val view = host.createView(context, appWidgetId, resolved)
        view.setPadding(0, 0, 0, 0)
        view.setPaddingRelative(0, 0, 0, 0)
        return view
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun isAlive(appWidgetId: Int): Boolean = appWidgetId > 0 && info(appWidgetId) != null

    fun delete(appWidgetId: Int) {
        if (appWidgetId > 0) host.deleteAppWidgetId(appWidgetId)
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(
        appWidgetId: Int,
        provider: ComponentName,
        options: Bundle
    ): Boolean = bindSafely {
        manager.bindAppWidgetIdIfAllowed(appWidgetId, provider, options)
    }

    fun bindIfAllowed(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        options: Bundle
    ): Boolean = bindSafely {
        manager.bindAppWidgetIdIfAllowed(
            appWidgetId,
            info.profile ?: Process.myUserHandle(),
            info.provider,
            options
        )
    }

    /**
     * Sony (and some other OEMs) keep the widget's options bundle null until bind finishes.
     * [AppWidgetManager.updateAppWidgetOptions] then NPEs on `Bundle.putAll`. Skip when the
     * widget is not bound yet, and swallow that OEM crash otherwise.
     */
    fun applyOptions(appWidgetId: Int, options: Bundle) {
        if (appWidgetId <= 0 || info(appWidgetId) == null) return
        try {
            manager.updateAppWidgetOptions(appWidgetId, options)
        } catch (_: RuntimeException) {
        }
    }

    private inline fun bindSafely(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (_: RuntimeException) {
            false
        }

    fun optionsFor(
        spanX: Int,
        spanY: Int,
        cellWidthPx: Int,
        cellHeightPx: Int
    ): Bundle {
        val density = appContext.resources.displayMetrics.density.coerceAtLeast(0.1f)
        val widthDp = ((spanX * cellWidthPx) / density).roundToInt().coerceAtLeast(1)
        val heightDp = ((spanY * cellHeightPx) / density).roundToInt().coerceAtLeast(1)
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            }
        }
    }

    fun homeProviders(): List<AppWidgetProviderInfo> {
        val ours = SearchWidgetProvider.component(appContext)
        return manager.installedProviders
            .filter { isHomeCategory(it) }
            .sortedWith(
                compareBy(
                    { it.provider != ours },
                    { it.loadLabel(appContext.packageManager).toString().lowercase() }
                )
            )
    }

    /**
     * Places the built-in search bar on the first home row.
     *
     * Bind can fail on OEM hosts that have not granted [android.Manifest.permission.BIND_APPWIDGET]
     * yet. The workspace still inflates [com.example.ffdiamond.R.layout.widget_search] itself, so a
     * row with a zero widget id is enough for the pill to show.
     */
    fun seedSearchWidget(columns: Int): HomeItemEntity {
        val provider = SearchWidgetProvider.component(appContext)
        val span = columns.coerceAtLeast(1)
        val id = allocateId()
        val metrics = appContext.resources.displayMetrics
        val cell = (metrics.widthPixels / span).coerceAtLeast(1)
        val bound = bindIfAllowed(id, provider, optionsFor(span, 1, cell, cell))
        val widgetId = if (bound) {
            SearchWidgetProvider.push(appContext, manager, id)
            id
        } else {
            delete(id)
            0
        }
        return searchItem(span, provider, widgetId)
    }

    fun unboundSearchWidget(columns: Int): HomeItemEntity =
        searchItem(
            columns.coerceAtLeast(1),
            SearchWidgetProvider.component(appContext),
            appWidgetId = 0
        )

    private fun searchItem(
        columns: Int,
        provider: ComponentName,
        appWidgetId: Int
    ): HomeItemEntity = HomeItemEntity(
        type = HomeItemType.WIDGET.name,
        page = 0,
        cellX = 0,
        cellY = 0,
        spanX = columns,
        spanY = 1,
        container = Container.DESKTOP.name,
        component = provider.flattenToShortString(),
        appWidgetId = appWidgetId,
        title = appContext.getString(com.example.ffdiamond.R.string.search_widget_name)
    )

    companion object {
        /** Frozen. A new value would make every existing widget id unreadable. */
        const val HOST_ID = 0xA711A

        fun isConfigureOptional(info: AppWidgetProviderInfo): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (info.widgetFeatures and
                    AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0

        fun isHomeCategory(info: AppWidgetProviderInfo): Boolean {
            val category = info.widgetCategory
            return category == 0 ||
                (category and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
        }

        fun spansFor(
            info: AppWidgetProviderInfo,
            cellWidthPx: Int,
            cellHeightPx: Int,
            columns: Int,
            rows: Int,
            density: Float = 1f
        ): Pair<Int, Int> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                info.targetCellWidth > 0 &&
                info.targetCellHeight > 0
            ) {
                return info.targetCellWidth.coerceIn(1, columns) to
                    info.targetCellHeight.coerceIn(1, rows)
            }
            val d = density.coerceAtLeast(0.1f)
            val cellWDp = (cellWidthPx / d).coerceAtLeast(1f)
            val cellHDp = (cellHeightPx / d).coerceAtLeast(1f)
            val spanX = ceil(info.minWidth.toFloat() / cellWDp).toInt().coerceIn(1, columns)
            val spanY = ceil(info.minHeight.toFloat() / cellHDp).toInt().coerceIn(1, rows)
            return spanX to spanY
        }

        fun minSpans(
            info: AppWidgetProviderInfo,
            cellWidthPx: Int,
            cellHeightPx: Int,
            columns: Int,
            rows: Int,
            density: Float = 1f
        ): Pair<Int, Int> {
            val d = density.coerceAtLeast(0.1f)
            val cellWDp = (cellWidthPx / d).coerceAtLeast(1f)
            val cellHDp = (cellHeightPx / d).coerceAtLeast(1f)
            val minW = info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth
            val minH = info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight
            val spanX = ceil(minW.toFloat() / cellWDp).toInt().coerceIn(1, columns)
            val spanY = ceil(minH.toFloat() / cellHDp).toInt().coerceIn(1, rows)
            return spanX to spanY
        }
    }
}
