package tv.anion.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import tv.anion.data.repo.Bookmark
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.sync.HttpBookmarkRemote
import tv.anion.data.sync.HttpWatchProgressRemote
import tv.anion.data.sync.RemoteWatchProgress
import tv.anion.source.SourceId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WatchProgressRemoteTest {
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    private val baseUrl get() = server.url("/api").toString().trimEnd('/')

    private fun remote() = HttpWatchProgressRemote(OkHttpClient(), baseUrl)

    /** Форма ответа зеркалит `models.WatchProgress` из anion-go. */
    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "нет фикстуры $name" }.readText()

    @Test fun `прогресс аниме запрашивается с сессией и меткой клиента`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("watch-progress.json")))

        val progress = remote().get("session-1", "49030")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/watch-progress/49030", request.path)
        assertEquals("X-Session-ID=session-1", request.getHeader("Cookie"))
        assertEquals("anion-dl", request.getHeader("X-Anion-Client"))
        assertEquals(RemoteWatchProgress("49030", setOf(1, 2, 3, 7)), progress)
    }

    @Test fun `ответ 204 без тела означает, что аниме не смотрели`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        assertNull(remote().get("session-1", "49030"))
    }

    @Test fun `в синк уходят только номера и ключи, которые примет сервер`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        remote().sync(
            "session-1",
            mapOf(
                "49030" to setOf(3, 1, 0, 10_001),
                // alias AniLibria: у сервера такого ключа нет
                "grand-blue" to setOf(1),
                "7" to emptySet(),
            ),
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/watch-progress/sync", request.path)
        assertEquals("""{"items":[{"animeId":49030,"episodes":[1,3]}]}""", request.body.readUtf8())
    }

    @Test fun `большая история уходит пачками, которые пропустит сервер`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setBody("[]")) }

        remote().sync("session-1", (1..450).associate { it.toString() to setOf(1) })

        assertEquals(3, server.requestCount)
    }

    @Test fun `если отправлять нечего, запроса нет`() = runTest {
        val result = remote().sync("session-1", mapOf("grand-blue" to setOf(1)))

        assertEquals(emptyList(), result)
        assertEquals(0, server.requestCount)
    }

    @Test fun `удаление закладки уходит методом DELETE`() = runTest {
        server.enqueue(MockResponse().setBody("""{"watching":[]}"""))

        HttpBookmarkRemote(OkHttpClient(), baseUrl).delete("session-1", "b6a1c3de")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/bookmarks/b6a1c3de", request.path)
    }

    @Test fun `закладка уходит без счётчика серий — его выводит сервер`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"server-id","status":"watching","yumiId":42}"""))
        val bookmark = Bookmark(
            SourceId.KODIK, "42", "server-id", BookmarkKind.WATCHING, watchedEpisodes = 3,
            totalEpisodes = 12, title = "Тайтл", posterUrl = null, animeStatus = "ongoing",
            updatedAt = 1, syncedAt = null, dirty = true,
        )

        HttpBookmarkRemote(OkHttpClient(), baseUrl).upsert("session-1", bookmark)

        val body = server.takeRequest().body.readUtf8()
        assertFalse("watchedEpisodes" in body, body)
    }
}
