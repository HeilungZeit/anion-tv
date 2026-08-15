package tv.anion.tv.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tv.anion.data.db.AnionDatabase
import tv.anion.data.repo.CachedAnimeSource
import tv.anion.data.repo.RoomBookmarkRepository
import tv.anion.data.repo.RoomSearchHistoryRepository
import tv.anion.data.repo.RoomWatchProgressRepository
import tv.anion.data.sync.DefaultAccountRepository
import tv.anion.data.sync.DefaultBookmarkSync
import tv.anion.data.sync.HttpBookmarkRemote
import tv.anion.data.sync.PlaybackProgressRecorder
import tv.anion.data.sync.PreferencesSessionStore
import tv.anion.data.sync.PreferencesSyncStateStore
import tv.anion.player.ExoPlaybackController
import tv.anion.player.PlaybackController
import tv.anion.player.PlaybackProgressListener
import tv.anion.resolve.LocalKodikResolver
import tv.anion.resolve.StreamResolver
import tv.anion.source.DefaultSourceRegistry
import tv.anion.source.SourceRegistry
import tv.anion.source.anilibria.AniLibriaSource
import tv.anion.source.anilibria.HttpAniLibriaApi
import tv.anion.source.http.HttpClients
import tv.anion.source.kodik.HttpAnionGoApi
import tv.anion.source.kodik.KodikSource

/**
 * Ручная сборка графа вместо Hilt. На пять модулей кодогенератор даёт меньше,
 * чем стоит его совместимость с встроенным Kotlin в AGP 9.
 *
 * Единственная строка, которой [LocalKodikResolver] меняется на RemoteResolver
 * (PLAN §7) — поле [resolver].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val http: OkHttpClient = HttpClients.default()
    private val database = AnionDatabase.open(appContext)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val watchProgress = RoomWatchProgressRepository(database.watchProgress())
    val bookmarks = RoomBookmarkRepository(database.bookmarks())
    val searchHistory = RoomSearchHistoryRepository(database.searchHistory())
    private val sessions = PreferencesSessionStore(appContext)
    private val bookmarkRemote = HttpBookmarkRemote(http)
    val bookmarkSync = DefaultBookmarkSync(
        bookmarks, bookmarkRemote, sessions, watchProgress, PreferencesSyncStateStore(appContext),
    )
    val account = DefaultAccountRepository(bookmarkRemote, sessions)
    val progressRecorder = PlaybackProgressRecorder(
        applicationScope, watchProgress, bookmarks, bookmarkSync,
    )

    init {
        syncIfStale()
    }

    private var lastSyncAt = 0L

    /**
     * Синхронизация при каждом появлении приложения на экране, а не только при
     * холодном старте: контейнер живёт вместе с процессом, и возврат из фона
     * его `init` не вызывает — закладки, поставленные на сайте, приезжали бы
     * только после перезапуска.
     *
     * Порог нужен, чтобы быстрый выход-возврат не устраивал шторм запросов.
     */
    fun syncIfStale() {
        if (!account.signedIn.value) return
        val now = System.currentTimeMillis()
        if (now - lastSyncAt < MIN_SYNC_INTERVAL_MS) return
        lastSyncAt = now
        applicationScope.launch { runCatching { bookmarkSync.syncNow() } }
    }

    val resolver: StreamResolver = LocalKodikResolver(http)
    private val anilibria = AniLibriaSource(HttpAniLibriaApi(http))

    /**
     * Kodik первым: он даёт основной каталог. AniLibria обязана быть в реестре
     * отдельно, а не только делегатом внутри KodikSource: серии её озвучки
     * уходят в прогресс с `source = ANILIBRIA`, и «продолжить смотреть» ищет их
     * через `byId(ANILIBRIA)`. Без регистрации карточка открывалась с ошибкой
     * «источник ANILIBRIA не подключён».
     */
    val sources: SourceRegistry = DefaultSourceRegistry(
        listOf(
            CachedAnimeSource(
                KodikSource(HttpAnionGoApi(http), resolver, anilibria),
                database.catalogCache(),
            ),
            CachedAnimeSource(anilibria, database.catalogCache()),
        ),
    )

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .okHttpClient(http)
        .memoryCache {
            // 1.5 ГБ на боксе: постеры не должны съесть плеер (PLAN §6).
            MemoryCache.Builder(appContext).maxSizePercent(0.12).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(appContext.cacheDir.resolve("coil"))
                .maxSizeBytes(40L * 1024 * 1024)
                .build()
        }
        .build()

    fun createPlayer(progress: PlaybackProgressListener? = null): PlaybackController =
        ExoPlaybackController(appContext, http, resolver, progress)
}

/** Минимальный интервал между автоматическими синхронизациями. */
private const val MIN_SYNC_INTERVAL_MS = 60_000L

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer не предоставлен")
}
