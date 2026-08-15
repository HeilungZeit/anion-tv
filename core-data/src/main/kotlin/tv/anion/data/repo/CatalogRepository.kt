package tv.anion.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tv.anion.data.db.CatalogCacheDao
import tv.anion.data.db.CatalogCacheEntity
import tv.anion.source.AnimeSource
import tv.anion.source.SourceId
import tv.anion.source.model.Anime
import tv.anion.source.model.AnimeDetails
import tv.anion.source.model.Episode
import tv.anion.source.model.Page
import tv.anion.source.model.PlayableStream

/** Сеть обновляет Room; при отказе отдаётся последнее сохранённое значение. */
class CachedAnimeSource(
    private val upstream: AnimeSource,
    private val cache: CatalogCacheDao,
    private val now: () -> Long = System::currentTimeMillis,
) : AnimeSource by upstream {
    override suspend fun feed(page: Int): Page<Anime> = cached("feed:$page") { upstream.feed(page) }
    override suspend fun search(query: String, page: Int): Page<Anime> =
        cached("search:${query.trim().lowercase()}:$page") { upstream.search(query, page) }

    private suspend fun cached(suffix: String, load: suspend () -> Page<Anime>): Page<Anime> {
        val key = "${upstream.id.name}:$suffix"
        return try {
            load().also { value ->
                cache.upsert(CatalogCacheEntity(key, json.encodeToString(value.toCache()), now()))
            }
        } catch (network: Exception) {
            val saved = cache.get(key) ?: throw network
            runCatching { json.decodeFromString<CachedPage>(saved.json).toModel() }.getOrElse { throw network }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CachedPage(val items: List<CachedAnime>, val page: Int, val hasMore: Boolean)

@Serializable
private data class CachedAnime(
    val id: String,
    val source: String,
    val title: String,
    val titleOriginal: String?,
    val year: Int?,
    val posterUrl: String?,
    val thumbnailUrl: String?,
    val score: Double? = null,
    val status: String? = null,
    val statusCode: String? = null,
    val airedEpisodes: Int? = null,
    val episodesTotal: Int? = null,
)

private fun Page<Anime>.toCache() = CachedPage(items.map { anime ->
    CachedAnime(
        anime.id, anime.source.name, anime.title, anime.titleOriginal, anime.year,
        anime.posterUrl, anime.thumbnailUrl, anime.score, anime.status,
        anime.statusCode, anime.airedEpisodes, anime.episodesTotal,
    )
}, page, hasMore)

private fun CachedPage.toModel() = Page(items.map { anime ->
    Anime(
        anime.id, SourceId.valueOf(anime.source), anime.title, anime.titleOriginal,
        anime.year, anime.posterUrl, anime.thumbnailUrl, anime.score, anime.status,
        anime.statusCode, anime.airedEpisodes, anime.episodesTotal,
    )
}, page, hasMore)
