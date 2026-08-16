package tv.anion.tv.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.unit.dp

private val Scheme = darkColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF163A5F),
    onPrimaryContainer = Color(0xFF9BD0FF),
    secondary = Color(0xFF8BC8F8),
    onSecondary = Color(0xFF08233A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1C1B1B),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color(0xFFA8ABB2),
    error = Color(0xFFF87171),
)

private val AnionShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun AnionTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = AnionShapes, content = content)
}

/** Тёмная подложка фронта с очень мягким синим свечением, безопасным для OLED. */
@Composable
fun AnionBackground(content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides Scheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF171A20), Color(0xFF121212), Color(0xFF0D0D0F)),
                    ),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x291976D2), Color.Transparent),
                            center = Offset.Zero,
                            radius = 1100f,
                        ),
                    ),
            )
            content()
        }
    }
}

/**
 * Отступ контента от края экрана.
 *
 * Полей под обрез (safe area в 48dp по бокам из гайдлайнов) больше нет: их
 * держал общий контейнер вокруг каждого экрана, и он же не давал рельсу
 * навигации прижаться к краю — тот висел в 48dp от рамки, чего не делает ни
 * одно ТВ-приложение. Резать картинку по краям умеют ЭЛТ, а не панели, ради
 * которых пишется это приложение.
 *
 * Оставшийся гаттер держится не под обрез, а под саму разметку: тексту нужен
 * отбив от рамки, а плитке в фокусе — место под обводку и scale, потому что
 * ряды и сетки клипуют содержимое по своему viewport. Рельс и ряды карточек его
 * не берут: рельс стоит вплотную к краю, ряды обязаны уезжать за край.
 */
val ScreenGutter = 24.dp

/** Сверху меньше горизонтального: шапка не должна съедать высоту первого ряда. */
val ScreenTopGutter = 16.dp

/** Снизу — запас на увеличение последней строки в фокусе. */
val ScreenBottomGutter = 24.dp
