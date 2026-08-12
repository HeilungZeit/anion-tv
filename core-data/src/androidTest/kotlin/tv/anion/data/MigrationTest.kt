package tv.anion.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.anion.data.db.AnionDatabase

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AnionDatabase::class.java,
    )

    @Test fun `схема версии 1 создаётся из экспортированного json`() {
        helper.createDatabase("migration-test", 1).close()
    }
}
