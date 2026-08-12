package tv.anion.resolve

import okhttp3.OkHttpClient

/**
 * Резолв на стороне anion-go (PLAN §7, решение отложено).
 * GET {baseUrl}/api/anime/:id/stream?videoId=..&quality=..
 */
class RemoteResolver(
    private val http: OkHttpClient,
    private val baseUrl: String,
) : StreamResolver {
    override suspend fun resolve(iframeUrl: String, preferredQuality: Int): ResolvedStream = TODO("Э7")
}
