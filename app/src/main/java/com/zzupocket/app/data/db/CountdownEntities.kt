package com.zzupocket.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 考试 / 重要日程倒数日表（Room v11 新增）。
 */
@Entity(tableName = "countdowns")
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    /** 目标日期（ISO 格式 YYYY-MM-DD） */
    val targetDate: String,
    /** 分类：EXAM / EVENT / HOLIDAY */
    val category: String,
    val location: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
)

@Dao
interface CountdownDao {

    @Query("SELECT * FROM countdowns ORDER BY targetDate ASC, createdAt ASC")
    fun observeAll(): Flow<List<CountdownEntity>>

    @Query("SELECT * FROM countdowns ORDER BY targetDate ASC, createdAt ASC")
    suspend fun all(): List<CountdownEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CountdownEntity): Long

    @Query("DELETE FROM countdowns WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM countdowns")
    suspend fun count(): Int
}
