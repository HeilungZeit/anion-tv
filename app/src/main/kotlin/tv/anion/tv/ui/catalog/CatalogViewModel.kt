package tv.anion.tv.ui.catalog

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

data class CatalogUiState(
    val sourceId: SourceId,
    val sources: List<SourceId>,
    val items: List<Anime> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

class CatalogViewModel(private val registry: SourceRegistry) : ViewModel() {
    private val _state = MutableStateFlow(
        CatalogUiState(
            sourceId = registry.all.first().id,
            sources = registry.all.map { it.id },
        ),
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        loadMore()
    }

    fun select(id: SourceId) {
        if (id == _state.value.sourceId) return
        _state.value = CatalogUiState(
            sourceId = id,
            sources = _state.value.sources,
        )
        loadMore()
    }

    fun retry() = loadMore()

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || !snapshot.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val nextPage = snapshot.page + 1
            runCatching { registry.byId(snapshot.sourceId).feed(nextPage) }
                .onSuccess { page ->
                    _state.update {
                        if (it.sourceId != snapshot.sourceId) it
                        else it.copy(
                            // Страницы каталога умеют пересекаться, а повтор
                            // карточки — это дубликат ключа в LazyGrid и падение
                            // всего экрана, а не просто лишняя плитка.
                            items = (it.items + page.items).distinctBy { anime -> anime.source to anime.id },
                            page = nextPage,
                            hasMore = page.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.sourceId != snapshot.sourceId) it
                        else it.copy(loading = false, error = error.message)
                    }
                }
        }
    }
}
