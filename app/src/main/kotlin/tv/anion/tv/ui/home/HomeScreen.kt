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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
import tv.anion.tv.ui.components.rowFocus
import tv.anion.tv.ui.theme.ScreenBottomGutter
import tv.anion.tv.ui.theme.ScreenTopGutter

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
    // Снимочная карта, а не обычная: эффект начального фокуса ждёт появления
    // держателя через snapshotFlow, и он обязан узнать о записи.
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }

    if (state.rows.isEmpty() && state.loading) {
        MessagePane("Загрузка…")
        return
    }

    // Пустые ряды выкидываются до отрисовки: рисовать заголовок секции без
    // единой карточки под ним не для чего.
    val rows = state.rows.filter { it.sourceId != null || it.items.isNotEmpty() }

    // Фокус при заходе — на первой карточке, а не на рельсе: сфокусированный
    // рельс сам разворачивается, и приложение открывалось панелью навигации
    // поверх контента вместо самого контента.
    //
    // Ставится ровно один раз за заход на экран — отсюда ключ `Unit`, а не ряды.
    // Ряды приезжают по одному на источник, «продолжить смотреть» — отдельно из
    // Room, и на каждое обновление эффект перезапускался. К тому моменту фокус
    // уже жил своей жизнью: обновление списка выкидывало сфокусированную
    // карточку из композиции, система перекидывала фокус на случайную соседнюю,
    // та записывалась в `lastFocusedKey` — и следующий заход эффекта прилежно
    // возвращал фокус на неё. Так он и оказывался где-то в середине ряда.
    //
    // Оба ожидания — через snapshotFlow, без отмеренных кадров: и первая
    // карточка, и её держатель фокуса появляются тогда, когда появляются.
    // Держатель регистрируется, только когда LazyColumn дойдёт до карточки в
    // композиции, а карточек может не быть вовсе — если все источники отдали
    // ошибку, эффект просто ждёт, и фокус остаётся на рельсе.
    LaunchedEffect(Unit) {
        val key = snapshotFlow {
            // Запомненная карточка — для возврата из карточки тайтла: фокус не
            // должен прыгать в начало ряда после «назад».
            vm.lastFocusedKey ?: state.rows.firstOrNull { it.items.isNotEmpty() }
                ?.items?.firstOrNull()
                ?.let { "${it.source}:${it.id}" }
        }.filterNotNull().first()

        val card = snapshotFlow { requesters[key] }.filterNotNull().first()
        runCatching { card.requestFocus() }
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
    // гайдлайнов Android TV). Сам рельс прижат к левому краю экрана — полей под
    // обрез больше нет, и висеть в 48dp от рамки ему незачем.
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Справа отступа нет намеренно: ряд обязан уезжать за край
                // экрана, иначе не видно, что он продолжается.
                .padding(start = RAIL_COLLAPSED_WIDTH + 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = ScreenTopGutter, bottom = ScreenBottomGutter),
        ) {
            item { ContentBrand() }

            rows.forEach { row ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(row.title)
                        if (row.error != null) {
                            Text(row.error, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyRow(
                                modifier = Modifier.rowFocus(),
                                // Гаттер между карточками по гайдлайнам — 20dp, но
                                // 12 из них плитка несёт в себе: в её след заложен
                                // запас под увеличение в фокусе.
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                // Вертикальные 6dp — подушка поверх запаса внутри
                                // плитки: ряд клипует содержимое по своему
                                // viewport и срезал бы обводку в фокусе.
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
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
        )
    }
}

