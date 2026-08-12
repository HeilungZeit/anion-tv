package tv.anion.data.repo

import kotlinx.coroutines.flow.Flow
import tv.anion.data.db.SearchHistoryDao
import tv.anion.data.db.SearchQueryEntity

interface SearchHistoryRepository {
    fun observe(): Flow<List<String>>
    suspend fun add(query: String)
}

class RoomSearchHistoryRepository(
    private val dao: SearchHistoryDao,
    private val now: () -> Long = System::currentTimeMillis,
) : SearchHistoryRepository {
    override fun observe(): Flow<List<String>> = dao.observe(LIMIT)

    override suspend fun add(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        dao.upsert(SearchQueryEntity(normalized, now()))
        dao.trim(LIMIT)
    }

    private companion object { const val LIMIT = 12 }
}
