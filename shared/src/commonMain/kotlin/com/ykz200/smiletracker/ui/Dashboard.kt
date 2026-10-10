package com.ykz200.smiletracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.domain.FaceAnalysis
import com.ykz200.smiletracker.domain.MinuteStat
import com.ykz200.smiletracker.domain.WeeklySummary
import com.ykz200.smiletracker.domain.DailyMoodHeatmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.*
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════
// v3.0 全新视觉系统 — 深邃宇宙 + 流光粒子 + 动态玻璃
// ═══════════════════════════════════════════════════════════════

private val OledBackground = Color(0xFF000000)
private val DeepSpace      = Color(0xFF050510)
private val SurfaceCard    = Color(0xFF0D0D12)
private val SurfaceCardHi  = Color(0xFF131319)
private val SurfaceGlass   = Color(0xFF1A1A22).copy(alpha = 0.88f)
private val DividerColor   = Color(0xFF1A1A22)

// v3.0 莫兰迪 + 流光色系 — 更高级、更柔和
private val AccentYellow = Color(0xFFFFD480)    // 暖阳金
private val AccentBlue   = Color(0xFF7EB8F7)    // 冰穹蓝
private val AccentGreen  = Color(0xFF8FD6A0)    // 薄荷绿
private val AccentPink   = Color(0xFFF2A0C4)    // 樱粉
private val AccentPurple = Color(0xFFC4A8E8)    // 薰紫
private val AccentOrange = Color(0xFFFFC27A)    // 橘色
private val AccentCyan   = Color(0xFF6EE2E2)    // 流光青
private val AccentCoral  = Color(0xFFFFA08C)    // 珊瑚橙
private val AccentRose   = Color(0xFFFF8FA3)    // 玫瑰粉
private val AccentMint   = Color(0xFF7BF0C0)    // 薄荷冰

// 玻璃拟态 — v3.0 升级版 (高光+内阴影+微光边框)
private val GlassHighlight = Color.White.copy(alpha = 0.04f)
private val GlassBorder    = Color.White.copy(alpha = 0.06f)
private val GlassGlow      = Color.White.copy(alpha = 0.02f)

// 文字系统 — 更清晰的层级
private val TextPrimary   = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFA0A0B8)
private val TextMuted     = Color(0xFF50506A)
private val TextDisabled  = Color(0xFF2A2A38)
private val TextGlow      = Color(0xFFFFE0B2)    // 特殊发光文字

/**
 * Compose Multiplatform 共享 UI — Dashboard v3.0
 *
 * 升级亮点：
 *  - 深邃宇宙背景 + 微光粒子漂浮动画
 *  - 动态玻璃拟态 (内发光 + 外光晕 + 微光反射)
 *  - 双层微笑仪表盘 (外环进度 + 内环情绪环)
 *  - 情绪时间轴 (24h 纵向时间线)
 *  - 智能工作节律分析 (自动识别高峰/低谷)
 *  - 心情日记模式 (emoji 快速记录)
 *  - 自适应卡片间距 + spring 物理动画系统 v2
 */
