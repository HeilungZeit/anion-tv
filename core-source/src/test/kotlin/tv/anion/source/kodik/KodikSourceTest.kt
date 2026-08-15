package tv.anion.source.kodik

import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.anion.resolve.ResolvedStream
import tv.anion.resolve.StreamResolver
import tv.anion.source.Fixtures
import tv.anion.source.SourceId
import tv.anion.source.anilibria.AniLibriaApi
import tv.anion.source.anilibria.AniLibriaSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KodikSourceTest {

    private val resolver = RecordingResolver()
    private val source = KodikSource(FakeApi(), resolver)

    @Test
    fun `озвучки собираются из videos, а не приходят списком`() = runTest {
        val details = source.details("15")

        assertTrue(details.translations.isNotEmpty())
        assertEquals(details.translations.map { it.id }.distinct(), details.translations.map { it.id })
    }

    @Test
    fun `эпизоды фильтруются по озвучке и идут по возрастанию`() = runTest {
        val dubbing = source.details("15").translations.first().id
        val episodes = source.episodes("15", dubbing)

        assertTrue(episodes.isNotEmpty())
        assertTrue(episodes.all { it.translationId == dubbing })
        assertEquals(episodes.map { it.number }.sorted(), episodes.map { it.number })
    }

    @Test
    fun `у skips известно только начало - stop отсутствует`() = runTest {
        // anion-go отбрасывает Length в VideoSkipsDTO, отдавая одно число.
        val opening = source.episodes("15", null).first { it.skips.opening != null }.skips.opening!!

        assertTrue(opening.startSeconds > 0)
        assertNull(opening.stopSeconds)
        assertEquals(opening.startSeconds + 90, opening.endSeconds)
    }

    @Test
    fun `номера серий уникальны - иначе LazyGrid падает на дубликате ключа`() = runTest {
        // Одна серия одной озвучки приходит в нескольких плеерах; на этом
        // приложение крашилось при открытии карточки тайтла.
        val numbers = source.episodes("15", "Озвучка Dream Cast").map { it.number }

        assertEquals(numbers.distinct(), numbers, "дубли номеров: $numbers")
    }

    @Test
    fun `серии не из плеера Kodik отбрасываются`() = runTest {
        // Резолвер умеет только kodikplayer.com; CVH и Alloha дали бы
        // невнятную ошибку уже на воспроизведении.
        val episodes = source.episodes("15", "Озвучка Dream Cast")

        assertTrue(episodes.isNotEmpty())
        assertTrue(episodes.all { it.iframeUrl?.contains("kodikplayer.com") == true },
            "чужие плееры: ${episodes.map { it.iframeUrl }}")
    }

    @Test
    fun `stream отдаёт резолверу iframeUrl эпизода`() = runTest {
        val episode = source.episodes("15", null).first()
        val stream = source.stream(episode, preferredQuality = 480)

        assertEquals(episode.iframeUrl, resolver.lastIframeUrl)
        assertEquals(480, resolver.lastQuality)
        assertTrue(stream.headers.containsKey("Referer"), "Referer обязателен и для сегментов")
    }

    @Test
    fun `фид маппится в карточки`() = runTest {
        val page = source.feed()

        assertEquals(3, page.items.size)
        assertTrue(page.items.all { it.title.isNotBlank() })
        with(page.items.first()) {
            assertEquals(9.46963562753033, score)
            assertEquals("ongoing", statusCode)
            assertEquals(6, airedEpisodes)
            assertEquals(12, episodesTotal)
        }
    }

    @Test
    fun `постеры не берутся в AVIF - его не декодирует Android до 12`() = runTest {
        // Симптом на живом телевизоре: у карточек anion-go одни заглушки, при
        // том что фон деталей и постеры AniLibria на том же экране рисуются.
        val page = source.feed()

        assertTrue(
            page.items.none { it.posterUrl.orEmpty().endsWith(".avif") },
            "AVIF в posterUrl: ${page.items.map { it.posterUrl }}",
        )
        assertTrue(
            page.items.none { it.thumbnailUrl.orEmpty().endsWith(".avif") },
            "AVIF в thumbnailUrl: ${page.items.map { it.thumbnailUrl }}",
        )
    }

    @Test
    fun `битый размер пропускается, а не выигрывает у следующего`() = runTest {
        // Нормализатор anion-go приклеивает схему к чему угодно, включая пустое
        // значение и путь от корня: в живом фиде так приезжает
        // "https:/img/default-poster.jpg" (см. updates в aniongo-feed.json).
        // Для `?:` это валидная строка, и она перебивала реальный URL следом.
        val poster = PosterDto(
            fullsize = "https:",
            big = "https:/img/default-poster.jpg",
            medium = "https://static.yani.tv/posters/medium/1.webp",
            mega = "https://static.yani.tv/posters/mega/1.avif",
        )

        assertEquals("https://static.yani.tv/posters/medium/1.webp", poster.large())
        assertEquals("https://static.yani.tv/posters/medium/1.webp", poster.thumb())
        assertNull(PosterDto(fullsize = "https:").large())
    }

    @Test
    fun `детали лениво подключают AniLibria по alias из backend`() = runTest {
        val kodikResolver = RecordingResolver()
        val hybrid = KodikSource(FakeApi(), kodikResolver, AniLibriaSource(FakeAniLibriaApi()))

        val details = hybrid.details("15")
        assertEquals("anilibria", details.translations.first().id)

        val episode = hybrid.episodes("15", "anilibria").first()
        assertEquals(SourceId.ANILIBRIA, episode.source)
        assertEquals(720, hybrid.stream(episode, 720).quality)
        assertNull(kodikResolver.lastIframeUrl, "прямой HLS не должен идти через Kodik resolver")
    }

    @Test
    fun `детали сохраняют контент для TV hero`() = runTest {
        val details = source.details("15")

        assertEquals("Сериал", details.type)
        assertEquals("онгоинг", details.status)
        assertEquals("PG-13", details.ageRating)
        assertTrue((details.score ?: 0.0) > 9.0)
        assertTrue((details.views ?: 0) > 1_000_000)
        assertTrue(details.studios.isNotEmpty())
        assertTrue(details.otherTitles.isNotEmpty())
        assertTrue(details.backdropUrl.orEmpty().startsWith("https://"))
    }

    private class FakeApi : AnionGoApi {
        override suspend fun feed() = Fixtures.read("aniongo-feed.json")
        override suspend fun anime(id: String) = Fixtures.read("aniongo-anime.json")
        override suspend fun search(query: String, limit: Int, offset: Int) = "[]"
    }

    private class FakeAniLibriaApi : AniLibriaApi {
        override suspend fun catalog(page: Int, limit: Int) = Fixtures.read("anilibria-catalog.json")
        override suspend fun release(alias: String) = Fixtures.read("anilibria-release.json")
        override suspend fun search(query: String) = Fixtures.read("anilibria-search.json")
    }

    private class RecordingResolver : StreamResolver {
        var lastIframeUrl: String? = null
        var lastQuality: Int? = null

        override suspend fun resolve(iframeUrl: String, preferredQuality: Int): ResolvedStream {
            lastIframeUrl = iframeUrl
            lastQuality = preferredQuality
            return ResolvedStream(
                manifestUrl = "https://cdn/720.mp4:hls:manifest.m3u8",
                quality = 720,
                headers = mapOf("Referer" to "https://kodikplayer.com/"),
            )
        }
    }
}
