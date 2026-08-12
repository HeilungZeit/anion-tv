package tv.anion.data.db

import androidx.room.Entity

/** Room — источник правды; сервер подтверждает только успешно отправленные записи. */
@Entity(tableName = "watch_progress", primaryKeys = ["source", "animeId", "episode"])
data class WatchProgressEntity(
    val source: String,
    val animeId: String,
    val episode: Int,
    val translationId: String?,
    val title: String,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
    val syncedAt: Long?,
)

@Entity(tableName = "bookmarks", primaryKeys = ["source", "animeId"])
data class BookmarkEntity(
    val source: String,
    val animeId: String,
    val serverId: String?,
    val kind: String,
    val watchedEpisodes: Int,
    val totalEpisodes: Int,
    val title: String,
    val posterUrl: String?,
    val animeStatus: String,
    val updatedAt: Long,
    val syncedAt: Long?,
    val dirty: Boolean,
)

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @androidx.room.PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long,
)

@Entity(tableName = "search_queries")
data class SearchQueryEntity(
    @androidx.room.PrimaryKey val query: String,
    val usedAt: Long,
)
