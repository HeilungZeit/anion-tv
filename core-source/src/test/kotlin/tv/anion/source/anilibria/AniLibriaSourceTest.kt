package tv.anion.source.anilibria

import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.anion.source.Fixtures
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AniLibriaSourceTest {

    private val source = AniLibriaSource(FakeApi())

    @Test
    fun `маппит релиз в AnimeDetails`() = runTest {
        val details = source.details("grand-blue-season-3")

        assertEquals("Необъятный океан 3", details.anime.title)
        assertEquals(2026, details.anime.year)
        assertTrue(details.genres.isNotEmpty())
        assertNotNull(details.description)
        assertTrue(details.anime.thumbnailUrl.orEmpty().startsWith("https://anilibria.top/"))
    }

    @Test
    fun `эпизод отдаёт hls по качествам и полные отрезки opening ending`() = runTest {
        val first = source.episodes("grand-blue-season-3", null).first()

        assertEquals(listOf(480, 720, 1080), first.directStreams.keys.sorted())
        assertEquals(1441, first.durationSeconds)
        assertEquals(55, first.skips.opening?.startSeconds)
        // У AniLibria отрезок полный — в отличие от anion-go, где известно только начало.
        assertEquals(145, first.skips.opening?.stopSeconds)
        assertEquals(1425, first.skips.ending?.stopSeconds)
    }

    @Test
    fun `stream спускается до ближайшего качества не выше желаемого`() = runTest {
        val episode = source.episodes("grand-blue-season-3", null).first()

        assertEquals(720, source.stream(episode, preferredQuality = 720).quality)
        // 900 нет — берём 720, а не 1080: лишние пиксели на боксе дороже недостачи.
        assertEquals(720, source.stream(episode, preferredQuality = 900).quality)
        assertEquals(480, source.stream(episode, preferredQuality = 480).quality)
    }

    @Test
    fun `качество ниже минимального не роняет резолв`() = runTest {
        val episode = source.episodes("grand-blue-season-3", null).first()
        assertEquals(480, source.stream(episode, preferredQuality = 144).quality)
    }

    @Test
    fun `флаги блокировки прокидываются как есть`() = runTest {
        val details = source.details("grand-blue-season-3")
        assertFalse(details.blocked.byGeo)
        assertFalse(details.blocked.byCopyright)
    }

    @Test
    fun `каталог отдаёт страницу и знает про следующую`() = runTest {
        val page = source.feed(1)

        assertEquals(3, page.items.size)
        assertEquals(1, page.page)
        assertTrue(page.hasMore, "в фикстуре 632 страницы")
    }

    @Test
    fun `поиск отдаёт плоский список без страниц`() = runTest {
        val page = source.search("blue")

        assertTrue(page.items.isNotEmpty())
        assertFalse(page.hasMore)
    }

    private class FakeApi : AniLibriaApi {
        override suspend fun catalog(page: Int, limit: Int) = Fixtures.read("anilibria-catalog.json")
        override suspend fun release(alias: String) = Fixtures.read("anilibria-release.json")
        override suspend fun search(query: String) = Fixtures.read("anilibria-search.json")
    }
}
