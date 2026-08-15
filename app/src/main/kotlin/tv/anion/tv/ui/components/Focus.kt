package tv.anion.tv.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer

/**
 * На ТВ фокус — половина UI: куда уходит D-pad с края ряда и что фокусируется
 * при возврате назад. [focusRestorer] возвращает фокус последнему ребёнку ряда.
 */
fun Modifier.rowFocus(): Modifier = focusRestorer()

@Composable
fun rememberInitialFocus(ready: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(ready) {
        if (ready) runCatching { requester.requestFocus() }
    }
    return requester
}

fun Modifier.initialFocus(requester: FocusRequester): Modifier =
    focusRequester(requester)

/**
 * Держит сфокусированный ряд в середине экрана.
 *
 * Штатная прокрутка по фокусу подтягивает элемент ровно настолько, чтобы он
 * влез, — на ТВ это выходит боком дважды. На главной каждый шаг вдоль ряда
 * доскроллит колонку на несколько пикселей (карточка в фокусе крупнее своих
 * же границ), и соседние блоки ползают вверх-вниз. В поиске нижний ряд сетки
 * оказывается прижат к краю: постер видно, а название под ним срезано.
 *
 * Ряд по центру убирает оба случая: положение по вертикали перестаёт зависеть
 * от того, откуда пришёл фокус, и под карточкой всегда остаётся место на
 * подпись. У края списка [animateScrollBy] упирается в границы содержимого, так
 * что первый и последний ряды остаются на своих местах.
 */
suspend fun LazyListState.centerFocusedItem(index: Int) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        // Ряда ещё нет в разметке — сначала подводим его к экрану, центрируем
        // следующим вызовом, когда появятся его размеры.
        ?: return animateScrollToItem(index)
    animateScrollBy(
        centeringDistance(
            offset = visible.offset,
            size = visible.size,
            viewportStart = layoutInfo.viewportStartOffset,
            viewportEnd = layoutInfo.viewportEndOffset,
        ),
    )
}

suspend fun LazyGridState.centerFocusedItem(index: Int) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return animateScrollToItem(index)
    animateScrollBy(
        centeringDistance(
            offset = visible.offset.y,
            size = visible.size.height,
            viewportStart = layoutInfo.viewportStartOffset,
            viewportEnd = layoutInfo.viewportEndOffset,
        ),
    )
}

/** Насколько прокрутить, чтобы середина элемента совпала с серединой окна. */
private fun centeringDistance(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Float =
    offset + size / 2f - (viewportStart + viewportEnd) / 2f
