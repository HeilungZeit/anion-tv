package tv.anion.data.sync

import android.os.Build

/**
 * Как приложение представляется бэкенду: по User-Agent его не отличить — OkHttp
 * шлёт «okhttp/4.12.0», а [tv.anion.source.kodik.AnionGoApi.CLIENT_VALUE] намеренно
 * общий с anion-dl. Бэкенд складывает это в список устройств пользователя.
 *
 * Значения должны быть стабильны между запусками: на сервере они образуют ключ
 * дедупликации, и «плавающее» имя плодило бы новые записи на каждый вход.
 */
data class ClientInfo(
    val platform: String,
    val os: String,
    val deviceName: String,
) {
    companion object {
        /** Без обращения к [Build] — годится для JVM-тестов и как запасной вариант. */
        val TV = ClientInfo(platform = "tv", os = "Android", deviceName = "Anion TV")

        /** Реальная приставка: производитель, модель и версия Android. */
        fun current(): ClientInfo {
            val model = "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()}".trim()

            return ClientInfo(
                platform = TV.platform,
                os = "Android ${Build.VERSION.RELEASE.orEmpty()}".trim(),
                deviceName = if (model.isEmpty()) TV.deviceName else "${TV.deviceName} · $model",
            )
        }
    }
}
