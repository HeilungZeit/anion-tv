package tv.anion.source.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/**
 * Один пул сокетов на всё приложение: у OkHttp он живёт в клиенте, и плодить
 * клиенты на боксе с 1.5 ГБ памяти незачем.
 */
object HttpClients {
    fun default(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
}

val AnionJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

class HttpException(val code: Int, val url: String, message: String) : Exception(message)

/**
 * Ответ Vercel Attack Challenge Mode приходит под кодом 429 и выглядит как
 * рейт-лимит бэка, хотя до бэка запрос не дошёл вовсе. Отличается по заголовку,
 * который ставит edge (так же это различает anion-dl).
 */
class VercelChallengeException(url: String) : Exception(
    "Запрос $url заблокирован защитой Vercel: нужен заголовок обхода в правиле WAF"
)

internal suspend fun OkHttpClient.getString(url: String, headers: Map<String, String>): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.header("x-vercel-mitigated") != null) throw VercelChallengeException(url)
            if (!response.isSuccessful) throw HttpException(response.code, url, "Запрос $url ответил ${response.code}")
            body
        }
    }

internal suspend fun OkHttpClient.postJson(
    url: String,
    json: String,
    headers: Map<String, String>,
): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url)
        .post(json.toRequestBody("application/json".toMediaType()))
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()

    newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (response.header("x-vercel-mitigated") != null) throw VercelChallengeException(url)
        if (!response.isSuccessful) throw HttpException(response.code, url, "Запрос $url ответил ${response.code}")
        body
    }
}
