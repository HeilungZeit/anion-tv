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
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Ключ карточки, чтобы после «назад» фокус не прыгал в начало ряда. */
    var lastFocusedKey: String? = null

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
        sources.all.forEach { source ->
            viewModelScope.launch {
                val result = runCatching { source.feed(1) }
                _state.update { current ->
                    current.copy(
                        loading = false,
                        rows = current.rows.map { row ->
                            if (row.sourceId != source.id) row
                            else row.copy(
                                items = result.getOrNull()?.items.orEmpty()
                                    .distinctBy { anime -> anime.source to anime.id },
                                error = result.exceptionOrNull()?.message,
                            )
                        },
                    )
                }
            }
        }
    }
}
