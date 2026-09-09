package com.ominix.vidiio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ominix.vidiio.data.model.DownloadConverters
import com.ominix.vidiio.data.model.DownloadTask
import com.ominix.vidiio.data.model.FavoriteMovie
import com.ominix.vidiio.data.model.WatchProgress

@Database(
    entities = [FavoriteMovie::class, DownloadTask::class, WatchProgress::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(DownloadConverters::class)
abstract class VidiioDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        const val NAME = "vidiio-database"

        /**
         * Builds the app database.
         *
         * Deliberately *not* `fallbackToDestructiveMigration()`: that wiped every user's
         * favorites, watch progress and downloads on each schema bump. Only the
         * pre-schema-export versions may be dropped; anything newer without a migration
         * in [ALL_MIGRATIONS] fails loudly at open time. See [Migrations.kt].
         */
        fun build(context: Context): VidiioDatabase =
            Room.databaseBuilder(context.applicationContext, VidiioDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(
                    dropAllTables = true,
                    *LEGACY_DESTRUCTIVE_VERSIONS
                )
                .build()
    }
}
