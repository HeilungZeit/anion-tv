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
    suspend fun pendingFinishedSync(source: SourceId): List<WatchProgress>
    suspend fun finished(source: SourceId): List<WatchProgress>
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

    override suspend fun pendingFinishedSync(source: SourceId): List<WatchProgress> =
        store.pendingFinishedSync(source.name).map(WatchProgressEntity::toModel)

    override suspend fun finished(source: SourceId): List<WatchProgress> =
        store.finished(source.name).map(WatchProgressEntity::toModel)

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

/** Что показать на карточке тайтла по сериям. */
data class EpisodeMarks(
    val watched: Set<Int>,
    /** Доля просмотра недосмотренных серий — тонкая полоска на карточке. */
    val partial: Map<Int, Float>,
    /** Самая свежая недосмотренная серия, которую стоит предложить продолжить. */
    val resume: WatchProgress?,
)

object EpisodeMarksPolicy {
    /** Меньше — это случайный тык, а не просмотр; предлагать «продолжить» незачем. */
    const val RESUME_THRESHOLD_MS = 30_000L

    /**
     * Отметка важнее позиции: серия, отмеченная в аккаунте на сайте или другом
     * устройстве, считается просмотренной, даже если здесь её досмотрели лишь
     * частично. Такая серия не рисует полоску и не предлагается к продолжению.
     */
    fun of(local: List<WatchProgress>, remoteWatched: Set<Int>): EpisodeMarks {
        val watched = local.filter { it.finished }.mapTo(mutableSetOf()) { it.episode } + remoteWatched
        val inProgress = local.filterNot { it.episode in watched }
        return EpisodeMarks(
            watched = watched,
            partial = inProgress
                .filter { it.durationMs > 0 }
                .associate { it.episode to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) },
            resume = inProgress
                .filter { it.positionMs > RESUME_THRESHOLD_MS }
                .maxByOrNull { it.updatedAt },
        )
    }
}

private fun WatchProgressEntity.toModel() = WatchProgress(
    SourceId.valueOf(source), animeId, episode, translationId, title, thumbnailUrl,
    positionMs, durationMs, finished, updatedAt, syncedAt,
)
