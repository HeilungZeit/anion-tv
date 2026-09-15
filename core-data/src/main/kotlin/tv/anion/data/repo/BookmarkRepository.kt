package tv.anion.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tv.anion.data.db.BookmarkEntity
import tv.anion.data.db.BookmarkStore
import tv.anion.source.SourceId

enum class BookmarkKind(val wireName: String) {
    WATCHING("watching"), WILL_WATCH("will_watch"), WATCHED("watched"),
    ON_HOLD("on_hold"), DROPPED("dropped");

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.wireName == value } ?: WATCHING
    }
}

data class Bookmark(
    val source: SourceId,
    val animeId: String,
    val serverId: String?,
    val kind: BookmarkKind,
    val watchedEpisodes: Int,
    val totalEpisodes: Int,
    val title: String,
    val posterUrl: String?,
    val animeStatus: String,
    val updatedAt: Long,
    val syncedAt: Long?,
    val dirty: Boolean,
)

data class BookmarkSeed(
    val source: SourceId,
    val animeId: String,
    val title: String,
    val posterUrl: String?,
    val totalEpisodes: Int,
)

interface BookmarkRepository {
    fun observeAll(): Flow<List<Bookmark>>
    suspend fun all(): List<Bookmark>
    suspend fun get(source: SourceId, animeId: String): Bookmark?
    /**
     * Досмотрена последняя серия — существующая закладка переходит в
     * «Просмотрено». Новой не создаёт: закладки ставятся только руками.
     */
    suspend fun completeIfLast(source: SourceId, animeId: String, episode: Int, totalEpisodes: Int): Bookmark?
    /** Статус, выбранный руками на карточке тайтла. */
    suspend fun setKind(seed: BookmarkSeed, kind: BookmarkKind): Bookmark
    suspend fun remove(source: SourceId, animeId: String): Bookmark?
    suspend fun dirty(): List<Bookmark>
    suspend fun mergeRemote(remote: Bookmark, pulledAt: Long)
    suspend fun markSynced(bookmark: Bookmark, serverId: String?): Boolean
}

class RoomBookmarkRepository(
    private val store: BookmarkStore,
    private val now: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {
    override fun observeAll() = store.observeAll().map { it.map(BookmarkEntity::toModel) }
    override suspend fun all() = store.all().map(BookmarkEntity::toModel)
    override suspend fun get(source: SourceId, animeId: String) = store.get(source.name, animeId)?.toModel()

    override suspend fun completeIfLast(source: SourceId, animeId: String, episode: Int, totalEpisodes: Int): Bookmark? {
        val old = store.get(source.name, animeId) ?: return null
        val total = maxOf(totalEpisodes, old.totalEpisodes)
        val alreadyWatched = BookmarkKind.fromWire(old.kind) == BookmarkKind.WATCHED
        if (total <= 0 || episode < total || alreadyWatched) return old.toModel()

        // Меняется только статус: счётчик серий у закладки сервер выводит из прогресса.
        val seed = BookmarkSeed(source, animeId, old.title, old.posterUrl, total)
        return write(seed, old.watchedEpisodes, BookmarkKind.WATCHED, old)
    }

    override suspend fun setKind(seed: BookmarkSeed, kind: BookmarkKind): Bookmark {
        val old = store.get(seed.source.name, seed.animeId)
        val watched = old?.watchedEpisodes ?: 0
        val total = maxOf(seed.totalEpisodes, old?.totalEpisodes ?: 0)
        return write(seed.copy(totalEpisodes = total), watched, kind, old)
    }

    /**
     * Удаляем сразу локально, а серверу об этом сообщает [BookmarkSync]: строку
     * нужно отдать ему вместе с `serverId`, иначе удалять на сайте будет нечего.
     */
    override suspend fun remove(source: SourceId, animeId: String): Bookmark? {
        val old = store.get(source.name, animeId)?.toModel()
        store.delete(source.name, animeId)
        return old
    }

    private suspend fun write(seed: BookmarkSeed, watched: Int, kind: BookmarkKind, old: BookmarkEntity?): Bookmark {
        val row = BookmarkEntity(
            source = seed.source.name,
            animeId = seed.animeId,
            serverId = old?.serverId,
            kind = kind.wireName,
            watchedEpisodes = watched,
            totalEpisodes = seed.totalEpisodes,
            title = seed.title,
            posterUrl = seed.posterUrl,
            animeStatus = old?.animeStatus ?: "ongoing",
            updatedAt = now(),
            syncedAt = old?.syncedAt,
            dirty = true,
        )
        store.upsert(row)
        return row.toModel()
    }

    override suspend fun dirty() = store.dirty().map(BookmarkEntity::toModel)

    override suspend fun mergeRemote(remote: Bookmark, pulledAt: Long) {
        val local = store.get(remote.source.name, remote.animeId)?.toModel()
        val merged = BookmarkConflictResolver.merge(local, remote, pulledAt)
        store.upsert(merged.toEntity())
    }

    override suspend fun markSynced(bookmark: Bookmark, serverId: String?): Boolean =
        store.markSynced(bookmark.source.name, bookmark.animeId, bookmark.updatedAt, serverId, now()) == 1
}

object BookmarkConflictResolver {
    /** У ответа anion-go нет updatedAt, поэтому pull может заменить только чистую локальную запись. */
    fun merge(local: Bookmark?, remote: Bookmark, pulledAt: Long): Bookmark {
        val received = remote.copy(
            updatedAt = remote.updatedAt.takeIf { it > 0 } ?: pulledAt,
            syncedAt = pulledAt,
            dirty = false,
        )
        return when {
        local == null -> received
        local.dirty -> local
        received.updatedAt > local.updatedAt -> received
        else -> local
        }
    }
}

private fun BookmarkEntity.toModel() = Bookmark(
    SourceId.valueOf(source), animeId, serverId, BookmarkKind.fromWire(kind), watchedEpisodes,
    totalEpisodes, title, posterUrl, animeStatus, updatedAt, syncedAt, dirty,
)

private fun Bookmark.toEntity() = BookmarkEntity(
    source.name, animeId, serverId, kind.wireName, watchedEpisodes, totalEpisodes,
    title, posterUrl, animeStatus, updatedAt, syncedAt, dirty,
)
