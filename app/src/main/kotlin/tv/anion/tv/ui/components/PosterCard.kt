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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.RectangleShape
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

/**
 * Ширина плитки. Полный след в разметке — [CARD_FOOTPRINT]: на него настраивают
 * `GridCells.Adaptive`, иначе плитка не влезет в ячейку сетки.
 */
private val CARD_WIDTH = 144.dp

/**
 * Запас вокруг плитки под её увеличение в фокусе — с избытком, на плитку до
 * 240×400dp. Прокрутки он больше не касается (её держит неизменный размер
 * фокусируемого узла), но нужен, чтобы увеличенную плитку не срезал viewport
 * ряда или сетки: они клипуют содержимое по своим границам.
 */
private val FOCUS_INSET_H = 6.dp
private val FOCUS_INSET_V = 10.dp

val CARD_FOOTPRINT = CARD_WIDTH + FOCUS_INSET_H * 2

@Composable
fun PosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFeedMetadata: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    // 1.05 — средняя из трёх величин, которые гайдлайны Android TV называют для
    // фокуса (1.025 / 1.05 / 1.1). Больше на плитке этого размера уже дрожит.
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(180),
        label = "poster-focus",
    )
    val shape = RoundedCornerShape(12.dp)

    // Фокус держит вся плитка целиком, вместе с подписью, и её размер от фокуса
    // не меняется: увеличение живёт на содержимом внутри.
    //
    // Так сделано не для красоты. Прокрутка по фокусу тянет в кадр границы
    // сфокусированного узла — а `graphicsLayer` эти границы меняет. Пока
    // увеличение висело на самом узле, его размер менялся все 180 мс анимации, и
    // список подкручивался вслед за ним каждый кадр: экран полз и дёргался. А
    // пока фокус держал один постер, без подписи, в кадр тянуло только постер —
    // название под ним оставалось срезанным, и это лечили отдельной доводкой,
    // которая скроллила вторым заходом поверх штатной. Теперь узел неподвижен и
    // включает подпись: штатной прокрутки достаточно, и она одна.
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RectangleShape, RectangleShape),
        // Подложки у плитки нет — её рисует сам постер, а под подписью должен
        // остаться фон экрана.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            Modifier
                .padding(horizontal = FOCUS_INSET_H, vertical = FOCUS_INSET_V)
                .width(CARD_WIDTH)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            // Обводка фокуса рисуется снаружи постера, с зазором — так её описывают
            // гайдлайны (outline width + outline inset), и так она видна на кадре
            // любой яркости. Белая, а не брендовая: на постерах с синим небом
            // синяя рамка терялась.
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (focused) 3.dp else 0.dp,
                        color = if (focused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(5f / 7f)
                        .clip(shape),
                ) {
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
            // Подпись выровнена по постеру, а не по обводке: блок текста должен
            // совпадать по ширине с картинкой (гайдлайны по анатомии карточки),
            // поэтому здесь тот же отступ 4dp, что и у зазора под обводку.
            Column(Modifier.padding(horizontal = 4.dp)) {
                Spacer(Modifier.height(7.dp))
                // Название и метаданные намеренно находятся в одном блоке: Material TV
                // добавлял между отдельными слотами слишком большой системный отступ.
                Box(Modifier.height(38.dp)) {
                    Text(
                        anime.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // Белый и в фокусе, и без него: цветное название прыгало
                        // вместе с обводкой и читалось как второй акцент.
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                if (showFeedMetadata) {
                    FeedCardSubtitle(anime)
                } else {
                    Text(
                        listOfNotNull(
                            anime.year?.toString(),
                            when (anime.source) {
                                SourceId.ANILIBRIA -> "AniLibria"
                                SourceId.KODIK -> "Anion"
                            },
                        ).joinToString("  •  "),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
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
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(180),
        label = "continue-focus",
    )

    // Размер поверхности от фокуса не зависит, увеличение — на содержимом
    // внутри: прокрутка по фокусу тянет в кадр границы сфокусированного узла, и
    // меняющийся размер заставлял ряд ползти за ним всю анимацию. Подложка
    // уехала внутрь по той же причине — она обязана увеличиваться вместе с
    // содержимым, иначе фон отстаёт от текста.
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RectangleShape, RectangleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier
                .padding(horizontal = FOCUS_INSET_H, vertical = FOCUS_INSET_V)
                .width(288.dp)
                .height(114.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(
                    if (focused) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StablePosterImage(
                url = anime.thumbnailUrl ?: anime.posterUrl,
                title = anime.title,
                widthPx = 270,
                heightPx = 378,
                modifier = Modifier
                    // Карточка высотой 114.dp: 82 × 114 сохраняет постерные 5:7,
                    // поэтому ContentScale.Crop больше не отрезает верх и низ.
                    .width(82.dp)
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
                        SourceId.KODIK -> "Anion"
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
