package tv.anion.source.kodik

import okhttp3.OkHttpClient
import tv.anion.source.http.getString
import tv.anion.source.http.postJson

/**
 * Свой бэкенд. Он же держит авторизацию и закладки — отсюда бесплатная
 * синхронизация с вебом (PLAN §3, Э5).
 *
 * Путь именно `/proxy/api`, а не `/api`: на Vercel `/api/…` — каталог
 * serverless-функций, там SSR-рендер. Прокси к anion-go живёт на `/proxy/api`
 * (anion/middleware.js), и тем же путём ходит сам сайт.
 */
interface AnionGoApi {
    suspend fun feed(): String
    suspend fun anime(id: String): String
    suspend fun search(query: String, limit: Int, offset: Int): String

    companion object {
        const val BASE_URL = "https://anion.online/proxy/api"

        /**
         * Метка клиента для правила обхода в Vercel WAF. Attack Challenge Mode
         * отдаёт JS-проверку под кодом 429, а нативный клиент выполнить её не
         * может — без заголовка запрос до бэка не доходит.
         *
         * Это не секрет: значение видно в исходниках и в APK. Оно отсекает
         * массовые сканеры, но не того, кто вскроет пакет.
         *
         * Значение то же, что у anion-dl, а не своё: правило обхода уже заведено
         * под него, а отдельная метка на клиента ничего не даёт — отличать
         * приложения по ней всё равно нельзя, подделывается тривиально.
         */
        const val CLIENT_HEADER = "X-Anion-Client"
        const val CLIENT_VALUE = "anion-dl"
    }
}

class HttpAnionGoApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AnionGoApi.BASE_URL,
    private val clientValue: String = AnionGoApi.CLIENT_VALUE,
) : AnionGoApi {

    private val headers
        get() = mapOf(
            AnionGoApi.CLIENT_HEADER to clientValue,
            "Accept" to "application/json",
        )

    override suspend fun feed(): String = http.getString("$baseUrl/anime/feed", headers)

    override suspend fun anime(id: String): String = http.getString("$baseUrl/anime/$id", headers)

    /** Поиск на бэке — POST с телом, а не query: так же ходит anion-dl. */
    override suspend fun search(query: String, limit: Int, offset: Int): String =
        http.postJson(
            url = "$baseUrl/anime/search",
            json = """{"search":${quote(query)},"limit":$limit,"offset":$offset}""",
            headers = headers + ("Content-Type" to "application/json"),
        )

    private fun quote(value: String) = buildString {
        append('"')
        for (c in value) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            else -> append(c)
        }
        append('"')
    }
}
