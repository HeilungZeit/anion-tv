package tv.anion.player

import tv.anion.source.model.Segment
import tv.anion.source.model.Skips

/**
 * Кнопка «пропустить» по [Skips].
 *
 * У AniLibria отрезок полный (`start`+`stop`). У Kodik anion-go отдаёт только
 * начало — тогда окно берётся из [Segment.DEFAULT_WINDOW_SECONDS]. Эндинг
 * последней серии не предлагается: пропускать некуда.
 */
class SkipController(
    private val skips: Skips,
    private val isLastEpisode: Boolean = false,
) {

    fun visibleSkip(positionMs: Long): Segment? {
        val positionSec = (positionMs / 1000L).toInt()
        skips.opening?.let { if (positionSec in it.startSeconds until it.endSeconds) return it }
        if (!isLastEpisode) {
            skips.ending?.let { if (positionSec in it.startSeconds until it.endSeconds) return it }
        }
        return null
    }

    fun skipTargetMs(segment: Segment): Long = segment.endSeconds * 1000L
}
