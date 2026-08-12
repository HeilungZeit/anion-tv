package tv.anion.resolve

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalKodikResolverTest {

    private val server = MockWebServer()
    private val cache = EndpointCache.InMemory()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `известный эндпоинт отдаёт ссылки — скрипт плеера не качается`() = runTest {
        serve(ftor = linksJson())

        val stream = resolver().resolve(iframeUrl(), preferredQuality = 1080)

        assertEquals(720, stream.quality)
        assertTrue(stream.manifestUrl.contains("/720.mp4:hls:"))
        val paths = drainPaths()
        assertTrue(paths.any { it.startsWith("/ftor") })
        assertTrue(paths.none { "app.player_single" in it })
    }

    @Test
    fun `404 на известном эндпоинте — идёт в скрипт, находит короткий путь, повторяет POST`() = runTest {
        serve(ftor = null, script = SCRIPT, kor = linksJson())

        val stream = resolver().resolve(iframeUrl(), preferredQuality = 1080)

        assertEquals(720, stream.quality)
        val paths = drainPaths()
        assertTrue(paths.any { it.startsWith("/ftor") })
        assertEquals(1, paths.count { "app.player_single" in it })
        assertTrue(paths.any { it.startsWith("/kor") })
    }

    @Test
    fun `найденный адрес кэшируется — второй резолв не качает скрипт`() = runTest {
        serve(ftor = null, script = SCRIPT, kor = linksJson())
        val resolver = resolver()

        resolver.resolve(iframeUrl(), preferredQuality = 720)
        drainPaths()

        resolver.resolve(iframeUrl(), preferredQuality = 720)
        val second = drainPaths()

        assertTrue(second.none { "app.player_single" in it })
        assertTrue(second.none { it.startsWith("/ftor") })
        assertTrue(second.any { it.startsWith("/kor") })
    }

    @Test
    fun `в скрипте нет подходящего atob — EndpointNotFound`() = runTest {
        serve(ftor = null, script = SCRIPT_WITHOUT_ENDPOINT, kor = null)

        val error = assertFailsWith<ResolveException.EndpointNotFound> {
            resolver().resolve(iframeUrl(), preferredQuality = 720)
        }
        assertTrue("Другого эндпоинта" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `Error code rs из HTML-заглушки попадает в текст ошибки`() = runTest {
        serve(
            ftorBody = "<html><body>Error code: rs</body></html>",
            ftorCode = 500,
            script = SCRIPT_WITHOUT_ENDPOINT,
            kor = null,
        )

        val error = assertFailsWith<ResolveException.EndpointNotFound> {
            resolver().resolve(iframeUrl(), preferredQuality = 720)
        }
        assertTrue("rs" in error.message.orEmpty(), error.message)
        assertTrue("500" in error.message.orEmpty(), error.message)
    }

    private fun resolver(): LocalKodikResolver =
        LocalKodikResolver(http = client(), endpointCache = cache)

    private fun iframeUrl(): String = server.url("/seria/1028448/serihash").toString()

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            check(url.host == server.hostName || url.host == CDN_HOST) {
                "тест полез в сеть: $url"
            }
            val next = if (url.host == CDN_HOST) {
                request.newBuilder()
                    .url(
                        url.newBuilder()
                            .scheme("http")
                            .host(server.hostName)
                            .port(server.port)
                            .build(),
                    )
                    .build()
            } else {
                request
            }
            chain.proceed(next)
        }
        .build()

    private fun serve(
        ftor: String? = null,
        ftorBody: String = "not found",
        ftorCode: Int = 404,
        script: String? = SCRIPT,
        kor: String? = null,
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "GET" && path.startsWith("/seria/") ->
                        MockResponse().setBody(PAGE)
                    request.method == "POST" && path == "/ftor" -> when {
                        ftor != null -> json(ftor)
                        else -> MockResponse().setResponseCode(ftorCode).setBody(ftorBody)
                    }
                    request.method == "GET" && "app.player_single" in path -> when {
                        script != null -> MockResponse().setBody(script)
                        else -> MockResponse().setResponseCode(404)
                    }
                    request.method == "POST" && path == "/kor" -> when {
                        kor != null -> json(kor)
                        else -> MockResponse().setResponseCode(404)
                    }
                    "/720.mp4:hls:" in path ->
                        MockResponse().setBody("#EXTM3U\n#EXT-X-ENDLIST\n")
                    "manifest.m3u8" in path ->
                        MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    private fun drainPaths(): List<String> = buildList {
        while (true) {
            val request = server.takeRequest(0, TimeUnit.MILLISECONDS) ?: break
            add(request.path.orEmpty())
        }
    }

    private companion object {
        const val CDN_HOST = "cdn.test"

        val PAGE: String = readFixture("player-page.html")
        val SCRIPT: String = readFixture("player-script.js")

        val SCRIPT_WITHOUT_ENDPOINT: String = """
            var cdn = atob("Ly8oPzpnZXR8Y2xvdWQpLmtvZGlrLWNkbi5jb20=");
            var stats = atob("YWxsdmlkZW9tZXRyaWthLmNvbS9rb2Rpa3N0YXRzLnBocA==");
        """.trimIndent()

        fun readFixture(name: String): String =
            checkNotNull(LocalKodikResolverTest::class.java.getResource("/fixtures/$name")) {
                "нет фикстуры $name"
            }.readText()

        fun json(body: String) = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)

        fun linksJson(): String {
            val url = "https://$CDN_HOST/animes/x/y/360.mp4:hls:manifest.m3u8"
            val src = encodeSrc(url)
            return """{"links":{"360":[{"src":"$src"}]}}"""
        }

        fun encodeSrc(url: String, shift: Int = 18): String {
            val b64 = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8))
            return SrcDecoder.rotate(b64, (26 - shift % 26) % 26)
        }
    }
}
