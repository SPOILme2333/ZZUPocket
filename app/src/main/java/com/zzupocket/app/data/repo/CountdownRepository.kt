package com.zzupocket.app.data.repo

import com.zzupocket.app.data.db.AppDatabase
import com.zzupocket.app.data.db.CountdownEntity
import com.zzupocket.app.domain.CountdownCategory
import com.zzupocket.app.domain.CountdownItem
import com.zzupocket.app.domain.CountdownRules
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 倒数日仓库（纯本地 Room 存储）。
 */
class CountdownRepository(private val db: AppDatabase) {

    private val dao = db.countdownDao()

    fun observeAll(today: LocalDate = LocalDate.now()): Flow<List<CountdownItem>> =
        dao.observeAll().map { list ->
            CountdownRules.sortForDisplay(list.mapNotNull { it.toDomain() }, today)
        }

    suspend fun all(today: LocalDate = LocalDate.now()): List<CountdownItem> =
        CountdownRules.sortForDisplay(dao.all().mapNotNull { it.toDomain() }, today)

    suspend fun save(item: CountdownItem): Long {
        val now = System.currentTimeMillis()
        val entity = CountdownEntity(
            id = item.id,
            title = item.title.trim(),
            targetDate = item.targetDate.toString(),
            category = item.category.name,
            location = item.location?.trim()?.takeIf { it.isNotBlank() },
            pinned = item.pinned,
            createdAt = if (item.createdAt > 0L) item.createdAt else now,
        )
        return dao.upsert(entity)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun togglePinned(item: CountdownItem) {
        save(item.copy(pinned = !item.pinned))
    }

    private fun CountdownEntity.toDomain(): CountdownItem? {
        val date = runCatching { LocalDate.parse(targetDate) }.getOrNull() ?: return null
        return CountdownItem(
            id = id,
            title = title,
            targetDate = date,
            category = CountdownCategory.of(category),
            location = location,
            pinned = pinned,
            createdAt = createdAt,
        )
    }
}
