package tv.anion.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.anion.data.db.WatchProgressEntity
import tv.anion.data.db.WatchProgressStore
import tv.anion.data.repo.Bookmark
import tv.anion.data.repo.BookmarkConflictResolver
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.repo.ProgressUpdate
import tv.anion.data.repo.RoomWatchProgressRepository
import tv.anion.source.SourceId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchProgressRepositoryTest {
    @Test fun `последние 90 процентов серии считаются досмотром`() = runTest {
        val repository = RoomWatchProgressRepository(MemoryProgressStore()) { 100 }

        val before = repository.save(update(position = 89_999, duration = 100_000))
        val atThreshold = repository.save(update(position = 90_000, duration = 100_000))

        assertFalse(before.finished)
        assertTrue(atThreshold.finished)
    }

    @Test fun `продолжить смотреть сортируется по времени последнего просмотра`() = runTest {
        var time = 0L
        val repository = RoomWatchProgressRepository(MemoryProgressStore()) { ++time }
        repository.save(update(animeId = "old", position = 10_000))
        repository.save(update(animeId = "new", position = 20_000))

        assertEquals(listOf("new", "old"), repository.observeContinueWatching().first().map { it.animeId })
    }

    @Test fun `при конфликте побеждает более свежий updatedAt`() {
        val local = bookmark(updatedAt = 10, dirty = false, watched = 2)
        val remote = bookmark(updatedAt = 20, dirty = false, watched = 4)

        assertEquals(4, BookmarkConflictResolver.merge(local, remote, pulledAt = 30).watchedEpisodes)
        assertEquals(2, BookmarkConflictResolver.merge(local, remote.copy(updatedAt = 5), pulledAt = 30).watchedEpisodes)
    }

    @Test fun `локальные изменения не теряются, пока не подтверждён push`() {
        val local = bookmark(updatedAt = 10, dirty = true, watched = 5)
        val remote = bookmark(updatedAt = 20, dirty = false, watched = 2)

        val merged = BookmarkConflictResolver.merge(local, remote, pulledAt = 30)

        assertEquals(local, merged)
        assertTrue(merged.dirty)
    }

    private fun update(
        animeId: String = "42",
        position: Long,
        duration: Long = 100_000,
    ) = ProgressUpdate(SourceId.KODIK, animeId, 1, "dub", "Тайтл", null, position, duration)

    private fun bookmark(updatedAt: Long, dirty: Boolean, watched: Int) = Bookmark(
        SourceId.KODIK, "42", null, BookmarkKind.WATCHING, watched, 12,
        "Тайтл", null, "ongoing", updatedAt, null, dirty,
    )
}

private class MemoryProgressStore : WatchProgressStore {
    private val values = linkedMapOf<Triple<String, String, Int>, WatchProgressEntity>()
    private val state = MutableStateFlow<List<WatchProgressEntity>>(emptyList())

    override fun observeContinueWatching(): Flow<List<WatchProgressEntity>> = state
    override fun observeAnime(source: String, animeId: String): Flow<List<WatchProgressEntity>> =
        MutableStateFlow(values.values.filter { it.source == source && it.animeId == animeId })
    override suspend fun get(source: String, animeId: String, episode: Int) = values[Triple(source, animeId, episode)]
    override suspend fun pendingSync() = values.values.filter { it.syncedAt == null || it.updatedAt > it.syncedAt }
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
