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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import tv.anion.source.SourceId
import tv.anion.source.model.Anime
import tv.anion.tv.R

@Composable
fun PosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tween(180),
        label = "poster-focus",
    )
    val shape = RoundedCornerShape(12.dp)

    StandardCardContainer(
        modifier = modifier
            .width(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus },
        title = {
            Text(
                anime.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (focused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        subtitle = {
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
        },
        imageCard = { interactionSource ->
            Surface(
                onClick = onClick,
                interactionSource = interactionSource,
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
                StablePosterImage(
                    url = anime.thumbnailUrl ?: anime.posterUrl,
                    title = anime.title,
                    widthPx = 480,
                    heightPx = 672,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
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
            .height(142.dp),
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
                widthPx = 330,
                heightPx = 462,
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    "ПРОДОЛЖИТЬ",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    anime.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (anime.source) {
                        SourceId.ANILIBRIA -> "AniLibria"
                        SourceId.KODIK -> "anion"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
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
