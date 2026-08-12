package tv.anion.resolve

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerParamsTest {

    @Test
    fun `читает оба вида кавычек`() {
        val page = """var ref = "https://anion.online/"; vInfo.hash = 'abc123';"""
        assertEquals("https://anion.online/", JsLiterals.read(page, "var ref"))
        assertEquals("abc123", JsLiterals.read(page, "vInfo.hash"))
    }

    @Test
    fun `vInfo важнее одноимённого var на странице сезона`() {
        // В адресе стоит season, а серия внутри уже seria со своим id и хэшем.
        val params = PlayerParams.parse(SEASON_PAGE)
        assertEquals("seria", params.kind)
        assertEquals("1028448", params.id)
        assertEquals("serihash", params.hash)
    }

    @Test
    fun `отсутствие подписи - внятная ошибка, а не пустой запрос`() {
        val page = SEASON_PAGE.replace("""var d_sign = "dsign";""", "")
        val error = assertFailsWith<ResolveException.MissingPlayerParam> { PlayerParams.parse(page) }
        assertEquals("в разметке плеера нет d_sign", error.message)
    }

    @Test
    fun `в форму уходят подписи, а не только type id hash`() {
        val keys = PlayerParams.parse(SEASON_PAGE).form().map { it.first }
        assertEquals(
            listOf("d", "d_sign", "pd", "pd_sign", "ref", "ref_sign", "type", "id", "hash", "bad_user", "cdn_is_working"),
            keys,
        )
    }

    companion object {
        val SEASON_PAGE = """
            var domain = "kodikplayer.com";
            var d_sign = "dsign";
            var pd = "kodikplayer.com";
            var pd_sign = "pdsign";
            var ref = "https://anion.online/";
            var ref_sign = "refsign";
            var type = "season";
            var id = "121220";
            var hash = "seasonhash";
            vInfo.type = "seria";
            vInfo.id = "1028448";
            vInfo.hash = "serihash";
        """.trimIndent()
    }
}
