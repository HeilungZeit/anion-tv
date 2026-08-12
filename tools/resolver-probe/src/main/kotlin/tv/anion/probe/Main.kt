package tv.anion.probe

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.anion.resolve.LocalKodikResolver
import tv.anion.source.AnimeSource
import tv.anion.source.anilibria.AniLibriaSource
import tv.anion.source.anilibria.HttpAniLibriaApi
import tv.anion.source.http.HttpClients
import tv.anion.source.kodik.HttpAnionGoApi
import tv.anion.source.kodik.KodikSource
import tv.anion.source.model.PlayableStream

/**
 * Пробник Э0/Э1: живая проверка резолвера и источников без Android и UI.
 *
 *   ./gradlew :tools:resolver-probe:run --args="resolve <iframe-url> [качество]"
 *   ./gradlew :tools:resolver-probe:run --args="anilibria <alias>"
 *   ./gradlew :tools:resolver-probe:run --args="kodik <animeId>"
 *
 * Успех — не «пришёл JSON», а играющий поток: каждая команда заканчивается
 * скачиванием манифеста и проверкой, что он начинается с #EXTM3U.
 */
fun main(args: Array<String>) = runBlocking {
    val http = HttpClients.default()

    when (args.firstOrNull()) {
        "resolve" -> resolve(http, args.getOrNull(1), args.getOrNull(2)?.toIntOrNull() ?: 720)
        "anilibria" -> browse(http, AniLibriaSource(HttpAniLibriaApi(http)), args.getOrNull(1) ?: "grand-blue-season-3")
        "kodik" -> browse(http, KodikSource(HttpAnionGoApi(http), LocalKodikResolver(http)), args.getOrNull(1) ?: "15")
        else -> System.err.println("команды: resolve <iframe-url> | anilibria <alias> | kodik <animeId>")
    }
}

private suspend fun resolve(http: OkHttpClient, iframeUrl: String?, quality: Int) {
    if (iframeUrl == null) {
        System.err.println("нужен iframe-URL плеера")
        return
    }

    val stream = LocalKodikResolver(http).resolve(iframeUrl, quality)
    println("качество: ${stream.quality}")
    println(stream.manifestUrl)
    verify(http, stream.manifestUrl, stream.headers)
}

private suspend fun browse(http: OkHttpClient, source: AnimeSource, animeId: String) {
    println("=== ${source.displayName} / $animeId ===")

    val details = source.details(animeId)
    println("тайтл:    ${details.anime.title} (${details.anime.year})")
    println("жанры:    ${details.genres.joinToString().ifBlank { "—" }}")
    println("озвучки:  ${details.translations.joinToString { it.title }}")
    println("блокировки: гео=${details.blocked.byGeo} копирайт=${details.blocked.byCopyright}")

    val episodes = source.episodes(animeId, details.translations.firstOrNull()?.id)
    println("серий:    ${episodes.size}")

    val episode = episodes.firstOrNull() ?: run {
        System.err.println("серий нет — дальше проверять нечего")
        return
    }

    println("серия ${episode.number}: длительность=${episode.durationSeconds ?: "?"} " +
        "опенинг=${episode.skips.opening?.startSeconds ?: "—"}")

    val stream: PlayableStream = source.stream(episode, preferredQuality = 720)
    println("поток ${stream.quality}p: ${stream.url}")
    verify(http, stream.url, stream.headers)
}

/** Критерий готовности: манифест скачивается и это действительно плейлист. */
private fun verify(http: OkHttpClient, url: String, headers: Map<String, String>) {
    val request = Request.Builder().url(url)
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()

    http.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        val ok = response.isSuccessful && body.trimStart().startsWith("#EXTM3U")
        println(if (ok) "OK: HTTP ${response.code}, ${body.length} байт, #EXTM3U" else "НЕ ПЛЕЙЛИСТ: HTTP ${response.code}")
    }
}
