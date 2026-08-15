package tv.anion.tv.ui.player

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.anion.player.PlayerCommand
import tv.anion.player.RemoteKeyMap
import tv.anion.source.SourceId
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.ui.components.MessagePane
import tv.anion.tv.ui.theme.OverscanBox

@Composable
fun PlayerScreen(
    source: String,
    animeId: String,
    episode: Int,
    translationId: String?,
    onBack: () -> Unit,
    onPlayEpisode: (Int) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val session = remember(source, animeId, episode, translationId) {
        PlayerSession(container, SourceId.valueOf(source), animeId, episode, translationId)
    }
    DisposableEffect(session) {
        onDispose { session.release() }
    }

    val playback by session.controller.state.collectAsStateWithLifecycle()
    val loading by session.loading.collectAsStateWithLifecycle()
    val error by session.error.collectAsStateWithLifecycle()
    val neighbours by session.neighbours.collectAsStateWithLifecycle()
    val title by session.title.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf(false) }

    fun togglePlay() {
        if (playback.isPlaying) session.controller.pause() else session.controller.play()
    }

    // Два держателя фокуса. Без панели клавиши ловит экран целиком; с панелью
    // фокус уходит её кнопкам, иначе стрелки одновременно мотали бы видео и
    // переводили фокус между кнопками.
    val screenFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    // Полоса прокрутки — верхний фокусируемый элемент панели, и «вверх»
    // закрывает панель только с неё. Из ряда кнопок вверх сначала ведёт на
    // полосу: раньше одно случайное нажатие смахивало панель целиком.
    var seekBarFocused by remember { mutableStateOf(false) }

    LaunchedEffect(overlay, loading, error) {
        if (!overlay) seekBarFocused = false
        if (loading || error != null) return@LaunchedEffect
        runCatching { if (overlay) panelFocus.requestFocus() else screenFocus.requestFocus() }
    }

    // Отсчёт ведётся от последнего нажатия, а не от появления панели: пока
    // пультом пользуются — панель на экране, через семь секунд тишины уходит.
    // Гаснет и на паузе тоже: там остаётся значок по центру, и кадр не закрыт.
    var lastInput by remember { mutableIntStateOf(0) }
    LaunchedEffect(overlay, lastInput) {
        if (overlay) {
            delay(OVERLAY_TIMEOUT_MS)
            overlay = false
        }
    }

    // Серия доиграла — сразу следующая, без возврата в список: сценарий
    // «смотрю сезон подряд» основной.
    LaunchedEffect(playback.ended, neighbours.next) {
        val next = neighbours.next
        if (playback.ended && next != null) onPlayEpisode(next)
    }

    BackHandler {
        if (overlay) overlay = false else onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Порядок обязателен: обработчик клавиш должен стоять до
            // focusable(), иначе он не попадает в фокус-узел и события пульта
            // до него не доходят.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val command = RemoteKeyMap.map(native.keyCode, isLongPress = native.repeatCount > 0)
                    ?: return@onPreviewKeyEvent false
                if (native.repeatCount > 0 && command !is PlayerCommand.SeekBy) return@onPreviewKeyEvent true
                // Любое нажатие продлевает показ, включая стрелки, которые
                // уходят кнопкам панели мимо ветки ниже.
                lastInput++

                // При открытой панели стрелки и OK принадлежат её кнопкам —
                // перехватываем только то, что панель не обслуживает.
                if (overlay) {
                    return@onPreviewKeyEvent when (command) {
                        // Вверх убирает панель только с полосы прокрутки — выше
                        // неё в панели ничего нет. Из ряда кнопок он отдаётся
                        // системе фокуса и переводит фокус на полосу: панель
                        // вызывают тем же «вверх», и промахнуться по кнопке,
                        // смахнув её целиком, было слишком легко.
                        PlayerCommand.ShowPanel ->
                            if (seekBarFocused) { overlay = false; true } else false
                        // Вниз зеркально: с полосы возвращает к кнопкам, и
                        // только из ряда кнопок убирает панель. Иначе фокус,
                        // поднявшийся на полосу, обратно уже не спускался.
                        PlayerCommand.HidePanel ->
                            if (seekBarFocused) false else { overlay = false; true }
                        // Стрелки принадлежат кнопкам панели, но на самой
                        // полосе им положено мотать: сфокусированная полоса,
                        // которая ни на что не отзывается, выглядит зависшей.
                        is PlayerCommand.SeekBy ->
                            if (seekBarFocused) { session.controller.seekBy(command.deltaMs); true } else false
                        PlayerCommand.Back -> { overlay = false; true }
                        PlayerCommand.PlayPause -> { togglePlay(); true }
                        PlayerCommand.Play -> { session.controller.play(); true }
                        PlayerCommand.Pause -> { session.controller.pause(); true }
                        PlayerCommand.NextEpisode -> { neighbours.next?.let(onPlayEpisode); true }
                        PlayerCommand.PreviousEpisode -> { neighbours.previous?.let(onPlayEpisode); true }
                        else -> false
                    }
                }

                when (command) {
                    PlayerCommand.PlayPause -> { togglePlay(); overlay = true }
                    PlayerCommand.Play -> session.controller.play()
                    PlayerCommand.Pause -> { session.controller.pause(); overlay = true }
                    is PlayerCommand.SeekBy -> {
                        session.controller.seekBy(command.deltaMs)
                        overlay = true
                    }
                    // OK при скрытой панели не трогает воспроизведение: как в
                    // ютубе на ТВ, первое нажатие лишь показывает контролы с
                    // фокусом на паузе, а останавливает уже второе — по кнопке.
                    // Исключение — идущий опенинг: там OK сразу пропускает.
                    PlayerCommand.Confirm -> {
                        val skip = playback.visibleSkip
                        if (skip != null) session.controller.skipTo(skip) else overlay = true
                    }
                    PlayerCommand.ShowPanel -> overlay = true
                    PlayerCommand.HidePanel -> Unit
                    PlayerCommand.NextEpisode -> neighbours.next?.let(onPlayEpisode)
                    PlayerCommand.PreviousEpisode -> neighbours.previous?.let(onPlayEpisode)
                    PlayerCommand.Back -> onBack()
                }
                true
            }
            .focusRequester(screenFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).also { session.controller.setVideoSurfaceView(it) }
            },
            onRelease = { session.controller.setVideoSurfaceView(null) },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            loading -> MessagePane("Готовлю поток…")
            error != null -> MessagePane(error!!, action = "Назад", onAction = onBack)
            playback.error != null -> MessagePane(playback.error!!, action = "Назад", onAction = onBack)
        }

        // Пауза заметна и без панели: иначе непонятно, встало ли видео само или
        // подвисла сеть.
        AnimatedVisibility(
            visible = !playback.isPlaying && !playback.buffering && !loading && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PauseBadge()
        }

        AnimatedVisibility(
            visible = playback.buffering && !loading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Pill("Буферизация…")
        }

        AnimatedVisibility(
            visible = playback.visibleSkip != null && !overlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(56.dp),
        ) {
            Pill("OK  —  пропустить опенинг")
        }

        AnimatedVisibility(visible = overlay, enter = fadeIn(), exit = fadeOut()) {
            PlayerPanel(
                title = title,
                episode = episode,
                total = neighbours.total,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                isPlaying = playback.isPlaying,
                canSkip = playback.visibleSkip != null,
                playFocus = panelFocus,
                onSeekBarFocusChanged = { seekBarFocused = it },
                onPlayPause = ::togglePlay,
                onSeek = { session.controller.seekBy(it) },
                onSkip = { playback.visibleSkip?.let { session.controller.skipTo(it) } },
            )
        }
    }
}