@Composable
fun DashboardApp() {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AccentYellow,
        surface = OledBackground,
        background = OledBackground,
        onSurface = TextPrimary,
        onBackground = TextPrimary
    )) {
        DashboardScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen() {
    val repo = remember { RepositoryProvider.get() }
    var latestFace by remember { mutableStateOf<FaceAnalysis?>(null) }
    var todayStats by remember { mutableStateOf<List<MinuteStat>>(emptyList()) }
    var weeklySummary by remember { mutableStateOf<WeeklySummary?>(null) }
    var moodHeatmap by remember { mutableStateOf<List<DailyMoodHeatmap>>(emptyList()) }
    var showWeekly by remember { mutableStateOf(false) }
    var selectedInsightPage by remember { mutableIntStateOf(0) }

    // v3.0 烧屏保护 — 更精细的双轴漂移 (幅度 0-3px, 周期 90s)
    val burnInTransition = rememberInfiniteTransition(label = "burnIn")
    val burnInX by burnInTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(90000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "burnInX"
    )
    val burnInY by burnInTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(75000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "burnInY"
    )

    // 启动时拉取今日统计数据
    LaunchedEffect(Unit) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date
        val start = today.atTime(LocalTime(0, 0, 0))
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val end = today.atTime(23, 59, 59)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        todayStats = runCatching { repo.queryMinuteStats(start, end) }.getOrDefault(emptyList())
    }

    // 实时刷新 (每 2 秒拉取最新采样)
    LaunchedEffect(Unit) {
        while (isActive) {
            latestFace = runCatching { repo.getLatest() }.getOrNull()
            delay(2000L)
        }
    }

    // 模拟周数据 (实际生产从 repo 拉取)
    LaunchedEffect(Unit) {
        weeklySummary = WeeklySummary(
            weekStart = "2026-10-06",
            weekEnd = "2026-10-12",
            totalSmileCount = 168,
            avgDailySmile = 28.0f,
            bestDay = "周三",
            bestDaySmileCount = 42,
            trend = "+18%"
        )
        moodHeatmap = listOf(
            DailyMoodHeatmap("周一 10-06", 0.52f, 22),
            DailyMoodHeatmap("周二 10-07", 0.65f, 28),
            DailyMoodHeatmap("周三 10-08", 0.82f, 42),
            DailyMoodHeatmap("周四 10-09", 0.58f, 25),
            DailyMoodHeatmap("周五 10-10", 0.72f, 32),
            DailyMoodHeatmap("周六 10-11", 0.45f, 18),
            DailyMoodHeatmap("周日 10-12", 0.38f, 12),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // v3.0: 背景微光粒子动画
        ParticleBackground()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = showWeekly,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                            },
                            label = "titleSwitch"
                        ) { weekly ->
                            Text(
                                text = if (weekly) "📊 本周概览" else "😊 打工人表情追踪",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = if (weekly) AccentPurple else TextPrimary
                            )
                        }
                    },
                    actions = {
                        // 日/周切换 v3.0 — 胶囊式切换器 + 动画
                        Surface(
                            onClick = { showWeekly = !showWeekly },
                            shape = RoundedCornerShape(20.dp),
                            color = if (showWeekly) AccentPurple.copy(alpha = 0.12f)
                                   else DeepSpace.copy(alpha = 0.6f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (showWeekly) AccentPurple.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f)
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = if (showWeekly) "周" else "日",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showWeekly) AccentPurple else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showWeekly) "→ 日" else "→ 周",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        // 设置按钮 v3.0
                        Surface(
                            onClick = { /* 设置面板 */ },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "⚙", fontSize = 15.sp, color = TextSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp)
                    // v3.0 烧屏保护: 双轴微风漂移
                    .offset(x = burnInX.dp, y = burnInY.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                if (showWeekly) {
                    // ═══ v3.0 周视图 ═══
                    item { WeeklyOverviewCardV3(summary = weeklySummary) }
                    item { MoodHeatmapCardV3(heatmapData = moodHeatmap) }
                    item { WeeklyBarChartCardV3(heatmapData = moodHeatmap) }
                    item { WeeklyInsightCardV3(summary = weeklySummary) }
                } else {
                    // ═══ v3.0 日视图 ═══

                    // Section 1: 实时状态卡 — 双层仪表盘
                    item {
                        AnimatedDashboardItem(delayMs = 0) {
                            RealTimeFaceCardV3(face = latestFace)
                        }
                    }

                    // Section 2: 四大统计玻璃卡
                    item {
                        AnimatedDashboardItem(delayMs = 60) {
                            val smileSum = todayStats.sumOf { it.smileCount.toLong() }
                            val sampleSum = todayStats.sumOf { it.sampleCount.toLong() }
                            val avgSmileOfToday = if (todayStats.isNotEmpty())
                                todayStats.map { it.smilePercent.toDouble() }.average() else 0.0
                            val eyeStrain = if (todayStats.isNotEmpty()) {
                                val avgEye = todayStats.map {
                                    (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f
                                }.average()
                                ((1.0 - avgEye.coerceAtLeast(0.0)) * 100).coerceIn(0.0, 100.0)
                            } else 0.0
                            StatsGridV3(smileSum = smileSum, sampleSum = sampleSum,
                                        avgSmile = avgSmileOfToday, eyeStrain = eyeStrain)
                        }
                    }

                    // Section 3: 工作节律分析 (新增)
                    item {
                        AnimatedDashboardItem(delayMs = 120) {
                            WorkRhythmCard(todayStats = todayStats)
                        }
                    }

                    // Section 4: 智能洞察 v3.0
                    item {
                        AnimatedDashboardItem(delayMs = 180) {
                            InsightCardV3(todayStats = todayStats, latestFace = latestFace)
                        }
                    }

                    // Section 5: 趋势图表 v3.0
                    item {
                        AnimatedDashboardItem(delayMs = 240) {
                            TrendChartSectionV3(todayStats = todayStats)
                        }
                    }

                    // Section 6: 每分锺详情
                    if (todayStats.isNotEmpty()) {
                        item {
                            AnimatedDashboardItem(delayMs = 300) {
                                Text(
                                    text = "⏱ 每分锺详情",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(todayStats.reversed()) { index, stat ->
                            AnimatedDashboardItem(delayMs = 300 + index * 20) {
                                Column {
                                    MinuteStatRowV3(stat = stat)
                                    if (index < todayStats.size - 1) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = DividerColor,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacer
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v3.0 微光粒子背景 — 深邃宇宙感
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ParticleBackground() {
    val particles = remember {
        List(15) {
            ParticleData(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 0.5f,
                speed = Random.nextFloat() * 0.3f + 0.1f,
                phase = Random.nextFloat() * PI.toFloat() * 2f,
                alpha = Random.nextFloat() * 0.4f + 0.1f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = PI.toFloat() * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "particleTime"
    )

    Canvas(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        particles.forEach { p ->
            val currentAlpha = p.alpha * (0.5f + 0.5f * sin(time * p.speed + p.phase))
            val currentY = (p.y + time * p.speed * 0.01f) % 1f
            drawCircle(
                color = AccentCyan.copy(alpha = currentAlpha * 0.5f),
                radius = p.size,
                center = Offset(p.x * size.width, currentY * size.height)
            )
        }
        // 极光渐变叠加
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(AccentPurple.copy(alpha = 0.03f), Color.Transparent),
                center = Offset(size.width * 0.8f, size.height * 0.1f),
                radius = size.width * 0.6f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(AccentCyan.copy(alpha = 0.02f), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.9f),
                radius = size.width * 0.5f
            )
        )
    }
}

private data class ParticleData(
    val x: Float, val y: Float, val size: Float,
    val speed: Float, val phase: Float, val alpha: Float
)

// ═══════════════════════════════════════════════════════════════
// 可见性动画包装器 v3 — 带spring弹性的入场
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AnimatedDashboardItem(delayMs: Int = 0, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, easing = EaseOutCubic)) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)
        )
    ) { content() }
}

// ═══════════════════════════════════════════════════════════════
// v3.0 动态玻璃拟态卡片 — 内发光 + 外光晕
// ═══════════════════════════════════════════════════════════════
@Composable
private fun GlassCardV3(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = SurfaceGlass),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // 顶部微光高光
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(GlassHighlight, Color.Transparent),
                            startY = 0f, endY = size.height * 0.3f
                        )
                    )
                    // 底部微光 (增加深度)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, GlassGlow),
                            startY = size.height * 0.7f, endY = size.height
                        )
                    )
                    // 外发光 (可选)
                    if (glowColor != Color.Transparent) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(glowColor.copy(alpha = 0.15f), glowColor.copy(alpha = 0.05f)),
                                startY = 0f, endY = size.height
                            ),
                            style = Stroke(width = 1.5.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
                        )
                    }
                    // 玻璃边框
                    drawRoundRect(
                        color = GlassBorder,
                        style = Stroke(width = 0.75.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
                    )
                }
                .padding(18.dp),
            content = content
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 实时状态卡 v3.0 — 双层仪表盘 (外环微笑 + 内环情绪)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun RealTimeFaceCardV3(face: FaceAnalysis?) {
    // 多阶段呼吸动画 v3
    val breathingAnim = rememberInfiniteTransition(label = "breathingV3")
    val glowAlpha by breathingAnim.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "glowV3"
    )
    val ringPulse by breathingAnim.animateFloat(
        initialValue = 0.95f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "ringPulse"
    )

    val smileProgress = face?.smileProbability?.coerceAtLeast(0f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = smileProgress,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 70f),
        label = "smileSpring"
    )
    val animatedInnerRing by animateFloatAsState(
        targetValue = face?.let { if (it.isSmiling) 0.9f else if (it.smileProbability > 0.4f) 0.6f else 0.2f } ?: 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 90f),
        label = "innerRing"
    )

    val accentColor = when {
        animatedProgress >= 0.7f -> AccentYellow
        animatedProgress >= 0.4f -> AccentCyan
        face == null -> TextMuted
        else -> AccentBlue
    }

    GlassCardV3(glowColor = accentColor) {
        Column {
            // 顶部栏: 状态 + LIVE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // v3.0: 多层呼吸指示灯
                Box(
                    modifier = Modifier.size(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp * ringPulse)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(accentColor.copy(alpha = glowAlpha), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor.copy(alpha = glowAlpha), CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "实时状态",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                // LIVE 标签 — 带脉冲
                Box(
                    modifier = Modifier
                        .background(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            0.5.dp, accentColor.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(accentColor.copy(alpha = glowAlpha), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (face == null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎯", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "正在初始化摄像头...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "请将手机前置摄像头对准面部",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 左侧: 表情数据
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 情绪分类大标签
                        AnimatedContent(
                            targetState = face.isSmiling to (face.smileProbability >= 0.4f),
                            transitionSpec = {
                                fadeIn(tween(400)) scaleIn(
                                    initialScale = 0.8f,
                                    animationSpec = spring(dampingRatio = 0.7f)
                                ) togetherWith fadeOut(tween(200)) scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = tween(200)
                                )
                            },
                            label = "emotionLabel"
                        ) { (smiling, pleasant) ->
                            val (emoji, label, color) = when {
                                smiling -> Triple("😊", "微笑中", AccentYellow)
                                pleasant -> Triple("🙂", "心情愉悦", AccentCyan)
                                face.smileProbability >= 0.2f -> Triple("😐", "平静中性", AccentBlue)
                                else -> Triple("😶", "专注/疲劳", TextSecondary)
                            }
                            Text(
                                text = "$emoji $label",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                        // 微笑概率 (大数字 + 数字滚动动画)
                        AnimatedNumber(
                            targetValue = face.smileProbability.coerceAtLeast(0f),
                            fontSize = 36.sp,
                            color = accentColor,
                            suffix = "%"
                        )
                        Text(
                            text = "微笑概率",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        // 眼部数据
                        if (face.leftEyeOpenProbability >= 0f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            EyeOpenRowV3("L", face.leftEyeOpenProbability)
                            Spacer(modifier = Modifier.height(2.dp))
                            EyeOpenRowV3("R", face.rightEyeOpenProbability)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 右侧: 双层微笑仪表盘
                    DualRingGaugeV3(
                        progress = animatedProgress,
                        innerProgress = animatedInnerRing,
                        isSmiling = face.isSmiling,
                        size = 100.dp,
                        glowColor = accentColor
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v3.0 双层微笑仪表盘 — 外环 + 内环
// ═══════════════════════════════════════════════════════════════
@Composable
private fun DualRingGaugeV3(
    progress: Float,
    innerProgress: Float,
    isSmiling: Boolean,
    size: Dp,
    glowColor: Color
) {
    val sweepAngle = progress.coerceIn(0f, 1f) * 360f

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 7.dp.toPx()
            val outerRadius = (this.size.minDimension - strokeW) / 2
            val innerRadius = outerRadius - strokeW - 6.dp.toPx()
            val center = Offset(this.size.width / 2, this.size.height / 2)

            // 外环光晕
            if (progress > 0.05f) {
                drawCircle(
                    color = glowColor.copy(alpha = 0.12f),
                    radius = outerRadius + strokeW / 2 + 6.dp.toPx(),
                    center = center
                )
            }
            // 外环背景轨道
            drawArc(
                color = DividerColor, startAngle = 0f, sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // 外环渐变弧线
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.2f),
                            glowColor,
                            glowColor.copy(alpha = 0.8f)
                        )
                    ),
                    startAngle = -90f, sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
            // 外环顶端白点
            if (sweepAngle > 5f) {
                val angle = Math.toRadians((-90.0 + sweepAngle).toDouble())
                val dotX = center.x + outerRadius * cos(angle).toFloat()
                val dotY = center.y + outerRadius * sin(angle).toFloat()
                drawCircle(color = Color.White, radius = strokeW / 2 + 2.dp.toPx(), center = Offset(dotX, dotY))
            }

            // 内环 (情绪强度)
            val innerSweep = innerProgress * 360f
            val innerStrokeW = 4.dp.toPx()
            drawArc(
                color = DividerColor, startAngle = 0f, sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = innerStrokeW, cap = StrokeCap.Round)
            )
            if (innerSweep > 0f) {
                drawArc(
                    color = AccentPurple.copy(alpha = 0.6f),
                    startAngle = -90f, sweepAngle = innerSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = innerStrokeW, cap = StrokeCap.Round)
                )
            }
        }
        // 中心表情 v3.0
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSmiling) "😊" else if (progress >= 0.3f) "🙂" else "😐",
                fontSize = 24.sp
            )
            if (progress > 0f) {
                Text(
                    text = "${(progress * 100).toInt()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = glowColor
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 数字滚动动画 v3.0 — 让数字变化更自然
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AnimatedNumber(
    targetValue: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    suffix: String = ""
) {
    val currentValue by animateFloatAsState(
        targetValue = targetValue * 100,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "numAnim"
    )
    Text(
        text = "${"%.0f".format(currentValue)}$suffix",
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        letterSpacing = (-1).sp
    )
}

// ═══════════════════════════════════════════════════════════════
// 眼部数据行 v3.0
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EyeOpenRowV3(label: String, value: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextMuted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val animatedFraction by animateFloatAsState(
            targetValue = value.coerceIn(0f, 1f),
            animationSpec = tween(500),
            label = "eyeBar"
        )
        Box(modifier = Modifier.weight(1f).height(5.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.05f),
                    size = Size(size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f, 2.5f)
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.6f), AccentBlue)
                    ),
                    size = Size(size.width * animatedFraction, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f, 2.5f)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.0f%%".format(value.coerceIn(0f, 1f) * 100),
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier.width(32.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 统计数字网格 v3.0 — 毛玻璃 + 流光边框 + 数字滚动
// ═══════════════════════════════════════════════════════════════
@Composable
private fun StatsGridV3(
    smileSum: Long,
    sampleSum: Long,
    avgSmile: Double,
    eyeStrain: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCardV3(
                modifier = Modifier.weight(1f),
                icon = "😊",
                label = "微笑次数",
                value = smileSum.toString(),
                accent = AccentYellow,
                delayAnim = 0
            )
            StatCardV3(
                modifier = Modifier.weight(1f),
                icon = "📊",
                label = "采样总数",
                value = sampleSum.toString(),
                accent = AccentBlue,
                delayAnim = 60
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCardV3(
                modifier = Modifier.weight(1f),
                icon = "📈",
                label = "微笑占比",
                value = "%.1f%%".format(avgSmile),
                accent = AccentGreen,
                delayAnim = 120
            )
            StatCardV3(
                modifier = Modifier.weight(1f),
                icon = "👁",
                label = "眼部疲劳",
                value = "%.0f%%".format(eyeStrain),
                accent = AccentPink,
                delayAnim = 180
            )
        }
    }
}

@Composable
private fun StatCardV3(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    value: String,
    accent: Color,
    delayAnim: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayAnim.toLong()); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(
            initialScale = 0.9f,
            animationSpec = spring(dampingRatio = 0.7f)
        )
    ) {
        ElevatedCard(
            modifier = modifier,
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceCardHi),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 顶部流光装饰
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.6f),
                                        accent.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                        }
                )
                Column(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 14.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = icon, fontSize = 22.sp)
                    Text(
                        text = value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// v3.0 工作节律分析 — 自动识别情绪高峰和低谷
// ═══════════════════════════════════════════════════════════════
@Composable
private fun WorkRhythmCard(todayStats: List<MinuteStat>) {
    val peakHour = if (todayStats.isNotEmpty()) {
        todayStats.groupBy { it.minuteKey.substring(11, 13) }
            .mapValues { (_, stats) -> stats.sumOf { it.smileCount.toLong() } }
            .maxByOrNull { it.value }?.key
    } else null

    val troughHour = if (todayStats.isNotEmpty()) {
        todayStats.groupBy { it.minuteKey.substring(11, 13) }
            .mapValues { (_, stats) -> stats.sumOf { it.smileCount.toLong() } }
            .filter { it.value > 0 }
            .minByOrNull { it.value }?.key
    } else null

    val rhythmInsight = when {
        peakHour == null -> "数据采集中...请先让摄像头运行一段时间"
        peakHour == troughHour -> "今天工作节奏平稳，情绪较为均匀"
        else -> {
            val troughStr = if (troughHour != null) "${troughHour}时左右是你的低谷时段" else ""
            "${peakHour}时是你最开心的时段$troughStr。在低谷时段可以起身走动、喝水补充能量。"
        }
    }

    GlassCardV3(glowColor = AccentCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(AccentCyan, AccentBlue)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚡", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "工作节律",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Text(
                        text = "自动分析你的情绪规律",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // 节律时间轴 (简化版)
            if (todayStats.isNotEmpty()) {
                RhythmTimeline(stats = todayStats)
            }

            // 洞察文本
            Text(
                text = rhythmInsight,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            // 建议标签
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (peakHour != null) {
                    SuggestionChip("⏰ ${peakHour}时最开心", AccentYellow)
                }
                if (troughHour != null && troughHour != peakHour) {
                    SuggestionChip("💧 ${troughHour}时注意补水", AccentBlue)
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RhythmTimeline(stats: List<MinuteStat>) {
    val hourlyData = stats.groupBy { it.minuteKey.substring(11, 13) }
        .mapValues { (_, st) -> st.sumOf { it.smileCount.toLong() }.toInt() }
        .toSortedMap()
    val maxVal = hourlyData.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barWidth = if (hourlyData.size > 1) size.width / hourlyData.size else size.width
        val chartHeight = size.height

        hourlyData.entries.forEachIndexed { index, (_, count) ->
            val barHeight = (count.toFloat() / maxVal) * chartHeight
            val x = index * barWidth + barWidth * 0.15f
            val w = barWidth * 0.7f
            val y = chartHeight - barHeight
            val fraction = count.toFloat() / maxVal

            drawRoundRect(
                color = lerp(AccentBlue, AccentYellow, fraction).copy(alpha = 0.7f),
                topLeft = Offset(x, y),
                size = Size(w, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
        }

        // 基线
        drawLine(
            color = Color.White.copy(alpha = 0.05f),
            start = Offset(0f, chartHeight),
            end = Offset(size.width, chartHeight),
            strokeWidth = 0.5f
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 智能洞察卡片 v3.0 — 更丰富的上下文分析
// ═══════════════════════════════════════════════════════════════
@Composable
private fun InsightCardV3(todayStats: List<MinuteStat>, latestFace: FaceAnalysis?) {
    val insights = generateInsightsV3(todayStats, latestFace)
    val primaryInsight = insights.firstOrNull() ?: "正在收集数据，稍后将为你提供个性化的工作状态分析。"
    val stressLevel = calculateStressIndexV3(todayStats, latestFace)

    GlassCardV3(glowColor = AccentPurple) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(AccentPurple, AccentPink)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🧠", fontSize = 17.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "智能洞察",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple
                    )
                    Text(
                        text = "压力指数 $stressLevel",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 情绪徽章
                Surface(
                    color = AccentPurple.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, AccentPurple.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "AI 分析",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp()),
                        fontSize = 10.sp,
                        color = AccentPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 主洞察文本
            Text(
                text = primaryInsight,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            // 多洞察列表 v3.0
            if (insights.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insights.drop(1).take(2).forEach { insight ->
                        Surface(
                            color = Color.White.copy(alpha = 0.02f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(text = "·", color = AccentPurple, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = insight,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun calculateStressIndexV3(stats: List<MinuteStat>, face: FaceAnalysis?): String {
    if (stats.isEmpty() && face == null) return "--"
    val avgSmile = stats.map { it.smilePercent.toDouble() }.average()
    val eyeStrainPct = if (stats.isNotEmpty()) {
        val avgEye = stats.map {
            (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f
        }.average()
        ((1.0 - avgEye.coerceAtLeast(0.0)) * 100).coerceIn(0.0, 100.0)
    } else 0.0
    val stressScore = ((1.0 - avgSmile / 100.0) * 60 + eyeStrainPct * 0.4).coerceIn(0.0, 100.0)
    return when {
        stressScore < 25 -> "低 😌"
        stressScore < 50 -> "中 🙂"
        stressScore < 75 -> "较高 😐"
        else -> "高 😣"
    }
}

private fun generateInsightsV3(stats: List<MinuteStat>, face: FaceAnalysis?): List<String> {
    if (stats.isEmpty() && face == null) {
        return listOf("正在收集数据，稍后将为你提供个性化的工作状态分析。")
    }

    val insights = mutableListOf<String>()
    val avgSmile = stats.map { it.smilePercent.toDouble() }.average()
    val totalSamples = stats.sumOf { it.sampleCount.toLong() }
    val highStrainMinutes = stats.count { stat ->
        val eyeAvg = (stat.avgEyeLeft.coerceAtLeast(0f) + stat.avgEyeRight.coerceAtLeast(0f)) / 2f
        eyeAvg < 0.3f
    }
    val smileVariance = if (stats.size > 1) {
        val mean = avgSmile
        stats.map { (it.smilePercent - mean) * (it.smilePercent - mean) }.average()
    } else 0.0
    val smileVolatility = kotlin.math.sqrt(smileVariance)

    // v3.0 主洞察 — 更智能、更有温度
    insights += when {
        face == null ->
            "摄像头尚未就绪，请将手机前置摄像头对准面部。"
        avgSmile > 60 ->
            "今天心情不错！微笑占比较高（${"%.0f".format(avgSmile)}%），保持这份好状态 😊"
        avgSmile > 30 ->
            "微笑频率适中（${"%.0f".format(avgSmile)}%）。记得适时起身活动，眺望远方放松眼睛。"
        highStrainMinutes > 3 ->
            "检测到 ${highStrainMinutes} 次眼部疲劳信号，建议每 40 分钟做一次眼保健操 👀"
        totalSamples < 10 ->
            "数据采样较少（$totalSamples），保持手机亮屏运行以获取更准确的分析。"
        else ->
            "今天整体表情偏中性，工作压力可能偏高。午休时去户外走走晒晒太阳吧 ☀️"
    }

    // v3.0 副洞察 — 情绪波动
    if (stats.size > 5 && smileVolatility > 20) {
        insights += "今日情绪波动较大（标准差 ${"%.1f".format(smileVolatility)}），注意保持心态平和。"
    }

    // v3.0 副洞察 — 眼部疲劳
    if (highStrainMinutes in 1..3) {
        insights += "检测到零星的眼睛干涩信号，试试 20-20-20 护眼法则：每 20 分钟远眺 20 英尺 20 秒。"
    }

    // v3.0 新增 — 工作节奏建议
    if (stats.size > 20) {
        val latePeriod = stats.filter { it.minuteKey.substring(11, 13).toInt() >= 15 }
        if (latePeriod.isNotEmpty()) {
            val lateAvg = latePeriod.map { it.smilePercent.toDouble() }.average()
            if (lateAvg < avgSmile * 0.7) {
                insights += "下午时段微笑频率明显下降，建议在 15:00-16:00 安排短暂休息或茶歇。"
            }
        }
    }

    return insights
}

// ═══════════════════════════════════════════════════════════════
// 趋势图表 v3 — 更精美的填充 + 动画
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TrendChartSectionV3(todayStats: List<MinuteStat>) {
    GlassCardV3 {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(30.dp).background(
                        AccentYellow.copy(alpha = 0.08f), RoundedCornerShape(10.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📉", fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "微笑趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (todayStats.isNotEmpty()) {
                        Text(
                            text = "共 ${todayStats.size} 个数据点",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            EnhancedSmileyChartV3(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                data = todayStats
            )
        }
    }
}

@Composable
private fun EnhancedSmileyChartV3(modifier: Modifier, data: List<MinuteStat>) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "⏳", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "暂无数据", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "摄像头就绪后开始自动记录",
                    color = TextMuted.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }
        return
    }

    val lineColor = AccentYellow
    val fillTopColor = AccentYellow.copy(alpha = 0.2f)
    val fillBotColor = AccentYellow.copy(alpha = 0.0f)

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1800, easing = EaseOutCubic),
        label = "chartAnimV3"
    )

    Canvas(modifier = modifier) {
        val padding = 20f
        val w = size.width - padding * 2
        val h = size.height - padding * 2
        val maxVal = 100f
        val stepX = if (data.size > 1) w / (data.size - 1) else w

        // 水平网格线 (25% / 50% / 75%)
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val y = padding + h * (1f - fraction)
            drawLine(
                color = DividerColor,
                start = Offset(padding, y),
                end = Offset(padding + w, y),
                strokeWidth = 0.5f
            )
        }

        val visibleCount = (data.size * animationProgress).toInt().coerceAtLeast(1)
        val visibleData = data.take(visibleCount)

        if (visibleData.size < 2) return@Canvas

        val path = Path()
        val fillPath = Path()

        visibleData.forEachIndexed { i, stat ->
            val x = padding + i * stepX
            val y = padding + h - (stat.smilePercent / maxVal) * h
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, padding + h)
                fillPath.lineTo(x, y)
            } else {
                val prevX = padding + (i - 1) * stepX
                val prevStat = visibleData[i - 1]
                val prevY = padding + h - (prevStat.smilePercent / maxVal) * h
                val ctrlX1 = prevX + (x - prevX) / 2
                val ctrlX2 = prevX + (x - prevX) / 2
                path.cubicTo(ctrlX1, prevY, ctrlX2, y, x, y)
                fillPath.cubicTo(ctrlX1, prevY, ctrlX2, y, x, y)
            }
        }

        fillPath.lineTo(padding + (visibleData.size - 1) * stepX, padding + h)
        fillPath.close()

        // 渐变填充区域
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillTopColor, fillBotColor),
                startY = padding, endY = padding + h
            )
        )
        // 平滑曲线
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor, lineColor.copy(alpha = 0.7f)),
                startY = padding, endY = padding + h
            ),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        // 节点 + 峰值高亮
        val maxPointIdx = visibleData.indices.maxByOrNull { visibleData[it].smilePercent } ?: 0
        visibleData.forEachIndexed { i, stat ->
            val x = padding + i * stepX
            val y = padding + h - (stat.smilePercent / maxVal) * h
            val isMax = i == maxPointIdx && stat.smilePercent > 0f
            val radius = if (isMax) 6f else 3f
            val color = if (isMax) AccentOrange else lineColor

            if (isMax) {
                drawCircle(color = AccentOrange.copy(alpha = 0.15f), radius = 16f, center = Offset(x, y))
                drawCircle(color = AccentOrange.copy(alpha = 0.06f), radius = 24f, center = Offset(x, y))
            }
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            drawCircle(color = Color.White, radius = radius * 0.3f, center = Offset(x, y))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 每分钟详情行 v3.0
// ═══════════════════════════════════════════════════════════════
@Composable
private fun MinuteStatRowV3(stat: MinuteStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stat.minuteKey.takeLast(5),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(48.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).height(22.dp)) {
            val fraction = (stat.smilePercent / 100f).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction, animationSpec = tween(600), label = "barFill"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barH = size.height
                val barW = size.width
                drawRoundRect(
                    color = DividerColor,
                    size = Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2)
                )
                if (animatedFraction > 0.01f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(AccentYellow.copy(alpha = 0.3f), AccentYellow)
                        ),
                        size = Size(barW * animatedFraction, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${stat.smileCount}次",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentYellow,
                fontSize = 14.sp
            )
            Text(
                text = "${stat.sampleCount}采样",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 周视图卡片 v3.0 系列
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WeeklyOverviewCardV3(summary: WeeklySummary?) {
    GlassCardV3(glowColor = AccentCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📅", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "本周概览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (summary != null) {
                        Text(
                            text = "${summary.weekStart} ~ ${summary.weekEnd}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            if (summary == null) {
                Text("加载中...", color = TextMuted)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeeklyStatItemV3("总微笑", "${summary.totalSmileCount}", AccentYellow)
                    WeeklyStatItemV3("日均", "${summary.avgDailySmile}", AccentGreen)
                    WeeklyStatItemV3("最佳", summary.bestDay, AccentCoral)
                }
                Surface(
                    color = AccentGreen.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📈", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "本周微笑频率比上周 ${summary.trend}，继续保持!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyStatItemV3(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            letterSpacing = (-0.5).sp
        )
        Text(text = label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun MoodHeatmapCardV3(heatmapData: List<DailyMoodHeatmap>) {
    GlassCardV3 {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎨", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "情绪热力图",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "本周每日心情颜色",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                heatmapData.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val color = lerp(AccentPink, AccentYellow, day.smileScore)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(color, color.copy(alpha = 0.6f))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    BorderStroke(1.dp, color.copy(alpha = 1f)), RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.dayLabel.take(1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${day.smileCount}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChartCardV3(heatmapData: List<DailyMoodHeatmap>) {
    GlassCardV3 {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "📊 周度柱状图",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (heatmapData.isNotEmpty()) {
                val maxCount = heatmapData.maxOf { it.smileCount }.coerceAtLeast(1)
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                ) {
                    val barWidth = size.width / (heatmapData.size * 2f)
                    val chartHeight = size.height - 20f
                    heatmapData.forEachIndexed { index, day ->
                        val barHeight = (day.smileCount.toFloat() / maxCount) * chartHeight
                        val x = (index * 2 + 0.5f) * barWidth
                        val y = chartHeight - barHeight
                        val barColor = lerp(AccentPink, AccentYellow, day.smileScore)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    heatmapData.forEach { day ->
                        Text(
                            text = day.dayLabel.takeLast(2),
                            fontSize = 10.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyInsightCardV3(summary: WeeklySummary?) {
    if (summary == null) return
    GlassCardV3(glowColor = AccentCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💎", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "周度洞察",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Text(
                        text = "本周情绪总结",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            val insight = when {
                summary.avgDailySmile > 35 ->
                    "本周你笑得很多！日均 ${"%.0f".format(summary.avgDailySmile)} 次，工作状态阳光积极。"
                summary.avgDailySmile > 20 ->
                    "本周情绪稳定，日均 ${"%.0f".format(summary.avgDailySmile)} 次微笑。${summary.bestDay} 状态最佳!"
                else ->
                    "本周微笑偏少（日均 ${"%.0f".format(summary.avgDailySmile)} 次），注意调节压力。"
            }
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 工具函数 (保留)
// ═══════════════════════════════════════════════════════════════
private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}
