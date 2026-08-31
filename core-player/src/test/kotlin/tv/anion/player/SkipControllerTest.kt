package tv.anion.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.anion.source.model.Segment
import tv.anion.source.model.Skips

class SkipControllerTest {

    @Test
    fun `кнопка видна внутри окна опенинга и не раньше`() {
        val opening = Segment(startSeconds = 10, stopSeconds = 100)
        val skip = SkipController(Skips(opening = opening))

        assertNull(skip.visibleSkip(9_999))
        assertEquals(SkipHint(opening, SkipKind.OPENING), skip.visibleSkip(10_000))
        assertEquals(SkipHint(opening, SkipKind.OPENING), skip.visibleSkip(50_000))
        assertNull(skip.visibleSkip(100_000))
        assertEquals(100_000L, skip.skipTargetMs(opening))
    }

    @Test
    fun `эндинг не предлагается, если серия последняя`() {
        val ending = Segment(startSeconds = 1_300, stopSeconds = 1_400)
        val skips = Skips(ending = ending)

        assertEquals(
            SkipHint(ending, SkipKind.ENDING),
            SkipController(skips, isLastEpisode = false).visibleSkip(1_350_000),
        )
        assertNull(SkipController(skips, isLastEpisode = true).visibleSkip(1_350_000))
    }

    @Test
    fun `кнопка эндинга держится до конца серии, а не до конца отрезка`() {
        val ending = Segment(startSeconds = 1_300, stopSeconds = 1_400)
        val skip = SkipController(Skips(ending = ending))

        assertNull(skip.visibleSkip(1_299_000, durationMs = 1_500_000))
        assertEquals(SkipKind.ENDING, skip.visibleSkip(1_450_000, durationMs = 1_500_000)?.kind)
        assertNull(skip.visibleSkip(1_500_000, durationMs = 1_500_000))
    }

    @Test
    fun `в последние две минуты кнопка следующей серии есть и без разметки`() {
        val skip = SkipController(Skips())

        assertNull(skip.visibleSkip(1_379_000, durationMs = 1_500_000))
        assertEquals(SkipKind.ENDING, skip.visibleSkip(1_380_000, durationMs = 1_500_000)?.kind)
        assertEquals(SkipKind.ENDING, skip.visibleSkip(1_499_000, durationMs = 1_500_000)?.kind)
        // Без известной длительности хвоста нет: считать его не от чего.
        assertNull(skip.visibleSkip(1_380_000))
        // Последней серии переходить некуда.
        assertNull(SkipController(Skips(), isLastEpisode = true).visibleSkip(1_450_000, durationMs = 1_500_000))
    }

    @Test
    fun `хвост не перекрывает отрезок эндинга, если тот начинается раньше`() {
        val ending = Segment(startSeconds = 1_300, stopSeconds = 1_400)
        val skip = SkipController(Skips(ending = ending))

        assertEquals(SkipHint(ending, SkipKind.ENDING), skip.visibleSkip(1_310_000, durationMs = 1_500_000))
    }

    @Test
    fun `у Kodik известно только начало эндинга - окно всё равно есть`() {
        // anion-go отдаёт одно число, длину отбрасывает VideoSkipsDTO: окно
        // берётся из DEFAULT_WINDOW_SECONDS, и кнопка «следующая серия» живёт
        // ровно в нём.
        val ending = Segment(startSeconds = 1_430, stopSeconds = null)
        val skip = SkipController(Skips(ending = ending))

        assertNull(skip.visibleSkip(1_429_000))
        assertEquals(SkipKind.ENDING, skip.visibleSkip(1_430_000)?.kind)
        assertEquals(SkipKind.ENDING, skip.visibleSkip(1_500_000)?.kind)
        // Без известной длительности окно остаётся прежним, в 90 секунд.
        assertNull(skip.visibleSkip(1_520_000))
    }
}
