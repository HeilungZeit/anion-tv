package tv.anion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WatchProgressEntity::class, BookmarkEntity::class, CatalogCacheEntity::class, SearchQueryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AnionDatabase : RoomDatabase() {
    abstract fun watchProgress(): WatchProgressDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun catalogCache(): CatalogCacheDao
    abstract fun searchHistory(): SearchHistoryDao

    companion object {
        @Volatile private var instance: AnionDatabase? = null

        fun open(context: Context): AnionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AnionDatabase::class.java,
                "anion.db",
            ).build().also { instance = it }
        }
    }
}
