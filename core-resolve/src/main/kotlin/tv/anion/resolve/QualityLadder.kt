package tv.anion.resolve

import okhttp3.OkHttpClient

/**
 * Ключ качества в ответе эндпоинта доверия не заслуживает: у `links["720"]`
 * наблюдался URL с `480` в имени файла. Настоящее качество зашито в имя —
 * по нему и сравниваем, а до желаемого добираем подменой числа в URL.
 */
class QualityLadder(private val http: OkHttpClient) {

    /**
     * Подменяет качество в имени манифеста на лучшее доступное, не выше желаемого.
     *
     * Соседние качества лежат по тому же пути, но существуют не всегда:
     * 404 на 1080p при живых 720 и 480 — свойство CDN, а не плеера.
     * Любая неудача — возврат исходного URL: качество хуже ожидаемого лучше,
     * чем сорванный старт.
     */
    suspend fun upgrade(manifestUrl: String, preferred: Int): String {
        val split = splitQuality(manifestUrl) ?: return manifestUrl

        for (quality in LADDER.filter { it <= preferred }) {
            val candidate = split.prefix + quality + split.suffix
            if (isPlaylist(candidate)) return candidate
        }

        return manifestUrl
    }

    /**
     * Кода 200 мало: заглушка CDN приходит с ним же, и плеер получил бы HTML
     * вместо манифеста — а выглядело бы это как «поток битый».
     */
    internal suspend fun isPlaylist(url: String): Boolean = try {
        val result = http.get(url, KodikHeaders.cdn)
        result.isSuccess && result.body.trimStart().startsWith("#EXTM3U")
    } catch (_: Exception) {
        false
    }

    companion object {
        /** Качества, которые вообще встречаются у Kodik, от лучшего к худшему. */
        val LADDER = listOf(1080, 720, 480, 360)

        private const val MARKER = ".mp4:hls:"

        data class Split(val prefix: String, val suffix: String)

        /** Разрезает URL вокруг числа качества: `…/` + `360` + `.mp4:hls:manifest.m3u8`. */
        fun splitQuality(manifest: String): Split? {
            val markerAt = manifest.indexOf(MARKER).takeIf { it >= 0 } ?: return null

            val digitsStart = manifest.substring(0, markerAt)
                .indexOfLast { !it.isDigit() }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: return null

            if (digitsStart == markerAt) return null

            return Split(manifest.substring(0, digitsStart), manifest.substring(markerAt))
        }

        /**
         * Качество, зашитое в имя манифеста. У Kodik это единственный честный
         * источник: ключи в ответе эндпоинта с содержимым расходятся.
         */
        fun qualityOf(manifest: String): Int? {
            val split = splitQuality(manifest) ?: return null
            return manifest
                .substring(split.prefix.length, manifest.length - split.suffix.length)
                .toIntOrNull()
        }
    }
}
