package com.example.ffdiamond.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `launcher.db`.
 *
 * Version 3 adds `app_widget_id` so a widget row can find the host view it owns after a restart.
 */
@Database(
    entities = [IconCacheEntity::class, HomeItemEntity::class, FolderEntity::class],
    version = 3,
    exportSchema = true
)
abstract class LauncherDatabase : RoomDatabase() {

    abstract fun iconCacheDao(): IconCacheDao

    abstract fun homeItemDao(): HomeItemDao

    abstract fun folderDao(): FolderDao

    companion object {
        private const val NAME = "launcher.db"

        @Volatile
        private var instance: LauncherDatabase? = null

        fun get(context: Context): LauncherDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Adds the two layout tables. Nothing existing is touched, so an installed build keeps its
         * icon cache and simply gains an empty layout, which the repository then seeds.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `home_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `page` INTEGER NOT NULL,
                        `cell_x` INTEGER NOT NULL,
                        `cell_y` INTEGER NOT NULL,
                        `span_x` INTEGER NOT NULL,
                        `span_y` INTEGER NOT NULL,
                        `container` TEXT NOT NULL,
                        `component` TEXT,
                        `user_serial` INTEGER NOT NULL,
                        `folder_id` INTEGER,
                        `title` TEXT,
                        `rank` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_home_items_container_page` " +
                        "ON `home_items` (`container`, `page`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_home_items_folder_id` " +
                        "ON `home_items` (`folder_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_home_items_component_user_serial` " +
                        "ON `home_items` (`component`, `user_serial`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `folders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `color_seed` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `home_items` ADD COLUMN `app_widget_id` INTEGER"
                )
            }
        }

        private fun build(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
