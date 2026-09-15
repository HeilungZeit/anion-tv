package tv.anion.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.repo.BookmarkSeed
import tv.anion.data.repo.ProgressUpdate
import tv.anion.data.repo.RoomBookmarkRepository
import tv.anion.data.repo.RoomWatchProgressRepository
import tv.anion.data.sync.DefaultBookmarkSync
import tv.anion.data.sync.SessionStore
import tv.anion.source.SourceId
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchProgressSyncTest {
    private var time = 0L
    private val progress = RoomWatchProgressRepository(MemoryProgressStore()) { ++time }
    private val bookmarks = RoomBookmarkRepository(MemoryBookmarkStore()) { ++time }
    private val bookmarkRemote = FakeBookmarkRemote()
    private val progressRemote = FakeWatchProgressRemote()
    private val migration = MemoryFlag(value = true)

    private fun sync(sessions: SessionStore = MemorySession()) = DefaultBookmarkSync(
        repository = bookmarks,
        remote = bookmarkRemote,
        sessions = sessions,
        progress = progress,
        progressRemote = progressRemote,
        progressMigration = migration,
    )

    private suspend fun watch(
        animeId: String,
        episode: Int,
        source: SourceId = SourceId.KODIK,
        finished: Boolean = true,
    ) = progress.save(
        ProgressUpdate(
            source, animeId, episode, "dub", "Тайтл", null,
            positionMs = if (finished) 95_000 else 10_000,
            durationMs = 100_000,
        ),
    )

    @Test fun `досмотренные серии уходят одним синком по аниме и считаются отправленными`() = runTest {
        watch("42", 1)
        watch("42", 2)
        watch("7", 5)

        sync().syncNow()

        assertEquals(listOf(mapOf("42" to setOf(1, 2), "7" to setOf(5))), progressRemote.synced)
        assertEquals(emptyList(), progress.pendingFinishedSync(SourceId.KODIK))
    }

    @Test fun `недосмотренное и AniLibria на сервер не уходят`() = runTest {
        watch("42", 1, finished = false)
        watch("grand-blue", 1, source = SourceId.ANILIBRIA)

        sync().syncNow()

        assertEquals(emptyList(), progressRemote.synced)
    }

    @Test fun `принятое сервером повторно не отправляется`() = runTest {
        watch("42", 1)

        sync().syncNow()
        sync().syncNow()

        assertEquals(1, progressRemote.synced.size)
    }

    @Test fun `разовый перенос отправляет и то, что старый путь пометил отправленным`() = runTest {
        // Так строку помечал путь через счётчик закладки — серий на сервере при этом нет.
        progress.markSynced(watch("42", 3))
        migration.value = false

        sync().syncNow()
        sync().syncNow()

        assertEquals(listOf(mapOf("42" to setOf(3))), progressRemote.synced)
        assertTrue(migration.value)
    }

    @Test fun `сбой прогресса не держит закладки и не теряет серии`() = runTest {
        watch("42", 1)
        bookmarks.setKind(BookmarkSeed(SourceId.KODIK, "42", "Тайтл", null, 12), BookmarkKind.WATCHING)
        progressRemote.failure = IOException("нет сети")

        val error = runCatching { sync().syncNow() }.exceptionOrNull()

        assertTrue(error is IOException, "ожидали IOException, получили $error")
        assertEquals(1, bookmarkRemote.upserted.size)
        assertEquals(1, progress.pendingFinishedSync(SourceId.KODIK).size)
    }

    @Test fun `без сессии прогресс не отправляется и перенос не отмечается`() = runTest {
        watch("42", 1)
        migration.value = false

        sync(MemorySession(null)).syncNow()

        assertEquals(emptyList(), progressRemote.synced)
        assertEquals(false, migration.value)
    }
}
