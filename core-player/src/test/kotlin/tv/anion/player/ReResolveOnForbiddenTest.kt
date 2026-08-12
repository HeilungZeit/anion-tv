package tv.anion.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReResolveOnForbiddenTest {

    @Test
    fun `403 на сегменте вызывает один переролв, а не шторм`() {
        val gate = ReResolveOnForbidden()

        assertEquals(ReResolveOnForbidden.Decision.ReResolve, gate.onSegmentError(403))
        assertEquals(ReResolveOnForbidden.Decision.Fail, gate.onSegmentError(403))
        assertEquals(ReResolveOnForbidden.Decision.Fail, gate.onSegmentError(403))
    }

    @Test
    fun `после переролва позиция сохраняется`() {
        val gate = ReResolveOnForbidden()
        gate.rememberPosition(123_000)

        assertEquals(ReResolveOnForbidden.Decision.ReResolve, gate.onSegmentError(403))
        assertEquals(123_000L, gate.savedPositionMs)
    }

    @Test
    fun `повторный 403 подряд отдаёт ошибку пользователю`() {
        val gate = ReResolveOnForbidden()

        gate.onSegmentError(403)
        assertEquals(ReResolveOnForbidden.Decision.Fail, gate.onSegmentError(403))

        gate.onPlaybackResumed()
        assertEquals(ReResolveOnForbidden.Decision.ReResolve, gate.onSegmentError(403))
    }

    @Test
    fun `за 5 минут до штампа в URL пора обновлять`() {
        val url = "https://cloud.solodcdn.com/useruploads/abc/def:2026081307/360.mp4:hls:manifest.m3u8"
        val expiry = SignatureClock.expiryEpochMs(url)!!

        assertTrue(SignatureClock.shouldRefreshAhead(url, nowMs = expiry - 60_000))
        assertFalse(SignatureClock.shouldRefreshAhead(url, nowMs = expiry - 10 * 60_000))
        assertFalse(SignatureClock.shouldRefreshAhead("https://anilibria.top/hls/720.m3u8", nowMs = expiry))
    }
}
