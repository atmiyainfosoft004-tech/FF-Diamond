package com.example.ffdiamond.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ffdiamond.model.GridShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_prefs"
)

/**
 * Small key-value state that has to survive a reinstall-free restart. The layout database
 * arrives later in Room; this is only for flags.
 */
object LauncherPreferences {

    @Volatile
    private var cachedHomeSettings: HomeSettings? = null

    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val ASKED_POST_NOTIFICATIONS = booleanPreferencesKey("asked_post_notifications")
    private val GRID_COLUMNS = intPreferencesKey("grid_columns")
    private val GRID_ROWS = intPreferencesKey("grid_rows")
    private val SHOW_LABELS = booleanPreferencesKey("show_labels")
    private val THEMED_ICONS = booleanPreferencesKey("themed_icons")
    private val LAYOUT_SEEDED = booleanPreferencesKey("layout_seeded")
    private val SPARSE_HOME = booleanPreferencesKey("sparse_home_v1")
    private val GOOGLE_HOME = booleanPreferencesKey("google_home_v1")
    private val OWN_APP_PINNED = booleanPreferencesKey("own_app_pinned_v1")
    private val DOCK_ORDERED = booleanPreferencesKey("dock_roles_v2")
    private val HOTSEAT_FILLED = booleanPreferencesKey("hotseat_filled_v1")
    private val LAYOUT_COLUMNS = intPreferencesKey("layout_columns")
    private val LAYOUT_ROWS = intPreferencesKey("layout_rows")

    fun onboardingCompleted(context: Context): Flow<Boolean> =
        context.launcherDataStore.data.map { it[ONBOARDING_COMPLETED] == true }

    /**
     * Read once, on the main thread, before the first frame. Onboarding has to decide between the
     * wizard and the status screen before it inflates anything, and a single flag read is cheaper
     * than showing an empty screen and swapping content underneath the user.
     */
    fun onboardingCompletedBlocking(context: Context): Boolean =
        blockingIo { onboardingCompleted(context).first() }

    suspend fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.launcherDataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    /**
     * Whether the POST_NOTIFICATIONS dialog has already been shown once. After the first denial
     * Android silently drops further requests, so the card has to send the user to Settings.
     */
    fun askedForNotificationsBlocking(context: Context): Boolean =
        blockingIo { context.launcherDataStore.data.map { it[ASKED_POST_NOTIFICATIONS] == true }.first() }

    suspend fun setAskedForNotifications(context: Context) {
        context.launcherDataStore.edit { it[ASKED_POST_NOTIFICATIONS] = true }
    }

    fun homeSettings(context: Context): Flow<HomeSettings> =
        context.launcherDataStore.data.map { prefs ->
            HomeSettings(
                columns = prefs[GRID_COLUMNS] ?: HomeSettings.DEFAULT_COLUMNS,
                rows = prefs[GRID_ROWS] ?: HomeSettings.DEFAULT_ROWS,
                showLabels = prefs[SHOW_LABELS] != false,
                themedIcons = prefs[THEMED_ICONS] == true
            )
        }.distinctUntilChanged().map { settings ->
            cachedHomeSettings = settings
            settings
        }

    /**
     * Read before the first frame for the same reason as the onboarding flag: the grid shape
     * decides how many icons fit on a page, and discovering that one frame late would mean
     * building the workspace twice.
     */
    fun homeSettingsBlocking(context: Context): HomeSettings {
        cachedHomeSettings?.let { return it }
        return blockingIo { homeSettings(context).first() }.also { cachedHomeSettings = it }
    }

    suspend fun setGrid(context: Context, columns: Int, rows: Int) {
        context.launcherDataStore.edit {
            it[GRID_COLUMNS] = columns
            it[GRID_ROWS] = rows
        }
    }

    suspend fun setShowLabels(context: Context, show: Boolean) {
        context.launcherDataStore.edit { it[SHOW_LABELS] = show }
    }

    suspend fun setThemedIcons(context: Context, themed: Boolean) {
        context.launcherDataStore.edit { it[THEMED_ICONS] = themed }
    }

    /**
     * Whether the default layout has ever been written.
     *
     * "No rows in the table" is not the same question. A user who removes every icon has an empty
     * home screen on purpose, and re-seeding it behind their back would be the launcher undoing
     * their work every time they open it.
     */
    suspend fun isLayoutSeeded(context: Context): Boolean =
        context.launcherDataStore.data.map { it[LAYOUT_SEEDED] == true }.first()

    suspend fun setLayoutSeeded(context: Context, seeded: Boolean = true) {
        context.launcherDataStore.edit { it[LAYOUT_SEEDED] = seeded }
    }

    /**
     * Whether the home screen has been converted to the sparse layout (search + two app rows,
     * extras in the drawer). Distinct from [isLayoutSeeded] so an already-seeded dump can migrate
     * once without looking like a fresh install.
     */
    suspend fun isSparseHome(context: Context): Boolean =
        context.launcherDataStore.data.map { it[SPARSE_HOME] == true }.first()

    suspend fun setSparseHome(context: Context, sparse: Boolean = true) {
        context.launcherDataStore.edit { it[SPARSE_HOME] = sparse }
    }

    /**
     * Search + four last-row shortcuts + a Google-products folder. Distinct from [isSparseHome]
     * so a device that already sparsified can pick up the new arrangement once.
     */
    suspend fun isGoogleHome(context: Context): Boolean =
        context.launcherDataStore.data.map { it[GOOGLE_HOME] == true }.first()

    suspend fun setGoogleHome(context: Context, arranged: Boolean = true) {
        context.launcherDataStore.edit { it[GOOGLE_HOME] = arranged }
    }

    suspend fun isOwnAppPinned(context: Context): Boolean =
        context.launcherDataStore.data.map { it[OWN_APP_PINNED] == true }.first()

    suspend fun setOwnAppPinned(context: Context, pinned: Boolean = true) {
        context.launcherDataStore.edit { it[OWN_APP_PINNED] = pinned }
    }

    suspend fun isDockOrdered(context: Context): Boolean =
        context.launcherDataStore.data.map { it[DOCK_ORDERED] == true }.first()

    suspend fun setDockOrdered(context: Context, ordered: Boolean = true) {
        context.launcherDataStore.edit { it[DOCK_ORDERED] = ordered }
    }

    suspend fun isHotseatFilled(context: Context): Boolean =
        context.launcherDataStore.data.map { it[HOTSEAT_FILLED] == true }.first()

    suspend fun setHotseatFilled(context: Context, filled: Boolean = true) {
        context.launcherDataStore.edit { it[HOTSEAT_FILLED] = filled }
    }

    /**
     * The grid the stored cell coordinates were chosen for, which is not necessarily the grid in
     * use right now — the user can change the shape at any time.
     *
     * Comparing the two is how the layout knows a repack is due. Null means the shape was never
     * recorded, which the layout treats as a mismatch rather than a match — the coordinates could
     * have been arranged for anything.
     */
    suspend fun arrangedGrid(context: Context): GridShape? =
        context.launcherDataStore.data.map { prefs ->
            val columns = prefs[LAYOUT_COLUMNS]
            val rows = prefs[LAYOUT_ROWS]
            if (columns != null && rows != null) GridShape(columns, rows) else null
        }.first()

    suspend fun setArrangedGrid(context: Context, grid: GridShape) {
        context.launcherDataStore.edit {
            it[LAYOUT_COLUMNS] = grid.columns
            it[LAYOUT_ROWS] = grid.rows
        }
    }

    private fun <T> blockingIo(block: suspend () -> T): T =
        runBlocking(Dispatchers.IO) { block() }
}
