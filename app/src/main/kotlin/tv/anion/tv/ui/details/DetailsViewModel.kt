package tv.anion.tv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anion.source.SourceId
import tv.anion.source.SourceRegistry
import tv.anion.source.model.AnimeDetails
import tv.anion.source.model.Episode
import tv.anion.data.repo.WatchProgressRepository
import tv.anion.data.sync.BookmarkSync
import tv.anion.data.repo.BookmarkSeed
import tv.anion.data.repo.BookmarkRepository
import tv.anion.data.repo.BookmarkKind
import kotlinx.coroutines.Job

data class DetailsUiState(
    val details: AnimeDetails? = null,
    val episodes: List<Episode> = emptyList(),
    val translationId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val watchedEpisodes: Set<Int> = emptySet(),
    /** Доля просмотра недосмотренных серий — тонкая полоска на карточке. */
    val partial: Map<Int, Float> = emptyMap(),
    /** Куда ведёт главная кнопка: продолжить недосмотренное или начать сначала. */
    val resume: ResumePoint? = null,
    /** Текущий статус закладки; null — тайтла в закладках нет. */
    val bookmark: BookmarkKind? = null,
)

data class ResumePoint(val episode: Int, val positionMs: Long)

class DetailsViewModel(
    private val sources: SourceRegistry,
    private val progress: WatchProgressRepository,
    private val bookmarks: BookmarkRepository,
    private val sync: BookmarkSync,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()
    private var sourceId: SourceId? = null
    private var animeId: String? = null
    private var progressJob: Job? = null
    private var bookmarkJob: Job? = null

    fun load(sourceId: SourceId, animeId: String) {
        if (this.sourceId == sourceId && this.animeId == animeId && _state.value.details != null) return
        this.sourceId = sourceId
        this.animeId = animeId
        progressJob?.cancel()
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            bookmarks.observeAll().collect { all ->
                val kind = all.firstOrNull { it.source == sourceId && it.animeId == animeId }?.kind
                _state.value = _state.value.copy(bookmark = kind)
            }
        }
        progressJob = viewModelScope.launch {
            progress.observeAnime(sourceId, animeId).collect { values ->
                // Продолжаем самую свежую недосмотренную серию: на ТВ это
                // главный сценарий, и ради него не должно быть лишних нажатий.
                val resume = values
                    .filterNot { it.finished }
                    .filter { it.positionMs > RESUME_THRESHOLD_MS }
                    .maxByOrNull { it.updatedAt }
                    ?.let { ResumePoint(it.episode, it.positionMs) }

                _state.value = _state.value.copy(
                    watchedEpisodes = values.filter { it.finished }.mapTo(mutableSetOf()) { it.episode },
                    partial = values.filterNot { it.finished }
                        .filter { it.durationMs > 0 }
                        .associate { it.episode to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) },
                    resume = resume,
                )
            }
        }
        _state.value = DetailsUiState(loading = true)
        viewModelScope.launch {
            runCatching {
                val source = sources.byId(sourceId)
                val details = source.details(animeId)
                val translation = details.translations.firstOrNull()?.id
                Triple(details, source.episodes(animeId, translation), translation)
            }.onSuccess { (details, episodes, translation) ->
                _state.value = DetailsUiState(
                    details = details,
                    episodes = episodes,
                    translationId = translation,
                    loading = false,
                    watchedEpisodes = _state.value.watchedEpisodes,
                    partial = _state.value.partial,
                    resume = _state.value.resume,
                    // Статус из Room приходит раньше ответа сети, и целиком
                    // пересобранное состояние его затирало: кнопка показывала
                    // «В закладки» у тайтла, который в закладках уже был.
                    bookmark = _state.value.bookmark,
                )
            }.onFailure { error ->
                _state.value = DetailsUiState(loading = false, error = error.message)
            }
        }
    }

    /**
     * Ставит или снимает статус. Наверх уходит обычным синком: локальная запись
     * помечается dirty, а удаление отправляется сразу — потом от него не
     * останется следа, по которому серверу можно объяснить, что убрать.
     */
    fun setBookmark(kind: BookmarkKind?) {
        val sourceId = sourceId ?: return
        val animeId = animeId ?: return
        val details = _state.value.details ?: return

        viewModelScope.launch {
            runCatching {
                if (kind == null) {
                    val removed = bookmarks.remove(sourceId, animeId)
                    if (removed != null) sync.deleteRemote(removed)
                } else {
                    bookmarks.setKind(
                        BookmarkSeed(
                            source = sourceId,
                            animeId = animeId,
                            title = details.anime.title,
                            posterUrl = details.anime.thumbnailUrl ?: details.anime.posterUrl,
                            totalEpisodes = details.episodesTotal ?: _state.value.episodes.size,
                        ),
                        kind,
                    )
                    sync.pushDirty()
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message)
            }
        }
    }

    fun selectTranslation(id: String) {
        val sourceId = sourceId ?: return
        val animeId = animeId ?: return
        if (id == _state.value.translationId) return
        _state.value = _state.value.copy(translationId = id)
        viewModelScope.launch {
            runCatching { sources.byId(sourceId).episodes(animeId, id) }
                .onSuccess { episodes -> _state.value = _state.value.copy(episodes = episodes) }
                .onFailure { error -> _state.value = _state.value.copy(error = error.message) }
        }
    }
}

/** Меньше — это случайный тык, а не просмотр; предлагать «продолжить» незачем. */
private const val RESUME_THRESHOLD_MS = 30_000L
