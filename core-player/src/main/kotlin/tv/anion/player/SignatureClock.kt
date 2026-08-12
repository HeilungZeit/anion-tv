package tv.anion.player

import java.util.Calendar
import java.util.TimeZone

/**
 * Подпись в URL Kodik живёт до часа, зашитого в путь: `…:2026081307/…`
 * (PLAN §6). Ломается не старт, а середина длинной серии — поэтому плеер
 * обновляет поток заранее, а не ждёт 403.
 */
internal object SignatureClock {

    const val LEAD_MS = 5 * 60 * 1000L

    private val STAMP = Regex(""":(\d{10})(?:/|$)""")
    private val UTC = TimeZone.getTimeZone("UTC")

    fun expiryEpochMs(manifestUrl: String): Long? {
        val digits = STAMP.find(manifestUrl)?.groupValues?.get(1) ?: return null
        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.substring(8, 10).toIntOrNull() ?: return null
        val cal = Calendar.getInstance(UTC)
        cal.clear()
        cal.set(year, month - 1, day, hour, 0, 0)
        return cal.timeInMillis
    }

    fun shouldRefreshAhead(
        manifestUrl: String,
        nowMs: Long = System.currentTimeMillis(),
        leadMs: Long = LEAD_MS,
    ): Boolean {
        val expiryMs = expiryEpochMs(manifestUrl) ?: return false
        return nowMs >= expiryMs - leadMs
    }
}
