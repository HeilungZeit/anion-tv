package tv.anion.tv.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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

/** 5% полей: часть телевизоров режет картинку по краям (PLAN Э4). Плеер — без них. */
@Composable
fun OverscanBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val safeTop = maxHeight * 0.05f
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = maxWidth * 0.05f, vertical = safeTop),
            content = content,
        )

        // Мягкая «стеклянная» кромка без отдельного blur-слоя: blur обрезался
        // границей собственного bounds и давал заметную горизонтальную линию.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(safeTop + 42.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB8171A20),
                        0.28f to Color(0x9A18202A),
                        0.58f to Color(0x531B2632),
                        0.82f to Color(0x171D2935),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}
