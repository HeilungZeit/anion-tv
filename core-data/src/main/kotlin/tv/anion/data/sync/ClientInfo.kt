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
        fun current(): ClientInfo = of(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            release = Build.VERSION.RELEASE.orEmpty(),
        )

        /**
         * Отдельно от [current], чтобы правила сборки имени проверялись тестами:
         * [Build] в JVM-тестах отдаёт null-поля.
         */
        fun of(manufacturer: String, model: String, release: String): ClientInfo {
            val raw = when {
                model.isBlank() -> manufacturer.trim()
                // Haier рапортует MODEL как «Haier Android TV PRO» — иначе выйдет «Haier Haier …».
                model.trim().startsWith(manufacturer.trim(), ignoreCase = true) -> model.trim()
                else -> "${manufacturer.trim()} ${model.trim()}".trim()
            }

            // Чистим до склейки: у прошивки с целиком кириллическим именем от него
            // ничего не остаётся, и осмысленнее отдать «Anion TV», чем «Anion TV -».
            val device = headerSafe(raw, fallback = "")

            return ClientInfo(
                platform = TV.platform,
                os = headerSafe("Android $release".trim(), fallback = TV.os),
                deviceName = if (device.isEmpty()) TV.deviceName else "${TV.deviceName} - $device",
            )
        }

        /**
         * OkHttp роняет запрос на любом байте вне US-ASCII в значении заголовка
         * («Unexpected char 0xb7 …»), а [Build] волен вернуть что угодно — от
         * типографской пунктуации до кириллицы в прошивках вендоров. Поэтому
         * значение чистится здесь, до отправки, а не на месте использования.
         */
        private fun headerSafe(value: String, fallback: String): String {
            val cleaned = value.map { if (it.code in 0x20..0x7e) it else ' ' }
                .joinToString("")
                .replace(Regex(" {2,}"), " ")
                .trim()

            return cleaned.ifEmpty { fallback }
        }
    }
}
