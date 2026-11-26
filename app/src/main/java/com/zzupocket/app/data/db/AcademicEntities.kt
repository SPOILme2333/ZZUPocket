package com.zzupocket.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 教务成绩单缓存表（Room v12 新增）。
 */
@Entity(tableName = "academic_grades")
data class GradeEntity(
    @PrimaryKey val id: String,
    val semesterCode: String,
    val semesterName: String,
    val courseCode: String,
    val courseName: String,
    val credits: Double,
    val gp: Double?,
    val finalGrade: String,
    val passed: Boolean,
    val retake: Boolean,
    val isMinor: Boolean,
    val isUnevaluated: Boolean,
    val courseType: String?,
    val courseProperty: String?,
    val displayTag: String?,
    /** SubGradeItem 列表的 JSON 字符串 */
    val subGradesJson: String,
    val updatedAt: Long,
)

@Dao
interface GradeDao {

    @Query("SELECT * FROM academic_grades ORDER BY semesterCode DESC, courseCode ASC")
    fun observeAll(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM academic_grades")
    suspend fun all(): List<GradeEntity>

    @Query("SELECT MAX(updatedAt) FROM academic_grades")
    suspend fun latestUpdatedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GradeEntity>)

    @Query("DELETE FROM academic_grades")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<GradeEntity>) {
        deleteAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}

/**
 * 官方考场安排缓存表（Room v12 新增）。
 */
@Entity(tableName = "academic_exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val courseName: String,
    val examDate: String?,
    val weekDay: Int,
    val startTime: Int,
    val endTime: Int,
    val place: String,
    val examType: String?,
    val isMinor: Boolean,
    val updatedAt: Long,
)

@Dao
interface ExamDao {

    @Query("SELECT * FROM academic_exams ORDER BY CASE WHEN examDate IS NULL THEN 1 ELSE 0 END, examDate ASC, startTime ASC")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM academic_exams")
    suspend fun all(): List<ExamEntity>

    @Query("SELECT MAX(updatedAt) FROM academic_exams")
    suspend fun latestUpdatedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ExamEntity>)

    @Query("DELETE FROM academic_exams")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<ExamEntity>) {
        deleteAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}
