package com.example.rankingfilter.data.userfilter

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.rankingfilter.data.music.MusicMeta
import com.example.rankingfilter.data.music.MusicMetaDao

@Database(
    entities = [UserFilter::class, UserFilterPhoto::class, MusicMeta::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userFilterDao(): UserFilterDao

    abstract fun musicMetaDao(): MusicMetaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** 1 → 2: thêm bảng thời lượng nhạc. Không đụng tới bảng filter của người dùng. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `music_meta` (`id` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ranking_filter.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
