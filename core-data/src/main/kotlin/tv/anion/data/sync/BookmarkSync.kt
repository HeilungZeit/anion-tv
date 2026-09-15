package tv.anion.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import tv.anion.data.repo.Bookmark
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.repo.BookmarkRepository
import tv.anion.data.repo.WatchProgressRepository
import tv.anion.source.SourceId
import tv.anion.source.kodik.AnionGoApi
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Ошибка от anion-go вместе с машинным кодом из поля `code`. Код нужен там, где
 * текста сервера мало: часть ответов рассчитана на веб — например, требование
 * решить капчу, которое на пульте выполнить нечем.
 *
 * Коды зеркалят [../../anion-go/pkg/errors/errors.go].
 */
class ApiException(val code: String?, message: String) : IOException(message)

internal object ApiErrorCode {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED"
    const val CAPTCHA_INVALID = "CAPTCHA_INVALID"
    const val TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS"
}

/**
 * Что показывать на экране аккаунта. Ошибку держим отдельно: все автоматические
 * вызовы обёрнуты в runCatching и раньше глотали её молча — со стороны это
 * выглядело как «всё хорошо», хотя сессия могла протухнуть.
 */
data class SyncState(
    val lastSuccessAt: Long? = null,
    val running: Boolean = false,
    val error: String? = null,
)

interface BookmarkSync {
    val state: StateFlow<SyncState>
    suspend fun pull()
    suspend fun pushDirty()
    suspend fun syncNow()
    /** Убрать закладку и на сайте: локально её уже нет. */
    suspend fun deleteRemote(bookmark: Bookmark)
}

