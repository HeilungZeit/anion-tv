package tv.anion.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import tv.anion.tv.ui.components.ContentBrand
import tv.anion.tv.ui.components.ContinueWatchingCard
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.NavigationRail
import tv.anion.tv.ui.components.PosterCard
import tv.anion.tv.ui.components.RAIL_COLLAPSED_WIDTH
import tv.anion.tv.ui.components.RailDestination
import tv.anion.tv.ui.components.RailGlyph
import tv.anion.tv.ui.components.SectionHeader
import tv.anion.tv.ui.components.centerFocusedItem
import tv.anion.tv.ui.components.rowFocus

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

    // Пустые ряды выкидываются до отрисовки: индекс в LazyColumn должен совпадать
    // с позицией ряда, иначе центрируется не тот.
    val rows = state.rows.filter { it.sourceId != null || it.items.isNotEmpty() }
    val listState = rememberLazyListState()
    var focusedRow by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusedRow) {
        if (focusedRow >= 0) listState.centerFocusedItem(focusedRow)
    }

    val destinations = listOf(
        RailDestination(RailGlyph.Search, "Поиск", onOpenSearch),
        // «Главная» уже открыта — пункт нужен как указатель текущего раздела.
        RailDestination(RailGlyph.Home, "Главная") {},
        RailDestination(RailGlyph.Catalog, "Каталог", onOpenCatalog),
        RailDestination(RailGlyph.Bookmarks, "Закладки", onOpenBookmarks),
        RailDestination(RailGlyph.Account, "Аккаунт", onOpenAccount),
    )

    // Рельс лежит поверх контента, а контент отступает ровно на его свёрнутую
    // ширину: при разворачивании ряды не переливаются (вариант Modal из
    // гайдлайнов Android TV).
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = RAIL_COLLAPSED_WIDTH + 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { ContentBrand() }

            rows.forEachIndexed { rowIndex, row ->
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        // +1: нулевой элемент колонки — шапка.
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedRow = rowIndex + 1 },
                    ) {
                        SectionHeader(row.title)
                        if (row.error != null) {
                            Text(row.error, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyRow(
                                modifier = Modifier.rowFocus(),
                                // 20dp — гаттер между карточками из гайдлайнов.
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                // LazyRow клипует дочерние элементы по своему viewport.
                                // Крайним карточкам нужен gutter под обводку и scale
                                // фокуса, иначе первая плитка визуально срезается.
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                itemsIndexed(
                                    row.items,
                                    key = { _, anime -> "${anime.source}:${anime.id}" },
                                ) { _, anime ->
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
                                        .onFocusChanged { if (it.hasFocus) vm.lastFocusedKey = key }
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

        NavigationRail(
            destinations = destinations,
            activeIndex = 1,
            modifier = Modifier.align(Alignment.CenterStart),
            activeItemModifier = Modifier.focusRequester(navigationRequester),
        )
    }
}
