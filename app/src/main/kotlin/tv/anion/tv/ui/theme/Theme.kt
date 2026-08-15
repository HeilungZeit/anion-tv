package tv.anion.tv.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * Поля под обрез телевизора (PLAN Э4). Плеер — без них.
 *
 * Числа взяты из гайдлайнов Android TV (Styles → Layouts): safe area — 48dp по
 * бокам и 24–27dp сверху и снизу. Раньше здесь стояли одинаковые 5% от стороны:
 * по горизонтали это те же 48dp и было верно, а по вертикали давало столько же,
 * втрое больше нужного, — экран выглядел зажатым сверху и снизу.
 *
 * Градиентной «кромки» сверху больше нет: полоса в семь десятков dp и была тем,
 * что читалось как громоздкая рамка. Фон приложения и так тёмный, отделять
 * контент от края нечем.
 */
@Composable
fun OverscanBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        content = content,
    )
}
