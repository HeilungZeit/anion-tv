package tv.anion.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tv.anion.data.db.WatchProgressEntity
import tv.anion.data.db.WatchProgressStore
import tv.anion.source.SourceId
import tv.anion.source.model.Anime

data class WatchProgress(
    val source: SourceId,
    val animeId: String,
    val episode: Int,
    val translationId: String?,
    val title: String,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
    val syncedAt: Long?,
) {
    fun asAnime() = Anime(animeId, source, title, null, null, thumbnailUrl, thumbnailUrl)
}

data class ProgressUpdate(
    val source: SourceId,
    val animeId: String,
    val episode: Int,
    val translationId: String?,
    val title: String,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
)

interface WatchProgressRepository {
    fun observeContinueWatching(): Flow<List<WatchProgress>>
    fun observeAnime(source: SourceId, animeId: String): Flow<List<WatchProgress>>
    suspend fun get(source: SourceId, animeId: String, episode: Int): WatchProgress?
    suspend fun save(update: ProgressUpdate): WatchProgress
    suspend fun pendingSync(): List<WatchProgress>
    suspend fun markSynced(progress: WatchProgress): Boolean
}

class RoomWatchProgressRepository(
    private val store: WatchProgressStore,
    private val now: () -> Long = System::currentTimeMillis,
) : WatchProgressRepository {
    override fun observeContinueWatching(): Flow<List<WatchProgress>> =
        store.observeContinueWatching().map { rows -> rows.map(WatchProgressEntity::toModel) }

    override fun observeAnime(source: SourceId, animeId: String): Flow<List<WatchProgress>> =
        store.observeAnime(source.name, animeId).map { rows -> rows.map(WatchProgressEntity::toModel) }

    override suspend fun get(source: SourceId, animeId: String, episode: Int): WatchProgress? =
        store.get(source.name, animeId, episode)?.toModel()

    override suspend fun save(update: ProgressUpdate): WatchProgress {
        require(update.episode > 0) { "номер серии должен быть положительным" }
        require(update.positionMs >= 0) { "позиция не может быть отрицательной" }
        val previous = store.get(update.source.name, update.animeId, update.episode)
        val duration = update.durationMs.coerceAtLeast(previous?.durationMs ?: 0L)
        val position = update.positionMs.coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val finished = previous?.finished == true || ProgressPolicy.isFinished(position, duration)
        val row = WatchProgressEntity(
            source = update.source.name,
            animeId = update.animeId,
            episode = update.episode,
            translationId = update.translationId,
            title = update.title,
            thumbnailUrl = update.thumbnailUrl,
            positionMs = position,
            durationMs = duration,
            finished = finished,
            updatedAt = now(),
            syncedAt = previous?.syncedAt,
        )
        store.upsert(row)
        return row.toModel()
    }

    override suspend fun pendingSync(): List<WatchProgress> = store.pendingSync().map(WatchProgressEntity::toModel)

    override suspend fun markSynced(progress: WatchProgress): Boolean =
        store.markSynced(
            progress.source.name,
            progress.animeId,
            progress.episode,
            progress.updatedAt,
            now(),
        ) == 1
}

object ProgressPolicy {
    const val FINISHED_FRACTION = 0.90

    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs.toDouble() / durationMs >= FINISHED_FRACTION
}

private fun WatchProgressEntity.toModel() = WatchProgress(
    SourceId.valueOf(source), animeId, episode, translationId, title, thumbnailUrl,
    positionMs, durationMs, finished, updatedAt, syncedAt,
)
