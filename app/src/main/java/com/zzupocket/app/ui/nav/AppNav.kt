package com.zzupocket.app.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.prefs.UiPrefs
import com.zzupocket.app.data.update.AppUpdate
import com.zzupocket.app.data.update.UpdateCenter
import com.zzupocket.app.ui.about.AboutScreen
import com.zzupocket.app.ui.academic.ExamsScreen
import com.zzupocket.app.ui.academic.GradesScreen
import com.zzupocket.app.ui.academic.StudentStatusScreen
import com.zzupocket.app.ui.academic.TrainingPlanScreen
import com.zzupocket.app.ui.campus.CampusScreen
import com.zzupocket.app.ui.campus.CardScreen
import com.zzupocket.app.ui.campus.ElectricityScreen
import com.zzupocket.app.ui.campus.RoomScreen
import com.zzupocket.app.ui.components.LiquidBackdropState
import com.zzupocket.app.ui.components.LiquidGlassCanvasBackground
import com.zzupocket.app.ui.components.LiquidGlassRealTimeBar
import com.zzupocket.app.ui.components.LocalAmbientBackdrop
import com.zzupocket.app.ui.components.LocalGlassStyle
import com.zzupocket.app.ui.components.LocalLiquidBackdrop
import com.zzupocket.app.ui.components.LocalShowDescriptions
import com.zzupocket.app.ui.components.captureToLiquidBackdrop
import com.zzupocket.app.ui.diag.DiagnosticsScreen
import com.zzupocket.app.ui.focus.FocusScreen
import com.zzupocket.app.ui.focus.FocusStatsScreen
import com.zzupocket.app.ui.library.LibRecordsScreen
import com.zzupocket.app.ui.library.LibraryScreen
import com.zzupocket.app.ui.login.GatewayAuthScreen
import com.zzupocket.app.ui.login.LoginScreen
import com.zzupocket.app.ui.news.ArticleDetailScreen
import com.zzupocket.app.ui.news.RadarScreen
import com.zzupocket.app.ui.schedule.BackgroundCropScreen
import com.zzupocket.app.ui.schedule.CourseOverviewScreen
import com.zzupocket.app.ui.schedule.DisplaySettingsScreen
import com.zzupocket.app.ui.schedule.ScheduleScreen
import com.zzupocket.app.ui.schedule.WallpaperLayers
import com.zzupocket.app.ui.schedule.rememberScheduleBackground
import com.zzupocket.app.ui.settings.AppearanceScreen
import com.zzupocket.app.ui.settings.SettingsScreen
import com.zzupocket.app.ui.settings.WidgetSettingsScreen
import com.zzupocket.app.ui.theme.rememberEffectiveDark
import com.zzupocket.app.ui.today.TodayScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    TODAY(AppRoutes.TODAY, "今天", Icons.Filled.Today, Icons.Outlined.Today),
    SCHEDULE(AppRoutes.SCHEDULE, "课表", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    CAMPUS(AppRoutes.CAMPUS, "校园", Icons.Filled.Apps, Icons.Outlined.Apps),
    RADAR(AppRoutes.RADAR, "资讯", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
}

/** 非 tab 的全屏页面：这些路由下不显示底部悬浮 Dock */
private const val ROUTE_LOGIN = "login"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_OVERVIEW = "course_overview"
private const val ROUTE_DISPLAY = "display_settings"
private const val ROUTE_FOCUS_STATS = "focus_stats"
private const val ROUTE_BG_CROP = "background_crop"
private const val ROUTE_ROOMS = "room_occupancy"
private const val ROUTE_GATEWAY = "gateway_auth"
private const val ROUTE_ELECTRICITY = AppRoutes.ELECTRICITY
private const val ROUTE_CARD = AppRoutes.CARD
private const val ROUTE_LIBRARY = "library_web"
private const val ROUTE_LIB_RECORDS = "library_records"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_APPEARANCE = "appearance"
private const val ROUTE_WIDGET_SETTINGS = "widget_settings"
private const val CROP_SOURCE_SAVED = "saved"

@Composable
fun AppNav(initialRoute: String? = null, routeToken: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val prefs by AppGraph.uiPrefsStore.flow.collectAsStateWithLifecycle(initialValue = AppGraph.uiPrefsStore.latest)

    val dockDestinations = Destination.entries
    val dockRoutes = remember { Destination.entries.map { it.route }.toSet() }
    val showBottomBar = dockDestinations.any { it.route == currentRoute }

    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialRoute, routeToken) {
        if (initialRoute == null || initialRoute == Destination.TODAY.route) return@LaunchedEffect
        if (Destination.entries.any { it.route == initialRoute }) {
            switchTab(initialRoute)
        } else {
            navController.navigate(initialRoute)
        }
    }

    val wallpaperActive = currentRoute == Destination.SCHEDULE.route &&
        prefs.backgroundPath != null
    val wallpaper = rememberScheduleBackground(
        context = context,
        path = prefs.backgroundPath.takeIf { wallpaperActive },
        version = prefs.backgroundVersion,
    )
    val transparentBar = wallpaperActive && wallpaper != null && prefs.wallpaperNavBar
    val dockDark = rememberEffectiveDark(prefs.darkMode)

    val currentVersion = remember { AppUpdate.localVersion(context) }
    val remoteRelease by UpdateCenter.release.collectAsState(initial = null)
    val mutedVersion by UpdateCenter.mutedVersion.collectAsState(initial = null)
    val hasUpdate = AppUpdate.shouldShowDot(currentVersion, remoteRelease?.version, mutedVersion)

    // 实时液态玻璃采样图层：
    // ① ambientLayer 仅录制背景画布+壁纸，供全页卡片/按钮硬件实时折射（杜绝自递归）；
    // ② backdropLayer 录制整页内容树，供底部悬浮 Dock 实时折射穿透列表文字与徽章。
    val ambientLayer = rememberGraphicsLayer()
    var ambientOffset by remember { mutableStateOf(Offset.Zero) }
    val ambientState = remember(ambientLayer, ambientOffset) {
        LiquidBackdropState(ambientLayer, ambientOffset)
    }

    val backdropLayer = rememberGraphicsLayer()
    var backdropOffset by remember { mutableStateOf(Offset.Zero) }
    val backdropState = remember(backdropLayer, backdropOffset) {
        LiquidBackdropState(backdropLayer, backdropOffset)
    }

    CompositionLocalProvider(
        LocalShowDescriptions provides prefs.showDescriptions,
        LocalLiquidBackdrop provides backdropState,
        LocalAmbientBackdrop provides ambientState,
        LocalGlassStyle provides prefs.glassStyle,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ① 被采集内容树：独立背景采样层 + 全页面 NavHost（与悬浮 Dock 同级隔离）
            Box(
                Modifier
                    .fillMaxSize()
                    .captureToLiquidBackdrop(backdropState) { backdropOffset = it },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .captureToLiquidBackdrop(ambientState) { ambientOffset = it },
                ) {
                    LiquidGlassCanvasBackground(
                        dark = dockDark,
                        accentColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.secondary,
                        tertiaryColor = MaterialTheme.colorScheme.tertiary,
                    )

                    if (wallpaperActive && wallpaper != null) {
                        WallpaperLayers(image = wallpaper, prefs = prefs)
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = Destination.TODAY.route,
                            enterTransition = {
                                if (initialState.destination.route in dockRoutes && targetState.destination.route in dockRoutes) {
                                    EnterTransition.None
                                } else {
                                    fadeIn(tween(150, delayMillis = 55)) +
                                        slideInHorizontally(tween(180, delayMillis = 35)) { it / 20 }
                                }
                            },
                            exitTransition = {
                                if (initialState.destination.route in dockRoutes && targetState.destination.route in dockRoutes) {
                                    ExitTransition.None
                                } else {
                                    fadeOut(tween(55))
                                }
                            },
                            popEnterTransition = {
                                if (initialState.destination.route in dockRoutes && targetState.destination.route in dockRoutes) {
                                    EnterTransition.None
                                } else {
                                    fadeIn(tween(150, delayMillis = 55))
                                }
                            },
                            popExitTransition = {
                                if (initialState.destination.route in dockRoutes && targetState.destination.route in dockRoutes) {
                                    ExitTransition.None
                                } else {
                                    fadeOut(tween(55)) +
                                        slideOutHorizontally(tween(80)) { it / 24 }
                                }
                            },
                        ) {
                            composable(Destination.TODAY.route) {
                                TodayScreen(
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                                    onOpenSchedule = { switchTab(Destination.SCHEDULE.route) },
                                    onOpenElectricity = { navController.navigate(ROUTE_ELECTRICITY) },
                                    onOpenCard = { navController.navigate(ROUTE_CARD) },
                                    onOpenLibrary = { navController.navigate(ROUTE_LIBRARY) },
                                    onOpenRooms = { navController.navigate(ROUTE_ROOMS) },
                                    onOpenFocus = { navController.navigate(AppRoutes.FOCUS) },
                                    onOpenAppearance = { navController.navigate(ROUTE_APPEARANCE) },
                                )
                            }
                            composable(Destination.SCHEDULE.route) {
                                ScheduleScreen(
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onOpenOverview = { semesterId ->
                                        navController.navigate("$ROUTE_OVERVIEW/$semesterId")
                                    },
                                    onOpenDisplaySettings = { navController.navigate(ROUTE_DISPLAY) },
                                )
                            }
                            composable(Destination.CAMPUS.route) {
                                CampusScreen(
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onOpenElectricity = { navController.navigate(ROUTE_ELECTRICITY) },
                                    onOpenCard = { navController.navigate(ROUTE_CARD) },
                                    onOpenRooms = { navController.navigate(ROUTE_ROOMS) },
                                    onOpenLibrary = { navController.navigate(ROUTE_LIBRARY) },
                                    onOpenLibRecords = { navController.navigate(ROUTE_LIB_RECORDS) },
                                    onOpenFocus = { navController.navigate(AppRoutes.FOCUS) },
                                    onOpenGrades = { navController.navigate(AppRoutes.GRADES) },
                                    onOpenExams = { navController.navigate(AppRoutes.EXAMS) },
                                    onOpenStudentStatus = { navController.navigate(AppRoutes.STUDENT_STATUS) },
                                    onOpenTrainingPlan = { navController.navigate(AppRoutes.TRAINING_PLAN) },
                                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) },
                                    onOpenAppearance = { navController.navigate(ROUTE_APPEARANCE) },
                                )
                            }
                            composable(AppRoutes.GRADES) {
                                GradesScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                )
                            }
                            composable(AppRoutes.EXAMS) {
                                ExamsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                )
                            }
                            composable(AppRoutes.STUDENT_STATUS) {
                                StudentStatusScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onOpenTrainingPlan = { navController.navigate(AppRoutes.TRAINING_PLAN) },
                                )
                            }
                            composable(AppRoutes.TRAINING_PLAN) {
                                TrainingPlanScreen(
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Destination.RADAR.route) {
                                RadarScreen(
                                    onOpenArticle = { url ->
                                        navController.navigate("${AppRoutes.ARTICLE_DETAIL}/${Uri.encode(url)}")
                                    },
                                )
                            }
                            composable(AppRoutes.FOCUS) {
                                FocusScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenStats = { navController.navigate(ROUTE_FOCUS_STATS) },
                                )
                            }
                            composable(AppRoutes.SETTINGS) {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                    onOpenDiagnostics = { navController.navigate(ROUTE_DIAGNOSTICS) },
                                    onOpenGateway = { navController.navigate(ROUTE_GATEWAY) },
                                    onOpenAppearance = { navController.navigate(ROUTE_APPEARANCE) },
                                    onOpenWidgetSettings = { navController.navigate(ROUTE_WIDGET_SETTINGS) },
                                    onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                                )
                            }
                            composable(
                                route = "${AppRoutes.ARTICLE_DETAIL}/{url}",
                                arguments = listOf(navArgument("url") { type = NavType.StringType }),
                            ) { entry ->
                                val raw = entry.arguments?.getString("url") ?: return@composable
                                ArticleDetailScreen(
                                    articleUrl = Uri.decode(raw),
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(ROUTE_GATEWAY) {
                                GatewayAuthScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                )
                            }
                            composable(ROUTE_APPEARANCE) {
                                AppearanceScreen(onBack = { navController.popBackStack() })
                            }
                            composable(ROUTE_WIDGET_SETTINGS) {
                                WidgetSettingsScreen(onBack = { navController.popBackStack() })
                            }
                            composable(ROUTE_ABOUT) {
                                AboutScreen(onBack = { navController.popBackStack() })
                            }
                            composable(
                                route = "$ROUTE_OVERVIEW/{semesterId}",
                                arguments = listOf(navArgument("semesterId") { type = NavType.IntType }),
                            ) { entry ->
                                val semesterId = entry.arguments?.getInt("semesterId") ?: return@composable
                                CourseOverviewScreen(
                                    semesterId = semesterId,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(ROUTE_DISPLAY) {
                                DisplaySettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenCrop = { uri ->
                                        navController.navigate("$ROUTE_BG_CROP/${Uri.encode(uri.toString())}")
                                    },
                                    onRecrop = {
                                        navController.navigate("$ROUTE_BG_CROP/$CROP_SOURCE_SAVED")
                                    },
                                    onOpenOverview = { semesterId ->
                                        navController.navigate("$ROUTE_OVERVIEW/$semesterId")
                                    },
                                )
                            }
                            composable(
                                route = "$ROUTE_BG_CROP/{source}",
                                arguments = listOf(navArgument("source") { type = NavType.StringType }),
                            ) { entry ->
                                val raw = entry.arguments?.getString("source") ?: return@composable
                                BackgroundCropScreen(
                                    sourceUri = if (raw == CROP_SOURCE_SAVED) {
                                        null
                                    } else {
                                        Uri.parse(Uri.decode(raw))
                                    },
                                    useSavedSource = raw == CROP_SOURCE_SAVED,
                                    onSaved = { navController.popBackStack() },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(ROUTE_FOCUS_STATS) {
                                FocusStatsScreen(onBack = { navController.popBackStack() })
                            }
                            composable(ROUTE_ELECTRICITY) {
                                ElectricityScreen(onBack = { navController.popBackStack() })
                            }
                            composable(ROUTE_CARD) {
                                CardScreen(onBack = { navController.popBackStack() })
                            }
                            composable(ROUTE_ROOMS) {
                                RoomScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLogin = { navController.navigate(ROUTE_LOGIN) },
                                )
                            }
                            composable(ROUTE_LIBRARY) {
                                LibraryScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenRecords = { navController.navigate(ROUTE_LIB_RECORDS) },
                                )
                            }
                            composable(ROUTE_LIB_RECORDS) {
                                LibRecordsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenLibrary = {
                                        navController.navigate(ROUTE_LIBRARY) { launchSingleTop = true }
                                    },
                                )
                            }
                            composable(ROUTE_LOGIN) {
                                LoginScreen(
                                    onDone = { navController.popBackStack() },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(ROUTE_DIAGNOSTICS) {
                                DiagnosticsScreen(
                                    onBack = { navController.popBackStack() },
                                    onLoggedOut = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }

            // ② 悬浮实时液态玻璃底部 Dock（Issue #12 守护：外层仅吞 tap，不阻挡垂直滑动）
            AnimatedVisibility(
                visible = showBottomBar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = fadeIn(tween(140, delayMillis = 60)),
                exit = fadeOut(tween(55)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { /* 吞掉胶囊边缘留白区域的 tap，禁止穿透到底层列表 */ }
                        },
                ) {
                    LiquidGlassRealTimeBar(
                        dark = dockDark,
                        transparentWallpaper = transparentBar,
                        height = 64.dp,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dockDestinations.forEach { destination ->
                                val selected = currentRoute == destination.route
                                val capsuleColor = when {
                                    !selected -> null
                                    dockDark -> Color.White.copy(alpha = 0.10f)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                }
                                val itemTint = when {
                                    selected && dockDark -> MaterialTheme.colorScheme.onSurface
                                    selected -> MaterialTheme.colorScheme.primary
                                    dockDark -> Color.White.copy(alpha = 0.60f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                                }
                                val interactionSource = remember { MutableInteractionSource() }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Transparent,
                                    border = null,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .let { mod ->
                                            if (capsuleColor != null) mod.background(capsuleColor) else mod
                                        }
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = { switchTab(destination.route) },
                                        ),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box {
                                                Icon(
                                                    imageVector = if (selected) destination.icon else destination.outlinedIcon,
                                                    contentDescription = destination.label,
                                                    tint = itemTint,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                if (destination == Destination.CAMPUS && hasUpdate) {
                                                    Box(
                                                        Modifier
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 3.dp, y = (-2).dp)
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.tertiary),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(1.dp))
                                            Text(
                                                text = destination.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                                color = itemTint,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 悬浮液态玻璃 Dock 为滚动内容预留的底部空间：胶囊 62dp + 上下边距 16dp + 18dp 呼吸。
 */
val DOCK_CLEARANCE = 98.dp
