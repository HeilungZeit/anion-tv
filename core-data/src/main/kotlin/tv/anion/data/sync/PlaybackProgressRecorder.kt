package tv.anion.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.anion.data.repo.BookmarkRepository
import tv.anion.data.repo.BookmarkSeed
import tv.anion.data.repo.ProgressUpdate
import tv.anion.data.repo.WatchProgressRepository

/** Частые тики плеера схлопываются в Room, сетевой sync имеет отдельный долгий debounce. */
class PlaybackProgressRecorder(
    private val scope: CoroutineScope,
    private val progress: WatchProgressRepository,
    private val bookmarks: BookmarkRepository,
    private val sync: BookmarkSync,
    private val saveDebounceMs: Long = 2_000,
    private val syncDebounceMs: Long = 12_000,
) {
    private data class Pending(val update: ProgressUpdate, val seed: BookmarkSeed)
    private val pending = mutableMapOf<String, Pending>()
    private val saveJobs = mutableMapOf<String, Job>()
    private var syncJob: Job? = null

    fun begin(seed: BookmarkSeed) {
        scope.launch {
            // Иначе новый dirty с watched=0 может обогнать первый pull и затереть
            // уже существующий прогресс сайта.
            runCatching { sync.syncNow() }
            bookmarks.ensureWatching(seed)
            scheduleSync()
        }
    }

    fun record(update: ProgressUpdate, seed: BookmarkSeed) {
        val key = key(update)
        pending[key] = Pending(update, seed)
        saveJobs.remove(key)?.cancel()
        saveJobs[key] = scope.launch {
            delay(saveDebounceMs)
            flush(key)
        }
    }

    fun flush(update: ProgressUpdate, seed: BookmarkSeed) {
        val key = key(update)
        pending[key] = Pending(update, seed)
        saveJobs.remove(key)?.cancel()
        scope.launch { flush(key) }
    }

    private suspend fun flush(key: String) {
        saveJobs.remove(key)
        val value = pending.remove(key) ?: return
        val saved = progress.save(value.update)
        if (saved.finished) bookmarks.advanceWatched(value.seed, saved.episode)
        scheduleSync()
    }

    private fun scheduleSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(syncDebounceMs)
            runCatching { sync.syncNow() }
        }
    }

    private fun key(update: ProgressUpdate) = "${update.source}:${update.animeId}:${update.episode}"
}
