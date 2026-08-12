package tv.anion.resolve

/**
 * Всё, что нужно передать эндпоинту, чтобы он отдал ссылки.
 *
 * Вопреки «поправке к разбору» в PLAN §2, голых `type/id/hash` эндпоинту мало:
 * без подписанных параметров домена он отвечает 500 с `Error code: rs`.
 * Проверено живьём в anion-dl (`src-tauri/src/kodik.rs`) и подтверждено здесь:
 * `GET /ftor?type=..&id=..&hash=..` отдаёт 404.
 */
data class PlayerParams(
    val domain: String,
    val domainSign: String,
    val playerDomain: String,
    val playerDomainSign: String,
    val referer: String,
    val refererSign: String,
    val kind: String,
    val id: String,
    val hash: String,
) {
    fun form(): List<Pair<String, String>> = listOf(
        "d" to domain,
        "d_sign" to domainSign,
        "pd" to playerDomain,
        "pd_sign" to playerDomainSign,
        "ref" to referer,
        "ref_sign" to refererSign,
        "type" to kind,
        "id" to id,
        "hash" to hash,
        // Оба флага плеер шлёт всегда. Без них ответ тот же, но лишнее отличие
        // от настоящего запроса ничем не окупается.
        "bad_user" to "true",
        "cdn_is_working" to "true",
    )

    companion object {
        fun parse(page: String): PlayerParams {
            // `vInfo` — то, что плеер реально отправляет; одноимённые `var` рядом
            // существуют, но у страницы сезона расходятся с ним: в адресе стоит
            // `season`, а серия внутри уже `seria` со своим id и хэшем.
            fun field(name: String): String? =
                JsLiterals.read(page, "vInfo.$name") ?: JsLiterals.read(page, "var $name")

            fun required(name: String, value: String?): String =
                value ?: throw ResolveException.MissingPlayerParam(name)

            return PlayerParams(
                domain = required("domain", JsLiterals.read(page, "var domain")),
                domainSign = required("d_sign", JsLiterals.read(page, "var d_sign")),
                playerDomain = required("pd", JsLiterals.read(page, "var pd")),
                playerDomainSign = required("pd_sign", JsLiterals.read(page, "var pd_sign")),
                // Именно `var ref`, а не одноимённое поле `urlParams`: там оно
                // хранится percent-кодированным, и подпись под него не подходит.
                referer = required("ref", JsLiterals.read(page, "var ref")),
                refererSign = required("ref_sign", JsLiterals.read(page, "var ref_sign")),
                kind = required("type", field("type")),
                id = required("id", field("id") ?: field("videoId")),
                hash = required("hash", field("hash")),
            )
        }
    }
}

/** Читает строковый литерал из разметки: `var ref = "…"`, `vInfo.hash = '…'`. */
internal object JsLiterals {
    fun read(page: String, binding: String): String? {
        var from = 0

        while (true) {
            val at = page.indexOf(binding, from)
            if (at < 0) return null

            val after = at + binding.length
            from = after

            val rest = page.substring(after).trimStart()
            if (!rest.startsWith('=')) continue

            val value = rest.substring(1).trimStart()
            val quote = value.firstOrNull() ?: continue
            if (quote != '"' && quote != '\'') continue

            val body = value.substring(1)
            val end = body.indexOf(quote)
            if (end >= 0) return body.substring(0, end)
        }
    }
}
