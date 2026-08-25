package tv.anion.data

import okhttp3.Request
import org.junit.Test
import tv.anion.data.sync.ClientInfo
import kotlin.test.assertEquals

/**
 * Имя устройства уходит в HTTP-заголовок, а собирается из полей [android.os.Build],
 * которые вендор волен заполнить чем угодно. OkHttp на не-ASCII роняет весь запрос —
 * вход на Haier падал с «Unexpected char 0xb7 at 9 in X-Device-Name value».
 */
class ClientInfoTest {
    @Test fun `имя устройства не ломает заголовок`() {
        val info = ClientInfo.of(manufacturer = "Haier", model = "Haier Android TV PRO", release = "11")

        // Именно эта строка раньше не проходила проверку OkHttp.
        Request.Builder().url("https://example.com").header("X-Device-Name", info.deviceName).build()
        assertEquals("Anion TV - Haier Android TV PRO", info.deviceName)
        assertEquals("Android 11", info.os)
    }

    @Test fun `не-ASCII из прошивки вычищается, остаток сохраняется`() {
        val info = ClientInfo.of(manufacturer = "Яндекс", model = "Модуль·2", release = "9")

        Request.Builder().url("https://example.com").header("X-Device-Name", info.deviceName).build()
        assertEquals("Anion TV - 2", info.deviceName)
    }

    @Test fun `имя целиком из не-ASCII откатывается к запасному`() {
        val info = ClientInfo.of(manufacturer = "Яндекс", model = "Модуль", release = "9")

        assertEquals(ClientInfo.TV.deviceName, info.deviceName)
    }

    @Test fun `эмодзи из двух char-ов не оставляет половину суррогата`() {
        val info = ClientInfo.of(manufacturer = "Nebula", model = "Box\uD83D\uDCFA 4K", release = "12")

        Request.Builder().url("https://example.com").header("X-Device-Name", info.deviceName).build()
        assertEquals("Anion TV - Nebula Box 4K", info.deviceName)
    }

    @Test fun `пустой Build откатывается к запасному имени`() {
        val info = ClientInfo.of(manufacturer = "", model = "", release = "")

        assertEquals(ClientInfo.TV.deviceName, info.deviceName)
        assertEquals("Android", info.os)
    }
}