/**
 * Панель с настоящими кнопками, а не только подсказками: на дешёвых пультах
 * медиа-клавиш и «каналов» может не быть вовсе, и тогда переключить серию
 * нечем.
 */
@Composable
private fun PlayerPanel(
    title: String?,
    episode: Int,
    total: Int,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    canSkip: Boolean,
    playFocus: FocusRequester,
    onSeekBarFocusChanged: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Затемнение только снизу, под текстом и кнопками: сверху
                // затемнять нечего, там кадр, и тень выглядела грязью.
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.28f to Color.Transparent,
                    0.48f to Color.Black.copy(alpha = 0.06f),
                    0.64f to Color.Black.copy(alpha = 0.20f),
                    0.80f to Color.Black.copy(alpha = 0.52f),
                    1f to Color.Black.copy(alpha = 0.92f),
                )
            )
    ) {
        OverscanBox {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    buildString {
                        append("Серия $episode")
                        if (total > 0) append(" из $total")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))
                ProgressBar(positionMs, durationMs, onFocusChanged = onSeekBarFocusChanged)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(positionMs), style = MaterialTheme.typography.labelLarge)
                    // Остаток нагляднее полной длительности: он отвечает на
                    // вопрос «успею ли досмотреть».
                    Text(
                        "−" + formatMs((durationMs - positionMs).coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PanelButton("− 10 с", onClick = { onSeek(-RemoteKeyMap.SEEK_STEP_MS) })
                    PanelButton(
                        text = if (isPlaying) "Пауза" else "Продолжить",
                        onClick = onPlayPause,
                        modifier = Modifier.focusRequester(playFocus),
                        icon = { tint -> TransportIcon(playing = isPlaying, tint = tint, size = 18.dp) },
                    )
                    PanelButton("+ 10 с", onClick = { onSeek(RemoteKeyMap.SEEK_STEP_MS) })
                    if (canSkip) PanelButton("Пропустить опенинг", onSkip)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "OK — пауза   ·   ← → — перемотка   ·   Назад — выйти",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun PanelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val tint = Color.White

    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.invoke(tint)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Иконки нарисованы, а не взяты эмодзи: системный шрифт рисует ⏸/▶ цветными
 * картинками, и на тёмной панели они выглядели инородным оранжевым пятном.
 */
@Composable
private fun TransportIcon(playing: Boolean, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        if (playing) {
            val bar = this.size.width * 0.30f
            val gap = this.size.width * 0.16f
            val left = (this.size.width - (bar * 2 + gap)) / 2f
            drawRect(tint, topLeft = Offset(left, 0f), size = Size(bar, this.size.height))
            drawRect(tint, topLeft = Offset(left + bar + gap, 0f), size = Size(bar, this.size.height))
        } else {
            val path = Path().apply {
                moveTo(this@Canvas.size.width * 0.12f, 0f)
                lineTo(this@Canvas.size.width * 0.98f, this@Canvas.size.height / 2f)
                lineTo(this@Canvas.size.width * 0.12f, this@Canvas.size.height)
                close()
            }
            drawPath(path, tint)
        }
    }
}

/**
 * Полоса прокрутки фокусируется наравне с кнопками: она верхний элемент панели,
 * и «вверх» убирает панель именно с неё. Фокус на полосе видно по утолщению и
 * подсветке дорожки: иначе непонятно, куда уехало управление с кнопок.
 */
@Composable
private fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onFocusChanged: (Boolean) -> Unit,
) {
    val target = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction by animateFloatAsState(target, tween(220), label = "progress")
    var focused by remember { mutableStateOf(false) }
    val height by animateFloatAsState(if (focused) 14f else 8f, tween(160), label = "seekbar-height")

    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = if (focused) 0.32f else 0.20f))
            // Тот же порядок, что и у экрана: onFocusChanged обязан стоять до
            // focusable(), иначе он не попадает в фокус-узел.
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusable()
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun PauseBadge() {
    Box(
        Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        TransportIcon(playing = true, tint = Color.White, size = 34.dp)
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private const val OVERLAY_TIMEOUT_MS = 7_000L

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = (ms / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
