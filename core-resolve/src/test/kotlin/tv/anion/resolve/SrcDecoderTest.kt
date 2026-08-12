package tv.anion.resolve

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SrcDecoderTest {

    @Test
    fun `расшифровывает настоящий src из ответа эндпоинта`() {
        val url = SrcDecoder.decodeOrNull(SAMPLE_SRC)
        assertTrue(url != null && url.startsWith("https://sky.solodcdn.com/animes/"), "получено: $url")
        assertTrue(url.endsWith("/240.mp4:hls:manifest.m3u8"), "получено: $url")
    }

    @Test
    fun `не гадает на мусоре`() {
        assertNull(SrcDecoder.decodeOrNull("не base64 и не ссылка"))
    }

    @Test
    fun `находит сдвиг, даже если он не 18`() {
        // Kodik меняет адрес эндпоинта, а не шифр, но перебор стоит нуля и
        // превращает будущую смену сдвига в незаметность вместо отказа.
        val reencoded = SrcDecoder.rotate(SAMPLE_SRC, 26 - 18 + 5)
        assertTrue(SrcDecoder.decodeOrNull(reencoded)?.contains(".m3u8") == true)
    }

    @Test
    fun `сдвиг не трогает цифры и знаки base64`() {
        assertTrue(SrcDecoder.rotate("aA1+/=", 1) == "bB1+/=")
    }

    companion object {
        /** Настоящий `src` из ответа эндпоинта — образец перенесён из anion-dl. */
        const val SAMPLE_SRC =
            "iPZ0kPU6Tg9hi3sck29aj2ZrHO4cG29bT2NciE1tkg80U2Q2WBY1VBHtVLChGrC1VhseGhNtHOUhWERtU2Nq" +
                "GrQhUOVuGuC4ThCfVOUfVuRuUOVtHrIeVOC0VhQ4GBYeUrM3UOMeVOHrWrQeUrGeWLMhULCdUrYeTu1eVLxw" +
                "jPU6jENciEHtk3YcjBV1WI"
    }
}
