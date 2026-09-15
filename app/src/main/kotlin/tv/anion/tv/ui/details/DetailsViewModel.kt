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
import tv.anion.data.repo.EpisodeMarksPolicy
import tv.anion.data.repo.WatchProgress
import tv.anion.data.repo.WatchProgressRepository
import tv.anion.data.sync.AccountWatchedEpisodes
import tv.anion.data.sync.BookmarkSync
import tv.anion.data.repo.BookmarkSeed
import tv.anion.data.repo.BookmarkRepository
import tv.anion.data.repo.BookmarkKind
import tv.anion.tv.ui.CONTENT_TTL_MS
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
    private val accountWatched: AccountWatchedEpisodes,
    signedIn: StateFlow<Boolean>,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()
    private var sourceId: SourceId? = null
    private var animeId: String? = null
    private var progressJob: Job? = null
    private var bookmarkJob: Job? = null
    private var loadJob: Job? = null

    /** Когда получен показанный сейчас ответ сети; 0 — данных ещё нет. */
    private var loadedAt = 0L
    private var remoteJob: Job? = null

    /** Строки прогресса тайтла из Room — позиции и досмотры на этом устройстве. */
    private var localProgress: List<WatchProgress> = emptyList()

    /** Серии, отмеченные в аккаунте — на сайте или на другом устройстве. */
    private var remoteWatched: Set<Int> = emptySet()

    init {
        viewModelScope.launch {
            signedIn.collect { isSignedIn ->
                if (isSignedIn) {
                    refreshRemote()
                } else {
                    // Вышли из аккаунта — отметки с сайта больше не наши.
                    remoteJob?.cancel()
                    remoteWatched = emptySet()
                    publishProgress()
                }
            }
        }
    }

    fun load(sourceId: SourceId, animeId: String) {
        val sameAnime = this.sourceId == sourceId &&
            this.animeId == animeId &&
            _state.value.details != null

        if (sameAnime) {
            // Свежее — не трогаем. Протухшее обновляем молча, не сбрасывая
            // экран в «Загрузка…»: карточка уже нарисована, и мигать ею на
            // каждом возврате незачем.
            if (now() - loadedAt < CONTENT_TTL_MS) return
            fetch(sourceId, animeId, keepVisible = true)
            return
        }
        this.sourceId = sourceId
        this.animeId = animeId
        localProgress = emptyList()
        remoteWatched = emptySet()
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
                localProgress = values
                publishProgress()
            }
        }
        fetch(sourceId, animeId, keepVisible = false)
    }

    /**
     * @param keepVisible тихое обновление уже показанной карточки: ни экран
     * загрузки, ни ошибка не должны отбирать у зрителя то, что он уже видит.
     */
    private fun fetch(sourceId: SourceId, animeId: String, keepVisible: Boolean) {
        loadJob?.cancel()
        if (!keepVisible) {
            loadedAt = 0L
            _state.value = DetailsUiState(loading = true)
            // Отметки живут отдельно от ответа сети и сбрасываться вместе с ним не должны.
            publishProgress()
        }
        refreshRemote()
        loadJob = viewModelScope.launch {
            runCatching {
                val source = sources.byId(sourceId)
                val details = source.details(animeId)
                // Выбранную озвучку сохраняем, если она осталась в ответе:
                // иначе тихое обновление молча перекидывало бы список серий
                // на первую озвучку из списка.
                val translation = _state.value.translationId
                    ?.takeIf { current -> details.translations.any { it.id == current } }
                    ?: details.translations.firstOrNull()?.id
                Triple(details, source.episodes(animeId, translation), translation)
            }.onSuccess { (details, episodes, translation) ->
                loadedAt = now()
                // Правится текущее состояние, а не собирается новое: статус
                // закладки и прогресс приходят из Room раньше ответа сети, и
                // пересборка их затирала — кнопка показывала «В закладки» у
                // тайтла, который в закладках уже был.
                _state.value = _state.value.copy(
                    details = details,
                    episodes = episodes,
                    translationId = translation,
                    loading = false,
                    error = null,
                )
            }.onFailure { error ->
                if (keepVisible) return@onFailure
                _state.value = DetailsUiState(loading = false, error = error.message)
            }
        }
    }

    /**
     * Отметки собираются из двух источников: досмотра на этом устройстве и
     * аккаунта. Правило объединения — в [EpisodeMarksPolicy].
     */
    private fun publishProgress() {
        val marks = EpisodeMarksPolicy.of(localProgress, remoteWatched)
        _state.value = _state.value.copy(
            watchedEpisodes = marks.watched,
            partial = marks.partial,
            // Продолжаем самую свежую недосмотренную серию: на ТВ это главный
            // сценарий, и ради него не должно быть лишних нажатий.
            resume = marks.resume?.let { ResumePoint(it.episode, it.positionMs) },
        )
    }

    /** Серии из аккаунта. Ошибка сети не стирает показанное — протухшее обновляется молча. */
    private fun refreshRemote() {
        val sourceId = sourceId ?: return
        val animeId = animeId ?: return
        remoteJob?.cancel()
        remoteJob = viewModelScope.launch {
            runCatching { accountWatched.forAnime(sourceId, animeId) }
                .onSuccess { episodes ->
                    // Пока шёл запрос, могли открыть другой тайтл: чужие отметки
                    // в его карточку попасть не должны.
                    if (this@DetailsViewModel.sourceId != sourceId || this@DetailsViewModel.animeId != animeId) {
                        return@onSuccess
                    }
                    remoteWatched = episodes.orEmpty()
                    publishProgress()
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

