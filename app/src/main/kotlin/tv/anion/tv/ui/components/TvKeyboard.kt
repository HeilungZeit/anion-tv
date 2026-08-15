package tv.anion.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Своя экранная клавиатура вместо системной.
 *
 * Системная IME на Android TV показывается, но фокус ввода не отдаёт: с
 * физической клавиатуры проскакивают только цифры, а D-pad до её клавиш не
 * доходит вовсе — события остаются в приложении. Обычные кнопки Compose такой
 * проблемы не имеют по построению, и заодно раскладка становится нашей.
 */
@Composable
fun TvKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    /** На форме входа с логина ведёт «Далее», а не «Найти». */
    submitLabel: String = "Найти",
    /**
     * Поиск начинается с русского — так ищут тайтлы; вход наоборот, потому что
     * email и пароль набираются латиницей.
     */
    startLatin: Boolean = false,
    firstKeyFocus: Modifier = Modifier,
) {
    var latin by remember { mutableStateOf(startLatin) }
    var symbols by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }

    val letterRows = if (latin) LATIN_ROWS else CYRILLIC_ROWS
    val rows = if (symbols) SYMBOL_ROWS else letterRows

    // Shift одноразовый, как на телефоне: набрал заглавную — вернулись к
    // строчным. Иначе после каждой заглавной пришлось бы тянуться к «Aa»
    // отдельным нажатием пульта.
    val typeLetter: (Char) -> Unit = { char ->
        onKey(char)
        shift = false
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // @ и точка стоят прямо в цифровом ряду: без них не набрать ни один
        // email, и гонять ради двух символов в отдельный слой — издевательство.
        KeyRow(TOP_ROW, shift = false, onKey = onKey, firstKeyFocus = Modifier)

        rows.forEachIndexed { index, row ->
            // Фокус ставим на первую букву, а не на цифру: набор почти всегда
            // начинается с буквы.
            val rowKeyHandler = if (symbols) onKey else typeLetter
            KeyRow(row, shift && !symbols, rowKeyHandler, if (index == 0) firstKeyFocus else Modifier)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WideKey("Aa", width = 66.dp, accent = shift, enabled = !symbols) { shift = !shift }
            WideKey(if (symbols) "АБВ" else "!#@", width = 76.dp, accent = symbols) { symbols = !symbols }
            WideKey(if (latin) "РУС" else "ENG", width = 76.dp, enabled = !symbols) { latin = !latin }
            WideKey("Пробел", width = 128.dp) { onKey(' ') }
            WideKey("Стереть", width = 104.dp, onClick = onBackspace)
            WideKey("Очистить", width = 110.dp, onClick = onClear)
            WideKey(submitLabel, width = 108.dp, accent = true, onClick = onSubmit)
        }
    }
}

@Composable
private fun KeyRow(keys: String, shift: Boolean, onKey: (Char) -> Unit, firstKeyFocus: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEachIndexed { index, char ->
            Key(char, shift, onKey, if (index == 0) firstKeyFocus else Modifier)
        }
    }
}

@Composable
private fun Key(char: Char, shift: Boolean, onKey: (Char) -> Unit, modifier: Modifier) {
    // Клавиша показывает ровно тот символ, который вставит: иначе по экрану не
    // понять, включён регистр или нет.
    val symbol = if (shift) char.uppercaseChar() else char

    Button(
        onClick = { onKey(symbol) },
        modifier = modifier.size(KEY_SIZE),
        contentPadding = PaddingValues(0.dp),
        colors = keyColors(accent = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(symbol.toString(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun WideKey(
    label: String,
    width: Dp,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(width)
            .height(KEY_SIZE),
        contentPadding = PaddingValues(0.dp),
        colors = keyColors(accent),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun keyColors(accent: Boolean) = ButtonDefaults.colors(
    containerColor = if (accent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.10f),
    contentColor = Color.White,
    focusedContainerColor = MaterialTheme.colorScheme.primary,
    focusedContentColor = Color.White,
)

private val KEY_SIZE = 46.dp

private const val TOP_ROW = "1234567890@."

private val CYRILLIC_ROWS = listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё")
private val LATIN_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/**
 * Слой символов покрывает весь `atext` из RFC 5322 — то, что вообще разрешено
 * в локальной части адреса: `! # $ % & ' * + - / = ? ^ _ \` { | } ~` и точка.
 * Остальное (скобки, двоеточие, кавычки) добавлено ради паролей.
 */
private val SYMBOL_ROWS = listOf(
    "_-+=*/\\|",
    "!?#\$%&^~",
    "()[]{}<>",
    ":;,'\"`",
)
