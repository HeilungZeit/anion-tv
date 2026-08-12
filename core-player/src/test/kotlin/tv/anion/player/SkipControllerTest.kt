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
        assertEquals(opening, skip.visibleSkip(10_000))
        assertEquals(opening, skip.visibleSkip(50_000))
        assertNull(skip.visibleSkip(100_000))
        assertEquals(100_000L, skip.skipTargetMs(opening))
    }

    @Test
    fun `эндинг не предлагается, если серия последняя`() {
        val ending = Segment(startSeconds = 1_300, stopSeconds = 1_400)
        val skips = Skips(ending = ending)

        assertEquals(ending, SkipController(skips, isLastEpisode = false).visibleSkip(1_350_000))
        assertNull(SkipController(skips, isLastEpisode = true).visibleSkip(1_350_000))
    }
}
