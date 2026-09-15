package tv.anion.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.anion.source.kodik.AnionGoApi

/** Разбор ответов anion-go: DTO зеркалят Go-структуры и не падают на новых полях. */
internal val AnionGoJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/**
 * Метод перечислением, а не строкой. У билдера OkHttp метод по умолчанию — GET,
 * и строка без своей ветки в `when` молча уходила не тем запросом: так DELETE
 * закладки отправлялся как GET, и удалённая на ТВ закладка на сайте оставалась.
 * Исчерпывающий `when` по перечислению такое не пропустит.
 */
internal enum class HttpMethod { GET, POST, PUT, DELETE }

/**
 * Запрос к anion-go от имени пользователя: заголовки клиента, кука сессии и
 * разбор ошибки в [ApiException]. Общий для закладок и прогресса просмотра.
 */
internal class AnionGoHttp(
    private val http: OkHttpClient,
    private val clientInfo: ClientInfo,
) {
    /** Тело ответа; `null`, если сервер ответил без тела — например, 204. */
    suspend fun request(url: String, method: HttpMethod, body: String?, sessionId: String?): String? =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
                .header(AnionGoApi.CLIENT_HEADER, AnionGoApi.CLIENT_VALUE)
                .header("Accept", "application/json")
                .header("X-Client-Platform", clientInfo.platform)
                .header("X-Client-OS", clientInfo.os)
                .header("X-Device-Name", clientInfo.deviceName)
            if (sessionId != null) builder.header("Cookie", "X-Session-ID=$sessionId")
            val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
            when (method) {
                HttpMethod.GET -> builder.get()
                HttpMethod.POST -> builder.post(requireNotNull(requestBody) { "POST без тела" })
                HttpMethod.PUT -> builder.put(requireNotNull(requestBody) { "PUT без тела" })
                HttpMethod.DELETE -> builder.delete(requestBody)
            }
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching {
                        AnionGoJson.parseToJsonElement(text) as? JsonObject
                    }.getOrNull()
                    val field = { name: String -> error?.get(name)?.toString()?.trim('"') }
                    throw ApiException(
                        code = field("code"),
                        message = field("message") ?: "сервер ответил ${response.code}",
                    )
                }
                // 204 приходит без тела: декодировать пустую строку значило бы упасть.
                text.ifEmpty { null }
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
