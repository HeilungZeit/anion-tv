package tv.anion.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Ширина свёрнутого рельса. Контент отступает ровно на неё, поэтому при
 * разворачивании ничего не переливается: развёрнутый рельс ложится поверх
 * (вариант Modal из гайдлайнов Android TV).
 */
val RAIL_COLLAPSED_WIDTH = 60.dp
private val RAIL_EXPANDED_WIDTH = 232.dp

enum class RailGlyph { Search, Home, Catalog, Bookmarks, Account }

data class RailDestination(
    val glyph: RailGlyph,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Боковой рельс навигации вместо ряда кнопок сверху.
 *
 * Так устроена навигация во всех ТВ-приложениях, на которые смотрит зритель
 * (ютуб — ближайший референс), и так её описывают гайдлайны Android TV:
 * 3–7 пунктов, свёрнутое состояние — одни иконки, активный пункт с заметной
 * подложкой. Верхний ряд кнопок отнимал у контента целую строку и уводил фокус
 * вверх через весь экран.
 *
 * Иконки нарисованы `Canvas`, а не взяты из иконочной библиотеки: в проекте уже
 * так сделаны транспортные значки плеера, и лишняя зависимость ради пяти
 * глифов не нужна.
 */
@Composable
fun NavigationRail(
    destinations: List<RailDestination>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    activeItemModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) RAIL_EXPANDED_WIDTH else RAIL_COLLAPSED_WIDTH,
        animationSpec = tween(180),
        label = "rail-width",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            // Подложка нужна только развёрнутому: он ложится поверх карточек,
            // и без неё под подписями просвечивал бы контент.
            .background(
                Brush.horizontalGradient(
                    0f to Color(0xF20E1013).copy(alpha = if (expanded) 0.95f else 0f),
                    0.72f to Color(0xF20E1013).copy(alpha = if (expanded) 0.90f else 0f),
                    1f to Color.Transparent,
                ),
            )
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        destinations.forEachIndexed { index, destination ->
            RailItem(
                destination = destination,
                active = index == activeIndex,
                expanded = expanded,
                // Держатель фокуса вешается на текущий раздел: приходя на
                // экран, зритель должен видеть, где он находится.
                modifier = if (index == activeIndex) activeItemModifier else Modifier,
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: RailDestination,
    active: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Surface(
        onClick = destination.onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape, focusedShape = shape),
        colors = ClickableSurfaceDefaults.colors(
            // Активный пункт заметен и без фокуса: гайдлайны требуют, чтобы
            // текущий раздел читался сам по себе.
            containerColor = if (active) Color.White.copy(alpha = 0.10f) else Color.Transparent,
            contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                // 18dp: свёрнутый рельс шириной 60dp ставит иконку 24dp ровно
                // по центру, и при разворачивании она не дёргается.
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RailIcon(
                glyph = destination.glyph,
                tint = if (focused || active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 24.dp,
            )
            if (expanded) {
                Text(
                    destination.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Глифы рельса: только примитивы, чтобы не тянуть иконочную библиотеку. */
@Composable
private fun RailIcon(glyph: RailGlyph, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(width = this.size.width * 0.09f)
        when (glyph) {
            RailGlyph.Search -> drawSearch(tint, stroke)
            RailGlyph.Home -> drawHome(tint, stroke)
            RailGlyph.Catalog -> drawCatalog(tint)
            RailGlyph.Bookmarks -> drawBookmark(tint, stroke)
            RailGlyph.Account -> drawAccount(tint, stroke)
        }
    }
}

private fun DrawScope.drawSearch(tint: Color, stroke: Stroke) {
    val radius = size.minDimension * 0.30f
    val center = Offset(size.width * 0.42f, size.height * 0.42f)
    drawCircle(tint, radius = radius, center = center, style = stroke)
    drawLine(
        color = tint,
        start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
        end = Offset(size.width * 0.92f, size.height * 0.92f),
        strokeWidth = stroke.width,
    )
}

private fun DrawScope.drawHome(tint: Color, stroke: Stroke) {
    val roof = Path().apply {
        moveTo(size.width * 0.10f, size.height * 0.46f)
        lineTo(size.width * 0.50f, size.height * 0.12f)
        lineTo(size.width * 0.90f, size.height * 0.46f)
    }
    drawPath(roof, tint, style = stroke)
    val walls = Path().apply {
        moveTo(size.width * 0.22f, size.height * 0.42f)
        lineTo(size.width * 0.22f, size.height * 0.88f)
        lineTo(size.width * 0.78f, size.height * 0.88f)
        lineTo(size.width * 0.78f, size.height * 0.42f)
    }
    drawPath(walls, tint, style = stroke)
}

private fun DrawScope.drawCatalog(tint: Color) {
    val cell = size.width * 0.34f
    val gap = size.width * 0.12f
    val corner = CornerRadius(cell * 0.22f)
    listOf(
        Offset(size.width * 0.10f, size.height * 0.10f),
        Offset(size.width * 0.10f + cell + gap, size.height * 0.10f),
        Offset(size.width * 0.10f, size.height * 0.10f + cell + gap),
        Offset(size.width * 0.10f + cell + gap, size.height * 0.10f + cell + gap),
    ).forEach { topLeft ->
        drawRoundRect(tint, topLeft = topLeft, size = Size(cell, cell), cornerRadius = corner)
    }
}

private fun DrawScope.drawBookmark(tint: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(size.width * 0.24f, size.height * 0.12f)
        lineTo(size.width * 0.76f, size.height * 0.12f)
        lineTo(size.width * 0.76f, size.height * 0.88f)
        lineTo(size.width * 0.50f, size.height * 0.64f)
        lineTo(size.width * 0.24f, size.height * 0.88f)
        close()
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawAccount(tint: Color, stroke: Stroke) {
    drawCircle(
        color = tint,
        radius = size.minDimension * 0.20f,
        center = Offset(size.width * 0.50f, size.height * 0.32f),
        style = stroke,
    )
    // Плечи полуовалом: замкнутая «капля» читается как человек лучше, чем дуга.
    val shoulders = Path().apply {
        moveTo(size.width * 0.16f, size.height * 0.90f)
        cubicTo(
            size.width * 0.18f, size.height * 0.60f,
            size.width * 0.82f, size.height * 0.60f,
            size.width * 0.84f, size.height * 0.90f,
        )
    }
    drawPath(shoulders, tint, style = stroke)
}

/** Шапка контента: логотип и подпись, без ряда кнопок — навигация ушла в рельс. */
@Composable
fun ContentBrand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Anion",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                "TV",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
