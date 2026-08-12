package tv.anion.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import tv.anion.tv.di.LocalAppContainer
import tv.anion.tv.nav.AnionNavHost
import tv.anion.tv.ui.theme.AnionTvTheme
import tv.anion.tv.ui.theme.AnionBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AnionTvApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AnionTvTheme {
                    AnionBackground { AnionNavHost() }
                }
            }
        }
    }
}
