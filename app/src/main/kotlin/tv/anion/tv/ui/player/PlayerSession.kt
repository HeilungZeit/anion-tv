package tv.anion.tv.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anion.player.PlaybackController
import tv.anion.player.PlaybackSession
import tv.anion.data.repo.BookmarkSeed
import tv.anion.data.repo.ProgressUpdate
import tv.anion.source.SourceId
import tv.anion.tv.di.AppContainer

/**
 * Не ViewModel: Activity-scoped VM не уничтожается при уходе с экрана и держал бы
 * ExoPlayer после «назад». Сессия живёт вместе с [PlayerScreen].
 */
internal class PlayerSession(
    private val container: AppContainer,
    sourceId: SourceId,
    animeId: String,
    episodeNumber: Int,
    translationId: String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var lastUpdate: ProgressUpdate? = null

    /** Номера серий выбранной озвучки — для перехода к следующей и предыдущей. */
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _neighbours = MutableStateFlow(EpisodeNeighbours())
    val neighbours: StateFlow<EpisodeNeighbours> = _neighbours.asStateFlow()
    private var bookmarkSeed: BookmarkSeed? = null
    private var progressTemplate: ((Long, Long) -> ProgressUpdate)? = null
    val controller: PlaybackController = container.createPlayer { positionMs, durationMs ->
        val update = progressTemplate?.invoke(positionMs, durationMs) ?: return@createPlayer
        val seed = bookmarkSeed ?: return@createPlayer
        lastUpdate = update
        container.progressRecorder.record(update, seed)
    }

    init {
        scope.launch {
            runCatching {
                val source = container.sources.byId(sourceId)
                val details = source.details(animeId)
                val episodes = source.episodes(animeId, translationId)
                val episode = episodes.firstOrNull { it.number == episodeNumber }
                    ?: error("серия $episodeNumber не найдена")
                val numbers = episodes.map { it.number }.sorted()
                _neighbours.value = EpisodeNeighbours(
                    previous = numbers.lastOrNull { it < episodeNumber },
                    next = numbers.firstOrNull { it > episodeNumber },
                    total = numbers.size,
                )
                val stream = source.stream(episode)
                val seed = BookmarkSeed(
                    sourceId, animeId, details.anime.title,
                    details.anime.thumbnailUrl ?: details.anime.posterUrl,
                    details.episodesTotal ?: episodes.size,
                )
                _title.value = details.anime.title
                bookmarkSeed = seed
                container.progressRecorder.begin(seed)
                progressTemplate = { positionMs, durationMs ->
                    ProgressUpdate(
                        sourceId, animeId, episodeNumber, translationId,
                        details.anime.title, details.anime.thumbnailUrl ?: details.anime.posterUrl,
                        positionMs, durationMs,
                    )
                }
                val saved = container.watchProgress.get(sourceId, animeId, episodeNumber)
                controller.prepare(
                    PlaybackSession(
                        stream = stream,
                        skips = episode.skips,
                        startPositionMs = saved?.takeUnless { it.finished }?.positionMs ?: 0L,
                        isLastEpisode = episode.number == episodes.maxOf { it.number },
                        iframeUrl = episode.iframeUrl,
                    ),
                )
            }.onFailure { _error.value = it.message ?: "не удалось начать воспроизведение" }
            _loading.value = false
        }
    }

    fun release() {
        val update = lastUpdate
        val seed = bookmarkSeed
        if (update != null && seed != null) container.progressRecorder.flush(update, seed)
        scope.cancel()
        controller.release()
    }
}

/** Соседние серии текущей озвучки. */
internal data class EpisodeNeighbours(
    val previous: Int? = null,
    val next: Int? = null,
    val total: Int = 0,
)
