package com.zzupocket.app.data.news

import com.zzupocket.app.data.db.AppDatabase
import com.zzupocket.app.data.db.NewsArticleEntity
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 校园资讯雷达仓库（Room 离线快照 + 官网公开页直连）。
 */
class NewsRepository(
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val aiClient: AiSummaryClient,
    private val json: Json,
) {

    /** 栏目列表页刷新结果（包含下一页分页链接） */
    data class ChannelRefreshResult(
        val articles: List<NewsArticleEntity>,
        val nextPageUrl: String?,
    )

    private val dao = db.newsDao()

    fun observeAll(): Flow<List<NewsArticleEntity>> = dao.observeAll()

    fun observeByChannel(channel: NewsChannel): Flow<List<NewsArticleEntity>> =
        dao.observeByChannel(channel.name)

    fun observeArticle(url: String): Flow<NewsArticleEntity?> = dao.observeByUrl(url)

    suspend fun getArticle(url: String): NewsArticleEntity? = dao.byUrl(url)

    suspend fun lastFetchedAt(channel: NewsChannel): Long? = dao.lastFetchedAt(channel.name)

    /** 刷新指定栏目（或翻页 URL）并增量合并到 Room（保留已读/收藏/正文/AI 摘要/已有主栏目） */
    suspend fun refreshChannel(
        channel: NewsChannel,
        pageUrl: String? = null,
    ): ChannelRefreshResult = withContext(Dispatchers.IO) {
        val targetUrl = pageUrl ?: channel.listUrl
        val html = fetchHtml(targetUrl)
        val page = ZzuNewsParser.parseListPage(html, channel, targetUrl)
        val now = System.currentTimeMillis()
        val entities = page.items.map { item ->
            NewsArticleEntity(
                url = item.url,
                channel = item.channel.name,
                title = item.title,
                publishDate = item.publishDate,
                summarySnippet = item.summarySnippet,
                fetchedAt = now,
                channels = ",${item.channel.name},",
            )
        }
        if (entities.isNotEmpty()) {
            dao.insertAllIgnore(entities)
            entities.forEach { e ->
                dao.updateMetadata(
                    url = e.url,
                    channel = e.channel,
                    title = e.title,
                    publishDate = e.publishDate,
                    summarySnippet = e.summarySnippet,
                    fetchedAt = e.fetchedAt,
                )
            }
        }
        ChannelRefreshResult(articles = entities, nextPageUrl = page.nextPageUrl)
    }

    /** 读取或拉取详情页正文、来源与附件清单，并自动标为已读 */
    suspend fun loadDetail(url: String, forceRefresh: Boolean = false): NewsArticleEntity {
        val existing = dao.byUrl(url)
        val cachedContent = existing?.contentText
        if (!forceRefresh && existing != null && !cachedContent.isNullOrBlank() && cachedContent != CONTENT_FALLBACK_TEXT) {
            if (!existing.isRead) dao.markRead(url)
            return existing.copy(isRead = true)
        }
        val html = withContext(Dispatchers.IO) { fetchHtml(url) }
        val detail = ZzuNewsParser.parseDetail(
            html = html,
            fallbackTitle = existing?.title.orEmpty(),
            pageUrl = url,
        )
        val content = detail.contentText.ifBlank { CONTENT_FALLBACK_TEXT }
        val attachmentsJson = json.encodeToString(
            ListSerializer(NewsAttachment.serializer()),
            detail.attachments,
        )
        if (existing == null) {
            val fresh = NewsArticleEntity(
                url = url,
                channel = NewsChannel.NOTICE.name,
                title = detail.title.ifBlank { url },
                publishDate = detail.publishDate,
                summarySnippet = content.take(120),
                contentText = content,
                isRead = true,
                fetchedAt = System.currentTimeMillis(),
                channels = ",${NewsChannel.NOTICE.name},",
                sourceName = detail.sourceName,
                attachmentsJson = attachmentsJson,
            )
            dao.insertAllIgnore(listOf(fresh))
            return fresh
        }
        dao.saveDetailAndMarkRead(
            url = url,
            title = detail.title,
            publishDate = detail.publishDate,
            sourceName = detail.sourceName,
            contentText = content,
            attachmentsJson = attachmentsJson,
        )
        return existing.copy(
            title = detail.title.ifBlank { existing.title },
            publishDate = detail.publishDate.ifBlank { existing.publishDate },
            sourceName = detail.sourceName.ifBlank { existing.sourceName },
            contentText = content,
            attachmentsJson = attachmentsJson,
            isRead = true,
        )
    }

    /** 将数据库中存储的附件 JSON 解码为附件列表（损坏或空数据安全返回空列表） */
    fun decodeAttachments(rawJson: String): List<NewsAttachment> {
        if (rawJson.isBlank() || rawJson == "[]") return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(NewsAttachment.serializer()), rawJson)
        }.getOrDefault(emptyList())
    }

    suspend fun toggleBookmark(url: String, current: Boolean) {
        dao.setBookmarked(url, !current)
    }

    suspend fun generateArticleAiSummary(
        url: String,
        config: AiSummaryClient.Config,
    ): String {
        val article = loadDetail(url)
        val summary = aiClient.summarizeArticle(
            config = config,
            title = article.title,
            content = article.contentText.orEmpty().ifBlank { article.summarySnippet },
        )
        dao.saveAiSummary(url, summary)
        return summary
    }

    suspend fun generateDigest(
        articles: List<NewsArticleEntity>,
        config: AiSummaryClient.Config,
    ): String {
        val pairs = articles.take(6).map { it.title to it.summarySnippet }
        require(pairs.isNotEmpty()) { "暂无可用于生成速览的资讯" }
        return aiClient.summarizeDigest(config, pairs)
    }

    private fun fetchHtml(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw IOException("获取校园资讯失败：${e.message}", e)
        }
        return resp.use { r ->
            if (!r.isSuccessful) throw IOException("学校网站返回 HTTP ${r.code}")
            val bytes = r.body?.bytes() ?: return@use ""
            ZzuNewsParser.decodeHtmlBytes(bytes, r.header("Content-Type"))
        }
    }

    companion object {
        const val CONTENT_FALLBACK_TEXT = "暂未提取到正文内容，可点击右上角在浏览器打开原文查看。"
    }
}
