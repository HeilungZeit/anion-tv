package tv.anion.data

import org.junit.Test
import tv.anion.data.repo.EpisodeMarksPolicy
import tv.anion.data.repo.WatchProgress
import tv.anion.source.SourceId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeMarksPolicyTest {
    private fun row(episode: Int, position: Long, finished: Boolean = false, updatedAt: Long = 1) =
        WatchProgress(SourceId.KODIK, "42", episode, "dub", "Тайтл", null, position, 100_000, finished, updatedAt, null)

    @Test fun `отметки из аккаунта объединяются с досмотренным на этом устройстве`() {
        val marks = EpisodeMarksPolicy.of(listOf(row(1, 95_000, finished = true)), remoteWatched = setOf(2, 3))

        assertEquals(setOf(1, 2, 3), marks.watched)
    }

    @Test fun `серия из аккаунта не рисует полоску и не предлагается к продолжению`() {
        val marks = EpisodeMarksPolicy.of(listOf(row(4, 50_000)), remoteWatched = setOf(4))

        assertEquals(setOf(4), marks.watched)
        assertEquals(emptyMap(), marks.partial)
        assertNull(marks.resume)
    }

    @Test fun `продолжить предлагает самую свежую серию, не отмеченную нигде`() {
        val local = listOf(row(2, 40_000, updatedAt = 5), row(3, 60_000, updatedAt = 9))

        assertEquals(3, EpisodeMarksPolicy.of(local, remoteWatched = emptySet()).resume?.episode)
        assertEquals(2, EpisodeMarksPolicy.of(local, remoteWatched = setOf(3)).resume?.episode)
    }

    @Test fun `случайный тык не предлагается к продолжению, но полоску рисует`() {
        val marks = EpisodeMarksPolicy.of(listOf(row(1, 10_000)), remoteWatched = emptySet())

        assertNull(marks.resume)
        assertEquals(mapOf(1 to 0.1f), marks.partial)
    }
}
