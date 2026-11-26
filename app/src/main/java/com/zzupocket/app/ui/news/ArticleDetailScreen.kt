package com.zzupocket.app.ui.news

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.news.AiSummaryClient
import com.zzupocket.app.data.news.NewsChannel
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.GlassInsetDivider
import com.zzupocket.app.ui.components.GlassListCard
import com.zzupocket.app.ui.components.GlassListRow
import com.zzupocket.app.ui.components.GlassSectionHeader
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.StatusPill
import com.zzupocket.app.ui.theme.tabularNumbers
import kotlinx.coroutines.launch

/**
 * 校园资讯详情页：支持本地离线阅读、来源与附件展示、收藏、浏览器打开原文、分享及可选 AI 三条要点提炼。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    articleUrl: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val articleFlow = remember(articleUrl) { AppGraph.newsRepository.observeArticle(articleUrl) }
    val article by articleFlow.collectAsStateWithLifecycle(initialValue = null)
    val prefs by AppGraph.uiPrefsStore.flow.collectAsStateWithLifecycle(initialValue = AppGraph.uiPrefsStore.latest)

    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }

    fun refreshDetail(force: Boolean) {
        scope.launch {
            loading = true
            loadError = null
            runCatching {
                AppGraph.newsRepository.loadDetail(articleUrl, forceRefresh = force)
            }.onFailure { err ->
                loadError = err.message ?: "读取正文失败"
            }
            loading = false
        }
    }

    LaunchedEffect(articleUrl) {
        loading = true
        loadError = null
        runCatching {
            AppGraph.newsRepository.loadDetail(articleUrl)
        }.onFailure { err ->
            loadError = err.message ?: "读取正文失败"
        }
        loading = false
    }

    fun requestSummary() {
        val key = AppGraph.tokenStore.aiApiKey.orEmpty()
        if (!prefs.aiSummaryEnabled || key.isBlank()) {
            showAiDialog = true
            return
        }
        scope.launch {
            aiLoading = true
            aiError = null
            runCatching {
                AppGraph.newsRepository.generateArticleAiSummary(
                    url = articleUrl,
                    config = AiSummaryClient.Config(
                        baseUrl = prefs.aiBaseUrl,
                        apiKey = key,
                        model = prefs.aiModel,
                    ),
                )
            }.onFailure { err ->
                aiError = err.message ?: "AI 提炼失败"
            }
            aiLoading = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("资讯正文") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
                },
                actions = {
                    article?.let { a ->
                        LiquidGlassIconButton(
                            icon = if (a.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (a.isBookmarked) "取消收藏" else "收藏",
                            tint = if (a.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            onClick = { scope.launch { AppGraph.newsRepository.toggleBookmark(a.url, a.isBookmarked) } },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    LiquidGlassIconButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "在浏览器打开原文",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)))
                            }.onFailure {
                                Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Share,
                        contentDescription = "分享链接",
                        onClick = {
                            val title = article?.title.orEmpty()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n$articleUrl")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "分享校园资讯"))
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val channel = remember(article?.channel) { NewsChannel.of(article?.channel) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(
                        text = channel.label,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    article?.publishDate?.takeIf { it.isNotBlank() }?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    article?.sourceName?.takeIf { it.isNotBlank() }?.let { source ->
                        Text(
                            text = source,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                Text(
                    text = article?.title.orEmpty().ifBlank { "正在加载标题…" },
                    style = MaterialTheme.typography.headlineSmall,
                )

                // AI 要点提炼卡
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("AI 要点提炼", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            if (aiLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { requestSummary() }) {
                                    Text(if (article?.aiSummary == null) "生成 3 条要点" else "重新生成")
                                }
                            }
                        }
                        val summary = article?.aiSummary
                        if (!summary.isNullOrBlank()) {
                            Text(summary, style = MaterialTheme.typography.bodyMedium)
                        } else if (!aiError.isNullOrBlank()) {
                            Text(aiError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(
                                if (prefs.aiSummaryEnabled) "点击右侧按钮提取时间、对象与关键办理信息" else "点击配置自带 AI 接口即可快速提炼长公告",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (loadError != null && article?.contentText.isNullOrBlank()) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(loadError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { refreshDetail(force = true) }) {
                                Text("重新获取正文")
                            }
                        }
                    }
                }

                val bodyText = article?.contentText.orEmpty().ifBlank { article?.summarySnippet.orEmpty() }
                if (bodyText.isNotBlank()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                bodyText.split('\n')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach { para ->
                                        Text(
                                            text = para,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "原文：$articleUrl",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = { refreshDetail(force = true) },
                                        enabled = !loading,
                                    ) {
                                        Text("重抓正文")
                                    }
                                }
                            }
                        }
                    }
                }

                val attachments = remember(article?.attachmentsJson) {
                    AppGraph.newsRepository.decodeAttachments(article?.attachmentsJson.orEmpty())
                }
                if (attachments.isNotEmpty()) {
                    GlassSectionHeader("相关附件 (${attachments.size})")
                    GlassListCard {
                        attachments.forEachIndexed { idx, att ->
                            if (idx > 0) {
                                GlassInsetDivider()
                            }
                            GlassListRow(
                                icon = Icons.Filled.AttachFile,
                                title = att.name,
                                subtitle = "点击调用系统浏览器或下载器打开",
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(att.url)))
                                    }.onFailure {
                                        Toast.makeText(context, "未找到可打开附件的应用", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAiDialog) {
        AiConfigDialog(
            initialEnabled = prefs.aiSummaryEnabled,
            initialBaseUrl = prefs.aiBaseUrl,
            initialApiKey = AppGraph.tokenStore.aiApiKey.orEmpty(),
            initialModel = prefs.aiModel,
            onDismiss = { showAiDialog = false },
            onSave = { enabled, baseUrl, key, model ->
                scope.launch {
                    AppGraph.tokenStore.aiApiKey = key.trim().takeIf { it.isNotBlank() }
                    AppGraph.uiPrefsStore.setAiConfig(enabled = enabled, baseUrl = baseUrl, model = model)
                }
                showAiDialog = false
            },
        )
    }
}
