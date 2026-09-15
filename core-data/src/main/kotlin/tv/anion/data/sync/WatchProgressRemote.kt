package tv.anion.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import tv.anion.source.SourceId
import tv.anion.source.kodik.AnionGoApi

/** Просмотренные серии одного аниме в аккаунте. Позиции внутри серии сервер не знает. */
data class RemoteWatchProgress(val animeId: String, val episodes: Set<Int>)

/**
 * Прогресс просмотра в аккаунте anion-go. На сервере одна запись на
 * пользователя и аниме с номерами просмотренных серий внутри, а слияние —
 * объединение наборов, поэтому повторная отправка безопасна.
 *
 * Ключ — только Yumi ID: у тайтлов из ряда AniLibria серверного ключа нет.
 */
interface WatchProgressRemote {
    /** `null` — по этому аниме в аккаунте ничего не смотрели. */
    suspend fun get(sessionId: String, animeId: String): RemoteWatchProgress?

    /** Слить наборы серий в аккаунт. Возвращает то, что стало на сервере. */
    suspend fun sync(sessionId: String, items: Map<String, Set<Int>>): List<RemoteWatchProgress>
}

class HttpWatchProgressRemote(
    http: OkHttpClient,
    private val baseUrl: String = AnionGoApi.BASE_URL,
    clientInfo: ClientInfo = ClientInfo.TV,
) : WatchProgressRemote {
    private val anionGo = AnionGoHttp(http, clientInfo)

    override suspend fun get(sessionId: String, animeId: String): RemoteWatchProgress? {
        val body = anionGo.request("$baseUrl/watch-progress/$animeId", HttpMethod.GET, null, sessionId)
            ?: return null
        return AnionGoJson.decodeFromString<WatchProgressDto>(body).toModel()
    }

    override suspend fun sync(sessionId: String, items: Map<String, Set<Int>>): List<RemoteWatchProgress> =
        items.mapNotNull { (animeId, episodes) -> acceptable(animeId, episodes) }
            .chunked(MAX_SYNC_ITEMS)
            .flatMap { part ->
                val body = anionGo.request("$baseUrl/watch-progress/sync", HttpMethod.POST, payload(part), sessionId)
                    ?: return@flatMap emptyList()
                AnionGoJson.decodeFromString<List<WatchProgressDto>>(body).map { it.toModel() }
            }

    /**
     * Только то, что сервер примет: запрос с одним неверным элементом он
     * отклоняет целиком, и из-за него не доехала бы вся пачка.
     */
    private fun acceptable(animeId: String, episodes: Set<Int>): Pair<Int, List<Int>>? {
        val id = animeId.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val valid = episodes.filter { it in 1..MAX_EPISODE_NUMBER }.sorted().take(MAX_EPISODES_PER_ANIME)
        return if (valid.isEmpty()) null else id to valid
    }

    private fun payload(items: List<Pair<Int, List<Int>>>): String = buildJsonObject {
        put("items", JsonArray(items.map { (animeId, episodes) ->
            buildJsonObject {
                put("animeId", animeId)
                put("episodes", JsonArray(episodes.map { JsonPrimitive(it) }))
            }
        }))
    }.toString()

    private companion object {
        // Зеркалят ограничения anion-go/internal/dto/watch_progress.go.
        const val MAX_SYNC_ITEMS = 200
        const val MAX_EPISODES_PER_ANIME = 5000
        const val MAX_EPISODE_NUMBER = 10000
    }
}

/** Зеркалит `models.WatchProgress`; `maxEpisode` и `updatedAt` клиенту не нужны. */
@Serializable
private data class WatchProgressDto(val animeId: Int, val episodes: List<Int> = emptyList())

private fun WatchProgressDto.toModel() = RemoteWatchProgress(animeId.toString(), episodes.toSet())

/**
 * Серии, отмеченные в аккаунте, — для карточки тайтла. Позиций внутри серии
 * сервер не хранит, поэтому это только набор номеров.
 */
class AccountWatchedEpisodes(
    private val remote: WatchProgressRemote,
    private val sessions: SessionStore,
) {
    /**
     * `null` — показывать нечего: без входа или у тайтла нет серверного ключа
     * (ряд AniLibria). Пустой набор — аккаунт есть, но по тайтлу ничего не
     * отмечено. Ошибку сети пробрасывает: что с ней делать, решает экран.
     */
    suspend fun forAnime(source: SourceId, animeId: String): Set<Int>? {
        if (source != SourceId.KODIK) return null
        val session = sessions.read() ?: return null
        return remote.get(session, animeId)?.episodes.orEmpty()
    }
}
