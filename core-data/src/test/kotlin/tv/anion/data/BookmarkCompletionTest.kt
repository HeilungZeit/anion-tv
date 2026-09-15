package tv.anion.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.repo.BookmarkSeed
import tv.anion.data.repo.RoomBookmarkRepository
import tv.anion.source.SourceId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkCompletionTest {
    private val bookmarks = RoomBookmarkRepository(MemoryBookmarkStore()) { 1 }
    private val seed = BookmarkSeed(SourceId.KODIK, "42", "Тайтл", null, totalEpisodes = 12)

    @Test fun `досмотр последней серии переводит закладку в Просмотрено`() = runTest {
        bookmarks.setKind(seed, BookmarkKind.WATCHING)

        val result = bookmarks.completeIfLast(SourceId.KODIK, "42", episode = 12, totalEpisodes = 12)

        assertEquals(BookmarkKind.WATCHED, result?.kind)
    }

    @Test fun `без закладки новая не появляется`() = runTest {
        assertNull(bookmarks.completeIfLast(SourceId.KODIK, "42", episode = 12, totalEpisodes = 12))
        assertEquals(emptyList(), bookmarks.all())
    }

    @Test fun `не последняя серия статус не трогает`() = runTest {
        bookmarks.setKind(seed, BookmarkKind.ON_HOLD)

        val result = bookmarks.completeIfLast(SourceId.KODIK, "42", episode = 5, totalEpisodes = 12)

        assertEquals(BookmarkKind.ON_HOLD, result?.kind)
    }
}