class DefaultBookmarkSync(
    private val repository: BookmarkRepository,
    private val remote: BookmarkRemote,
    private val sessions: SessionStore,
    private val progress: WatchProgressRepository? = null,
    private val syncState: SyncStateStore? = null,
    private val progressRemote: WatchProgressRemote? = null,
    private val progressMigration: OneTimeFlag? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : BookmarkSync {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(SyncState(lastSuccessAt = syncState?.read()))
    override val state: StateFlow<SyncState> = _state.asStateFlow()
    override suspend fun pull() {
        val session = sessions.read() ?: return
        val pulledAt = now()
        remote.getAll(session).forEach { repository.mergeRemote(it, pulledAt) }
    }

    override suspend fun pushDirty() {
        val session = sessions.read() ?: return
        repository.dirty()
            .filter { it.source == SourceId.KODIK }
            .forEach { local ->
                val confirmed = remote.upsert(session, local)
                // CAS по updatedAt: поздний тик плеера не станет ошибочно clean.
                repository.markSynced(local, confirmed.serverId)
            }
    }

    /**
     * Досмотренные серии уходят отдельным эндпоинтом, а не счётчиком закладки:
     * счётчик терял точечные отметки и существовал только у тайтлов в закладках.
     * Очередь — сама Room: строка остаётся pending, пока сервер её не принял, а
     * CAS по updatedAt не подтвердит тик, записанный уже после выборки.
     */
    private suspend fun pushProgress() {
        val session = sessions.read() ?: return
        val progress = progress ?: return
        val remote = progressRemote ?: return

        // Старый путь доносил до сервера только счётчик, точечных серий там нет.
        // Один раз отправляем все досмотренные, дальше — только новые.
        val migrating = progressMigration?.isSet() == false
        val rows = if (migrating) progress.finished(SourceId.KODIK) else progress.pendingFinishedSync(SourceId.KODIK)

        if (rows.isNotEmpty()) {
            remote.sync(session, rows.groupBy({ it.animeId }, { it.episode }).mapValues { it.value.toSet() })
            rows.forEach { progress.markSynced(it) }
        }
        if (migrating) progressMigration?.set()
    }

    override suspend fun deleteRemote(bookmark: Bookmark) {
        val session = sessions.read() ?: return
        val serverId = bookmark.serverId ?: return
        runCatching { remote.delete(session, serverId) }
    }

    override suspend fun syncNow(): Unit = mutex.withLock {
        if (sessions.read() == null) return@withLock
        _state.value = _state.value.copy(running = true, error = null)
        try {
            // Сначала pull: dirty защищён от перезаписи, затем он отправляется наверх.
            pull()
            // Прогресс раньше закладок: создание закладки берёт счётчик из него. Его
            // сбой не должен держать закладки, поэтому ошибка всплывает после них.
            val progressError = try {
                pushProgress()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error
            }
            pushDirty()
            progressError?.let { throw it }
            val at = now()
            syncState?.write(at)
            _state.value = SyncState(lastSuccessAt = at, running = false, error = null)
        } catch (error: Throwable) {
            // Наружу пробрасываем как раньше — вызывающие сами решают, шуметь ли.
            _state.value = _state.value.copy(running = false, error = error.message ?: "сбой синхронизации")
            throw error
        }
    }
}

/** Профиль пользователя с сайта — чтобы на ТВ было видно, под кем вошли. */
data class UserProfile(val username: String, val email: String)

interface BookmarkRemote {
    suspend fun login(login: String, password: String): String
    suspend fun logout(sessionId: String)
    suspend fun delete(sessionId: String, serverId: String)
    suspend fun profile(sessionId: String): UserProfile
    suspend fun getAll(sessionId: String): List<Bookmark>
    suspend fun upsert(sessionId: String, bookmark: Bookmark): Bookmark
}

class HttpBookmarkRemote(
    private val http: OkHttpClient,
    private val baseUrl: String = AnionGoApi.BASE_URL,
    private val clientInfo: ClientInfo = ClientInfo.TV,
) : BookmarkRemote {
    private val anionGo = AnionGoHttp(http, clientInfo)

    override suspend fun login(login: String, password: String): String {
        val key = if ('@' in login) "email" else "username"
        val body = buildJsonObject { put(key, login); put("password", password) }
        val response = request("$baseUrl/user/login", HttpMethod.POST, body.toString(), sessionId = null)
        return AnionGoJson.decodeFromString<LoginDto>(response).session
            .takeIf(String::isNotBlank) ?: error("сервер не вернул сессию")
    }

    /**
     * Сессию гасит сервер: без этого запись в его таблице живёт ещё месяц, и
     * «вышел на телевизоре» ничего не означало бы.
     */
    override suspend fun logout(sessionId: String) {
        runCatching { request("$baseUrl/user/logout", HttpMethod.POST, "{}", sessionId) }
    }

    /** Удаление на сайте идёт по serverId, а не по animeId — так в роутере. */
    override suspend fun delete(sessionId: String, serverId: String) {
        request("$baseUrl/bookmarks/$serverId", HttpMethod.DELETE, null, sessionId)
    }

    override suspend fun profile(sessionId: String): UserProfile {
        val body = request("$baseUrl/user", HttpMethod.GET, null, sessionId)
        val dto = AnionGoJson.decodeFromString<UserDto>(body)
        return UserProfile(username = dto.username, email = dto.email)
    }

    override suspend fun getAll(sessionId: String): List<Bookmark> {
        val body = request("$baseUrl/bookmarks", HttpMethod.GET, null, sessionId)
        val response = AnionGoJson.decodeFromString<BookmarksDto>(body)
        return (response.watching + response.willWatch + response.watched + response.onHold + response.dropped)
            .map { it.toModel() }
    }

    override suspend fun upsert(sessionId: String, bookmark: Bookmark): Bookmark {
        val existing = if (bookmark.serverId != null) bookmark
        else getAll(sessionId).firstOrNull { it.animeId == bookmark.animeId }
        val payload = bookmark.payload()
        val body = if (existing != null) {
            request("$baseUrl/bookmarks/${bookmark.animeId}", HttpMethod.PUT, payload, sessionId)
        } else {
            request("$baseUrl/bookmarks", HttpMethod.POST, bookmark.createPayload(), sessionId)
        }
        return if (existing != null) {
            AnionGoJson.decodeFromString<RemoteBookmarkDto>(body).toModel()
        } else {
            val grouped = AnionGoJson.decodeFromString<BookmarksDto>(body)
            (grouped.watching + grouped.willWatch + grouped.watched + grouped.onHold + grouped.dropped)
                .firstOrNull { it.yumiId.toString() == bookmark.animeId }
                ?.toModel() ?: bookmark
        }
    }

    /** У всех эндпоинтов закладок и аккаунта ответ с телом; пустой — нарушение контракта. */
    private suspend fun request(url: String, method: HttpMethod, body: String?, sessionId: String?): String =
        anionGo.request(url, method, body, sessionId) ?: throw IOException("сервер ответил без тела")
}

@Serializable private data class LoginDto(val session: String)
@Serializable private data class UserDto(val username: String = "", val email: String = "")
@Serializable private data class BookmarksDto(
    val watching: List<RemoteBookmarkDto> = emptyList(),
    val willWatch: List<RemoteBookmarkDto> = emptyList(),
    val watched: List<RemoteBookmarkDto> = emptyList(),
    val onHold: List<RemoteBookmarkDto> = emptyList(),
    val dropped: List<RemoteBookmarkDto> = emptyList(),
)
@Serializable private data class RemoteBookmarkDto(
    val id: String? = null,
    val status: String,
    val watchedEpisodes: Int = 0,
    val totalEpisodes: Int = 0,
    val yumiId: Int,
    val title: String = "",
    val poster: RemotePosterDto = RemotePosterDto(),
    val animeStatus: String = "",
)
@Serializable private data class RemotePosterDto(
    val fullsize: String = "", val big: String = "", val small: String = "",
    val medium: String = "", val huge: String = "", val mega: String = "",
)

private fun RemoteBookmarkDto.toModel() = Bookmark(
    source = SourceId.KODIK,
    animeId = yumiId.toString(),
    serverId = id,
    kind = BookmarkKind.fromWire(status),
    watchedEpisodes = watchedEpisodes,
    totalEpisodes = totalEpisodes,
    title = title,
    posterUrl = poster.big.ifBlank { poster.fullsize.ifBlank { poster.medium.ifBlank { poster.small } } },
    animeStatus = animeStatus,
    // Сервер это поле не отдаёт; pull получает реальное время в конфликт-резолвере.
    updatedAt = 0,
    syncedAt = null,
    dirty = false,
)

/*
 * Счётчик просмотренных серий в закладку не отправляется: его выводит сервер из
 * прогресса просмотра, а устаревшее число с ТВ раньше откатывало прогресс сайта.
 */
private fun Bookmark.payload(): String = buildJsonObject {
    put("status", kind.wireName)
    put("totalEpisodes", totalEpisodes)
    put("animeStatus", animeStatus)
}.toString()

private fun Bookmark.createPayload(): String = buildJsonObject {
    put("yumiId", animeId.toIntOrNull() ?: error("Kodik animeId должен быть числом"))
    put("yumiSlug", animeId)
    put("title", title)
    put("status", kind.wireName)
    put("totalEpisodes", totalEpisodes)
    put("animeStatus", animeStatus)
    put("poster", buildJsonObject {
        val url = posterUrl.orEmpty()
        put("fullsize", url); put("big", url); put("small", url); put("medium", url)
        put("huge", url); put("mega", url)
    })
}.toString()
