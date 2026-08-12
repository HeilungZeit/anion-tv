package tv.anion.player

import android.view.KeyEvent

/**
 * Раскладка пульта. Вынесена отдельно: кнопки у боксов разъезжаются, и это
 * первое, что придётся править под конкретное железо (PLAN Э3).
 */
object RemoteKeyMap {
    const val SEEK_STEP_MS = 10_000L
    private const val LONG_SEEK_STEP_MS = 30_000L

    fun map(keyCode: Int, isLongPress: Boolean = false): PlayerCommand? {
        val seek = if (isLongPress) LONG_SEEK_STEP_MS else SEEK_STEP_MS
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerCommand.PlayPause
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerCommand.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerCommand.Pause
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            -> PlayerCommand.SeekBy(seek)
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT,
            -> PlayerCommand.SeekBy(-seek)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> PlayerCommand.Confirm
            // Вверх — панель, вниз — убрать: привычная пара для ТВ-плееров,
            // и она не отнимает стрелки влево/вправо у перемотки.
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_INFO,
            -> PlayerCommand.ShowPanel
            KeyEvent.KEYCODE_DPAD_DOWN -> PlayerCommand.HidePanel
            // Переключение серий: у ТВ-пультов для этого есть и медийные
            // кнопки, и «канал вверх/вниз» — на дешёвых боксах бывает только
            // вторая пара.
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_CHANNEL_UP,
            -> PlayerCommand.NextEpisode
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            -> PlayerCommand.PreviousEpisode
            KeyEvent.KEYCODE_BACK -> PlayerCommand.Back
            else -> null
        }
    }
}

sealed class PlayerCommand {
    data object PlayPause : PlayerCommand()
    data object Play : PlayerCommand()
    data object Pause : PlayerCommand()
    data class SeekBy(val deltaMs: Long) : PlayerCommand()
    /**
     * OK: пропуск опенинга, если кнопка видна; иначе пауза с показом панели —
     * «остановить» это первое, чего ждут от центральной кнопки.
     */
    data object Confirm : PlayerCommand()
    data object ShowPanel : PlayerCommand()
    data object HidePanel : PlayerCommand()
    data object NextEpisode : PlayerCommand()
    data object PreviousEpisode : PlayerCommand()
    data object Back : PlayerCommand()
}
