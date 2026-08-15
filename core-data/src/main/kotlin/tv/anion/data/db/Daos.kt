package tv.anion.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

interface WatchProgressStore {
    fun observeContinueWatching(): Flow<List<WatchProgressEntity>>
    fun observeAnime(source: String, animeId: String): Flow<List<WatchProgressEntity>>
    suspend fun get(source: String, animeId: String, episode: Int): WatchProgressEntity?
    suspend fun pendingSync(): List<WatchProgressEntity>
    suspend fun upsert(value: WatchProgressEntity)
    suspend fun markSynced(source: String, animeId: String, episode: Int, updatedAt: Long, syncedAt: Long): Int
}

@Dao
interface WatchProgressDao : WatchProgressStore {
    @Query("SELECT * FROM watch_progress WHERE finished = 0 AND positionMs > 0 ORDER BY updatedAt DESC")
    override fun observeContinueWatching(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE source = :source AND animeId = :animeId ORDER BY episode")
    override fun observeAnime(source: String, animeId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE source = :source AND animeId = :animeId AND episode = :episode")
    override suspend fun get(source: String, animeId: String, episode: Int): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE syncedAt IS NULL OR updatedAt > syncedAt ORDER BY updatedAt")
    override suspend fun pendingSync(): List<WatchProgressEntity>

    @Upsert override suspend fun upsert(value: WatchProgressEntity)

    @Query("UPDATE watch_progress SET syncedAt = :syncedAt WHERE source = :source AND animeId = :animeId AND episode = :episode AND updatedAt = :updatedAt")
    override suspend fun markSynced(source: String, animeId: String, episode: Int, updatedAt: Long, syncedAt: Long): Int
}

interface BookmarkStore {
    fun observeAll(): Flow<List<BookmarkEntity>>
    suspend fun all(): List<BookmarkEntity>
    suspend fun get(source: String, animeId: String): BookmarkEntity?
    suspend fun dirty(): List<BookmarkEntity>
    suspend fun upsert(value: BookmarkEntity)
    suspend fun markSynced(source: String, animeId: String, updatedAt: Long, serverId: String?, syncedAt: Long): Int
    suspend fun delete(source: String, animeId: String)
}

@Dao
interface BookmarkDao : BookmarkStore {
    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC")
    override fun observeAll(): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks") override suspend fun all(): List<BookmarkEntity>
    @Query("DELETE FROM bookmarks WHERE source = :source AND animeId = :animeId")
    override suspend fun delete(source: String, animeId: String)
    @Query("SELECT * FROM bookmarks WHERE source = :source AND animeId = :animeId")
    override suspend fun get(source: String, animeId: String): BookmarkEntity?
    @Query("SELECT * FROM bookmarks WHERE dirty = 1 ORDER BY updatedAt")
    override suspend fun dirty(): List<BookmarkEntity>
    @Upsert override suspend fun upsert(value: BookmarkEntity)
    @Query("UPDATE bookmarks SET dirty = 0, syncedAt = :syncedAt, serverId = COALESCE(:serverId, serverId) WHERE source = :source AND animeId = :animeId AND updatedAt = :updatedAt")
    override suspend fun markSynced(source: String, animeId: String, updatedAt: Long, serverId: String?, syncedAt: Long): Int
}

@Dao
interface CatalogCacheDao {
    @Query("SELECT * FROM catalog_cache WHERE `key` = :key") suspend fun get(key: String): CatalogCacheEntity?
    @Upsert suspend fun upsert(value: CatalogCacheEntity)
    @Query("DELETE FROM catalog_cache WHERE `key` LIKE :prefix || '%'") suspend fun invalidate(prefix: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT query FROM search_queries ORDER BY usedAt DESC LIMIT :limit")
    fun observe(limit: Int = 12): Flow<List<String>>
    @Upsert suspend fun upsert(value: SearchQueryEntity)
    @Query("DELETE FROM search_queries WHERE query NOT IN (SELECT query FROM search_queries ORDER BY usedAt DESC LIMIT :limit)")
    suspend fun trim(limit: Int = 12)
}
