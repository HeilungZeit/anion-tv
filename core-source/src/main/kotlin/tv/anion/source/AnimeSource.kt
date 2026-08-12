package tv.anion.source

import tv.anion.source.model.Anime
import tv.anion.source.model.AnimeDetails
import tv.anion.source.model.Episode
import tv.anion.source.model.Page
import tv.anion.source.model.PlayableStream

/**
 * Один источник каталога и потоков (PLAN §3). Реализации:
 * [tv.anion.source.anilibria.AniLibriaSource] — HLS отдаётся сразу, резолва нет;
 * [tv.anion.source.kodik.KodikSource]        — каталог из anion-go + резолв iframe.
 */
interface AnimeSource {
    val id: SourceId
    val displayName: String

    suspend fun feed(page: Int = 1): Page<Anime>
    suspend fun search(query: String, page: Int = 1): Page<Anime>
    suspend fun details(animeId: String): AnimeDetails
    suspend fun episodes(animeId: String, translationId: String?): List<Episode>

    /** Для AniLibria — готовый URL; для Kodik — проход через StreamResolver. */
    suspend fun stream(episode: Episode, preferredQuality: Int = 720): PlayableStream
}

enum class SourceId { ANILIBRIA, KODIK }

/** Порядок опроса источников и переключение при отказе одного из них. */
interface SourceRegistry {
    val all: List<AnimeSource>
    fun byId(id: SourceId): AnimeSource
}

class DefaultSourceRegistry(override val all: List<AnimeSource>) : SourceRegistry {
    override fun byId(id: SourceId): AnimeSource =
        all.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("источник $id не подключён")
}
