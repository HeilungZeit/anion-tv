package tv.anion.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.anion.data.repo.Bookmark
import tv.anion.data.repo.BookmarkKind
import tv.anion.data.repo.BookmarkRepository
import tv.anion.data.repo.WatchProgressRepository
import tv.anion.source.SourceId
import tv.anion.source.kodik.AnionGoApi
import java.io.IOException

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
        progress?.pendingSync()
            ?.filter { it.source == SourceId.KODIK && it.finished }
            ?.forEach { watched ->
                val bookmark = repository.get(watched.source, watched.animeId)
                if (bookmark != null && !bookmark.dirty && bookmark.watchedEpisodes >= watched.episode) {
                    progress.markSynced(watched)
                }
            }
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
            pushDirty()
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
) : BookmarkRemote {
    override suspend fun login(login: String, password: String): String {
        val key = if ('@' in login) "email" else "username"
        val body = buildJsonObject { put(key, login); put("password", password) }
        val response = request("$baseUrl/user/login", "POST", body.toString(), sessionId = null)
        return json.decodeFromString<LoginDto>(response).session
            .takeIf(String::isNotBlank) ?: error("сервер не вернул сессию")
    }

    /**
     * Сессию гасит сервер: без этого запись в его таблице живёт ещё месяц, и
     * «вышел на телевизоре» ничего не означало бы.
     */
    override suspend fun logout(sessionId: String) {
        runCatching { request("$baseUrl/user/logout", "POST", "{}", sessionId) }
    }

    /** Удаление на сайте идёт по serverId, а не по animeId — так в роутере. */
    override suspend fun delete(sessionId: String, serverId: String) {
        request("$baseUrl/bookmarks/$serverId", "DELETE", null, sessionId)
    }

    override suspend fun profile(sessionId: String): UserProfile {
        val body = request("$baseUrl/user", "GET", null, sessionId)
        val dto = json.decodeFromString<UserDto>(body)
        return UserProfile(username = dto.username, email = dto.email)
    }

    override suspend fun getAll(sessionId: String): List<Bookmark> {
        val body = request("$baseUrl/bookmarks", "GET", null, sessionId)
        val response = json.decodeFromString<BookmarksDto>(body)
        return (response.watching + response.willWatch + response.watched + response.onHold + response.dropped)
            .map { it.toModel() }
    }

    override suspend fun upsert(sessionId: String, bookmark: Bookmark): Bookmark {
        val existing = if (bookmark.serverId != null) bookmark
        else getAll(sessionId).firstOrNull { it.animeId == bookmark.animeId }
        val payload = bookmark.payload()
        val body = if (existing != null) {
            request("$baseUrl/bookmarks/${bookmark.animeId}", "PUT", payload, sessionId)
        } else {
            request("$baseUrl/bookmarks", "POST", bookmark.createPayload(), sessionId)
        }
        return if (existing != null) {
            json.decodeFromString<RemoteBookmarkDto>(body).toModel()
        } else {
            val grouped = json.decodeFromString<BookmarksDto>(body)
            (grouped.watching + grouped.willWatch + grouped.watched + grouped.onHold + grouped.dropped)
                .firstOrNull { it.yumiId.toString() == bookmark.animeId }
                ?.toModel() ?: bookmark
        }
    }

    private suspend fun request(url: String, method: String, body: String?, sessionId: String?): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
                .header(AnionGoApi.CLIENT_HEADER, AnionGoApi.CLIENT_VALUE)
                .header("Accept", "application/json")
            if (sessionId != null) builder.header("Cookie", "X-Session-ID=$sessionId")
            val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(requireNotNull(requestBody))
                "PUT" -> builder.put(requireNotNull(requestBody))
            }
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.parseToJsonElement(text).let { it as? JsonObject }?.get("message")?.toString()?.trim('"')
                    }.getOrNull()
                    throw IOException(message ?: "сервер ответил ${response.code}")
                }
                text
            }
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
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

private fun Bookmark.payload(): String = buildJsonObject {
    put("status", kind.wireName)
    put("watchedEpisodes", watchedEpisodes)
    put("totalEpisodes", totalEpisodes)
    put("animeStatus", animeStatus)
}.toString()

private fun Bookmark.createPayload(): String = buildJsonObject {
    put("yumiId", animeId.toIntOrNull() ?: error("Kodik animeId должен быть числом"))
    put("yumiSlug", animeId)
    put("title", title)
    put("status", kind.wireName)
    put("watchedEpisodes", watchedEpisodes)
    put("totalEpisodes", totalEpisodes)
    put("animeStatus", animeStatus)
    put("poster", buildJsonObject {
        val url = posterUrl.orEmpty()
        put("fullsize", url); put("big", url); put("small", url); put("medium", url)
        put("huge", url); put("mega", url)
    })
}.toString()
