package com.zzupocket.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.db.NewsArticleEntity
import com.zzupocket.app.data.news.AiSummaryClient
import com.zzupocket.app.data.news.NewsChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 校园资讯雷达视图模型。
 *
 * `selectedChannel == null` 时展示「已收藏」集合；
 * 切换栏目时若无缓存或超过 15 分钟未拉取，自动静默同步学校官网公开页面。
 */
data class RadarUiState(
    val selectedChannel: NewsChannel? = NewsChannel.NOTICE,
    val searchQuery: String = "",
    val quickTag: String? = null,
    val articles: List<NewsArticleEntity> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val digestText: String? = null,
    val digestLoading: Boolean = false,
    val digestError: String? = null,
)

class NewsViewModel : ViewModel() {

    private val repo = AppGraph.newsRepository
    private val prefsStore = AppGraph.uiPrefsStore
    private val tokenStore = AppGraph.tokenStore

    private val selectedChannel = MutableStateFlow<NewsChannel?>(NewsChannel.NOTICE)
    private val searchQuery = MutableStateFlow("")
    private val quickTag = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val loadingMore = MutableStateFlow(false)
    private val nextPageByChannel = MutableStateFlow<Map<NewsChannel, String?>>(emptyMap())
    private val errorByChannel = MutableStateFlow<Map<NewsChannel, String?>>(emptyMap())
    private val digestText = MutableStateFlow<String?>(null)
    private val digestLoading = MutableStateFlow(false)
    private val digestError = MutableStateFlow<String?>(null)

    private var activeFetchJob: Job? = null

    private val filterFlow = combine(selectedChannel, searchQuery, quickTag) { ch, q, tag ->
        Triple(ch, q, tag)
    }

    private val statusFlow = combine(
        refreshing,
        loadingMore,
        nextPageByChannel,
        errorByChannel,
        combine(digestText, digestLoading, digestError) { d, dl, de -> Triple(d, dl, de) },
    ) { r, lm, nextMap, errMap, digest ->
        StatusSlice(r, lm, nextMap, errMap, digest.first, digest.second, digest.third)
    }

