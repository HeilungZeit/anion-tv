package tv.anion.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import tv.anion.data.db.BookmarkEntity
import tv.anion.data.db.BookmarkStore
import tv.anion.data.db.WatchProgressEntity
import tv.anion.data.db.WatchProgressStore
import tv.anion.data.repo.Bookmark
import tv.anion.data.sync.BookmarkRemote
import tv.anion.data.sync.OneTimeFlag
import tv.anion.data.sync.RemoteWatchProgress
import tv.anion.data.sync.SessionStore
import tv.anion.data.sync.UserProfile
import tv.anion.data.sync.WatchProgressRemote

internal class MemoryProgressStore : WatchProgressStore {
    private val values = linkedMapOf<Triple<String, String, Int>, WatchProgressEntity>()
    private val state = MutableStateFlow<List<WatchProgressEntity>>(emptyList())

    override fun observeContinueWatching(): Flow<List<WatchProgressEntity>> = state
    override fun observeAnime(source: String, animeId: String): Flow<List<WatchProgressEntity>> =
        MutableStateFlow(values.values.filter { it.source == source && it.animeId == animeId })
    override suspend fun get(source: String, animeId: String, episode: Int) = values[Triple(source, animeId, episode)]
    override suspend fun pendingFinishedSync(source: String) = values.values.filter {
        it.source == source && it.finished && (it.syncedAt == null || it.updatedAt > it.syncedAt)
    }
    override suspend fun finished(source: String) = values.values.filter { it.source == source && it.finished }
    override suspend fun upsert(value: WatchProgressEntity) {
        values[Triple(value.source, value.animeId, value.episode)] = value
        state.value = values.values.filter { !it.finished && it.positionMs > 0 }.sortedByDescending { it.updatedAt }
    }
    override suspend fun markSynced(source: String, animeId: String, episode: Int, updatedAt: Long, syncedAt: Long): Int {
        val key = Triple(source, animeId, episode)
        val current = values[key] ?: return 0
        if (current.updatedAt != updatedAt) return 0
        values[key] = current.copy(syncedAt = syncedAt)
        return 1
    }
}

internal class MemoryBookmarkStore : BookmarkStore {
    private val values = linkedMapOf<Pair<String, String>, BookmarkEntity>()

    override fun observeAll(): Flow<List<BookmarkEntity>> = MutableStateFlow(values.values.toList())
    override suspend fun all() = values.values.toList()
    override suspend fun get(source: String, animeId: String) = values[source to animeId]
    override suspend fun dirty() = values.values.filter { it.dirty }
    override suspend fun upsert(value: BookmarkEntity) { values[value.source to value.animeId] = value }
    override suspend fun markSynced(source: String, animeId: String, updatedAt: Long, serverId: String?, syncedAt: Long): Int {
        val current = values[source to animeId] ?: return 0
        if (current.updatedAt != updatedAt) return 0
        values[source to animeId] = current.copy(dirty = false, syncedAt = syncedAt, serverId = serverId ?: current.serverId)
        return 1
    }
    override suspend fun delete(source: String, animeId: String) { values.remove(source to animeId) }
}

internal class MemorySession(private var value: String? = "session") : SessionStore {
    override fun read() = value
    override fun write(sessionId: String?) { value = sessionId }
}

internal class MemoryFlag(var value: Boolean = false) : OneTimeFlag {
    override fun isSet() = value
    override fun set() { value = true }
}

internal class FakeBookmarkRemote : BookmarkRemote {
    val upserted = mutableListOf<Bookmark>()

    override suspend fun login(login: String, password: String) = "session"
    override suspend fun logout(sessionId: String) = Unit
    override suspend fun delete(sessionId: String, serverId: String) = Unit
    override suspend fun profile(sessionId: String) = UserProfile("", "")
    override suspend fun getAll(sessionId: String) = emptyList<Bookmark>()
    override suspend fun upsert(sessionId: String, bookmark: Bookmark): Bookmark {
        upserted += bookmark
        return bookmark.copy(serverId = "server-id")
    }
}

internal class FakeWatchProgressRemote : WatchProgressRemote {
    val synced = mutableListOf<Map<String, Set<Int>>>()
    var failure: Exception? = null
    var stored: Map<String, Set<Int>> = emptyMap()

    override suspend fun get(sessionId: String, animeId: String): RemoteWatchProgress? {
        failure?.let { throw it }
        return stored[animeId]?.let { RemoteWatchProgress(animeId, it) }
    }

    override suspend fun sync(sessionId: String, items: Map<String, Set<Int>>): List<RemoteWatchProgress> {
        failure?.let { throw it }
        synced += items
        return items.map { (animeId, episodes) -> RemoteWatchProgress(animeId, episodes) }
    }
}
