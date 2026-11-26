package com.zzupocket.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.prefs.UiPrefs
import com.zzupocket.app.data.update.AppUpdate
import com.zzupocket.app.data.update.UpdateCenter
import com.zzupocket.app.ui.components.DescriptionText
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassChip
import com.zzupocket.app.ui.components.LocalShowDescriptions
import kotlinx.coroutines.launch

/**
 * 独立设置中心：收拢账号、网关认证、课前/低电智能提醒、外观、小组件设置与版本检测。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGateway: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var loggedIn by remember { mutableStateOf(AppGraph.authRepository.isLoggedIn()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        loggedIn = AppGraph.authRepository.isLoggedIn()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by AppGraph.uiPrefsStore.flow.collectAsStateWithLifecycle(initialValue = AppGraph.uiPrefsStore.latest)
    val currentVersion = remember { AppUpdate.localVersion(context) }
    val remoteRelease by UpdateCenter.release.collectAsState(initial = null)
    val mutedVersion by UpdateCenter.mutedVersion.collectAsState(initial = null)
    val showDot = AppUpdate.shouldShowDot(currentVersion, remoteRelease?.version, mutedVersion)
    var showUpdateDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "请在系统设置中授予通知权限以便接收提醒", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
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
            SettingsGroupTitle("账号与认证")
            SettingsSectionCard {
                SettingsEntry(
                    icon = Icons.Filled.Person,
                    title = "账号",
                    subtitle = if (loggedIn) "已登录" else "未登录 · 点这里登录",
                    onClick = if (loggedIn) onOpenDiagnostics else onOpenLogin,
                    alwaysShowSubtitle = true,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsEntry(
                    icon = Icons.Filled.VpnKey,
                    title = "网关认证",
                    subtitle = "统一身份认证状态与手动处理",
                    onClick = onOpenGateway,
                )
            }

            SettingsGroupTitle("智能提醒")
            SettingsSectionCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("上课前通知提醒") },
                    supportingContent = if (prefs.showDescriptions) {
                        { DescriptionText("开课前按设定时间推送课程名与教室，支持本地离线触发") }
                    } else {
                        null
                    },
                    trailingContent = {
                        Switch(
                            checked = prefs.classReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) requestNotificationIfNeeded()
                                scope.launch {
                                    AppGraph.uiPrefsStore.setClassReminder(enabled = enabled)
                                    AppGraph.classReminderScheduler.reschedule()
                                }
                            },
                        )
                    },
                )
                if (prefs.classReminderEnabled) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 20, 30).forEach { min ->
                            LiquidGlassChip(
                                label = "提前 $min 分钟",
                                selected = prefs.classReminderMinutes == min,
                                onClick = {
                                    scope.launch {
                                        AppGraph.uiPrefsStore.setClassReminder(enabled = true, minutes = min)
                                        AppGraph.classReminderScheduler.reschedule()
                                    }
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            Icons.Filled.ElectricBolt,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text("宿舍低电量预警") },
                    supportingContent = if (prefs.showDescriptions) {
                        { DescriptionText("后台定时采样电量低于阈值时发送一次提醒，避免半夜断电") }
                    } else {
                        null
                    },
                    trailingContent = {
                        Switch(
                            checked = prefs.lowEnergyAlertEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) requestNotificationIfNeeded()
                                scope.launch {
                                    AppGraph.uiPrefsStore.setLowEnergyAlert(enabled = enabled)
                                }
                            },
                        )
                    },
                )
                if (prefs.lowEnergyAlertEnabled) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 20).forEach { kwh ->
                            LiquidGlassChip(
                                label = "低于 $kwh 度",
                                selected = prefs.lowEnergyThresholdKwh == kwh,
                                onClick = {
                                    scope.launch {
                                        AppGraph.uiPrefsStore.setLowEnergyAlert(enabled = true, thresholdKwh = kwh)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            SettingsGroupTitle("外观与桌面")
            SettingsSectionCard {
                SettingsEntry(
                    icon = Icons.Filled.Palette,
                    title = "主题配色与液态玻璃",
                    subtitle = "${prefs.themePreset.label()} · ${prefs.glassStyle.label()} · 自选配色",
                    onClick = onOpenAppearance,
                    alwaysShowSubtitle = true,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsEntry(
                    icon = Icons.Filled.SettingsInputAntenna,
                    title = "小组件设置",
                    subtitle = "时间 / 地点 / 行样式 · 各桌面组件",
                    onClick = onOpenWidgetSettings,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("显示功能描述") },
                    supportingContent = if (prefs.showDescriptions) {
                        { DescriptionText("开启时显示原理、例外与辅助提示") }
                    } else {
                        null
                    },
                    trailingContent = {
                        Switch(
                            checked = prefs.showDescriptions,
                            onCheckedChange = {
                                scope.launch { AppGraph.uiPrefsStore.setShowDescriptions(it) }
                            },
                        )
                    },
                )
            }

            SettingsGroupTitle("系统与关于")
            SettingsSectionCard {
                SettingsEntry(
                    icon = Icons.Filled.SystemUpdate,
                    title = "检测更新",
                    subtitle = if (showDot) {
                        "发现新版本 v${remoteRelease?.version} · 点击查看"
                    } else {
                        "当前 v$currentVersion · 点这里手动检查"
                    },
                    onClick = { showUpdateDialog = true },
                    alwaysShowSubtitle = true,
                    badge = showDot,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsEntry(
                    icon = Icons.Filled.Info,
                    title = "关于 ZZUPocket",
                    subtitle = "数据来源、隐私声明与致谢",
                    onClick = onOpenAbout,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showUpdateDialog) {
        UpdateCheckDialog(
            current = currentVersion,
            onDismiss = { showUpdateDialog = false },
            onMute = { version ->
                scope.launch { UpdateCenter.setUpdateMutedVersion(version) }
            },
        )
    }
}

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsSectionCard(
    content: @Composable () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    alwaysShowSubtitle: Boolean = false,
    badge: Boolean = false,
) {
    val enabled = onClick != null
    val shownSubtitle = subtitle.takeIf { alwaysShowSubtitle || LocalShowDescriptions.current }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = if (shownSubtitle != null) {
            {
                Text(
                    shownSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Box {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                if (badge) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
        modifier = if (enabled) Modifier.clickable(onClick = onClick!!) else Modifier,
    )
}

/**
 * 「检测更新」弹窗：有新版 / 已是最新 / 网络不通三种分支。
 */
