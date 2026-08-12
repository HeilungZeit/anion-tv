package tv.anion.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import tv.anion.resolve.ResolvedStream

/**
 * Referer обязан уходить и на манифест, и на каждый сегмент: без него CDN
 * отвечает 403 на середине серии (PLAN §2). Заголовки берутся из резолва,
 * а не хардкодятся — у AniLibria свой набор, у Kodik — `KodikHeaders.cdn`.
 */
object StreamDataSourceFactory {

    fun create(okHttp: OkHttpClient, headers: Map<String, String>): DataSource.Factory =
        OkHttpDataSource.Factory(okHttp).setDefaultRequestProperties(headers)

    fun create(okHttp: OkHttpClient, stream: ResolvedStream): DataSource.Factory =
        create(okHttp, stream.headers)
}
