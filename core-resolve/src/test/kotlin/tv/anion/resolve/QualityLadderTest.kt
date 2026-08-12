package tv.anion.resolve

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QualityLadderTest {

    private val manifest = "https://sky.solodcdn.com/animes/x/y/360.mp4:hls:manifest.m3u8"

    @Test
    fun `качество берётся из имени файла`() {
        assertEquals(360, QualityLadder.qualityOf(manifest))
    }

    @Test
    fun `URL разрезается вокруг числа качества`() {
        val split = QualityLadder.splitQuality(manifest)!!
        assertEquals("https://sky.solodcdn.com/animes/x/y/", split.prefix)
        assertEquals(".mp4:hls:manifest.m3u8", split.suffix)
    }

    @Test
    fun `чужой формат URL не разрезается и качество не выдумывается`() {
        assertNull(QualityLadder.splitQuality("https://cdn/anime/manifest.m3u8"))
        assertNull(QualityLadder.qualityOf("https://cdn/anime/manifest.m3u8"))
    }

    @Test
    fun `лестница идёт от лучшего к худшему`() {
        assertEquals(listOf(1080, 720, 480, 360), QualityLadder.LADDER)
    }
}
