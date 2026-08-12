package tv.anion.source.anilibria

import okhttp3.OkHttpClient
import tv.anion.source.http.getString

/** Тонкий клиент: OkHttp + kotlinx.serialization, без retrofit. */
interface AniLibriaApi {
    suspend fun catalog(page: Int, limit: Int): String
    suspend fun release(alias: String): String
    suspend fun search(query: String): String

    companion object {
        const val BASE_URL = "https://anilibria.top/api/v1"
        const val PAGE_SIZE = 30
    }
}

class HttpAniLibriaApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AniLibriaApi.BASE_URL,
) : AniLibriaApi {

    override suspend fun catalog(page: Int, limit: Int): String =
        http.getString("$baseUrl/anime/catalog/releases?page=$page&limit=$limit", HEADERS)

    override suspend fun release(alias: String): String =
        http.getString("$baseUrl/anime/releases/$alias", HEADERS)

    override suspend fun search(query: String): String =
        http.getString("$baseUrl/app/search/releases?query=${encode(query)}", HEADERS)

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        val HEADERS = mapOf("Accept" to "application/json")
    }
}
