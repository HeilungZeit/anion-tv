package tv.anion.player

import tv.anion.source.model.Segment
import tv.anion.source.model.Skips

/**
 * Кнопка «пропустить» по [Skips].
 *
 * У AniLibria отрезок полный (`start`+`stop`). У Kodik anion-go отдаёт только
 * начало — тогда окно берётся из [Segment.DEFAULT_WINDOW_SECONDS]. Эндинг
 * последней серии не предлагается: пропускать некуда.
 *
 * Окно эндинга тянется до конца серии, а не до конца отрезка: после титров
 * идёт превью следующей серии, и предложение включить её там всё ещё уместно —
 * а вот исчезнувшая на середине титров кнопка выглядит потерянной. Длина
 * отрезка для этого не нужна, что кстати при Kodik, где её и нет.
 *
 * Последние [TAIL_SECONDS] секунд кнопка «следующая серия» показывается и без
 * отрезка эндинга: разметка есть не у всех эпизодов, а к концу серии переход
 * уместен независимо от неё.
 */
class SkipController(
    private val skips: Skips,
    private val isLastEpisode: Boolean = false,
) {

    fun visibleSkip(positionMs: Long, durationMs: Long = 0L): SkipHint? {
        val positionSec = (positionMs / 1000L).toInt()
        skips.opening?.let {
            if (positionSec in it.startSeconds until it.endSeconds) return SkipHint(it, SkipKind.OPENING)
        }
        if (isLastEpisode) return null
        val durationSec = if (durationMs > 0) (durationMs / 1000L).toInt() else 0
        skips.ending?.let {
            // Пока длительность неизвестна (первые тики после prepare),
            // окно то же, что у опенинга: показать кнопку без границы
            // было бы честнее, но она рискует залипнуть навсегда.
            val end = if (durationSec > 0) durationSec else it.endSeconds
            if (positionSec in it.startSeconds until end) return SkipHint(it, SkipKind.ENDING)
        }
        if (durationSec > 0) {
            val tailStart = (durationSec - TAIL_SECONDS).coerceAtLeast(0)
            if (positionSec in tailStart until durationSec) {
                return SkipHint(Segment(tailStart, durationSec), SkipKind.ENDING)
            }
        }
        return null
    }

    fun skipTargetMs(segment: Segment): Long = segment.endSeconds * 1000L

    companion object {
        /** Хвост серии, в котором кнопка следующей серии видна всегда. */
        const val TAIL_SECONDS = 120
    }
}

/**
 * Что именно предлагается пропустить. Разница не косметическая: опенинг
 * перематывается внутри серии, а эндинг — повод включить следующую, потому что
 * дальше идут титры и превью.
 */
enum class SkipKind { OPENING, ENDING }

data class SkipHint(val segment: Segment, val kind: SkipKind)
