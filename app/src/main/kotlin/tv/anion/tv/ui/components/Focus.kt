package tv.anion.tv.ui.components

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
