package tv.anion.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import tv.anion.source.SourceId
import tv.anion.source.model.AnimeDetails
import tv.anion.source.model.Episode
import tv.anion.source.model.Translation
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.SectionHeader
import tv.anion.tv.ui.components.StablePosterImage
import tv.anion.tv.ui.components.initialFocus
import tv.anion.tv.ui.components.rememberInitialFocus
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DetailsScreen(
    source: String,
    animeId: String,
    onPlay: (episode: Int, translationId: String?) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { DetailsViewModel(container.sources, container.watchProgress) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(source, animeId) {
        listState.scrollToItem(0)
        vm.load(SourceId.valueOf(source), animeId)
    }
    val initial = rememberInitialFocus(state.episodes.isNotEmpty())

    when {
        state.loading -> MessagePane("Загрузка…")
        state.error != null -> MessagePane(state.error!!, action = "Повторить") {
            vm.load(SourceId.valueOf(source), animeId)
        }
        state.details != null -> {
            val details = state.details!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                // Фокус главной кнопки просит небольшой bring-into-view.
                // Верхний запас поглощает этот сдвиг, не обрезая углы hero.
                contentPadding = PaddingValues(top = 32.dp, bottom = 28.dp),
            ) {
                item {
                    val resume = state.resume?.takeIf { point ->
                        state.episodes.any { it.number == point.episode }
                    }
                    DetailsHero(
                        details = details,
                        canPlay = state.episodes.isNotEmpty(),
                        playLabel = when {
                            resume != null ->
                                "▶  Продолжить серию ${resume.episode}  ·  ${formatMs(resume.positionMs)}"
                            state.watchedEpisodes.isEmpty() -> "▶  Смотреть с первой серии"
                            else -> "▶  Смотреть"
                        },
                        onPlay = {
                            val target = resume?.episode
                                ?: state.episodes.firstOrNull { it.number !in state.watchedEpisodes }?.number
                                ?: state.episodes.firstOrNull()?.number
                            target?.let { onPlay(it, state.translationId) }
                        },
                        modifier = if (state.episodes.isNotEmpty()) Modifier.initialFocus(initial) else Modifier,
                    )
                }

                if (details.translations.size > 1) {
                    item { SectionHeader("Озвучка") }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(details.translations, key = { it.id }) { translation ->
                                TranslationCard(
                                    translation = translation,
                                    selected = translation.id == state.translationId,
                                    onClick = { vm.selectTranslation(translation.id) },
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        buildString {
                            append("Серии")
                            details.episodesTotal?.let { append("  ·  $it") }
                        },
                    )
                }
                item {
                    if (state.episodes.isEmpty()) {
                        Text(
                            "Для выбранной озвучки серии пока не найдены",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val rowCount = if (state.episodes.size > 6) 2 else 1
                        val orderedEpisodes = state.episodes.forHorizontalGrid(rowCount)
                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(rowCount),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (rowCount == 2) 188.dp else 90.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp),
                        ) {
                            itemsIndexed(orderedEpisodes, key = { _, episode -> episode.number }) { _, episode ->
                                EpisodeCard(
                                    episode = episode,
                                    watched = episode.number in state.watchedEpisodes,
                                    progress = state.partial[episode.number] ?: 0f,
                                    onClick = { onPlay(episode.number, state.translationId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsHero(
    details: AnimeDetails,
    canPlay: Boolean,
    playLabel: String,
    onPlay: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        shape = shape,
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            details.backdropUrl?.let { BackdropImage(it) }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.surface,
                            0.58f to MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                StablePosterImage(
                    url = details.anime.posterUrl ?: details.anime.thumbnailUrl,
                    title = details.anime.title,
                    widthPx = 600,
                    heightPx = 840,
                    modifier = Modifier
                        .width(250.dp)
                        .fillMaxHeight()
                        .aspectRatio(5f / 7f)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                Text(
                    details.anime.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val alternative = details.otherTitles.firstOrNull() ?: details.anime.titleOriginal
                alternative?.takeIf { it.isNotBlank() && it != details.anime.title }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(details.metadataLabels(), key = { it }) { MetaChip(it) }
                }
                if (details.genres.isNotEmpty()) {
                    Text(
                        details.genres.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = onPlay, enabled = canPlay, modifier = modifier) {
                    Text(playLabel)
                }
                details.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                details.studios.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        "Студия: ${it.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                    if (details.blocked.byGeo || details.blocked.byCopyright) {
                        Text("Может быть недоступно в вашем регионе", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackdropImage(url: String) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(1500, 500)
        .crossfade(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    AsyncImage(
        model = request,
        contentDescription = null,
        // Кадры приходят в разных пропорциях: hero всегда должен быть закрыт
        // изображением целиком, без пустых полос по краям секции.
        contentScale = ContentScale.FillBounds,
        alpha = 0.48f,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MetaChip(label: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TranslationCard(translation: Translation, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape, shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(
            Modifier
                .width(230.dp)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(translation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            translation.type?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, watched: Boolean, progress: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(152.dp)
            .height(82.dp),
        shape = ClickableSurfaceDefaults.shape(shape, shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxSize()) {
        // Недосмотренная серия видна с одного взгляда: полоска внизу карточки
        // отвечает на вопрос «где я остановился» без захода в плеер.
        if (progress > 0f && !watched) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("СЕРИЯ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(episode.number.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (watched) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", fontWeight = FontWeight.Bold) }
            }
        }
        }
    }
}

/** Позиция для подписи «Продолжить серию N · 12:30». */
private fun formatMs(ms: Long): String {
    val total = (ms / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun AnimeDetails.metadataLabels(): List<String> = buildList {
    anime.year?.let { add(it.toString()) }
    type?.let(::add)
    status?.let(::add)
    ageRating?.let(::add)
    score?.takeIf { it > 0 }?.let { value ->
        val votes = scoreVotes?.takeIf { it > 0 }?.let { " · ${NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(it)}" }.orEmpty()
        add("★ ${String.format(Locale.US, "%.1f", value)}$votes")
    }
    val aired = airedEpisodes
    val total = episodesTotal
    when {
        aired != null && total != null -> add("$aired из $total серий")
        total != null -> add("$total серий")
    }
    views?.takeIf { it > 0 }?.let {
        add("${NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(it)} просмотров")
    }
}

/**
 * LazyHorizontalGrid заполняет элементы сверху вниз, а затем слева направо.
 * Переставляем данные так, чтобы пользователь видел обычный порядок по строкам:
 * `1 2 3 4` сверху и `5 6 7 8` снизу.
 */
private fun List<Episode>.forHorizontalGrid(rows: Int): List<Episode> {
    if (rows <= 1 || size <= 1) return this
    val columns = (size + rows - 1) / rows
    return buildList(size) {
        repeat(columns) { column ->
            repeat(rows) { row ->
                val index = row * columns + column
                if (index < this@forHorizontalGrid.size) add(this@forHorizontalGrid[index])
            }
        }
    }
}
