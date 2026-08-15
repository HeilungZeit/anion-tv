package tv.anion.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.PosterCard
import tv.anion.tv.ui.components.ContinueWatchingCard
import tv.anion.tv.ui.components.rowFocus
import tv.anion.tv.ui.components.BrandHeader
import tv.anion.tv.ui.components.HeaderAction
import tv.anion.tv.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onOpenCatalog: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAnime: (source: String, animeId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { HomeViewModel(container.sources, container.watchProgress) }
    val state by vm.state.collectAsStateWithLifecycle()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val navigationRequester = remember { FocusRequester() }

    LaunchedEffect(state.loading, state.rows) {
        if (state.loading) return@LaunchedEffect
        val target = vm.lastFocusedKey?.let(requesters::get) ?: navigationRequester
        runCatching { target.requestFocus() }
    }

    if (state.rows.isEmpty() && state.loading) {
        MessagePane("Загрузка…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            BrandHeader {
                HeaderAction(
                    label = "Каталог",
                    onClick = onOpenCatalog,
                    modifier = Modifier.focusRequester(navigationRequester),
                )
                HeaderAction("Поиск", onOpenSearch)
                HeaderAction("Закладки", onOpenBookmarks)
                HeaderAction("Аккаунт", onOpenAccount)
            }
        }
        state.rows.forEach { row ->
            if (row.sourceId == null && row.items.isEmpty()) return@forEach
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(row.title, Modifier.padding(top = 4.dp))
                    if (row.error != null) {
                        Text(row.error, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyRow(
                            modifier = Modifier.rowFocus(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            // LazyRow клипует дочерние элементы по своему viewport.
                            // Крайним карточкам нужен gutter для focus scale и рамки,
                            // иначе первая плитка визуально срезается слева.
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            itemsIndexed(row.items, key = { _, anime -> "${anime.source}:${anime.id}" }) { _, anime ->
                                val key = "${anime.source}:${anime.id}"
                                val requester = remember(key) { FocusRequester() }
                                DisposableEffect(key, requester) {
                                    requesters[key] = requester
                                    onDispose {
                                        if (requesters[key] === requester) requesters.remove(key)
                                    }
                                }
                                val cardModifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged { if (it.isFocused) vm.lastFocusedKey = key }
                                if (row.sourceId == null) {
                                    ContinueWatchingCard(
                                        anime = anime,
                                        onClick = { onOpenAnime(anime.source.name, anime.id) },
                                        modifier = cardModifier,
                                    )
                                } else {
                                    PosterCard(
                                        anime = anime,
                                        onClick = { onOpenAnime(anime.source.name, anime.id) },
                                        modifier = cardModifier,
                                        showFeedMetadata = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
