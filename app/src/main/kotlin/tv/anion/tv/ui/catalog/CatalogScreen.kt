package tv.anion.tv.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.PosterCard
import tv.anion.tv.ui.components.initialFocus
import tv.anion.tv.ui.components.rememberInitialFocus
import tv.anion.tv.ui.components.rowFocus
import tv.anion.tv.ui.components.ScreenHeader

@Composable
fun CatalogScreen(
    onOpenAnime: (source: String, animeId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { CatalogViewModel(container.sources) }
    val state by vm.state.collectAsStateWithLifecycle()
    val initial = rememberInitialFocus(state.items.isNotEmpty())

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader("Каталог", "Выберите источник и продолжайте с пульта")
        LazyRow(
            modifier = Modifier.rowFocus(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.sources, key = { it.name }) { id ->
                val selected = id == state.sourceId
                val label = container.sources.byId(id).displayName
                if (selected) {
                    Button(onClick = { vm.select(id) }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { vm.select(id) }) { Text(label) }
                }
            }
        }

        when {
            state.loading && state.items.isEmpty() -> MessagePane("Загрузка…")
            state.error != null && state.items.isEmpty() ->
                MessagePane(state.error!!, action = "Повторить", onAction = vm::retry)
            else -> {
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState, state.items.size, state.hasMore) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { last ->
                            if (last != null && last >= state.items.size - 6) vm.loadMore()
                        }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(state.items, key = { _, anime -> "${anime.source}:${anime.id}" }) { index, anime ->
                        PosterCard(
                            anime = anime,
                            onClick = { onOpenAnime(anime.source.name, anime.id) },
                            modifier = if (index == 0) Modifier.initialFocus(initial) else Modifier,
                        )
                    }
                }
            }
        }
    }
}
