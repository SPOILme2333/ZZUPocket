package com.zzupocket.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 校园资讯雷达缓存文章表（Room v11 新增，v13 扩展多栏目归属、信息来源与附件清单）。
 *
 * 主键使用规范化后的 HTTPS 文章详情页 URL，天然去重。
 * `channels` 使用逗号包裹的多栏目集合（如 `",HEADLINE,ACADEMIC,"`），避免同一篇文章跨栏目抓取时相互覆盖消失。
 */
@Entity(
    tableName = "news_articles",
    indices = [
        Index(value = ["channel"]),
        Index(value = ["publishDate"]),
    ],
)
data class NewsArticleEntity(
    @PrimaryKey val url: String,
    val channel: String,
    val title: String,
    val publishDate: String,
    val summarySnippet: String,
    val contentText: String? = null,
    val aiSummary: String? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val fetchedAt: Long,
    val channels: String = "",
    val sourceName: String = "",
    val attachmentsJson: String = "[]",
)

@Dao
interface NewsDao {

    @Query("SELECT * FROM news_articles ORDER BY publishDate DESC, fetchedAt DESC")
    fun observeAll(): Flow<List<NewsArticleEntity>>

    @Query(
        "SELECT * FROM news_articles " +
            "WHERE channel = :channel OR channels LIKE '%,' || :channel || ',%' " +
            "ORDER BY publishDate DESC, fetchedAt DESC",
    )
    fun observeByChannel(channel: String): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): NewsArticleEntity?

    @Query("SELECT * FROM news_articles WHERE url = :url LIMIT 1")
    fun observeByUrl(url: String): Flow<NewsArticleEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<NewsArticleEntity>)

    /**
     * 刷新列表时保留用户本地状态（已读、收藏、正文缓存、AI 摘要、主栏目归属），
     * 幂等合并新栏目至 `channels`，且仅在抓取到非空字段时更新日期与摘要。
     */
    @Query(
        "UPDATE news_articles SET " +
            "channels = CASE " +
            "  WHEN channels = '' THEN ',' || :channel || ',' " +
            "  WHEN instr(channels, ',' || :channel || ',') = 0 THEN channels || :channel || ',' " +
            "  ELSE channels END, " +
            "title = CASE WHEN :title != '' THEN :title ELSE title END, " +
            "publishDate = CASE WHEN :publishDate != '' THEN :publishDate ELSE publishDate END, " +
            "summarySnippet = CASE WHEN :summarySnippet != '' THEN :summarySnippet ELSE summarySnippet END, " +
            "fetchedAt = :fetchedAt WHERE url = :url",
    )
    suspend fun updateMetadata(
        url: String,
        channel: String,
        title: String,
        publishDate: String,
        summarySnippet: String,
        fetchedAt: Long,
    )

    @Query("UPDATE news_articles SET contentText = :contentText, isRead = 1 WHERE url = :url")
    suspend fun saveContentAndMarkRead(url: String, contentText: String)

    /** 详情页抓取成功后补全标题、发布日期、来源、正文及附件清单，并标为已读 */
    @Query(
        "UPDATE news_articles SET " +
            "title = CASE WHEN :title != '' THEN :title ELSE title END, " +
            "publishDate = CASE WHEN :publishDate != '' THEN :publishDate ELSE publishDate END, " +
            "sourceName = CASE WHEN :sourceName != '' THEN :sourceName ELSE sourceName END, " +
            "contentText = :contentText, " +
            "attachmentsJson = :attachmentsJson, " +
            "isRead = 1 " +
            "WHERE url = :url",
    )
    suspend fun saveDetailAndMarkRead(
        url: String,
        title: String,
        publishDate: String,
        sourceName: String,
        contentText: String,
        attachmentsJson: String,
    )

    @Query("UPDATE news_articles SET aiSummary = :aiSummary WHERE url = :url")
    suspend fun saveAiSummary(url: String, aiSummary: String)

    @Query("UPDATE news_articles SET isRead = 1 WHERE url = :url")
    suspend fun markRead(url: String)

    @Query("UPDATE news_articles SET isBookmarked = :bookmarked WHERE url = :url")
    suspend fun setBookmarked(url: String, bookmarked: Boolean)

    @Query("SELECT COUNT(*) FROM news_articles WHERE channel = :channel OR channels LIKE '%,' || :channel || ',%'")
    suspend fun countByChannel(channel: String): Int

    @Query("SELECT MAX(fetchedAt) FROM news_articles WHERE channel = :channel OR channels LIKE '%,' || :channel || ',%'")
    suspend fun lastFetchedAt(channel: String): Long?
}
