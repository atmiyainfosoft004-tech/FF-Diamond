package com.example.ffdiamond

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ffdiamond.R
import com.example.ffdiamond.apps.AppLauncher
import com.example.ffdiamond.apps.AppUninstaller
import com.example.ffdiamond.data.HomeSettings
import com.example.ffdiamond.data.LauncherPreferences
import com.example.ffdiamond.databinding.ActivityLauncherBinding
import com.example.ffdiamond.model.Container
import com.example.ffdiamond.model.GridShape
import com.example.ffdiamond.model.HomeItem
import com.example.ffdiamond.model.HomeLayout
import com.example.ffdiamond.ui.DeviceProfile
import com.example.ffdiamond.ui.drag.DragController
import com.example.ffdiamond.ui.drag.setHomeItem
import com.example.ffdiamond.ui.drawer.DrawerController
import com.example.ffdiamond.guide.DiamondHomeFragment
import com.example.ffdiamond.ads.InstallSource
import com.example.ffdiamond.funnel.AppLocale
import com.example.ffdiamond.funnel.FunnelNav
import com.example.ffdiamond.funnel.FunnelPreferences
import com.example.ffdiamond.funnel.FunnelStep
import com.example.ffdiamond.ads.AdsBinder
import com.example.ffdiamond.ads.AdsGate
import com.example.ffdiamond.system.AccessChecks
import com.example.ffdiamond.ui.feed.FeedFragment
import com.example.ffdiamond.ui.folder.FolderAddAppsSheet
import com.example.ffdiamond.ui.folder.FolderPanel
import com.example.ffdiamond.ui.settings.HomeSettingsSheet
import com.example.ffdiamond.ui.workspace.AppIconView
import com.example.ffdiamond.ui.workspace.CellLayout
import com.example.ffdiamond.ui.workspace.FolderIconView
import com.example.ffdiamond.ui.workspace.IconCellView
import com.example.ffdiamond.ui.workspace.WorkspaceBinder
import com.example.ffdiamond.widget.GoogleIntents
import com.example.ffdiamond.widget.SearchWidgetProvider
import com.example.ffdiamond.widget.WidgetFrameView
import com.example.ffdiamond.widget.WidgetPickerSheet
import com.example.ffdiamond.widget.WidgetResizeOverlay
import com.example.ffdiamond.widget.WidgetStore
import com.example.ffdiamond.util.applyAppSlideTransitions
import com.example.ffdiamond.util.overrideAppOpenTransition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The home screen. Runs under a transparent, wallpaper-showing theme and is never finished —
 * pressing Back on a launcher must do nothing once there is nothing left to dismiss.
 *
 * Onboarding deliberately lives somewhere else: this activity never shows it, not even on a
 * fresh install, because the system can start it at boot before any user interaction.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private lateinit var binder: WorkspaceBinder
    private lateinit var dragController: DragController
    private lateinit var drawerController: DrawerController

    private var profile: DeviceProfile? = null
    private var settings: HomeSettings = HomeSettings()
    private var layout: HomeLayout = HomeLayout.EMPTY
    private var dragging = false
    private var pendingBind: Pair<HomeLayout, HomeSettings>? = null
    private var openDownloaderPending = false
    private var homePagesReady = false
    private var lastWorkspacePage = -1
    private var skipNextSwipeAd = false
    private var drawerBannerBound = false
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        val info = pendingWidgetInfo
        if (result.resultCode == RESULT_OK &&
            id != AppWidgetManager.INVALID_APPWIDGET_ID &&
            info != null &&
            !isFinishing &&
            !isDestroyed
        ) {
            configureOrPlace(id, info)
        } else {
            cancelPendingWidget()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val openDownloader = intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADER, false)
        super.onCreate(savedInstanceState)
        if (openDownloader) {
            applyAppSlideTransitions()
            overrideAppOpenTransition()
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        revealSystemWallpaper()

        if (!openDownloader && !InstallSource.isOrganic(this) && !AccessChecks.isDefaultLauncher(this)) {
            FunnelNav.open(this, FunnelStep.SET_DEFAULT_GATE)
            finish()
            return
        }

        if (!openDownloader && !FunnelPreferences.isCompletedBlocking(this)) {
            FunnelNav.resumeIncompleteFunnel(this)
        }

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        revealSystemWallpaper()

        val launcher = LauncherApp.from(this)

        binder = WorkspaceBinder(
            context = this,
            workspace = binding.workspace,
            dock = binding.dock,
            indicator = binding.pageIndicator,
            iconCache = launcher.iconCache,
            scope = lifecycleScope,
            onEmptySpaceLongPress = {
                if (!binding.folderPanel.isOpen &&
                    !dragController.isDragging &&
                    drawerController.isFullyClosed
                ) {
                    HomeSettingsSheet.show(supportFragmentManager) { openWidgetPicker() }
                }
            },
            onItemLongPress = ::onItemLongPress,
            onFolderClick = ::openFolder,
            onWidgetEdit = ::editWidget,
            onSearchWidgetClick = { GoogleIntents.openSearch(this) },
            widgets = launcher.widgetStore
        )

        dragController = DragController(
            dragLayer = binding.dragLayer,
            actionBar = binding.dropActionBar,
            host = binder,
            repository = launcher.homeLayoutRepository,
            scope = lifecycleScope,
            profile = { profile ?: launcher.deviceProfile },
            dock = binding.dock,
            onDragStateChanged = { active ->
                dragging = active
                if (!active) {
                    pendingBind?.let { (nextLayout, nextSettings) ->
                        pendingBind = null
                        applyBind(nextLayout, nextSettings)
                    }
                }
            }
        )
        binding.dragLayer.controller = dragController

        drawerController = DrawerController(
            overlay = binding.drawerOverlay,
            homeRoot = binding.launcherRoot,
            workspace = binding.workspace,
            dock = binding.dock,
            indicator = binding.pageIndicator,
            blurView = binding.drawerBlur,
            window = window
        )
        binding.drawerOverlay.controller = drawerController
        binding.drawerOverlay.onOpened = { bindDrawerBannerIfOpen() }
        binding.drawerOverlay.attach(
            iconCache = launcher.iconCache,
            scope = lifecycleScope,
            appsProvider = { launcher.appRepository.apps.value }
        )
        binding.drawerOverlay.onOverflowClick = {
            HomeSettingsSheet.show(supportFragmentManager) { openWidgetPicker() }
        }
        binding.drawerOverlay.onPinToHome = { app ->
            lifecycleScope.launch {
                val grid = GridShape(settings.columns, settings.rows)
                val added = launcher.homeLayoutRepository.pinToHome(app, grid)
                Toast.makeText(
                    this@LauncherActivity,
                    if (added) R.string.pin_added_to_home else R.string.pin_already_on_home,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.drawerOverlay.onUninstall = { app ->
            AppUninstaller.start(this, app.packageName, app.user)
        }
        binding.dragLayer.drawer = drawerController
        binding.dragLayer.allowDrawerGesture = {
            !binding.folderPanel.isOpen &&
                !dragController.isDragging &&
                !binding.widgetResize.isEditing &&
                binding.workspace.scrollProgress > 0.5f &&
                binding.workspace.scrollProgress < binding.workspace.childCount - 1.5f
        }

        binding.folderPanel.memberBinder = FolderPanel.MemberBinder { page, members, folder ->
            bindFolderMembers(page, members, folder)
        }
        binding.folderPanel.onRename = { folderId, title ->
            lifecycleScope.launch { launcher.homeLayoutRepository.renameFolder(folderId, title) }
        }
        binding.folderPanel.onAddApps = { folder ->
            FolderAddAppsSheet.show(
                supportFragmentManager,
                candidates = addableApps(folder),
                iconCache = launcher.iconCache,
                themed = settings.themedIcons
            ) { app ->
                lifecycleScope.launch {
                    launcher.homeLayoutRepository.addToFolder(
                        app.id, folder.folderId, folder.members.size
                    )
                }
            }
        }

        // The indicator is driven by the raw scroll rather than by settled pages, which is what
        // makes the active marker grow under the finger instead of jumping when the swipe lands.
        binding.workspace.onScroll = { progress ->
            binding.pageIndicator.setPosition(progress)
            applyHomeChrome(progress)
        }
        binding.workspace.onPageChanged = { page ->
            feedFragment()?.setActive(page == 0)
            diamondHomeFragment()?.resetScrollToTop()
            applyHomeChrome(binding.workspace.scrollProgress)
            maybeShowSwipeAd(page)
        }

        // Read the grid shape synchronously so the very first measure pass already has it. Waiting
        // for the flow would lay the whole home screen out once at the wrong size and again a
        // frame later, which is visible as a flash on a cold start.
        profile = profileFor(LauncherPreferences.homeSettingsBlocking(this)).also {
            binding.launcherRoot.applyProfile(it)
        }

        openDownloaderPending = intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADER, false)
        if (openDownloaderPending) skipNextSwipeAd = true

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.feed_container, FeedFragment())
                .replace(R.id.downloader_container, DiamondHomeFragment())
                .commit()
        }

        binding.widgetResize.onCommit = { itemId, page, cellX, cellY, spanX, spanY ->
            val widgetItem = (layout.pages.flatten() + layout.dock)
                .filterIsInstance<HomeItem.Widget>()
                .firstOrNull { it.id == itemId }
            if (widgetItem != null) {
                val (cellW, cellH) = widgetCellPx()
                val opts = launcher.widgetStore.optionsFor(spanX, spanY, cellW, cellH)
                launcher.widgetStore.applyOptions(widgetItem.appWidgetId, opts)
            }
            lifecycleScope.launch {
                launcher.homeLayoutRepository.resizeWidget(itemId, page, cellX, cellY, spanX, spanY)
            }
        }
        binding.widgetResize.onRemove = { widget ->
            lifecycleScope.launch { launcher.homeLayoutRepository.remove(widget.id) }
        }

        launcher.appRepository.start()
        binder.landOnDownloader = openDownloaderPending
        if (openDownloaderPending) {
            binding.launcherRoot.visibility = View.INVISIBLE
            binding.launcherCover.visibility = View.VISIBLE
        }

        observeHomeScreen()
        observeDrawerApps()
        handleSearchIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.widgetResize.isEditing -> binding.widgetResize.dismiss(commit = true)
                    dragController.isDragging -> dragController.cancelDrag()
                    binding.folderPanel.isOpen -> binding.folderPanel.close()
                    binding.drawerOverlay.closePopup() -> Unit
                    binding.drawerOverlay.closeSearch() -> Unit
                    drawerController.isOpen -> drawerController.close()
                    binding.workspace.currentPage == 0 && feedFragment()?.onBack() == true -> Unit
                    binding.workspace.currentPage == downloaderPageIndex() ->
                        binding.workspace.snapToPage(HOME_PAGE_INDEX, animate = true)
                    else -> binding.workspace.snapToPage(HOME_PAGE_INDEX, animate = true)
                }
            }
        })
    }

    private fun feedFragment(): FeedFragment? =
        supportFragmentManager.findFragmentById(R.id.feed_container) as? FeedFragment

    private fun diamondHomeFragment(): DiamondHomeFragment? =
        supportFragmentManager.findFragmentById(R.id.downloader_container) as? DiamondHomeFragment

    /** Dock on home grid pages only — hidden on Discover (left) and the downloader (right). */
    private fun applyHomeChrome(progress: Float) {
        if (::drawerController.isInitialized && !drawerController.isFullyClosed) {
            drawerController.syncHomeLayer()
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = false
            return
        }
        val last = (binding.workspace.childCount - 1).coerceAtLeast(0)
        val onDiscover = progress < 0.5f
        val onDownloader = last > HOME_PAGE_INDEX && progress > last - 0.5f
        val showDock = !onDiscover && !onDownloader
        val dockAlpha = when {
            showDock -> 1f
            onDiscover -> progress.coerceIn(0f, 1f)
            else -> (last - progress).coerceIn(0f, 1f)
        }
        binding.dock.alpha = dockAlpha
        binding.dock.visibility = if (showDock) View.VISIBLE else View.INVISIBLE

        binding.pageIndicator.alpha = dockAlpha
        binding.pageIndicator.visibility = if (showDock) View.VISIBLE else View.INVISIBLE
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = onDiscover
    }

    fun openDownloaderPage() {
        if (dragController.isDragging) dragController.cancelDrag()
        if (binding.widgetResize.isEditing) binding.widgetResize.dismiss(commit = true)
        if (binding.folderPanel.isOpen) binding.folderPanel.close()
        binding.drawerOverlay.closePopup()
        binding.drawerOverlay.closeSearch()
        if (drawerController.isOpen) drawerController.close()
        binding.workspace.snapToPage(downloaderPageIndex(), animate = true)
        diamondHomeFragment()?.resetScrollToTop()
    }

    private fun downloaderPageIndex(): Int =
        (binding.workspace.childCount - 1).coerceAtLeast(0)

    private fun editWidget(item: HomeItem.Widget, @Suppress("UNUSED_PARAMETER") view: WidgetFrameView, dbPage: Int) {
        val page = binder.pageAt(dbPage) ?: return
        val info = LauncherApp.from(this).widgetStore.info(item.appWidgetId)
        val density = resources.displayMetrics.density
        val (minX, minY) = if (info != null) {
            WidgetStore.minSpans(info, page.cellWidth, page.cellHeight, page.columns, page.rows, density)
        } else {
            1 to 1
        }
        binding.widgetResize.show(
            WidgetResizeOverlay.Edit(
                item = item,
                page = page,
                dbPage = dbPage,
                minSpanX = minX,
                minSpanY = minY
            )
        )
    }

    fun openWidgetPicker() {
        val store = LauncherApp.from(this).widgetStore
        WidgetPickerSheet.show(
            supportFragmentManager,
            store.homeProviders()
        ) { info -> beginAddWidget(info) }
    }

    private fun beginAddWidget(info: AppWidgetProviderInfo) {
        val store = LauncherApp.from(this).widgetStore
        val id = store.allocateId()
        pendingWidgetId = id
        pendingWidgetInfo = info
        val options = widgetOptions(info)
        if (store.bindIfAllowed(id, info, options)) {
            configureOrPlace(id, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }
            try {
                bindWidgetLauncher.launch(intent)
            } catch (_: Exception) {
                cancelPendingWidget()
                Toast.makeText(this, R.string.error_add_widget, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureOrPlace(id: Int, info: AppWidgetProviderInfo) {
        val store = LauncherApp.from(this).widgetStore
        runCatching { store.applyOptions(id, widgetOptions(info)) }
        if (info.configure == null) {
            placeWidget(id, info)
            return
        }
        try {
            store.host.startAppWidgetConfigureActivityForResult(
                this,
                id,
                0,
                REQUEST_CONFIGURE_WIDGET,
                null
            )
        } catch (_: Throwable) {
            // Google Search (and some Gallery builds) keep configure unexported. The widget
            // still binds and renders, so place it instead of crashing the home screen.
            placeWidget(id, info)
        }
    }

    private fun canLaunchWidgetConfigure(info: AppWidgetProviderInfo): Boolean {
        val component = info.configure ?: return false
        val activityInfo = runCatching {
            packageManager.getActivityInfo(component, 0)
        }.getOrNull() ?: return false
        return activityInfo.exported
    }

    @Deprecated("AppWidgetHost configure still reports through this callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) onConfigureWidgetResult(resultCode)
    }

    private fun onConfigureWidgetResult(resultCode: Int) {
        val id = pendingWidgetId
        val info = pendingWidgetInfo
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || info == null) return
        when {
            resultCode == RESULT_OK -> placeWidget(id, info)
            WidgetStore.isConfigureOptional(info) -> placeWidget(id, info)
            else -> {
                cancelPendingWidget()
                Toast.makeText(this, R.string.error_add_widget, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun widgetOptions(info: AppWidgetProviderInfo): android.os.Bundle {
        val store = LauncherApp.from(this).widgetStore
        val (cellW, cellH) = widgetCellPx()
        val columns = profile?.columns ?: 4
        val rows = profile?.rows ?: 6
        val density = resources.displayMetrics.density
        val (spanX, spanY) = WidgetStore.spansFor(info, cellW, cellH, columns, rows, density)
        return store.optionsFor(spanX, spanY, cellW, cellH)
    }

    private fun widgetCellPx(): Pair<Int, Int> {
        val page = binder.pageAt(0)
        if (page != null && page.cellWidth > 0 && page.cellHeight > 0) {
            return page.cellWidth to page.cellHeight
        }
        val columns = (profile?.columns ?: 4).coerceAtLeast(1)
        val cell = (resources.displayMetrics.widthPixels / columns).coerceAtLeast(1)
        return cell to cell
    }

    private fun placeWidget(id: Int, info: AppWidgetProviderInfo) {
        val launcher = LauncherApp.from(this)
        launcher.widgetStore.applyOptions(id, widgetOptions(info))
        if (info.provider == SearchWidgetProvider.component(this)) {
            SearchWidgetProvider.push(this, launcher.widgetStore.manager, id)
        }
        val (cellW, cellH) = widgetCellPx()
        val columns = profile?.columns ?: 4
        val rows = profile?.rows ?: 6
        val density = resources.displayMetrics.density
        val (spanX, spanY) = WidgetStore.spansFor(info, cellW, cellH, columns, rows, density)
        val title = info.loadLabel(packageManager).toString()
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetInfo = null
        lifecycleScope.launch {
            launcher.homeLayoutRepository.addWidget(
                appWidgetId = id,
                provider = info.provider.flattenToShortString(),
                title = title,
                spanX = spanX,
                spanY = spanY,
                grid = GridShape(columns, rows)
            )
        }
    }

    private fun cancelPendingWidget() {
        val id = pendingWidgetId
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetInfo = null
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            LauncherApp.from(this).widgetStore.delete(id)
        }
    }

    private fun handleSearchIntent(intent: Intent?) {
        val open = intent?.action == SearchWidgetProvider.ACTION_OPEN_SEARCH ||
            intent?.getBooleanExtra(SearchWidgetProvider.EXTRA_OPEN_SEARCH, false) == true
        if (open) GoogleIntents.openSearch(this)
    }

    private fun onItemLongPress(
        item: HomeItem,
        view: IconCellView,
        container: Container,
        dbPage: Int
    ): Boolean {
        if (binding.folderPanel.isOpen) binding.folderPanel.close(immediate = true)
        return dragController.startDrag(item, view, container, dbPage)
    }

    private fun openFolder(folder: HomeItem.Folder, icon: FolderIconView) {
        val profile = profile ?: return
        binding.folderPanel.open(folder, icon, binding.launcherRoot, profile)
    }

    private fun bindFolderMembers(
        page: CellLayout,
        members: List<HomeItem.App>,
        folder: HomeItem.Folder
    ): List<View> {
        val profile = profile ?: return emptyList()
        val launcher = LauncherApp.from(this)
        val themed = settings.themedIcons
        page.removeAllViews()

        return members.mapIndexed { index, member ->
            AppIconView(this).also { view ->
                view.applyProfile(profile)
                view.bind(member.info, launcher.iconCache.placeholderBitmap(), member.title)
                val cached = launcher.iconCache.peek(member.info, themed)
                if (cached != null) {
                    view.setIcon(cached, animate = false)
                } else {
                    view.iconJob = lifecycleScope.launch {
                        view.setIcon(launcher.iconCache.get(member.info, themed), animate = true)
                    }
                }
                view.setOnClickListener { AppLauncher.launch(it, member.info) }
                view.setOnLongClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    if (AppLauncher.consumeOwnLongPress(it, member.info.packageName)) {
                        true
                    } else {
                        val started = dragController.startDrag(
                            member, view, Container.FOLDER, folder.page
                        )
                        if (started) binding.folderPanel.close(immediate = true)
                        started
                    }
                }
                view.setHomeItem(member)
                page.addInCell(view, index % FOLDER_COLUMNS, index / FOLDER_COLUMNS)
            }
        }
    }

    private fun addableApps(folder: HomeItem.Folder): List<HomeItem.App> {
        val inFolder = folder.members.mapTo(HashSet()) { it.info.key }
        return layout.apps().filter { it.info.key !in inFolder }
    }

    /**
     * One collector for both inputs. The layout and the display settings change independently but
     * the workspace can only be built from both at once, so combining them avoids rebuilding the
     * grid twice when they happen to arrive together.
     *
     * The layout arriving here is already reconciled and already persisted — the repository has
     * matched the stored rows against the installed apps and written any repair back before
     * emitting, so what the workspace draws is what the next cold start will read.
     */
    private fun observeHomeScreen() {
        val launcher = LauncherApp.from(this)
        val settingsFlow = LauncherPreferences.homeSettings(this)
        val grid = settingsFlow.map { GridShape(it.columns, it.rows) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    launcher.homeLayoutRepository.layout(grid),
                    settingsFlow
                ) { layout: HomeLayout, current: HomeSettings -> layout to current }
                    .collect { (nextLayout, current) ->
                        if (dragging) {
                            pendingBind = nextLayout to current
                        } else {
                            applyBind(nextLayout, current)
                        }
                    }
            }
        }
    }

    private fun observeDrawerApps() {
        val launcher = LauncherApp.from(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    launcher.appRepository.apps,
                    LauncherPreferences.homeSettings(this@LauncherActivity)
                ) { apps, current -> apps to current }
                    .collect { (apps, current) ->
                        val nextProfile = profileFor(current)
                        binding.drawerOverlay.bind(apps, nextProfile, current.themedIcons)
                    }
            }
        }
    }

    private fun applyBind(nextLayout: HomeLayout, current: HomeSettings) {
        layout = nextLayout
        settings = current
        val nextProfile = profileFor(current)
        profile = nextProfile
        binding.launcherRoot.applyProfile(nextProfile)
        binder.bind(nextLayout, nextProfile, current)
        applyHomeChrome(binding.workspace.scrollProgress)
        if (openDownloaderPending) {
            openDownloaderPending = false
            binding.workspace.snapToPage(downloaderPageIndex(), animate = false)
            binding.launcherRoot.visibility = View.VISIBLE
            binding.launcherCover.visibility = View.GONE
        }

        if (binding.folderPanel.isOpen) {
            val open = binding.folderPanel.folder()
            val updated = open?.let { nextLayout.folder(it.folderId) }
            if (updated == null) {
                binding.folderPanel.close(immediate = true)
            } else {
                binding.folderPanel.refresh(updated, nextProfile)
            }
        }
    }

    private fun profileFor(settings: HomeSettings) =
        LauncherApp.from(this).deviceProfile.withGrid(settings.columns, settings.rows)

    /** Home and the app drawer stay clear so the picture set in Android settings shows through. */
    private fun revealSystemWallpaper() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        findViewById<View>(android.R.id.content)?.setBackgroundColor(Color.TRANSPARENT)
        if (::binding.isInitialized) {
            binding.root.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * Home pressed while already home. The activity is singleTask, so this arrives instead of a
     * fresh instance and must not recreate anything.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!InstallSource.isOrganic(this) && !AccessChecks.isDefaultLauncher(this)) {
            FunnelNav.open(this, FunnelStep.SET_DEFAULT_GATE)
            finish()
            return
        }
        skipNextSwipeAd = true
        if (dragController.isDragging) dragController.cancelDrag()
        if (binding.widgetResize.isEditing) binding.widgetResize.dismiss(commit = true)
        if (binding.folderPanel.isOpen) binding.folderPanel.close()
        binding.drawerOverlay.closePopup()
        binding.drawerOverlay.closeSearch()
        if (drawerController.isOpen) drawerController.close()
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADER, false)) {
            binding.workspace.snapToPage(downloaderPageIndex(), animate = true)
            diamondHomeFragment()?.resetScrollToTop()
        } else {
            binding.workspace.snapToPage(HOME_PAGE_INDEX, animate = true)
        }
        handleSearchIntent(intent)
        FunnelNav.coverHomeIfNeeded(this)
        FunnelNav.resumeIncompleteFunnel(this)
    }

    override fun onResume() {
        super.onResume()
        if (!InstallSource.isOrganic(this) && !AccessChecks.isDefaultLauncher(this)) {
            FunnelNav.open(this, FunnelStep.SET_DEFAULT_GATE)
            finish()
            return
        }
        if (!FunnelPreferences.isCompletedBlocking(this)) {
            FunnelNav.resumeIncompleteFunnel(this)
            return
        }
        AdsGate.onResume(this)
        bindDrawerBannerIfOpen()
        if (drawerController.isOpen) drawerController.syncHomeLayer()
        binding.drawerOverlay.clearSearchIfPending()
    }

    private fun maybeShowSwipeAd(page: Int) {
        if (!homePagesReady) {
            homePagesReady = true
            lastWorkspacePage = page
            return
        }
        if (skipNextSwipeAd) {
            skipNextSwipeAd = false
            lastWorkspacePage = page
            return
        }
        if (page == lastWorkspacePage) return
        val from = lastWorkspacePage
        lastWorkspacePage = page
        val downloader = downloaderPageIndex()
        val extraGrid = { p: Int -> p > HOME_PAGE_INDEX && p < downloader }
        val leavingHome = from == HOME_PAGE_INDEX && page != HOME_PAGE_INDEX
        if (leavingHome && extraGrid(page)) return
        val afterDotScreen = extraGrid(from) && page == downloader
        if (!leavingHome && !afterDotScreen) return
        if (!FunnelPreferences.isCompletedBlocking(this)) return
        if (drawerController.isOpen) return
        AdsGate.onHomeSwipe(this)
    }

    private fun bindDrawerBannerIfOpen() {
        if (drawerBannerBound || !drawerController.isFullyOpen) return
        drawerBannerBound = true
        AdsBinder.bindBanner(this, binding.drawerOverlay.findViewById(R.id.drawer_ad_banner))
    }

    override fun onStart() {
        super.onStart()
        if (!InstallSource.isOrganic(this) && !AccessChecks.isDefaultLauncher(this)) {
            FunnelNav.open(this, FunnelStep.SET_DEFAULT_GATE)
            finish()
            return
        }
        FunnelNav.coverHomeIfNeeded(this)
        FunnelNav.resumeIncompleteFunnel(this)
        val app = LauncherApp.from(this)
        app.widgetStore.startListening()
        app.openSearchHandler = { GoogleIntents.openSearch(this) }
        if (app.pendingOpenSearch) {
            app.pendingOpenSearch = false
            GoogleIntents.openSearch(this)
        }
        binding.drawerOverlay.clearSearchIfPending()
    }

    override fun onStop() {
        val app = LauncherApp.from(this)
        app.openSearchHandler = null
        app.widgetStore.stopListening()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            drawerController.prepareBlur()
            if (drawerController.isOpen) drawerController.syncHomeLayer()
            binding.drawerOverlay.clearSearchIfPending()
        }
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADER = "open_downloader"

        /** Child 0 of the workspace is Discover; child 1 is home; the last child is the downloader. */
        private const val HOME_PAGE_INDEX = 1
        private const val FOLDER_COLUMNS = 4
        private const val REQUEST_CONFIGURE_WIDGET = 0xA711
    }
}
