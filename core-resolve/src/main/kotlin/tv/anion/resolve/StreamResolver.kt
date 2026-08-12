package tv.anion.resolve

/**
 * Резолв iframe-URL плеера в играбельный HLS-манифест.
 *
 * Две реализации, взаимозаменяемые одной строкой в DI (PLAN §4, §7):
 * [LocalKodikResolver] — HTTP прямо в приложении, дефолт;
 * [RemoteResolver] — эндпоинт в anion-go, если он появится.
 */
interface StreamResolver {
    suspend fun resolve(iframeUrl: String, preferredQuality: Int = 720): ResolvedStream
}

/** Результат резолва: URL манифеста + заголовки, без которых CDN отдаёт 403. */
data class ResolvedStream(
    val manifestUrl: String,
    val quality: Int?,
    val headers: Map<String, String>,
)

sealed class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class BadIframeUrl(url: String) : ResolveException("некорректный URL плеера: $url")
    class PageUnavailable(reason: String) : ResolveException("страница плеера недоступна: $reason")
    class MissingPlayerParam(name: String) : ResolveException("в разметке плеера нет $name")
    class EndpointRejected(endpoint: String, code: Int, errorCode: String?) :
        ResolveException("$endpoint ответил $code" + (errorCode?.let { " (код $it)" } ?: ""))
    class EndpointNotFound(previous: String) :
        ResolveException("$previous. Другого эндпоинта в скрипте плеера нет")
    class NoDecodableSrc : ResolveException("ни одна ссылка плеера не расшифровалась")
}
