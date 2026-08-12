package tv.anion.source.model

import tv.anion.source.SourceId

data class Page<T>(val items: List<T>, val page: Int, val hasMore: Boolean)

data class Anime(
    val id: String,
    val source: SourceId,
    val title: String,
    val titleOriginal: String?,
    val year: Int?,
    val posterUrl: String?,
    /** Мелкая картинка под размер плитки: оригиналы жрут память бокса (PLAN §6). */
    val thumbnailUrl: String?,
)

data class AnimeDetails(
    val anime: Anime,
    val description: String?,
    val genres: List<String>,
    val episodesTotal: Int?,
    val translations: List<Translation>,
    val blocked: BlockedFlags,
    val type: String? = null,
    val status: String? = null,
    val ageRating: String? = null,
    val score: Double? = null,
    val scoreVotes: Int? = null,
    val airedEpisodes: Int? = null,
    val views: Int? = null,
    val studios: List<String> = emptyList(),
    val otherTitles: List<String> = emptyList(),
    val backdropUrl: String? = null,
)

/** Озвучка. У AniLibria всегда одна, у Kodik — список. */
data class Translation(val id: String, val title: String, val type: String?)

data class BlockedFlags(val byGeo: Boolean = false, val byCopyright: Boolean = false)

data class Episode(
    val animeId: String,
    val source: SourceId,
    val number: Int,
    val title: String?,
    val durationSeconds: Int?,
    val previewUrl: String?,
    val skips: Skips,
    /** AniLibria: готовые hls_480/720/1080. */
    val directStreams: Map<Int, String> = emptyMap(),
    /** Kodik: iframe, который надо резолвить. */
    val iframeUrl: String? = null,
    val translationId: String? = null,
)

data class Skips(val opening: Segment? = null, val ending: Segment? = null)

/**
 * Отрезок, который можно пропустить.
 *
 * `stopSeconds` необязателен, и это не небрежность: AniLibria отдаёт
 * `opening: {start, stop}`, а anion-go — одно число. Длина у него есть во
 * внешней модели (`VideoSkips.Opening.Length`), но
 * [VideoSkipsDTO][anion-go/internal/dto/anime.go] её отбрасывает, оставляя
 * только `Time`. Пока это так, у Kodik-эпизодов известно лишь начало, и плеер
 * показывает кнопку окном [DEFAULT_WINDOW_SECONDS] (Э3).
 */
data class Segment(val startSeconds: Int, val stopSeconds: Int?) {
    val endSeconds: Int get() = stopSeconds ?: (startSeconds + DEFAULT_WINDOW_SECONDS)

    companion object {
        /** Типичный опенинг — полторы минуты; используется, когда длина неизвестна. */
        const val DEFAULT_WINDOW_SECONDS = 90
    }
}

data class PlayableStream(
    val url: String,
    val quality: Int?,
    val headers: Map<String, String>,
)
