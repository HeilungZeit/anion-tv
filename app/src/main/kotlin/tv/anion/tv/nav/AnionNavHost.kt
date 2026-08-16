package tv.anion.tv.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import tv.anion.tv.ui.catalog.CatalogScreen
import tv.anion.tv.ui.account.AccountScreen
import tv.anion.tv.ui.bookmarks.BookmarksScreen
import tv.anion.tv.ui.details.DetailsScreen
import tv.anion.tv.ui.home.HomeScreen
import tv.anion.tv.ui.player.PlayerScreen
import tv.anion.tv.ui.search.SearchScreen

/**
 * Экраны: Home → Catalog → Details → Player, плюс Search.
 * Свой стек, без navigation-compose: на пяти экранах библиотека не окупается,
 * а фокус при возврате восстанавливается ViewModel'ами, которые живут в Activity.
 */
sealed interface Route {
    data object Home : Route
    data object Catalog : Route
    data object Search : Route
    data object Account : Route
    data object Bookmarks : Route
    data class Details(val source: String, val animeId: String) : Route
    data class Player(
        val source: String,
        val animeId: String,
        val episode: Int,
        val translationId: String?,
    ) : Route
}

class Navigator {
    private val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    /** Замена текущего экрана: переход к соседней серии не должен копить стек. */
    fun replace(route: Route) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop(): Boolean {
        if (!canPop) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

@Composable
fun AnionNavHost() {
    val navigator = remember { Navigator() }
    BackHandler(enabled = navigator.canPop) { navigator.pop() }

    // Общей рамки у экранов нет: каждый рисует во весь экран и сам отмеряет
    // отступы от края через ScreenGutter. Рельс навигации и ряды карточек
    // обязаны доходить до самого края, а хост про это ничего не знает.
    when (val route = navigator.current) {
        Route.Home -> HomeScreen(
            onOpenCatalog = { navigator.push(Route.Catalog) },
            onOpenSearch = { navigator.push(Route.Search) },
            onOpenBookmarks = { navigator.push(Route.Bookmarks) },
            onOpenAccount = { navigator.push(Route.Account) },
            onOpenAnime = { source, id -> navigator.push(Route.Details(source, id)) },
        )
        Route.Catalog -> CatalogScreen(
            onOpenAnime = { source, id -> navigator.push(Route.Details(source, id)) },
        )
        Route.Search -> SearchScreen(
            onOpenAnime = { source, id -> navigator.push(Route.Details(source, id)) },
        )
        Route.Bookmarks -> BookmarksScreen(
            onOpenAnime = { source, id -> navigator.push(Route.Details(source, id)) },
        )
        Route.Account -> AccountScreen()
        is Route.Details -> DetailsScreen(
            source = route.source,
            animeId = route.animeId,
            onPlay = { episode, translationId ->
                navigator.push(
                    Route.Player(route.source, route.animeId, episode, translationId),
                )
            },
        )
        is Route.Player -> PlayerScreen(
            source = route.source,
            animeId = route.animeId,
            episode = route.episode,
            translationId = route.translationId,
            onBack = { navigator.pop() },
            onPlayEpisode = { next ->
                navigator.replace(
                    Route.Player(route.source, route.animeId, next, route.translationId),
                )
            },
        )
    }
}
