package tv.anion.resolve

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

object KodikHeaders {
    /**
     * Referer, без которого CDN не отдаёт ни манифест, ни сегменты.
     * Проверено в anion-dl: без него 403 на середине серии.
     */
    const val CDN_REFERER = "https://kodikplayer.com/"

    /**
     * UA настоящего браузера. UA вебвью Kodik тоже принимает, но привязываться
     * к версии WebView на конкретном боксе незачем.
     */
    const val BROWSER_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Заголовки для запросов к самому CDN (манифест, сегменты). */
    val cdn: Map<String, String> = mapOf("Referer" to CDN_REFERER, "User-Agent" to BROWSER_UA)
}

internal data class HttpResult(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

/** Блокирующий OkHttp, уведённый в IO — отдельного асинхронного клиента не заводим. */
internal suspend fun OkHttpClient.get(
    url: String,
    headers: Map<String, String> = KodikHeaders.cdn,
): HttpResult = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()

    newCall(request).execute().use { HttpResult(it.code, it.body?.string().orEmpty()) }
}

internal suspend fun OkHttpClient.postForm(
    url: String,
    form: List<Pair<String, String>>,
    headers: Map<String, String>,
): HttpResult = withContext(Dispatchers.IO) {
    val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
    val request = Request.Builder()
        .url(url)
        .post(body)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()

    newCall(request).execute().use { HttpResult(it.code, it.body?.string().orEmpty()) }
}

/** Origin без path. Нестандартный порт обязателен: иначе POST уходит на :80/:443. */
internal fun originOf(url: String): String {
    val httpUrl = url.toHttpUrlOrNull() ?: throw ResolveException.BadIframeUrl(url)
    val defaultPort = if (httpUrl.scheme == "https") 443 else 80
    return buildString {
        append(httpUrl.scheme).append("://").append(httpUrl.host)
        if (httpUrl.port != defaultPort) append(':').append(httpUrl.port)
    }
}
