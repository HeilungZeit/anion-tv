package tv.anion.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anion.source.SourceRegistry
import tv.anion.source.model.Anime
import tv.anion.data.repo.SearchHistoryRepository

data class SearchUiState(
    val history: List<String> = emptyList(),
    val items: List<Anime> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(
    private val sources: SourceRegistry,
    private val history: SearchHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            history.observe().collect { queries ->
                _state.value = _state.value.copy(history = queries)
            }
        }
    }

    fun search(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        viewModelScope.launch { history.add(query) }
        _state.value = _state.value.copy(
            loading = true,
            searched = true,
            error = null,
            items = emptyList(),
        )
        viewModelScope.launch {
            val pages = sources.all.map { source ->
                async { runCatching { source.search(query) }.getOrNull()?.items.orEmpty() }
            }.awaitAll()
            _state.value = _state.value.copy(
                loading = false,
                // Один тайтл приходит из разных источников — ключи в сетке
                // должны остаться уникальными.
                items = pages.flatten().distinctBy { it.source to it.id },
            )
        }
    }

    /**
     * Распознавание закрылось без результата: на ТВ это чаще всего молчание в
     * микрофон или отказ в правах, и молча возвращаться в пустой экран нельзя.
     */
    fun voiceCancelled() {
        _state.value = _state.value.copy(error = "Не расслышал. Повторите или наберите название на клавиатуре")
    }

    fun voiceUnavailable() {
        _state.value = _state.value.copy(error = "Голосовой ввод на этом устройстве недоступен")
    }
}
