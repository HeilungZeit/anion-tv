package tv.anion.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.anion.data.sync.AccountWatchedEpisodes
import tv.anion.source.SourceId
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountWatchedEpisodesTest {
    private val remote = FakeWatchProgressRemote()

    @Test fun `у тайтла из ряда AniLibria серверного ключа нет`() = runTest {
        remote.stored = mapOf("grand-blue" to setOf(1))

        assertNull(AccountWatchedEpisodes(remote, MemorySession()).forAnime(SourceId.ANILIBRIA, "grand-blue"))
    }

    @Test fun `без входа показывать нечего`() = runTest {
        remote.stored = mapOf("42" to setOf(1))

        assertNull(AccountWatchedEpisodes(remote, MemorySession(null)).forAnime(SourceId.KODIK, "42"))
    }

    @Test fun `аккаунт без просмотров по тайтлу — пустой набор, а не отсутствие`() = runTest {
        assertEquals(emptySet(), AccountWatchedEpisodes(remote, MemorySession()).forAnime(SourceId.KODIK, "42"))
    }

    @Test fun `отмеченные серии приходят набором`() = runTest {
        remote.stored = mapOf("42" to setOf(1, 2, 7))

        assertEquals(setOf(1, 2, 7), AccountWatchedEpisodes(remote, MemorySession()).forAnime(SourceId.KODIK, "42"))
    }

    @Test fun `ошибка сети не маскируется под «ничего не смотрели»`() = runTest {
        remote.failure = IOException("нет сети")

        val error = runCatching { AccountWatchedEpisodes(remote, MemorySession()).forAnime(SourceId.KODIK, "42") }
            .exceptionOrNull()

        assertTrue(error is IOException, "ожидали IOException, получили $error")
    }
}
