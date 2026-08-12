package tv.anion.resolve

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EndpointDiscoveryTest {

    @Test
    fun `берёт короткий путь и пропускает соседние atob`() {
        assertTrue(LocalKodikResolver.isEndpointPath("/ftor"))
        assertTrue(LocalKodikResolver.isEndpointPath("/kor"))
        assertFalse(LocalKodikResolver.isEndpointPath("//(?:get|cloud).kodik-cdn.com"))
        assertFalse(LocalKodikResolver.isEndpointPath("allvideometrika.com/kodikstats.php"))
    }

    @Test
    fun `находит путь к скрипту плеера в разметке`() {
        val page = """<script src="/assets/js/app.player_single.abc123.js"></script>"""
        assertEquals("/assets/js/app.player_single.abc123.js", LocalKodikResolver.findScript(page, "app.player_single"))
    }

    @Test
    fun `origin сохраняет нестандартный порт`() {
        assertEquals("http://localhost:8080", originOf("http://localhost:8080/seria/1"))
        assertEquals("https://kodikplayer.com", originOf("https://kodikplayer.com/seria/1"))
    }
}
