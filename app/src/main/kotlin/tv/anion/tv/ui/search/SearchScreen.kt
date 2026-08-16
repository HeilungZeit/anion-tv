package tv.anion.tv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.anion.tv.R
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.CARD_FOOTPRINT
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.components.PosterCard
import tv.anion.tv.ui.components.SectionHeader
import tv.anion.tv.ui.components.TvKeyboard
import tv.anion.tv.ui.components.initialFocus
import tv.anion.tv.ui.components.rememberInitialFocus
import tv.anion.tv.ui.components.rowFocus
import tv.anion.tv.ui.theme.ScreenBottomGutter
import tv.anion.tv.ui.theme.ScreenGutter
import tv.anion.tv.ui.theme.ScreenTopGutter

/** Одна высота на кнопку, поле и «Найти»: разнобой сразу бросается в глаза. */
private val CONTROL_HEIGHT = 46.dp

@Composable
fun SearchScreen(
    onOpenAnime: (source: String, animeId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm = viewModel { SearchViewModel(container.sources, container.searchHistory) }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var keyboardOpen by remember { mutableStateOf(false) }
    val initial = rememberInitialFocus()
    val keyboardFocus = rememberInitialFocus(keyboardOpen)

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            vm.voiceCancelled()
            return@rememberLauncherForActivityResult
        }
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

        if (spoken == null) {
            vm.voiceCancelled()
            return@rememberLauncherForActivityResult
        }
        query = spoken
        vm.search(spoken)
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите название аниме")
        }
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            vm.voiceUnavailable()
        }
    }

    BackHandler(enabled = keyboardOpen) { keyboardOpen = false }

    // Поля от края экрана экран отмеряет сам: общей рамки вокруг экранов нет.
    //
    // Вертикаль на экране поиска — самый дорогой ресурс, и раздаётся она сверху
    // вниз по важности: строка запроса, счётчик, дальше всё оставшееся место
    // забирает сетка. Крупного заголовка «Поиск» с подписью здесь больше нет:
    // вместе с историей запросов он съедал половину экрана, из-за чего у
    // единственного видимого ряда результатов срезало названия под постерами.
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenGutter, end = ScreenGutter, top = ScreenTopGutter),
        ) {
            VoiceButton(
                onClick = ::startVoice,
                modifier = Modifier
                    .size(CONTROL_HEIGHT)
                    .initialFocus(initial),
            )

            QueryField(
                query = query,
                onClick = { keyboardOpen = true },
                modifier = Modifier
                    .weight(1f)
                    .height(CONTROL_HEIGHT),
            )

            Button(
                onClick = { vm.search(query) },
                modifier = Modifier.height(CONTROL_HEIGHT),
                contentPadding = PaddingValues(horizontal = 22.dp),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
            ) {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("Найти", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (keyboardOpen) {
            TvKeyboard(
                modifier = Modifier.padding(horizontal = ScreenGutter),
                onKey = { query += it },
                onBackspace = { query = query.dropLast(1) },
                onClear = { query = "" },
                onSubmit = {
                    keyboardOpen = false
                    vm.search(query)
                },
                firstKeyFocus = Modifier.initialFocus(keyboardFocus),
            )
            Text(
                "Назад — убрать клавиатуру",
                modifier = Modifier.padding(horizontal = ScreenGutter),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
            return@Column
        }

        // История — только пока результатов нет: до первого запроса и когда
        // ничего не нашлось. Над найденным она висела всегда и отнимала у сетки
        // ряд целиком, а нужна ровно тогда, когда показывать больше нечего.
        if (state.items.isEmpty() && !state.loading && state.history.isNotEmpty()) {
            SectionHeader("Недавние запросы", Modifier.padding(horizontal = ScreenGutter))
            LazyRow(
                modifier = Modifier.rowFocus(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = 10.dp),
            ) {
                items(state.history, key = { it }) { item ->
                    HistoryChip(item) {
                        query = item
                        vm.search(item)
                    }
                }
            }
        }

        when {
            state.loading -> MessagePane("Ищу…")
            state.error != null -> MessagePane(state.error!!)
            state.searched && state.items.isEmpty() -> MessagePane("Ничего не нашлось")
            state.items.isNotEmpty() -> {
                Text(
                    "Найдено  ·  ${state.items.size}",
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(CARD_FOOTPRINT),
                    // Остаток экрана сетке отдаёт weight: прокрутка по фокусу
                    // считает видимой областью viewport сетки, и он обязан
                    // совпадать с экраном. fillMaxSize просил бы максимум по
                    // входящим ограничениям и держался на том, что Column их
                    // урежет, — полагаться тут на побочный эффект не стоит.
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = ScreenGutter,
                        end = ScreenGutter,
                        top = 4.dp,
                        bottom = ScreenBottomGutter,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.items, key = { "${it.source}:${it.id}" }) { anime ->
                        PosterCard(
                            anime = anime,
                            onClick = { onOpenAnime(anime.source.name, anime.id) },
                        )
                    }
                }
            }
            // Пустой экран на ТВ выглядит как поломка, поэтому вместо пустоты —
            // короткая инструкция, что делать пультом.
            else -> EmptyHint()
        }
    }
}

/** Только микрофон: подпись рядом с ним ничего не добавляла, а место ела. */
@Composable
private fun VoiceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        // Свой contentPadding обязателен: дефолтный у Button несимметричен по
        // вертикали, и на фиксированной высоте содержимое уезжает от центра.
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Голосовой поиск",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Поле только показывает набранное. Редактирует его [TvKeyboard]: системная IME
 * на Android TV фокус ввода не отдаёт — с физической клавиатуры проскакивают
 * одни цифры, а D-pad до её клавиш не доходит вовсе.
 */
@Composable
private fun QueryField(query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape, shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
                    shape = shape,
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = query.ifEmpty { "Название аниме" },
                style = MaterialTheme.typography.titleSmall,
                color = if (query.isEmpty()) Color.White.copy(alpha = 0.45f) else Color.White,
            )
        }
    }
}

@Composable
private fun HistoryChip(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
    ) { Text(text) }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 40.dp, start = ScreenGutter, end = ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Как искать",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Микрофон — назовите тайтл вслух, это быстрее всего.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Или наведитесь на поле и нажмите OK — откроется клавиатура для пульта.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Ищем сразу по всем подключённым источникам.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