    val uiPrefs = prefsStore.flow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        prefsStore.latest,
    )

    val state: StateFlow<RadarUiState> = combine(
        repo.observeAll(),
        filterFlow,
        statusFlow,
    ) { all, filter, status ->
        val (channel, query, tag) = filter
        val trimmedQuery = query.trim().lowercase()
        val filtered = all.filter { item ->
            val matchChannel = if (channel == null) {
                item.isBookmarked
            } else {
                item.channel == channel.name || ",${channel.name}," in item.channels
            }
            if (!matchChannel) return@filter false
            val text = "${item.title} ${item.summarySnippet} ${item.sourceName}".lowercase()
            val matchTag = tag == null || tag.lowercase() in text
            val matchQuery = trimmedQuery.isEmpty() || trimmedQuery in text
            matchTag && matchQuery
        }
        RadarUiState(
            selectedChannel = channel,
            searchQuery = query,
            quickTag = tag,
            articles = filtered,
            refreshing = status.refreshing,
            loadingMore = status.loadingMore,
            hasMore = channel != null && !status.nextPageByChannel[channel].isNullOrBlank(),
            error = channel?.let { status.errorByChannel[it] },
            digestText = status.digestText,
            digestLoading = status.digestLoading,
            digestError = status.digestError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RadarUiState())

    init {
        maybeAutoRefresh(NewsChannel.NOTICE)
    }

    fun selectChannel(channel: NewsChannel?) {
        selectedChannel.value = channel
        activeFetchJob?.cancel()
        refreshing.value = false
        loadingMore.value = false
        if (channel != null) {
            clearChannelError(channel)
            maybeAutoRefresh(channel)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleQuickTag(tag: String) {
        quickTag.update { if (it == tag) null else tag }
    }

    fun refreshCurrent(force: Boolean = true) {
        val ch = selectedChannel.value ?: NewsChannel.NOTICE
        activeFetchJob?.cancel()
        loadingMore.value = false
        activeFetchJob = viewModelScope.launch {
            if (!force) {
                val last = repo.lastFetchedAt(ch) ?: 0L
                if (System.currentTimeMillis() - last < AUTO_REFRESH_INTERVAL_MS && nextPageByChannel.value.containsKey(ch)) {
                    return@launch
                }
            }
            refreshing.value = true
            clearChannelError(ch)
            try {
                val res = repo.refreshChannel(ch)
                nextPageByChannel.update { it + (ch to res.nextPageUrl) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                setChannelError(ch, err.message ?: "获取资讯失败，请检查网络连接")
            } finally {
                refreshing.value = false
            }
        }
    }

    /** 加载当前栏目的下一页历史资讯 */
    fun loadMore() {
        val ch = selectedChannel.value ?: return
        val nextUrl = nextPageByChannel.value[ch]?.takeIf { it.isNotBlank() } ?: return
        if (refreshing.value || loadingMore.value) return
        activeFetchJob?.cancel()
        activeFetchJob = viewModelScope.launch {
            loadingMore.value = true
            clearChannelError(ch)
            try {
                val res = repo.refreshChannel(ch, pageUrl = nextUrl)
                nextPageByChannel.update { it + (ch to res.nextPageUrl) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                setChannelError(ch, err.message ?: "加载更多失败，请重试")
            } finally {
                loadingMore.value = false
            }
        }
    }

    private fun maybeAutoRefresh(channel: NewsChannel) {
        activeFetchJob?.cancel()
        activeFetchJob = viewModelScope.launch {
            val last = repo.lastFetchedAt(channel) ?: 0L
            val expired = System.currentTimeMillis() - last >= AUTO_REFRESH_INTERVAL_MS
            // 若内存分页映射尚未记录该栏目，且已过期（或无缓存），执行抓取以同步首屏与分页指针
            if (expired || !nextPageByChannel.value.containsKey(channel)) {
                refreshing.value = true
                clearChannelError(channel)
                try {
                    val res = repo.refreshChannel(channel)
                    nextPageByChannel.update { it + (channel to res.nextPageUrl) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (err: Throwable) {
                    setChannelError(channel, err.message ?: "获取资讯失败，请检查网络连接")
                } finally {
                    refreshing.value = false
                }
            }
        }
    }

    private fun clearChannelError(ch: NewsChannel) {
        errorByChannel.update { it - ch }
    }

    private fun setChannelError(ch: NewsChannel, msg: String?) {
        errorByChannel.update { it + (ch to msg) }
    }

    fun toggleBookmark(article: NewsArticleEntity) {
        viewModelScope.launch {
            repo.toggleBookmark(article.url, article.isBookmarked)
        }
    }

    fun saveAiConfig(enabled: Boolean, baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            tokenStore.aiApiKey = apiKey.trim().takeIf { it.isNotBlank() }
            prefsStore.setAiConfig(enabled = enabled, baseUrl = baseUrl, model = model)
        }
    }

    fun currentAiKey(): String = tokenStore.aiApiKey.orEmpty()

    fun generateDigest() {
        val prefs = uiPrefs.value
        val key = tokenStore.aiApiKey.orEmpty()
        if (!prefs.aiSummaryEnabled || key.isBlank()) {
            digestError.value = "请先在 AI 摘要设置中开启功能并填入 API Key"
            return
        }
        val targets = state.value.articles.take(6)
        if (targets.isEmpty()) {
            digestError.value = "当前列表暂无资讯可总结"
            return
        }
        viewModelScope.launch {
            digestLoading.value = true
            digestError.value = null
            runCatching {
                repo.generateDigest(
                    articles = targets,
                    config = AiSummaryClient.Config(
                        baseUrl = prefs.aiBaseUrl,
                        apiKey = key,
                        model = prefs.aiModel,
                    ),
                )
            }.onSuccess { text ->
                digestText.value = text
            }.onFailure { err ->
                digestError.value = err.message ?: "生成速览失败"
            }
            digestLoading.value = false
        }
    }

    private data class StatusSlice(
        val refreshing: Boolean,
        val loadingMore: Boolean,
        val nextPageByChannel: Map<NewsChannel, String?>,
        val errorByChannel: Map<NewsChannel, String?>,
        val digestText: String?,
        val digestLoading: Boolean,
        val digestError: String?,
    )

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        val QUICK_TAGS = listOf("考试", "选课", "讲座", "放假", "奖学金")
    }
}
