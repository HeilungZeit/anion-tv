package tv.anion.source.kodik

import kotlinx.serialization.builtins.ListSerializer
import tv.anion.resolve.StreamResolver
import tv.anion.source.AnimeSource
import tv.anion.source.SourceId
import tv.anion.source.http.AnionJson
import tv.anion.source.model.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Каталог/поиск/детали — из anion-go, поток — iframeUrl через [StreamResolver]
 * (PLAN §3).
 *
 * У одного тайтла в `videos` лежат все серии всех озвучек вперемешку: одна
 * запись — это «серия N в озвучке X». Поэтому озвучки собираются группировкой
 * по `data.dubbing`, а не приходят отдельным списком.
 */
class KodikSource(
    private val api: AnionGoApi,
    private val resolver: StreamResolver,
    private val anilibria: AnimeSource? = null,
) : AnimeSource {

    override val id = SourceId.KODIK
    override val displayName = "Сейчас смотрят"
    private val animeCache = ConcurrentHashMap<String, AnimeDto>()

    /** Фид не листается — бэк отдаёт один срез сезона. */
    override suspend fun feed(page: Int): Page<Anime> {
        val dto = AnionJson.decodeFromString(AnimeFeedDto.serializer(), api.feed())
        return Page(items = dto.seasonAnime.map(::toAnime), page = 1, hasMore = false)
    }

    override suspend fun search(query: String, page: Int): Page<Anime> {
        val offset = (page - 1) * PAGE_SIZE
        val list = AnionJson.decodeFromString(
            ListSerializer(AnimeDetailsDto.serializer()),
            api.search(query, PAGE_SIZE, offset),
        )

        return Page(items = list.map(::toAnime), page = page, hasMore = list.size == PAGE_SIZE)
    }

    override suspend fun details(animeId: String): AnimeDetails {
        val dto = animeDto(animeId)
        val alias = dto.anilibriaAlias()
        val libriaSource = anilibria
        val libriaDetails = if (alias != null && libriaSource != null) {
            runCatching { libriaSource.details(alias) }.getOrNull()
        } else null

        return AnimeDetails(
            anime = toAnime(dto),
            description = dto.description ?: libriaDetails?.description,
            genres = dto.genres.mapNotNull { it.title }.ifEmpty { libriaDetails?.genres.orEmpty() },
            episodesTotal = dto.episodes?.count ?: dto.episodes?.aired ?: libriaDetails?.episodesTotal,
            // Прямой HLS без резолва идёт первым; Kodik остаётся полным fallback
            // и даёт выбор остальных озвучек.
            translations = buildList {
                if (libriaDetails != null) add(ANILIBRIA_TRANSLATION)
                addAll(translationsOf(dto.videos))
            },
            // Гео-блокировка у Kodik — это список стран, а не флаг. Что бокс
            // стоит в одной из них, выяснится только на воспроизведении.
            blocked = BlockedFlags(byGeo = dto.blockedIn.isNotEmpty()),
            type = dto.type?.name ?: dto.type?.shortname,
            status = dto.animeStatus?.title,
            ageRating = dto.minAge?.title,
            score = dto.rating?.average,
            scoreVotes = dto.rating?.counters,
            airedEpisodes = dto.episodes?.aired,
            views = dto.views,
            studios = dto.studios.mapNotNull { it.title },
            otherTitles = dto.otherTitles.filter(String::isNotBlank),
            backdropUrl = dto.randomScreenshots
                .firstNotNullOfOrNull { firstImageUrl(it.sizes?.full, it.sizes?.small) },
        )
    }

    override suspend fun episodes(animeId: String, translationId: String?): List<Episode> {
        val dto = animeDto(animeId)
        if (translationId == ANILIBRIA_TRANSLATION.id) {
            val alias = requireNotNull(dto.anilibriaAlias()) { "у тайтла нет привязки к AniLibria" }
            return requireNotNull(anilibria) { "источник AniLibria не подключён" }
                .episodes(alias, ANILIBRIA_TRANSLATION.id)
        }
        val wanted = translationId ?: translationsOf(dto.videos).firstOrNull()?.id

        return dto.videos
            .filter { it.isKodikPlayer() }
            .filter { wanted == null || translationIdOf(it) == wanted }
            .mapNotNull { video ->
                val number = video.number?.toIntOrNull() ?: return@mapNotNull null

                Episode(
                    animeId = animeId,
                    source = SourceId.KODIK,
                    number = number,
                    title = null,
                    durationSeconds = null,
                    previewUrl = null,
                    skips = Skips(
                        opening = video.skips?.opening?.let { Segment(it, stopSeconds = null) },
                        ending = video.skips?.ending?.let { Segment(it, stopSeconds = null) },
                    ),
                    iframeUrl = video.iframeUrl,
                    translationId = translationIdOf(video),
                )
            }
            .sortedBy { it.number }
            // Одна и та же серия одной озвучки приходит в нескольких плеерах,
            // и без дедупликации список серий содержит дубли номеров — на этом
            // падал LazyGrid: «Key "1" was already used».
            .distinctBy { it.number }
    }

    override suspend fun stream(episode: Episode, preferredQuality: Int): PlayableStream {
        if (episode.source == SourceId.ANILIBRIA) {
            return requireNotNull(anilibria) { "источник AniLibria не подключён" }
                .stream(episode, preferredQuality)
        }
        val iframeUrl = requireNotNull(episode.iframeUrl) { "у эпизода Kodik нет iframeUrl" }
        val resolved = resolver.resolve(iframeUrl, preferredQuality)

        return PlayableStream(resolved.manifestUrl, resolved.quality, resolved.headers)
    }

    private fun translationsOf(videos: List<VideoDto>): List<Translation> =
        videos.filter { it.isKodikPlayer() }
            .mapNotNull { video ->
                val dubbing = video.data?.dubbing ?: return@mapNotNull null
                Translation(id = dubbing, title = dubbing, type = video.data.player)
            }
            .distinctBy { it.id }

    /**
     * Бэк отдаёт под одним тайтлом видео разных плееров: «Плеер Kodik»
     * (kodikplayer.com), «Плеер CVH» (ru.yummyani.me), «Плеер Alloha»
     * (alloha.yani.tv). Резолвер умеет только первый, остальные дали бы
     * невнятную ошибку уже на воспроизведении.
     *
     * Проверяется подпись, а не хост: так же отбирает anion-dl
     * (`anime.component.ts`), а список зеркал Kodik меняется чаще подписи.
     */
    private fun VideoDto.isKodikPlayer(): Boolean =
        data?.player?.contains(KODIK_PLAYER, ignoreCase = true) == true

    private fun translationIdOf(video: VideoDto): String? = video.data?.dubbing

    private suspend fun animeDto(animeId: String): AnimeDto = animeCache[animeId] ?: AnionJson
        .decodeFromString(AnimeDto.serializer(), api.anime(animeId))
        .also { animeCache[animeId] = it }

    private fun AnimeDto.anilibriaAlias(): String? = remoteIds?.anilibriaAlias
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun toAnime(dto: AnimeDto) = Anime(
        id = dto.animeId.toString(),
        source = SourceId.KODIK,
        title = dto.title.orEmpty(),
        titleOriginal = dto.otherTitles.firstOrNull() ?: dto.original,
        year = dto.year,
        posterUrl = dto.poster?.large(),
        thumbnailUrl = dto.poster?.thumb(),
        score = dto.rating?.average,
        status = dto.animeStatus?.title,
        statusCode = dto.animeStatus?.alias,
        airedEpisodes = dto.episodes?.aired,
        episodesTotal = dto.episodes?.count,
    )

    private fun toAnime(dto: AnimeDetailsDto) = Anime(
        id = dto.animeId.toString(),
        source = SourceId.KODIK,
        title = dto.title.orEmpty(),
        titleOriginal = dto.original,
        year = dto.year,
        posterUrl = dto.poster?.large(),
        thumbnailUrl = dto.poster?.thumb(),
        score = dto.rating?.average,
        status = dto.animeStatus?.title,
        statusCode = dto.animeStatus?.alias,
        airedEpisodes = dto.episodes?.aired,
        episodesTotal = dto.episodes?.count,
    )

    private companion object {
        const val PAGE_SIZE = 30
        const val KODIK_PLAYER = "Kodik"
        val ANILIBRIA_TRANSLATION = Translation(
            id = "anilibria",
            title = "AniLibria · прямой HLS",
            type = "без резолва",
        )
    }
}

