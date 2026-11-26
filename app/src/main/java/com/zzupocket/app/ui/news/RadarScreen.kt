package com.zzupocket.app.ui.news

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.db.NewsArticleEntity
import com.zzupocket.app.data.news.NewsChannel
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.LiquidGlassChip
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.LiquidGlassOutlinedButton
import com.zzupocket.app.ui.components.StatusPill
import com.zzupocket.app.ui.components.glassClickable
import com.zzupocket.app.ui.nav.DOCK_CLEARANCE
import com.zzupocket.app.ui.theme.tabularNumbers

/**
 * 4 号 Tab「资讯」—— 郑州大学校园资讯雷达。
 *
 * 聚合郑大官网「通知公告」及新闻网四大栏目，支持 Room 本地离线缓存、翻页加载、关键词快筛、收藏与可选 BYOK AI 摘要。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    onOpenArticle: (String) -> Unit,
    vm: NewsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.uiPrefs.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("资讯") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    LiquidGlassIconButton(
                        icon = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "搜索资讯",
                        onClick = {
                            if (showSearch) vm.setSearchQuery("")
                            showSearch = !showSearch
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.AutoAwesome,
                        contentDescription = "AI 摘要设置",
                        tint = if (prefs.aiSummaryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = { showAiDialog = true },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新栏目",
                        onClick = { vm.refreshCurrent(force = true) },
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.refreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::setSearchQuery,
                    placeholder = { Text("搜索通知、学院新闻或关键词…") },
                    singleLine = true,
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清除")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // 5 个官方栏目 + 已收藏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NewsChannel.entries.forEach { channel ->
                    LiquidGlassChip(
                        label = channel.label,
                        selected = state.selectedChannel == channel,
                        onClick = { vm.selectChannel(channel) },
                    )
                }
                LiquidGlassChip(
                    label = "已收藏",
                    leadingIcon = Icons.Filled.Bookmark,
                    selected = state.selectedChannel == null,
                    onClick = { vm.selectChannel(null) },
                )
            }

            // 关键词快筛片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NewsViewModel.QUICK_TAGS.forEach { tag ->
                    LiquidGlassChip(
                        label = tag,
                        selected = state.quickTag == tag,
                        onClick = { vm.toggleQuickTag(tag) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = DOCK_CLEARANCE,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (prefs.aiSummaryEnabled) {
                    item(key = "ai_digest") {
                        AiDigestCard(
                            text = state.digestText,
                            loading = state.digestLoading,
                            error = state.digestError,
                            onGenerate = vm::generateDigest,
                        )
                    }
                }

                state.error?.let { err ->
                    item(key = "error_banner") {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.refreshCurrent(force = true) }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                if (state.articles.isEmpty() && !state.refreshing) {
                    item(key = "empty_state") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (state.selectedChannel == null) {
                                        "还没有收藏任何校园资讯"
                                    } else {
                                        "当前筛选下暂无资讯"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = if (state.selectedChannel == null) {
                                        "在任意新闻或通知卡片右上角点按书签即可离线保存"
                                    } else {
                                        "点按刷新向郑州大学官网同步最新公告与新闻"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.selectedChannel != null) {
                                    OutlinedButton(onClick = { vm.refreshCurrent(force = true) }) {
                                        Text("立即同步")
                                    }
                                }
                            }
                        }
                    }
                }

                items(state.articles, key = { it.url }) { article ->
                    NewsArticleCard(
                        article = article,
                        displayChannel = state.selectedChannel ?: NewsChannel.of(article.channel),
                        onClick = { onOpenArticle(article.url) },
                        onToggleBookmark = { vm.toggleBookmark(article) },
                    )
                }

                if (state.selectedChannel != null && state.hasMore && state.searchQuery.isBlank() && state.quickTag == null) {
                    item(key = "load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                LiquidGlassOutlinedButton(
                                    onClick = vm::loadMore,
                                ) {
                                    Text("加载更多历史资讯")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiDialog) {
        AiConfigDialog(
            initialEnabled = prefs.aiSummaryEnabled,
            initialBaseUrl = prefs.aiBaseUrl,
            initialApiKey = vm.currentAiKey(),
            initialModel = prefs.aiModel,
            onDismiss = { showAiDialog = false },
            onSave = { enabled, baseUrl, key, model ->
                vm.saveAiConfig(enabled, baseUrl, key, model)
                showAiDialog = false
            },
        )
    }
}

@Composable
private fun AiDigestCard(
    text: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("AI 今日校园速览", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onGenerate) {
                        Text(if (text == null) "生成速览" else "重新提炼")
                    }
                }
            }
            if (!text.isNullOrBlank()) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            } else if (!error.isNullOrBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    "基于当前栏目最新动态，使用你自带的模型密钥提炼一条简报。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewsArticleCard(
    article: NewsArticleEntity,
    displayChannel: NewsChannel,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val unread = !article.isRead
    val attachCount = remember(article.attachmentsJson) {
        AppGraph.newsRepository.decodeAttachments(article.attachmentsJson).size
    }
    val metaFooter = remember(article.sourceName, attachCount, article.contentText) {
        buildList {
            if (article.sourceName.isNotBlank()) add(article.sourceName)
            if (attachCount > 0) add("含 $attachCount 份附件")
            if (article.contentText != null) add("已离线")
        }.joinToString(" · ")
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = displayChannel.label,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                if (article.publishDate.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = article.publishDate,
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .glassClickable(onClick = onToggleBookmark),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (article.isBookmarked) "取消收藏" else "收藏",
                        tint = if (article.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                color = if (unread) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (article.summarySnippet.isNotBlank()) {
                Text(
                    text = article.summarySnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (metaFooter.isNotBlank()) {
                Text(
                    text = metaFooter,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** BYOK（自带密钥）AI 摘要配置弹窗（资讯列表与文章详情页复用） */
@Composable
internal fun AiConfigDialog(
    initialEnabled: Boolean,
    initialBaseUrl: String,
    initialApiKey: String,
    initialModel: String,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, baseUrl: String, apiKey: String, model: String) -> Unit,
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var model by remember { mutableStateOf(initialModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.zzupocket.app.ui.components.liquidGlassDialogContainerColor(),
        title = { Text("AI 校园摘要 (BYOK)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用 AI 摘要", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "仅直连你填的兼容接口，密钥本地加密存放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (sk-...)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(enabled, baseUrl, apiKey, model) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
