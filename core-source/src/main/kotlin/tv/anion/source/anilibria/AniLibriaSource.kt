package tv.anion.source.anilibria

import kotlinx.serialization.builtins.ListSerializer
import tv.anion.source.AnimeSource
import tv.anion.source.SourceId
import tv.anion.source.http.AnionJson
import tv.anion.source.model.*

/**
 * https://anilibria.top/api/v1 — без ключа. В эпизоде сразу hls_480/720/1080,
 * duration и полные отрезки opening/ending. Резолва нет: ExoPlayer получает URL
 * и играет (PLAN §3).
 *
 * Оговорка, которая видна прямо в URL манифеста: `isAuthorized=0` и
 * `isWithVideoAds=1` — для неавторизованных в поток вшита реклама. Снимает ли
 * это авторизация, пока не проверено.
 */
class AniLibriaSource(private val api: AniLibriaApi) : AnimeSource {

    override val id = SourceId.ANILIBRIA
    override val displayName = "AniLibria"

    override suspend fun feed(page: Int): Page<Anime> {
        val dto = AnionJson.decodeFromString(CatalogDto.serializer(), api.catalog(page, AniLibriaApi.PAGE_SIZE))
        val pagination = dto.meta?.pagination

        return Page(
            items = dto.data.map(::toAnime),
            page = pagination?.currentPage ?: page,
            hasMore = (pagination?.currentPage ?: page) < (pagination?.totalPages ?: page),
        )
    }

    /** Поиск у AniLibria без страниц: отдаёт плоский список. */
    override suspend fun search(query: String, page: Int): Page<Anime> {
        val list = AnionJson.decodeFromString(ListSerializer(ReleaseDto.serializer()), api.search(query))
        return Page(items = list.map(::toAnime), page = 1, hasMore = false)
    }

    override suspend fun details(animeId: String): AnimeDetails {
        val dto = AnionJson.decodeFromString(ReleaseDto.serializer(), api.release(animeId))

        return AnimeDetails(
            anime = toAnime(dto),
            description = dto.description,
            genres = dto.genres.mapNotNull { it.name },
            episodesTotal = dto.episodesTotal ?: dto.episodes.size.takeIf { it > 0 },
            // Своя озвучка одна — но список не пустой, иначе UI пришлось бы
            // учить особому случаю «выбора нет».
            translations = listOf(Translation(id = SELF_TRANSLATION, title = "AniLibria", type = null)),
            blocked = BlockedFlags(dto.isBlockedByGeo, dto.isBlockedByCopyrights),
            type = dto.type?.description ?: dto.type?.value,
            status = if (dto.isInProduction) "Онгоинг" else "Завершён",
            ageRating = dto.ageRating?.label,
            airedEpisodes = dto.episodes.size.takeIf { it > 0 },
            backdropUrl = dto.episodes.firstNotNullOfOrNull { episode ->
                episode.preview?.let { it.optimized?.preview ?: it.preview ?: it.src }?.absoluteMediaUrl()
            },
        )
    }

    override suspend fun episodes(animeId: String, translationId: String?): List<Episode> {
        val dto = AnionJson.decodeFromString(ReleaseDto.serializer(), api.release(animeId))
        val alias = dto.alias ?: animeId

        return dto.episodes.map { episode ->
            Episode(
                animeId = alias,
                source = SourceId.ANILIBRIA,
                number = episode.ordinal?.toInt() ?: 0,
                title = episode.name,
                durationSeconds = episode.duration,
                previewUrl = episode.preview?.let { it.thumbnail ?: it.preview ?: it.src }?.absoluteMediaUrl(),
                skips = Skips(
                    opening = episode.opening?.toSegment(),
                    ending = episode.ending?.toSegment(),
                ),
                directStreams = buildMap {
                    episode.hls480?.let { put(480, it) }
                    episode.hls720?.let { put(720, it) }
                    episode.hls1080?.let { put(1080, it) }
                },
                translationId = SELF_TRANSLATION,
            )
        }
    }

    /**
     * Ближайшее качество не выше желаемого, иначе — минимальное доступное.
     * Спуск важнее подъёма: на боксе лишние пиксели дороже, чем недостача.
     */
    override suspend fun stream(episode: Episode, preferredQuality: Int): PlayableStream {
        require(episode.directStreams.isNotEmpty()) { "у эпизода AniLibria нет ни одного hls" }

        val quality = episode.directStreams.keys.filter { it <= preferredQuality }.maxOrNull()
            ?: episode.directStreams.keys.min()

        return PlayableStream(
            url = episode.directStreams.getValue(quality),
            quality = quality,
            headers = emptyMap(),
        )
    }

    private fun toAnime(dto: ReleaseDto) = Anime(
        id = dto.alias ?: dto.id.toString(),
        source = SourceId.ANILIBRIA,
        title = dto.name?.main ?: dto.alias.orEmpty(),
        titleOriginal = dto.name?.english ?: dto.name?.alternative,
        year = dto.year,
        posterUrl = (dto.poster?.optimized?.src ?: dto.poster?.src)?.absoluteMediaUrl(),
        // `thumbnail` у AniLibria всего 18x25 и на телевизоре превращается в
        // заметное мыло. Preview обычно 455x650 — достаточно для TV-плитки.
        thumbnailUrl = (dto.poster?.optimized?.preview
            ?: dto.poster?.optimized?.src
            ?: dto.poster?.preview
            ?: dto.poster?.src)?.absoluteMediaUrl(),
        status = if (dto.isInProduction) "Онгоинг" else "Вышло",
        statusCode = if (dto.isInProduction) "ongoing" else "released",
        airedEpisodes = dto.episodes.size.takeIf { it > 0 },
        episodesTotal = dto.episodesTotal,
    )

    private fun TimeRangeDto.toSegment(): Segment? =
        start?.let { Segment(startSeconds = it, stopSeconds = stop) }

    private companion object {
        const val SELF_TRANSLATION = "anilibria"
    }
}

/** AniLibria отдаёт медиа как абсолютными URL, так и путями от корня сайта. */
private fun String.absoluteMediaUrl(): String = when {
    startsWith("https://") || startsWith("http://") -> this
    startsWith("//") -> "https:$this"
    startsWith("/") -> "https://anilibria.top$this"
    else -> "https://anilibria.top/$this"
}