@Composable
private fun UpdateCheckDialog(
    current: String,
    onDismiss: () -> Unit,
    onMute: (String) -> Unit,
) {
    val context = LocalContext.current
    val release by UpdateCenter.release.collectAsState(initial = null)
    val phase by UpdateCenter.phase.collectAsState(initial = UpdateCenter.Phase.Idle)
    val hasNew = AppUpdate.shouldShowDot(current, release?.version, muted = null)

    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "没有找到可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.zzupocket.app.ui.components.liquidGlassDialogContainerColor(),
        title = {
            Text(
                when {
                    hasNew -> "发现新版本"
                    phase == UpdateCenter.Phase.Checking -> "正在检查更新"
                    else -> "检测更新"
                },
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    hasNew && release != null -> {
                        val r = release!!
                        Text(
                            text = "当前 v$current → 最新 v${r.version}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = r.notes.takeIf { it.isNotBlank() }
                                ?: "（这次 release 没写更新说明）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        DescriptionText("下载需能访问 GitHub")
                    }

                    phase == UpdateCenter.Phase.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text("正在连接 GitHub……", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    release != null -> {
                        Text(
                            "当前已是最新版本 v$current",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> {
                        Text(
                            text = if (phase == UpdateCenter.Phase.Done) {
                                "连不上 GitHub"
                            } else {
                                "点「检查更新」检查一次"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (phase == UpdateCenter.Phase.Done) {
                            DescriptionText("校园网可能拦截，可换移动数据再试")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasNew && release != null) {
                TextButton(onClick = { open(release!!.downloadUrl ?: release!!.pageUrl) }) {
                    Text("去下载")
                }
            } else {
                TextButton(
                    onClick = { UpdateCenter.checkExplicit() },
                    enabled = phase != UpdateCenter.Phase.Checking,
                ) {
                    Text(if (phase == UpdateCenter.Phase.Checking) "检查中…" else "检查更新")
                }
            }
        },
        dismissButton = {
            if (hasNew && release != null) {
                Row {
                    TextButton(onClick = {
                        onMute(release!!.version)
                        onDismiss()
                    }) {
                        Text("不再提醒此版本")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}
