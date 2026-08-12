package tv.anion.resolve

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/**
 * Резолв манифеста Kodik обычными HTTP-запросами, без вебвью.
 *
 * Схема перенесена из anion-dl (`src-tauri/src/kodik.rs`), где она проверена
 * живьём, а не выведена из чужого кода:
 *
 * 1. GET страницы плеера с Referer сайта-владельца вставки. В разметке лежат
 *    `type`/`id`/`hash` серии и подписанные параметры домена.
 * 2. POST на эндпоинт этими параметрами → JSON со ссылками по качествам.
 * 3. Каждый `src` расшифровывается сдвигом и base64 в готовый m3u8.
 *
 * Одним GET это не делается: без подписей эндпоинт отвечает 500 `Error code: rs`,
 * а `GET /ftor?type=..&id=..&hash=..` — 404. Расхождение с «поправкой» в
 * PLAN §2 разрешено в пользу боевого кода.
 */
class LocalKodikResolver(
    private val http: OkHttpClient,
    /**
     * Referer страницы плеера. Kodik выдаёт подписи именно под домен-владелец
     * вставки, и он же приезжает обратно в поле `d`.
     */
    private val siteReferer: String = DEFAULT_SITE_REFERER,
    private val ladder: QualityLadder = QualityLadder(http),
    private val endpointCache: EndpointCache = EndpointCache.InMemory(),
) : StreamResolver {

    override suspend fun resolve(iframeUrl: String, preferredQuality: Int): ResolvedStream {
        val origin = originOf(iframeUrl)
        val page = fetchPage(iframeUrl)
        val params = PlayerParams.parse(page)

        val known = endpointCache.get() ?: (origin + KNOWN_ENDPOINT)

        val payload = try {
            requestLinks(known, origin, iframeUrl, params)
        } catch (first: ResolveException.EndpointRejected) {
            val discovered = discoverEndpoint(page, origin)?.takeIf { it != known }
                ?: throw ResolveException.EndpointNotFound(first.message.orEmpty())

            requestLinks(discovered, origin, iframeUrl, params)
                .also { endpointCache.put(discovered) }
        }

        val best = bestLink(payload) ?: throw ResolveException.NoDecodableSrc()
        val upgraded = ladder.upgrade(best, preferredQuality)

        return ResolvedStream(
            manifestUrl = upgraded,
            quality = QualityLadder.qualityOf(upgraded),
            headers = KodikHeaders.cdn,
        )
    }

    private suspend fun fetchPage(iframeUrl: String): String {
        val result = http.get(
            iframeUrl,
            mapOf("Referer" to siteReferer, "User-Agent" to KodikHeaders.BROWSER_UA),
        )

        if (!result.isSuccess) throw ResolveException.PageUnavailable("ответ ${result.code}")
        return result.body
    }

    private suspend fun requestLinks(
        endpoint: String,
        origin: String,
        pageUrl: String,
        params: PlayerParams,
    ): JsonObject {
        val result = http.postForm(
            url = endpoint,
            form = params.form(),
            headers = mapOf(
                "Referer" to pageUrl,
                "Origin" to origin,
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to KodikHeaders.BROWSER_UA,
            ),
        )

        if (!result.isSuccess) {
            // Тело здесь — HTML-заглушка Kodik, и в ней бывает код вида
            // `Error code: rs`. Он единственная зацепка, когда параметры приняты,
            // но не подошли.
            val code = result.body.substringAfter("Error code: ", "")
                .substringBefore('<', "")
                .takeIf { it.isNotBlank() }
            throw ResolveException.EndpointRejected(endpoint, result.code, code)
        }

        return runCatching { JSON.parseToJsonElement(result.body).jsonObject }
            .getOrElse { throw ResolveException.EndpointRejected(endpoint, result.code, "не JSON") }
    }

    /**
     * Ищет актуальный адрес выдачи в скрипте плеера.
     *
     * Kodik держит его в `atob("…")` и меняет время от времени (`/ftor` → `/kor`
     * → …). Скрипт весит полтораста килобайт, поэтому качается только когда
     * известный адрес уже отказал.
     */
    private suspend fun discoverEndpoint(page: String, origin: String): String? {
        val script = findScript(page, "app.player_single") ?: return null
        val body = runCatching { http.get(origin + script, KodikHeaders.cdn) }
            .getOrNull()
            ?.takeIf { it.isSuccess }
            ?.body
            ?: return null

        return ATOB.findAll(body)
            .mapNotNull { match ->
                runCatching {
                    String(java.util.Base64.getDecoder().decode(match.groupValues[1]), Charsets.UTF_8)
                }.getOrNull()
            }
            .firstOrNull(::isEndpointPath)
            ?.let { origin + it }
    }

    private fun bestLink(payload: JsonObject): String? =
        (payload["links"] as? JsonObject)
            ?.values
            ?.filterIsInstance<JsonArray>()
            ?.flatten()
            ?.mapNotNull { it.jsonObject["src"]?.jsonPrimitive?.content }
            ?.mapNotNull(SrcDecoder::decodeOrNull)
            ?.maxByOrNull { QualityLadder.qualityOf(it) ?: 0 }

    companion object {
        /** Известный адрес выдачи ссылок. Пробуется первым. */
        const val KNOWN_ENDPOINT = "/ftor"

        /** Домен, под который Kodik подписывает параметры для наших вставок. */
        const val DEFAULT_SITE_REFERER = "https://anion.online/"

        private val JSON = Json { ignoreUnknownKeys = true }
        private val ATOB = Regex("""atob\("([A-Za-z0-9+/=]+)"\)""")

        /**
         * Похоже ли расшифрованное на адрес выдачи ссылок.
         *
         * Эндпоинт — короткий абсолютный путь из одного сегмента (`/ftor`, `/kor`).
         * Соседние `atob` в том же файле прячут домен CDN и адрес статистики —
         * точка и второй слэш отсеивают оба.
         */
        internal fun isEndpointPath(text: String): Boolean =
            text.length in 2..31 && text.startsWith('/') &&
                text.drop(1).none { it == '/' || it == '.' }

        /** Путь к скрипту плеера в разметке. */
        internal fun findScript(page: String, name: String): String? {
            val at = page.indexOf(name).takeIf { it >= 0 } ?: return null
            val start = page.lastIndexOf('"', at).takeIf { it >= 0 }?.plus(1) ?: return null
            val end = page.indexOf('"', at).takeIf { it >= 0 } ?: return null
            return page.substring(start, end)
        }
    }
}

interface EndpointCache {
    fun get(): String?
    fun put(endpoint: String)

    class InMemory : EndpointCache {
        @Volatile private var value: String? = null
        override fun get() = value
        override fun put(endpoint: String) { value = endpoint }
    }
}
