package com.example.ffdiamond.home

import android.content.Context
import com.example.ffdiamond.apps.DefaultLayout
import com.example.ffdiamond.data.LauncherPreferences
import androidx.room.withTransaction
import com.example.ffdiamond.data.db.FolderEntity
import com.example.ffdiamond.data.db.HomeItemEntity
import com.example.ffdiamond.data.db.LauncherDatabase
import com.example.ffdiamond.model.AppInfo
import com.example.ffdiamond.model.Container
import com.example.ffdiamond.model.GridShape
import com.example.ffdiamond.model.HomeItem
import com.example.ffdiamond.model.HomeItemType
import com.example.ffdiamond.model.HomeLayout
import com.example.ffdiamond.widget.SearchWidgetProvider
import com.example.ffdiamond.widget.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * The home screen layout: what is where, and what it resolves to.
 *
 * Two sources have to agree. The database remembers positions the user chose; `LauncherApps`
 * reports which apps actually exist right now. Neither is authoritative on its own — a stored row
 * for an uninstalled app is a hole, and an installed app with no row is invisible — so every
 * emission runs both through [reconcile], which repairs the difference *and writes the repair
 * back*. That is what makes the layout stable: the next launch reads a database that already
 * agrees with reality instead of re-deriving the same fix.
 */
