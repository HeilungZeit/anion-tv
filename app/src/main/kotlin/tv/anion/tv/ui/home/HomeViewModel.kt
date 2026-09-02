package tv.anion.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.anion.source.SourceId
import tv.anion.source.SourceRegistry
import tv.anion.source.model.Anime
import tv.anion.data.repo.WatchProgressRepository
import tv.anion.tv.ui.CONTENT_TTL_MS

data class HomeRow(
    val sourceId: SourceId?,
    val title: String,
    val items: List<Anime> = emptyList(),
    val error: String? = null,
)

data class HomeUiState(
    val rows: List<HomeRow> = emptyList(),
    val loading: Boolean = true,
)

class HomeViewModel(
    private val sources: SourceRegistry,
    progress: WatchProgressRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Ключ карточки, чтобы после «назад» фокус не прыгал в начало ряда. */
    var lastFocusedKey: String? = null

    /** Время последнего удачного ответа источника; 0 — рядов ещё нет. */
    private var loadedAt = 0L
    private var inFlight = 0

    init {
        _state.value = HomeUiState(
            rows = listOf(HomeRow(null, "Продолжить смотреть")) +
                sources.all.map { HomeRow(it.id, it.displayName) },
            loading = true,
        )
        viewModelScope.launch {
            progress.observeContinueWatching().collect { saved ->
                _state.update { current ->
                    current.copy(rows = current.rows.map { row ->
                        if (row.sourceId == null) {
                            row.copy(items = saved.map { it.asAnime() }.distinctBy { it.source to it.id })
                        } else row
                    })
                }
            }
        }
        refreshIfStale()
    }

    /**
     * Перезагрузка рядов, если они протухли.
     *
     * Вызывается при каждом появлении экрана — заход, «назад» из карточки,
     * возврат из фона. ViewModel живёт в Activity, а та переживает выход на
     * главный экран приставки: без этого ряды оставались такими же, какими их
     * загрузил холодный старт, и вышедшая серия не показывалась вовсе.
     */
    fun refreshIfStale() {
        if (inFlight > 0 || (loadedAt != 0L && now() - loadedAt < CONTENT_TTL_MS)) return

        sources.all.forEach { source ->
            inFlight++
            viewModelScope.launch {
                val result = runCatching { source.feed(1) }
                if (result.isSuccess) loadedAt = now()
                inFlight--
                _state.update { current ->
                    current.copy(
                        loading = false,
                        rows = current.rows.map { row ->
                            if (row.sourceId != source.id) row
                            else result.fold(
                                onSuccess = { page ->
                                    row.copy(
                                        items = page.items
                                            .distinctBy { anime -> anime.source to anime.id },
                                        error = null,
                                    )
                                },
                                // Уже показанный ряд не стирается: отвалившаяся
                                // сеть — не повод оставить зрителя с пустым
                                // экраном вместо вчерашнего списка.
                                onFailure = { error ->
                                    if (row.items.isEmpty()) row.copy(error = error.message) else row
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
