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
import tv.anion.tv.ui.CONTENT_TTL_MS

data class CatalogUiState(
    val sourceId: SourceId,
    val sources: List<SourceId>,
    val items: List<Anime> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

class CatalogViewModel(
    private val registry: SourceRegistry,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CatalogUiState(
            sourceId = registry.all.first().id,
            sources = registry.all.map { it.id },
        ),
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    /** Время последнего удачного ответа источника; 0 — списка ещё нет. */
    private var loadedAt = 0L

    init {
        loadMore()
    }

    fun select(id: SourceId) {
        if (id == _state.value.sourceId) return
        _state.value = CatalogUiState(
            sourceId = id,
            sources = _state.value.sources,
        )
        loadedAt = 0L
        loadMore()
    }

    fun retry() = loadMore()

    /**
     * Перезагрузка каталога с первой страницы, если данные протухли.
     *
     * Вызывается при каждом появлении экрана. ViewModel живёт в Activity и
     * переживает выход на главный экран приставки, а фид Kodik отдаёт
     * `hasMore = false` — то есть [loadMore] после первой страницы больше
     * ничего не делает, и без этого каталог не обновлялся до перезапуска.
     */
    fun refreshIfStale() {
        if (_state.value.loading || now() - loadedAt < CONTENT_TTL_MS) return
        fetch(page = 1, replace = true)
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || !snapshot.hasMore) return
        fetch(page = snapshot.page + 1, replace = false)
    }

    /**
     * @param replace список собирается заново, а не дописывается: обновление
     * начинает с первой страницы, и старые элементы после неё — это уже другой
     * срез каталога.
     */
    private fun fetch(page: Int, replace: Boolean) {
        val snapshot = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { registry.byId(snapshot.sourceId).feed(page) }
                .onSuccess { loaded ->
                    loadedAt = now()
                    _state.update {
                        if (it.sourceId != snapshot.sourceId) it
                        else it.copy(
                            // Страницы каталога умеют пересекаться, а повтор
                            // карточки — это дубликат ключа в LazyGrid и падение
                            // всего экрана, а не просто лишняя плитка.
                            items = (if (replace) loaded.items else it.items + loaded.items)
                                .distinctBy { anime -> anime.source to anime.id },
                            page = page,
                            hasMore = loaded.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.sourceId != snapshot.sourceId) it
                        // Тихое обновление не жалуется поверх уже показанного
                        // списка: зрителю нечего чинить, а список остаётся.
                        else if (replace && it.items.isNotEmpty()) it.copy(loading = false)
                        else it.copy(loading = false, error = error.message)
                    }
                }
        }
    }
}