class HomeLayoutRepository(
    context: Context,
    /** Already gated on the first enumeration finishing; see `AppRepository.loadedApps`. */
    private val apps: Flow<List<AppInfo>>,
    private val database: LauncherDatabase,
    private val widgets: WidgetStore? = null
) {

    private val appContext = context.applicationContext
    private val homeItemDao = database.homeItemDao()
    private val folderDao = database.folderDao()

    /**
     * The resolved layout, re-emitted whenever the stored rows, the installed apps or the grid
     * shape change.
     *
     * Reconciliation writes, and those writes make Room emit again — so every change settles in two
     * passes, the second finding nothing to do. `distinctUntilChanged` keeps that second pass from
     * reaching the workspace as a redundant rebuild.
     */
    fun layout(grid: Flow<GridShape>): Flow<HomeLayout> =
        combine(
            homeItemDao.observeAll(),
            folderDao.observeAll(),
            apps,
            grid.distinctUntilChanged()
        ) { rows, folders, installed, shape -> Inputs(rows, folders, installed, shape) }
            .map { reconcile(it) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    private data class Inputs(
        val rows: List<HomeItemEntity>,
        val folders: List<FolderEntity>,
        val apps: List<AppInfo>,
        val grid: GridShape
    )

    private suspend fun reconcile(input: Inputs): HomeLayout {
        val seeded = input.rows.ifEmpty { seedIfNeeded(input.apps, input.grid) }
        val sparse = sparsifyIfNeeded(seeded, input.grid)
        val googleHome = applyGoogleHomeIfNeeded(sparse, input.apps, input.grid)
        val docked = applyDockOrderIfNeeded(googleHome, input.apps)
        val rows = ensureSearchWidget(
            dropForeignSearchWidgets(fillHotseatIfNeeded(docked, input.apps)),
            input.grid
        )
        val folders = folderDao.all()

        // Cell coordinates only mean something relative to a grid shape. When the user changes the
        // shape, items that no longer fit are relocated; items that still fit keep their cells so
        // a sparse home is not poured back into a packed dump.
        val arrangedFor = LauncherPreferences.arrangedGrid(appContext)
        val analysis = analyse(
            rows = rows,
            folders = folders,
            apps = input.apps,
            grid = input.grid
        )

        // One transaction, so a repair is either fully applied or not applied at all. A half-written
        // reconciliation would leave the very inconsistency it exists to fix.
        val added = database.withTransaction {
            // Deletes first: they free the cells relocations and additions are about to claim.
            if (analysis.removedIds.isNotEmpty()) homeItemDao.deleteByIds(analysis.removedIds)
            if (analysis.removedFolderIds.isNotEmpty()) {
                folderDao.deleteByIds(analysis.removedFolderIds)
            }
            if (analysis.changed.isNotEmpty()) homeItemDao.update(analysis.changed)

            // Inserted last and read back with their generated ids, so nothing downstream — least
            // of all drag and drop — ever sees two items both claiming id 0.
            if (analysis.added.isEmpty()) {
                emptyList()
            } else {
                val ids = homeItemDao.insert(analysis.added)
                analysis.added.zip(ids) { entity, id -> entity.copy(id = id) }
            }
        }

        // Recorded only once the repack it describes is durably written, so a crash part way
        // through leaves the reshape still pending rather than silently skipped.
        if (arrangedFor != input.grid) LauncherPreferences.setArrangedGrid(appContext, input.grid)

        val survivingFolders = folders.filterNot { it.id in analysis.removedFolderIds }
        return build(analysis.surviving + added, survivingFolders, input.apps)
    }

    // ---------------------------------------------------------------- seeding

    /**
     * Writes the starting layout, once ever.
     *
     * Gated on a preference rather than on the table being empty, because a user who deleted every
     * icon meant it, and re-seeding would be the launcher quietly overruling them.
     */
    private suspend fun seedIfNeeded(apps: List<AppInfo>, grid: GridShape): List<HomeItemEntity> {
        if (apps.isEmpty() || LauncherPreferences.isLayoutSeeded(appContext)) return emptyList()

        val seeds = defaultLayout(apps, grid)
        val ids = homeItemDao.insert(seeds)
        LauncherPreferences.setLayoutSeeded(appContext)
        LauncherPreferences.setSparseHome(appContext)
        LauncherPreferences.setArrangedGrid(appContext, grid)
        return seeds.zip(ids) { entity, id -> entity.copy(id = id) }
    }

    /**
     * One-shot conversion of a packed first-run dump into the sparse home: dock stays, a search
     * bar stays (or is seeded), two rows of apps stay, everything else is hidden in the drawer.
     */
    private suspend fun sparsifyIfNeeded(
        rows: List<HomeItemEntity>,
        grid: GridShape
    ): List<HomeItemEntity> {
        if (LauncherPreferences.isSparseHome(appContext)) return rows
        if (rows.isEmpty()) {
            LauncherPreferences.setSparseHome(appContext)
            return rows
        }

        val updated = database.withTransaction {
            val occupancy = Occupancy(grid)
            val desktop = rows.filter { it.container == Container.DESKTOP.name }
            val widgetsOnDesktop = desktop.filter { it.type == HomeItemType.WIDGET.name }
            val desktopIcons = desktop
                .filter { it.type == HomeItemType.APP.name || it.type == HomeItemType.FOLDER.name }
                .sortedWith(compareBy({ it.page }, { it.cellY }, { it.cellX }))

            val toUpdate = mutableListOf<HomeItemEntity>()
            val search = widgetsOnDesktop.firstOrNull(::isOursSearch)
            if (search != null) {
                val placed = search.copy(
                    page = 0,
                    cellX = 0,
                    cellY = 0,
                    spanX = grid.columns.coerceAtLeast(1),
                    spanY = 1
                )
                occupancy.occupy(0, placed.cellX, placed.cellY, placed.spanX, placed.spanY)
                if (placed != search) toUpdate += placed
            } else {
                val seeded = searchBarEntity(grid.columns)
                occupancy.occupy(0, seeded.cellX, seeded.cellY, seeded.spanX, seeded.spanY)
                homeItemDao.insert(seeded)
            }
            if (grid.rows > 1) {
                occupancy.occupy(0, 0, 1, grid.columns, 1)
            }

            widgetsOnDesktop.filter { it.id != search?.id }.forEach { widget ->
                val keep = widget.page == 0 &&
                    occupancy.isFree(0, widget.cellX, widget.cellY, widget.spanX, widget.spanY)
                if (keep) {
                    occupancy.occupy(0, widget.cellX, widget.cellY, widget.spanX, widget.spanY)
                } else {
                    val slot = occupancy.firstFree(widget.spanX, widget.spanY)
                    toUpdate += widget.copy(page = slot.page, cellX = slot.cellX, cellY = slot.cellY)
                }
            }

            val keepers = desktopIcons.take(SparseHome.homeAppSlots(grid))
            val extras = desktopIcons.drop(keepers.size)
            keepers.forEach { icon ->
                val cell = SparseHome.nextSlot(occupancy, grid)
                if (cell == null) {
                    hideDesktop(icon)
                } else {
                    occupancy.occupy(0, cell.first, cell.second)
                    toUpdate += icon.copy(page = 0, cellX = cell.first, cellY = cell.second)
                }
            }
            extras.forEach { hideDesktop(it) }
            if (toUpdate.isNotEmpty()) homeItemDao.update(toUpdate)
            homeItemDao.all()
        }
        LauncherPreferences.setSparseHome(appContext)
        return updated
    }

    /**
     * One-shot: four last-row shortcuts and a folder of installed Google products. Dock and the
     * search bar stay put; leftover desktop icons go back to the drawer.
     */
    private suspend fun applyGoogleHomeIfNeeded(
        rows: List<HomeItemEntity>,
        apps: List<AppInfo>,
        grid: GridShape
    ): List<HomeItemEntity> {
        // An empty catalog is "not loaded yet", not "the user has no apps". Hiding the desktop
        // against that and flipping the one-shot flag is what left home with only the search bar.
        if (apps.isEmpty()) return rows
        if (LauncherPreferences.isGoogleHome(appContext)) {
            return restoreGoogleHomeIfEmpty(rows, apps, grid)
        }
        if (rows.isEmpty()) {
            LauncherPreferences.setGoogleHome(appContext)
            return rows
        }

        val updated = database.withTransaction {
            rows.filter {
                it.container == Container.DESKTOP.name &&
                    (it.type == HomeItemType.APP.name || it.type == HomeItemType.FOLDER.name)
            }.forEach { hideDesktop(it) }

            arrangeGoogleHome(apps, grid)
            homeItemDao.all()
        }
        LauncherPreferences.setGoogleHome(appContext)
        return updated
    }

    /**
     * The one-shot pass can finish with a search bar and nothing else if placement ran against an
     * empty app list, or if analyse later dropped an empty Google folder. Put the folder and the
     * four last-row shortcuts back without hiding anything the user has since pinned.
     */
    private suspend fun restoreGoogleHomeIfEmpty(
        rows: List<HomeItemEntity>,
        apps: List<AppInfo>,
        grid: GridShape
    ): List<HomeItemEntity> {
        val hasDesktopIcons = rows.any {
            it.container == Container.DESKTOP.name &&
                (it.type == HomeItemType.APP.name || it.type == HomeItemType.FOLDER.name)
        }
        if (hasDesktopIcons) return rows
        return database.withTransaction {
            arrangeGoogleHome(apps, grid)
            homeItemDao.all()
        }
    }

    /**
     * Dock is Phone, Messages, this app, Camera — including devices that already had a sparse home.
     */
    private suspend fun applyDockOrderIfNeeded(
        rows: List<HomeItemEntity>,
        apps: List<AppInfo>
    ): List<HomeItemEntity> {
        if (apps.isEmpty()) return rows
        if (LauncherPreferences.isDockOrdered(appContext)) return rows
        val desired = DefaultLayout.dockApps(appContext, apps, GridShape.DOCK_COLUMNS)
        if (desired.isEmpty()) return rows
        return database.withTransaction {
            homeItemDao.all()
                .filter { it.container == Container.DOCK.name }
                .forEach { hideDesktop(it) }
            desired.forEachIndexed { index, app ->
                placeApp(app, Container.DOCK, page = 0, cellX = index, cellY = 0)
            }
            LauncherPreferences.setDockOrdered(appContext)
            LauncherPreferences.setOwnAppPinned(appContext)
            LauncherPreferences.setHotseatFilled(appContext)
            homeItemDao.all()
        }
    }

    /**
     * The Google-folder pass stole Phone/Messages/Chrome/Camera off the dock because those are
     * also Google packages. Put the four role apps back into the hotseat once.
     */
    private suspend fun fillHotseatIfNeeded(
        rows: List<HomeItemEntity>,
        apps: List<AppInfo>
    ): List<HomeItemEntity> {
        if (apps.isEmpty()) return rows
        val docked = rows.count { it.container == Container.DOCK.name }
        if (docked >= GridShape.DOCK_COLUMNS) {
            LauncherPreferences.setHotseatFilled(appContext)
            return rows
        }
        // A short dock after the first fill is the user removing an icon. An empty dock after
        // the flag is set is the same one-shot miss as the empty desktop: repair it.
        if (LauncherPreferences.isHotseatFilled(appContext) && docked > 0) return rows

        val updated = database.withTransaction {
            val taken = BooleanArray(GridShape.DOCK_COLUMNS)
            homeItemDao.all()
                .filter { it.container == Container.DOCK.name }
                .forEach { row ->
                    if (row.cellX in taken.indices) taken[row.cellX] = true
                }
            DefaultLayout.dockApps(appContext, apps, GridShape.DOCK_COLUMNS).forEach { app ->
                val slot = taken.indexOfFirst { !it }
                if (slot < 0) return@forEach
                taken[slot] = true
                placeApp(app, Container.DOCK, page = 0, cellX = slot, cellY = 0)
            }
            homeItemDao.all()
        }
        val filled = updated.count { it.container == Container.DOCK.name }
        if (filled > 0) LauncherPreferences.setHotseatFilled(appContext)
        return updated
    }

    /**
     * Google's own empty Search widget can sit on row 1 from an earlier bind and push our pill
     * against the status bar. Drop it; the launcher already draws its own bar.
     */
    private suspend fun dropForeignSearchWidgets(rows: List<HomeItemEntity>): List<HomeItemEntity> {
        val extras = rows.filter { isForeignSearchWidget(it) }
        if (extras.isEmpty()) return rows
        extras.forEach { extra -> extra.appWidgetId?.let { widgets?.delete(it) } }
        homeItemDao.deleteByIds(extras.map { it.id })
        return homeItemDao.all()
    }

    private fun isForeignSearchWidget(row: HomeItemEntity): Boolean {
        if (row.type != HomeItemType.WIDGET.name) return false
        val component = row.component ?: return false
        if (SearchWidgetProvider.isOurs(component, appContext)) return false
        return component.contains("googlequicksearchbox", ignoreCase = true)
    }

    private suspend fun hideDesktop(item: HomeItemEntity) {
        homeItemDao.place(item.id, Container.HIDDEN.name, 0, 0, 0, folderId = null, rank = 0)
        if (item.type == HomeItemType.FOLDER.name && item.folderId != null) {
            homeItemDao.membersOf(item.folderId).forEach { member ->
                homeItemDao.place(
                    member.id, Container.HIDDEN.name, 0, 0, 0, folderId = null, rank = 0
                )
            }
        }
    }

    private fun defaultLayout(apps: List<AppInfo>, grid: GridShape): List<HomeItemEntity> {
        val dock = DefaultLayout.dockApps(appContext, apps, GridShape.DOCK_COLUMNS)
        val entities = mutableListOf<HomeItemEntity>()
        dock.forEachIndexed { index, app ->
            entities += appRow(app, Container.DOCK, page = 0, cellX = index, cellY = 0)
        }
        entities += searchBarEntity(grid.columns)
        return entities
    }

    /**
     * The Google-style search pill belongs on row 0 of page 0. Reinstalls and failed binds used to
     * drop it; put it back whenever it is missing. The workspace draws the pill itself, so a live
     * AppWidget id is not required.
     */
    private suspend fun ensureSearchWidget(
        rows: List<HomeItemEntity>,
        grid: GridShape
    ): List<HomeItemEntity> {
        val span = grid.columns.coerceAtLeast(1)
        val ours = rows.filter(::isOursSearch)
        val primary = ours.firstOrNull { it.container == Container.DESKTOP.name }
        if (primary != null) {
            val extras = ours.filter { it.id != primary.id }
            val needsPlace = primary.page != 0 ||
                primary.cellX != 0 ||
                primary.cellY != 0 ||
                primary.spanX != span ||
                primary.spanY != 1
            if (extras.isEmpty() && !needsPlace) return rows
            return database.withTransaction {
                if (extras.isNotEmpty()) {
                    extras.forEach { extra -> extra.appWidgetId?.let { widgets?.delete(it) } }
                    homeItemDao.deleteByIds(extras.map { it.id })
                }
                if (needsPlace) homeItemDao.placeSpan(primary.id, 0, 0, 0, span, 1)
                homeItemDao.all()
            }
        }
        return database.withTransaction {
            if (ours.isNotEmpty()) {
                ours.forEach { extra -> extra.appWidgetId?.let { widgets?.delete(it) } }
                homeItemDao.deleteByIds(ours.map { it.id })
            }
            homeItemDao.insert(searchBarEntity(span))
            homeItemDao.all()
        }
    }

    private fun searchBarEntity(columns: Int): HomeItemEntity =
        widgets?.seedSearchWidget(columns)
            ?: HomeItemEntity(
                type = HomeItemType.WIDGET.name,
                page = 0,
                cellX = 0,
                cellY = 0,
                spanX = columns.coerceAtLeast(1),
                spanY = 1,
                container = Container.DESKTOP.name,
                component = SearchWidgetProvider.component(appContext).flattenToShortString(),
                appWidgetId = 0,
                title = appContext.getString(com.example.ffdiamond.R.string.search_widget_name)
            )

    private fun isOursSearch(row: HomeItemEntity): Boolean =
        row.type == HomeItemType.WIDGET.name &&
            row.component?.let { SearchWidgetProvider.isOurs(it, appContext) } == true

    private fun markClockOccupied(
        occupancy: Occupancy,
        desktopItems: Iterable<HomeItemEntity>,
        grid: GridShape
    ) {
        val search = desktopItems.firstOrNull {
            it.container == Container.DESKTOP.name && it.page == 0 && isOursSearch(it)
        }
        val clockY = (search?.cellY ?: 0) + (search?.spanY ?: 1)
        if (clockY < grid.rows) {
            occupancy.occupy(0, 0, clockY, grid.columns, 1)
        }
    }

    /**
     * Places the Google folder and the four last-row shortcuts. Dock rows must already exist so
     * they are not duplicated.
     */
    private suspend fun arrangeGoogleHome(apps: List<AppInfo>, grid: GridShape) {
        val occupancy = Occupancy(grid)
        val current = homeItemDao.all()
        current.filter { it.container == Container.DESKTOP.name }.forEach { row ->
            occupancy.occupy(row.page, row.cellX, row.cellY, row.spanX, row.spanY)
        }
        markClockOccupied(occupancy, current, grid)

        val search = current.firstOrNull {
            it.container == Container.DESKTOP.name && isOursSearch(it)
        }
        if (search == null) {
            val seeded = searchBarEntity(grid.columns)
            occupancy.occupy(seeded.page, seeded.cellX, seeded.cellY, seeded.spanX, seeded.spanY)
            homeItemDao.insert(seeded)
        }

        val dockRows = current.filter { it.container == Container.DOCK.name }
        val dockPackages = dockRows.mapNotNull { it.component?.substringBefore('/') }.toSet()
        val dockApps = dockRows.mapNotNull { row ->
            apps.firstOrNull {
                it.component.flattenToShortString() == row.component &&
                    it.userSerial == row.userSerial
            }
        }.ifEmpty {
            DefaultLayout.dockApps(appContext, apps, GridShape.DOCK_COLUMNS)
        }
        val google = DefaultLayout.googleProducts(apps, dockApps)
            .filter { it.packageName !in dockPackages }
        val folderY = SparseHome.folderRow(grid.rows)
        if (google.size >= 2 && occupancy.isFree(0, 0, folderY)) {
            val title = appContext.getString(com.example.ffdiamond.R.string.folder_google)
            val folderId = folderDao.insert(
                FolderEntity(title = title, colorSeed = Random.nextInt())
            )
            occupancy.occupy(0, 0, folderY)
            homeItemDao.insert(
                HomeItemEntity(
                    type = HomeItemType.FOLDER.name,
                    container = Container.DESKTOP.name,
                    page = 0,
                    cellX = 0,
                    cellY = folderY,
                    folderId = folderId,
                    title = title
                )
            )
            google.forEachIndexed { rank, app ->
                placeApp(app, Container.FOLDER, page = 0, cellX = 0, cellY = 0, folderId, rank)
            }
        }

        DefaultLayout.homeShortcuts(
            appContext,
            apps,
            dockApps,
            google,
            SparseHome.homeAppSlots(grid)
        ).forEach { app ->
            val cell = SparseHome.nextSlot(occupancy, grid) ?: return@forEach
            occupancy.occupy(0, cell.first, cell.second)
            placeApp(app, Container.DESKTOP, page = 0, cellX = cell.first, cellY = cell.second)
        }
    }

    private suspend fun placeApp(
        app: AppInfo,
        container: Container,
        page: Int,
        cellX: Int,
        cellY: Int,
        folderId: Long? = null,
        rank: Int = 0
    ) {
        val key = app.component.flattenToShortString()
        val existing = homeItemDao.all().firstOrNull {
            it.component == key && it.userSerial == app.userSerial
        }
        if (existing != null) {
            // Never steal a hotseat icon into the Google folder or the last home row.
            if (existing.container == Container.DOCK.name && container != Container.DOCK) return
            homeItemDao.place(existing.id, container.name, page, cellX, cellY, folderId, rank)
        } else {
            homeItemDao.insert(
                listOf(
                    appRow(app, container, page, cellX, cellY).copy(
                        folderId = folderId,
                        rank = rank
                    )
                )
            )
        }
    }

    // ---------------------------------------------------------- reconciliation

    private class Analysis(
        val surviving: List<HomeItemEntity>,
        val changed: List<HomeItemEntity>,
        val added: List<HomeItemEntity>,
        val removedIds: List<Long>,
        val removedFolderIds: List<Long>
    )

    private fun analyse(
        rows: List<HomeItemEntity>,
        folders: List<FolderEntity>,
        apps: List<AppInfo>,
        grid: GridShape
    ): Analysis {
        val appsByKey = apps.associateBy { it.key }
        val folderIds = folders.mapTo(HashSet()) { it.id }

        val working = LinkedHashMap<Long, HomeItemEntity>(rows.size)
        rows.forEach { working[it.id] = it }

        val removedIds = mutableListOf<Long>()
        val removedFolderIds = mutableListOf<Long>()
        val changed = LinkedHashMap<Long, HomeItemEntity>()
        val added = mutableListOf<HomeItemEntity>()

        fun drop(row: HomeItemEntity) {
            working.remove(row.id)
            changed.remove(row.id)
            removedIds += row.id
        }

        fun change(row: HomeItemEntity) {
            working[row.id] = row
            changed[row.id] = row
        }

        // 1. Rows pointing at something that no longer exists.
        working.values.toList().forEach { row ->
            val gone = when (row.type) {
                HomeItemType.APP.name -> row.appKey()?.let { it !in appsByKey } ?: true
                HomeItemType.FOLDER.name -> row.folderId == null || row.folderId !in folderIds
                HomeItemType.WIDGET.name -> {
                    if (isOursSearch(row)) {
                        false
                    } else {
                        val id = row.appWidgetId ?: 0
                        val dead = id > 0 && widgets?.isAlive(id) == false
                        if (dead && id > 0) widgets?.delete(id)
                        dead
                    }
                }
                else -> false
            }
            if (gone) drop(row)
        }

        // 2. Members of a folder whose own row is gone. Dropping them loses nothing: step 5 sees
        //    the app is no longer placed anywhere and puts it back on the grid.
        val liveFolders = working.values
            .filter { it.type == HomeItemType.FOLDER.name }
            .mapNotNullTo(HashSet()) { it.folderId }
        working.values.toList().forEach { row ->
            if (row.container == Container.FOLDER.name && row.folderId !in liveFolders) drop(row)
        }

        // 3. A folder that an uninstall emptied out. One survivor is promoted into the folder's own
        //    cell, which is the same rule the drag gesture uses when a folder is emptied by hand.
        val membersByFolder = working.values
            .filter { it.container == Container.FOLDER.name }
            .groupBy { it.folderId }
        working.values.toList()
            .filter { it.type == HomeItemType.FOLDER.name }
            .forEach { folderRow ->
                val members = membersByFolder[folderRow.folderId].orEmpty()
                if (members.size > 1) return@forEach

                members.firstOrNull()?.let { survivor ->
                    change(
                        survivor.copy(
                            container = folderRow.container,
                            page = folderRow.page,
                            cellX = folderRow.cellX,
                            cellY = folderRow.cellY,
                            folderId = null,
                            rank = 0
                        )
                    )
                }
                folderRow.folderId?.let { removedFolderIds += it }
                drop(folderRow)
            }

        // 4. Positions. Keep any cell that still fits; only out-of-bounds or overlapping items
        //    move. Pouring the whole grid on a reshape would pack a sparse home back into a dump.
        val occupancy = Occupancy(grid)
        val misplaced = mutableListOf<HomeItemEntity>()
        markClockOccupied(occupancy, working.values, grid)

        working.values
            .filter { it.container == Container.DESKTOP.name }
            .sortedWith(
                compareBy(
                    { if (isOursSearch(it)) 0 else 1 },
                    { it.page },
                    { it.cellY },
                    { it.cellX }
                )
            )
            .forEach { row ->
                if (occupancy.isFree(row.page, row.cellX, row.cellY, row.spanX, row.spanY)) {
                    occupancy.occupy(row.page, row.cellX, row.cellY, row.spanX, row.spanY)
                } else {
                    misplaced += row
                }
            }

        // The dock keeps its own width, so a reshape never strands anything in it and there is
        // nothing here for a repack to do.
        val dockTaken = BooleanArray(GridShape.DOCK_COLUMNS)
        val dockOverflow = mutableListOf<HomeItemEntity>()
        working.values
            .filter { it.container == Container.DOCK.name }
            .sortedBy { it.cellX }
            .forEach { row ->
                val slot = if (row.cellX in dockTaken.indices && !dockTaken[row.cellX]) {
                    row.cellX
                } else {
                    dockTaken.indexOfFirst { !it }
                }
                when {
                    slot < 0 -> dockOverflow += row
                    slot == row.cellX && row.cellY == 0 -> dockTaken[slot] = true
                    else -> {
                        dockTaken[slot] = true
                        change(row.copy(cellX = slot, cellY = 0))
                    }
                }
            }

        misplaced.forEach { row ->
            val slot = occupancy.firstFree(row.spanX, row.spanY)
            // Writing only genuine moves keeps a repack that changes nothing from looping: the
            // update would make Room emit again, and reconciliation would plan the same update.
            if (slot.page != row.page || slot.cellX != row.cellX || slot.cellY != row.cellY) {
                change(row.copy(page = slot.page, cellX = slot.cellX, cellY = slot.cellY))
            }
        }

        // A dock that no longer has room for everything moves the surplus onto the grid rather than
        // dropping it, which would silently uninstall an icon from the user's point of view.
        dockOverflow.forEach { row ->
            val slot = occupancy.firstFree()
            change(
                row.copy(
                    container = Container.DESKTOP.name,
                    page = slot.page,
                    cellX = slot.cellX,
                    cellY = slot.cellY
                )
            )
        }

        // 5. Apps that this pass unplaced (a dissolved folder, a dropped orphan) go back on the
        //    grid. Newly installed apps stay in the drawer until the user pins them.
        val placed = working.values.mapNotNullTo(HashSet()) { it.appKey() }
        val previouslyPlaced = rows.mapNotNullTo(HashSet()) { it.appKey() }
        apps.asSequence()
            .filter { it.key !in placed && it.key in previouslyPlaced }
            .forEach { app ->
                val slot = occupancy.firstFree()
                added += appRow(app, Container.DESKTOP, slot.page, slot.cellX, slot.cellY)
            }

        return Analysis(
            surviving = working.values.toList(),
            changed = changed.values.toList(),
            added = added,
            removedIds = removedIds,
            removedFolderIds = removedFolderIds
        )
    }

    // ------------------------------------------------------------- projection

    private fun build(
        entities: List<HomeItemEntity>,
        folders: List<FolderEntity>,
        apps: List<AppInfo>
    ): HomeLayout {
        val appsByKey = apps.associateBy { it.key }
        val folderById = folders.associateBy { it.id }

        fun toApp(row: HomeItemEntity): HomeItem.App? {
            val info = row.appKey()?.let { appsByKey[it] } ?: return null
            return HomeItem.App(
                id = row.id,
                container = row.container.toContainer(),
                page = row.page,
                cellX = row.cellX,
                cellY = row.cellY,
                spanX = row.spanX,
                spanY = row.spanY,
                info = info,
                title = row.title ?: info.label,
                rank = row.rank
            )
        }

        val membersByFolder = entities
            .filter { it.container == Container.FOLDER.name }
            .sortedBy { it.rank }
            .groupBy({ it.folderId }, { toApp(it) })

        fun toItem(row: HomeItemEntity): HomeItem? = when (row.type) {
            HomeItemType.FOLDER.name -> {
                val folder = row.folderId?.let { folderById[it] }
                folder?.let {
                    HomeItem.Folder(
                        id = row.id,
                        container = row.container.toContainer(),
                        page = row.page,
                        cellX = row.cellX,
                        cellY = row.cellY,
                        spanX = row.spanX,
                        spanY = row.spanY,
                        folderId = it.id,
                        title = row.title ?: it.title,
                        colorSeed = it.colorSeed,
                        members = membersByFolder[it.id].orEmpty().filterNotNull()
                    )
                }
            }

            HomeItemType.WIDGET.name -> {
                val ours = isOursSearch(row)
                val widgetId = row.appWidgetId
                if (!ours && (widgetId == null || widgetId <= 0)) {
                    null
                } else {
                    HomeItem.Widget(
                        id = row.id,
                        container = row.container.toContainer(),
                        page = row.page,
                        cellX = row.cellX,
                        cellY = row.cellY,
                        spanX = row.spanX,
                        spanY = row.spanY,
                        appWidgetId = widgetId ?: 0,
                        provider = row.component.orEmpty(),
                        title = row.title.orEmpty()
                    )
                }
            }

            else -> toApp(row)
        }

        val desktop = entities
            .filter { it.container == Container.DESKTOP.name }
            .mapNotNull(::toItem)
        val dock = entities
            .filter { it.container == Container.DOCK.name }
            .mapNotNull(::toItem)
            .sortedBy { it.cellX }

        val pageCount = ((desktop.maxOfOrNull { it.page } ?: 0) + 1).coerceAtLeast(1)
        val pages = List(pageCount) { page -> desktop.filter { it.page == page } }

        return HomeLayout(pages = pages, dock = dock)
    }

    // -------------------------------------------------------------- mutations

    /**
     * Every rearrangement goes through here rather than through the views, so the database is the
     * layout and the workspace is only a rendering of it. Kill the process mid-drag and the last
     * committed position is what comes back.
     */
    suspend fun place(
        itemId: Long,
        container: Container,
        page: Int,
        cellX: Int,
        cellY: Int,
        folderId: Long? = null,
        rank: Int = 0
    ) = homeItemDao.place(itemId, container.name, page, cellX, cellY, folderId, rank)

    /**
     * A whole rearrangement at once.
     *
     * A drop moves the icon the user dragged *and* the two or three that stepped aside to make room
     * for it. Writing those one at a time would make Room emit a layout between each, and the
     * workspace would rebuild itself against half-applied states — briefly showing two icons in one
     * cell. One transaction means the layout flow sees the move as a single event.
     */
    suspend fun applyPlacements(placements: List<Placement>) {
        if (placements.isEmpty()) return
        database.withTransaction {
            val rows = homeItemDao.all().associateBy { it.id }
            val updated = placements.mapNotNull { placement ->
                val existing = rows[placement.itemId] ?: return@mapNotNull null
                existing.copy(
                    container = placement.container.name,
                    page = placement.page,
                    cellX = placement.cellX,
                    cellY = placement.cellY,
                    rank = placement.rank
                )
            }
            if (updated.isNotEmpty()) homeItemDao.update(updated)
        }
    }

    data class Placement(
        val itemId: Long,
        val container: Container,
        val page: Int,
        val cellX: Int,
        val cellY: Int,
        val folderId: Long? = null,
        val rank: Int = 0
    )

    suspend fun addToFolder(itemId: Long, folderId: Long, rank: Int) =
        homeItemDao.place(itemId, Container.FOLDER.name, 0, 0, 0, folderId, rank)

    suspend fun memberCount(folderId: Long): Int = homeItemDao.membersOf(folderId).size

    /**
     * Takes an icon off the home screen without uninstalling it.
     *
     * Deleting the row would look like a new install on the next reconcile — every app without a
     * row is seeded onto the first free cell — so the icon would vanish and then reappear. Hiding
     * it keeps the row so the app stays off the grid until the user puts it back from the drawer.
     */
    suspend fun remove(itemId: Long) {
        val row = homeItemDao.all().firstOrNull { it.id == itemId } ?: return
        if (row.type == HomeItemType.WIDGET.name) {
            row.appWidgetId?.let { widgets?.delete(it) }
            homeItemDao.deleteByIds(listOf(itemId))
            return
        }
        database.withTransaction {
            homeItemDao.place(itemId, Container.HIDDEN.name, 0, 0, 0, folderId = null, rank = 0)
            // A folder taken off the home screen takes its members with it, otherwise they would
            // sit in a folder that has no icon and never be reachable.
            if (row.type == HomeItemType.FOLDER.name && row.folderId != null) {
                homeItemDao.membersOf(row.folderId).forEach { member ->
                    homeItemDao.place(
                        member.id, Container.HIDDEN.name, 0, 0, 0, folderId = null, rank = 0
                    )
                }
            }
        }
    }

    suspend fun addWidget(
        appWidgetId: Int,
        provider: String,
        title: String,
        spanX: Int,
        spanY: Int,
        grid: GridShape
    ): Long {
        val occupancy = Occupancy(grid)
        val allItems = homeItemDao.all()
        allItems
            .filter { it.container == Container.DESKTOP.name }
            .forEach { occupancy.occupy(it.page, it.cellX, it.cellY, it.spanX, it.spanY) }
        markClockOccupied(occupancy, allItems, grid)
        val slot = occupancy.firstFree(spanX, spanY)
        return homeItemDao.insert(
            HomeItemEntity(
                type = HomeItemType.WIDGET.name,
                page = slot.page,
                cellX = slot.cellX,
                cellY = slot.cellY,
                spanX = spanX,
                spanY = spanY,
                container = Container.DESKTOP.name,
                component = provider,
                appWidgetId = appWidgetId,
                title = title
            )
        )
    }

    suspend fun resizeWidget(
        itemId: Long,
        page: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int
    ) = homeItemDao.placeSpan(itemId, page, cellX, cellY, spanX, spanY)

    suspend fun setTitle(itemId: Long, title: String?) = homeItemDao.setTitle(itemId, title)

    /**
     * Turns two items into a folder in one transaction, so the intermediate state — a folder row
     * with no members — is never observable, even if the process dies mid-way.
     */
    suspend fun createFolder(
        title: String,
        container: Container,
        page: Int,
        cellX: Int,
        cellY: Int,
        memberIds: List<Long>
    ): Long = database.withTransaction {
        val folderId = folderDao.insert(
            FolderEntity(title = title, colorSeed = Random.nextInt())
        )
        homeItemDao.insert(
            HomeItemEntity(
                type = HomeItemType.FOLDER.name,
                container = container.name,
                page = page,
                cellX = cellX,
                cellY = cellY,
                folderId = folderId,
                title = title
            )
        )
        memberIds.forEachIndexed { rank, id ->
            homeItemDao.place(id, Container.FOLDER.name, 0, 0, 0, folderId, rank)
        }
        folderId
    }

    suspend fun renameFolder(folderId: Long, title: String) = folderDao.rename(folderId, title)

    /**
     * Puts [app] on the first free desktop cell. No-op if it is already on the home screen
     * (desktop, dock, or a folder), so the drawer cannot mint a second copy.
     */
    suspend fun pinToHome(app: AppInfo, grid: GridShape): Boolean {
        val rows = homeItemDao.all()
        val key = app.component.flattenToShortString()
        val alreadyVisible = rows.any {
            it.component == key &&
                it.userSerial == app.userSerial &&
                it.container != Container.HIDDEN.name
        }
        if (alreadyVisible) return false

        val occupancy = Occupancy(grid)
        rows.filter { it.container == Container.DESKTOP.name }
            .forEach { occupancy.occupy(it.page, it.cellX, it.cellY, it.spanX, it.spanY) }
        markClockOccupied(occupancy, rows, grid)
        val slot = occupancy.firstFree()

        val hidden = rows.firstOrNull {
            it.component == key &&
                it.userSerial == app.userSerial &&
                it.container == Container.HIDDEN.name
        }
        if (hidden != null) {
            homeItemDao.place(
                hidden.id, Container.DESKTOP.name, slot.page, slot.cellX, slot.cellY, null, 0
            )
        } else {
            homeItemDao.insert(
                listOf(appRow(app, Container.DESKTOP, slot.page, slot.cellX, slot.cellY))
            )
        }
        return true
    }

    // ----------------------------------------------------------------- helpers

    private fun appRow(
        app: AppInfo,
        container: Container,
        page: Int,
        cellX: Int,
        cellY: Int
    ) = HomeItemEntity(
        type = HomeItemType.APP.name,
        page = page,
        cellX = cellX,
        cellY = cellY,
        container = container.name,
        component = app.component.flattenToShortString(),
        userSerial = app.userSerial
    )

    /** Matches the key AppInfo builds, so a row and an app can be compared directly. */
    private fun HomeItemEntity.appKey(): String? =
        component?.let { "$it|$userSerial" }

    private fun String.toContainer(): Container =
        runCatching { Container.valueOf(this) }.getOrDefault(Container.DESKTOP)
}
