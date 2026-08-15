package tv.anion.tv.ui.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anion.data.repo.Bookmark
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.repo.BookmarkRepository
import tv.anion.source.model.Anime
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.PosterCard
import tv.anion.tv.ui.components.ScreenHeader
import tv.anion.tv.ui.components.initialFocus
import tv.anion.tv.ui.components.rememberInitialFocus
import tv.anion.tv.ui.components.rowFocus

/** Порядок вкладок повторяет сайт, чтобы привычка переносилась с веба на ТВ. */
private val TABS = listOf(
    BookmarkKind.WATCHING to "Смотрю",
    BookmarkKind.WILL_WATCH to "Буду смотреть",
    BookmarkKind.WATCHED to "Просмотрено",
    BookmarkKind.ON_HOLD to "Отложено",
    BookmarkKind.DROPPED to "Брошено",
)

data class BookmarksUiState(val all: List<Bookmark> = emptyList(), val signedIn: Boolean = false)

class BookmarksViewModel(
    bookmarks: BookmarkRepository,
    signedIn: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(BookmarksUiState(signedIn = signedIn))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarks.observeAll().collect { list -> _state.value = _state.value.copy(all = list) }
        }
    }
}

@Composable
fun BookmarksScreen(onOpenAnime: (source: String, animeId: String) -> Unit) {
    val container = LocalAppContainer.current
    val signedIn by container.account.signedIn.collectAsStateWithLifecycle()
    val vm = viewModel { BookmarksViewModel(container.bookmarks, signedIn) }
    val state by vm.state.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(BookmarkKind.WATCHING) }
    val items = remember(state.all, tab) { state.all.filter { it.kind == tab } }
    val initial = rememberInitialFocus()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Закладки", "То же, что на сайте: статусы и просмотренные серии")

        LazyRow(
            modifier = Modifier.rowFocus(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        ) {
            items(TABS, key = { it.first.name }) { (kind, label) ->
                val count = state.all.count { it.kind == kind }
                TabChip(
                    label = if (count > 0) "$label  ·  $count" else label,
                    selected = kind == tab,
                    onClick = { tab = kind },
                    modifier = if (kind == BookmarkKind.WATCHING) Modifier.initialFocus(initial) else Modifier,
                )
            }
        }

        when {
            !signedIn -> MessagePane("Войдите в аккаунт — закладки подтянутся с сайта")
            items.isEmpty() -> MessagePane("Здесь пусто. Добавьте тайтл с его страницы")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(items, key = { _, item -> "${item.source}:${item.animeId}" }) { _, item ->
                    PosterCard(
                        anime = item.asCard(),
                        onClick = { onOpenAnime(item.source.name, item.animeId) },
                    )
                }
            }
        }
    }
}

/**
 * У закладки нет полной карточки тайтла — только то, что вернул сайт. Этого
 * хватает на плитку, а подробности подтянутся при открытии.
 */
private fun Bookmark.asCard() = Anime(
    id = animeId,
    source = source,
    title = title,
    titleOriginal = null,
    year = null,
    posterUrl = posterUrl,
    thumbnailUrl = posterUrl,
)

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        // Без увеличения в фокусе: ряд обрезает всё, что выходит за его
        // границы, и у кнопки срезало верх с низом. Фокус и так виден цветом.
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
    ) { Text(label) }
}