/**
 * Постер под крупную плитку.
 *
 * Порядок размеров — не про качество, а про формат файла. `static.yani.tv`
 * раздаёт `mega`/`huge` в **AVIF**, `big`/`small`/`medium` в WebP и только
 * `fullsize` в JPEG. Android умеет декодировать AVIF с API 31, а `minSdk` у нас
 * 23: на телевизоре постарше Coil молча не разворачивает такую картинку, и
 * карточка остаётся с заглушкой. Симптом обманчивый — «не грузятся картинки
 * только с нашего бэка», хотя фон деталей (скриншоты Kodik, JPEG) и постеры
 * AniLibria (WebP) на том же экране рисуются.
 *
 * Поэтому AVIF стоит последним: на новых боксах до него очередь не доходит,
 * на старых он и не нужен.
 */
internal fun PosterDto.large(): String? = firstImageUrl(fullsize, big, medium, mega, huge)

/** Мелкая плитка: те же соображения по формату, но начиная с меньших размеров. */
internal fun PosterDto.thumb(): String? = firstImageUrl(big, medium, small, fullsize, mega, huge)

/**
 * Первый пригодный к загрузке адрес.
 *
 * Недостающий размер бэк отдаёт не как `null`, а строкой: нормализатор anion-go
 * приклеивает схему к чему угодно, и в живом фиде приезжает
 * `"https:/img/default-poster.jpg"` (заглушка) или голое `"https:"`. Для `?:`
 * это валидное значение, и оно выигрывало у настоящего URL, стоящего следом.
 */
private fun firstImageUrl(vararg candidates: String?): String? = candidates
    .asSequence()
    .mapNotNull { it?.trim() }
    .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
