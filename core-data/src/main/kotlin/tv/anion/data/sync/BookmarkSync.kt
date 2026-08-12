package tv.anion.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

interface BookmarkSync {
    suspend fun pull()
    suspend fun pushDirty()
    suspend fun syncNow()
}

class DefaultBookmarkSync(
    private val repository: BookmarkRepository,
    private val remote: BookmarkRemote,
    private val sessions: SessionStore,
    private val progress: WatchProgressRepository? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : BookmarkSync {
    private val mutex = Mutex()
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

    override suspend fun syncNow() = mutex.withLock {
        // Сначала pull: dirty защищён от перезаписи, затем он отправляется наверх.
        pull()
        pushDirty()
    }
}

interface BookmarkRemote {
    suspend fun login(login: String, password: String): String
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
    posterUrl = poster.small.ifBlank { poster.medium.ifBlank { poster.big } },
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
