package tv.anion.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import tv.anion.data.repo.Bookmark
import tv.anion.data.sync.ApiException
import tv.anion.data.sync.BookmarkRemote
import tv.anion.data.sync.DefaultAccountRepository
import tv.anion.data.sync.HttpBookmarkRemote
import tv.anion.data.sync.SessionStore
import tv.anion.data.sync.UserProfile
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Вход на телевизоре живёт с ограничениями бэкенда на перебор паролей. Здесь
 * проверяется то, что видит пользователь, когда упирается в них с пульта.
 */
class AccountLoginErrorsTest {
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    private fun remote(): HttpBookmarkRemote =
        HttpBookmarkRemote(OkHttpClient(), server.url("/api").toString().trimEnd('/'))

    @Test fun `код ошибки достаётся из тела ответа, а не теряется по дороге`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"code":"CAPTCHA_REQUIRED","message":"Подтвердите, что вы не робот"}""")
        )

        val error = runCatching { remote().login("alice@example.com", "secret") }.exceptionOrNull()

        assertTrue(error is ApiException, "ожидали ApiException, получили $error")
        assertEquals("CAPTCHA_REQUIRED", error.code)
        assertEquals("Подтвердите, что вы не робот", error.message)
    }

    @Test fun `ответ без тела всё равно даёт внятную ошибку`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>gateway</html>"))

        val error = runCatching { remote().login("alice@example.com", "secret") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(null, error.code)
        assertEquals("сервер ответил 502", error.message)
    }

    // Решать арифметику пультом негде, поэтому буквальный текст сервера человеку
    // ничего не даёт: ему нужно знать, что делать дальше.
    @Test fun `требование капчи превращается в понятную с телевизора инструкцию`() = runTest {
        val repository = DefaultAccountRepository(
            FakeRemote(ApiException("CAPTCHA_REQUIRED", "Подтвердите, что вы не робот")),
            MemorySessionStore(),
        )

        val error = runCatching { repository.login("alice@example.com", "secret") }.exceptionOrNull()

        assertEquals(
            "Слишком много неудачных попыток. Подождите 15 минут или войдите на сайте anion.online",
            error?.message,
        )
    }

    @Test fun `текст про лимит попыток берётся у сервера как есть`() = runTest {
        val repository = DefaultAccountRepository(
            FakeRemote(ApiException("TOO_MANY_ATTEMPTS", "Слишком много попыток входа. Попробуйте позже")),
            MemorySessionStore(),
        )

        val error = runCatching { repository.login("alice@example.com", "secret") }.exceptionOrNull()

        assertEquals("Слишком много попыток входа. Попробуйте позже", error?.message)
    }

    @Test fun `неверный пароль не создаёт сессию`() = runTest {
        val sessions = MemorySessionStore()
        val repository = DefaultAccountRepository(
            FakeRemote(ApiException("UNAUTHORIZED", "Неверный логин или пароль")),
            sessions,
        )

        val error = runCatching { repository.login("alice@example.com", "secret") }.exceptionOrNull()

        assertEquals("Неверный логин или пароль", error?.message)
        assertEquals(null, sessions.read())
        assertFalse(repository.signedIn.value)
    }

    @Test fun `успешный вход сохраняет сессию`() = runTest {
        val sessions = MemorySessionStore()
        val repository = DefaultAccountRepository(FakeRemote(null), sessions)

        repository.login("alice@example.com", "secret")

        assertEquals("session-id", sessions.read())
        assertTrue(repository.signedIn.value)
    }

    private class MemorySessionStore : SessionStore {
        private var session: String? = null
        override fun read(): String? = session
        override fun write(sessionId: String?) { session = sessionId }
    }

    private class FakeRemote(private val failure: IOException?) : BookmarkRemote {
        override suspend fun login(login: String, password: String): String =
            failure?.let { throw it } ?: "session-id"

        override suspend fun logout(sessionId: String) = Unit
        override suspend fun delete(sessionId: String, serverId: String) = Unit
        override suspend fun profile(sessionId: String): UserProfile = UserProfile("alice", "alice@example.com")
        override suspend fun getAll(sessionId: String): List<Bookmark> = emptyList()
        override suspend fun upsert(sessionId: String, bookmark: Bookmark): Bookmark = bookmark
    }
}
