package tv.anion.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import tv.anion.source.SourceId
import tv.anion.source.model.Anime
import tv.anion.tv.R
import java.util.Locale

@Composable
fun PosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFeedMetadata: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tween(180),
        label = "poster-focus",
    )
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .width(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus },
    ) {
        Surface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier
                .aspectRatio(5f / 7f)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                    shape = shape,
                )
                .clip(shape),
        ) {
            Box(Modifier.fillMaxSize()) {
                StablePosterImage(
                    // Крупный постер первым: миниатюра рассчитана на список из
                    // мелких плиток, а на 4K-экране одна плитка — это сотни
                    // пикселей, и small растягивался в мыло. Coil декодирует под
                    // размер виджета, так что памяти это не прибавляет.
                    url = anime.posterUrl ?: anime.thumbnailUrl,
                    title = anime.title,
                    widthPx = 480,
                    heightPx = 672,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showFeedMetadata) {
                    anime.feedStatus()?.let { status ->
                        StatusBadge(
                            status = status,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                        )
                    }
                    anime.score
                        ?.takeIf { it > 0.0 }
                        ?.let { score ->
                            RatingBadge(
                                score = score,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp),
                            )
                        }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        // Название и метаданные намеренно находятся в одном блоке: Material TV
        // добавлял между отдельными слотами слишком большой системный отступ.
        Box(Modifier.height(35.dp)) {
            Text(
                anime.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (focused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(1.dp))
        if (showFeedMetadata) {
            FeedCardSubtitle(anime)
        } else {
            Text(
                listOfNotNull(
                    anime.year?.toString(),
                    when (anime.source) {
                        SourceId.ANILIBRIA -> "AniLibria"
                        SourceId.KODIK -> "anion"
                    },
                ).joinToString("  •  "),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun FeedCardSubtitle(anime: Anime) {
    val episodes = anime.episodesLabel()
    val text = listOfNotNull(anime.year?.toString(), episodes).joinToString("  •  ")
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun StatusBadge(status: FeedStatus, modifier: Modifier = Modifier) {
    val background = when (status) {
        FeedStatus.ONGOING -> Color(0xE62E7D5B)
        FeedStatus.RELEASED -> Color(0xE6294A68)
    }
    Text(
        text = status.label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        maxLines = 1,
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun RatingBadge(score: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xD914171C), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "★",
            color = Color(0xFFFFC857),
            fontSize = 12.sp,
            lineHeight = 12.sp,
        )
        Text(
            text = String.format(Locale.US, "%.1f", score),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 12.sp,
        )
    }
}

private enum class FeedStatus(val label: String) {
    ONGOING("ОНГОИНГ"),
    RELEASED("ВЫШЛО"),
}

private fun Anime.feedStatus(): FeedStatus? {
    val value = listOfNotNull(statusCode, status).joinToString(" ").lowercase()
    return when {
        value.contains("ongoing") || value.contains("онгоинг") || value.contains("в эфире") -> FeedStatus.ONGOING
        value.contains("released") || value.contains("выш") || value.contains("заверш") -> FeedStatus.RELEASED
        else -> null
    }
}

private fun Anime.episodesLabel(): String? {
    val aired = airedEpisodes?.takeIf { it >= 0 }
    val total = episodesTotal?.takeIf { it > 0 }
    return when {
        aired != null && total != null -> "$aired из $total ${episodeWord(total)}"
        aired != null -> "$aired ${episodeWord(aired)}"
        total != null && feedStatus() == FeedStatus.RELEASED -> "$total ${episodeWord(total)}"
        else -> null
    }
}

private fun episodeWord(value: Int): String {
    val lastTwo = value % 100
    if (lastTwo in 11..14) return "серий"
    return when (value % 10) {
        1 -> "серия"
        2, 3, 4 -> "серии"
        else -> "серий"
    }
}

/** Низкая широкая плитка истории: не заслоняет каталог на первом TV-экране. */
@Composable
fun ContinueWatchingCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(320.dp)
            .height(126.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.045f),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            StablePosterImage(
                url = anime.thumbnailUrl ?: anime.posterUrl,
                title = anime.title,
                widthPx = 270,
                heightPx = 378,
                modifier = Modifier
                    // Карточка высотой 126.dp: 90 × 126 сохраняет постерные 5:7,
                    // поэтому ContentScale.Crop больше не отрезает верх и низ.
                    .width(90.dp)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "ПРОДОЛЖИТЬ",
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
                Text(
                    anime.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    when (anime.source) {
                        SourceId.ANILIBRIA -> "AniLibria"
                        SourceId.KODIK -> "anion"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

/** Не пересоздаёт запрос при каждом движении фокуса и всегда оставляет подложку. */
@Composable
fun StablePosterImage(
    url: String?,
    title: String,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (!url.isNullOrBlank()) {
                    memoryCacheKey(url)
                    diskCacheKey(url)
                }
            }
            .build()
    }

    Box(modifier.background(Color(0xFF202020))) {
        Image(
            painter = painterResource(R.drawable.anion_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.16f,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = request,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
