package tv.anion.player

import android.view.KeyEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteKeyMapTest {

    @Test
    fun `вверх открывает панель, вниз убирает`() {
        assertEquals(PlayerCommand.ShowPanel, RemoteKeyMap.map(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PlayerCommand.HidePanel, RemoteKeyMap.map(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `стрелки влево-вправо остаются за перемоткой`() {
        assertEquals(PlayerCommand.SeekBy(10_000), RemoteKeyMap.map(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(PlayerCommand.SeekBy(-10_000), RemoteKeyMap.map(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun `долгое нажатие мотает крупнее`() {
        assertEquals(PlayerCommand.SeekBy(30_000), RemoteKeyMap.map(KeyEvent.KEYCODE_DPAD_RIGHT, isLongPress = true))
    }

    @Test
    fun `серии переключаются и медийными кнопками, и каналами`() {
        assertEquals(PlayerCommand.NextEpisode, RemoteKeyMap.map(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(PlayerCommand.NextEpisode, RemoteKeyMap.map(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(PlayerCommand.PreviousEpisode, RemoteKeyMap.map(KeyEvent.KEYCODE_CHANNEL_DOWN))
    }

    @Test
    fun `незнакомая кнопка не перехватывается`() {
        assertNull(RemoteKeyMap.map(KeyEvent.KEYCODE_A))
    }
}
