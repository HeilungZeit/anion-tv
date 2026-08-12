package tv.anion.resolve

import java.util.Base64

/**
 * `src` из ответа эндпоинта — base64, до которого латинские буквы сдвинуты по
 * кольцу. Меняется не шифр, а адрес эндпоинта: сдвиг 18 держится годами (той же
 * схемой живёт kodikwrapper). Поэтому 18 пробуется первым, остальные — следом:
 * цена перебора нулевая, а смена сдвига перестаёт быть отказом (PLAN §2).
 */
object SrcDecoder {

    private const val DEFAULT_SHIFT = 18
    private const val ALPHABET_SIZE = 26

    fun decodeOrNull(src: String): String? =
        (sequenceOf(DEFAULT_SHIFT) + (0 until ALPHABET_SIZE).asSequence())
            .mapNotNull { decodeWithShift(src, it) }
            .firstOrNull()

    internal fun decodeWithShift(src: String, shift: Int): String? {
        val rotated = rotate(src, shift)
        val padded = rotated + "=".repeat((4 - rotated.length % 4) % 4)

        val text = try {
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val url = if (text.startsWith("//")) "https:$text" else text

        // Проверка обязательна: неверный сдвиг иногда даёт валидный base64 и даже
        // валидный UTF-8, и без неё вернулся бы мусор вместо манифеста.
        return url.takeIf { it.startsWith("https://") && it.contains(".m3u8") }
    }

    /** Сдвиг латиницы по кольцу; регистр сохраняется, прочие символы не трогаются. */
    internal fun rotate(input: String, shift: Int): String {
        if (shift % ALPHABET_SIZE == 0) return input
        return buildString(input.length) {
            for (c in input) {
                append(
                    when (c) {
                        in 'a'..'z' -> 'a' + (c - 'a' + shift) % ALPHABET_SIZE
                        in 'A'..'Z' -> 'A' + (c - 'A' + shift) % ALPHABET_SIZE
                        else -> c
                    }
                )
            }
        }
    }
}
