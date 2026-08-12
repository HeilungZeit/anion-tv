package tv.anion.player

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow
import tv.anion.source.model.PlayableStream
import tv.anion.source.model.Segment
import tv.anion.source.model.Skips

/**
 * Обвязка Media3 (PLAN §4, Э3). Держит три вещи, которых нет в голом ExoPlayer:
 * скипы опенинга, восстановление позиции и тихий переролв по 403.
 *
 * Без `PlayerView`: его фокус-модель на пульте ведёт себя непредсказуемо,
 * панель рисуется в Compose (Э4).
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    suspend fun prepare(session: PlaybackSession)
    fun play()
    fun pause()
    fun seekBy(deltaMs: Long)
    fun skipTo(segment: Segment)
    /** Своя Surface, не PlayerView: его фокус на пульте ведёт себя непредсказуемо. */
    fun setVideoSurfaceView(view: SurfaceView?)
    fun release()
}

data class PlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val visibleSkip: Segment? = null,
    /** Серия доиграна до конца — повод предложить следующую (Э3). */
    val ended: Boolean = false,
)

data class PlaybackSession(
    val stream: PlayableStream,
    val skips: Skips = Skips(),
    val startPositionMs: Long = 0,
    val isLastEpisode: Boolean = false,
    /** Для переролва по 403. У AniLibria нет — поток прямой, подпись не протухает. */
    val iframeUrl: String? = null,
    val preferredQuality: Int = 720,
)

/** Позиция пишется часто; очередь на сервер — Э5. */
fun interface PlaybackProgressListener {
    fun onProgress(positionMs: Long, durationMs: Long)
}
