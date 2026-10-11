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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
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
// v4.0 全新视觉系统 — 深空极光 + 呼吸玻璃 + 智能叙事
// ═══════════════════════════════════════════════════════════════

private val OledBackground = Color(0xFF000000)
private val DeepSpace      = Color(0xFF050510)
private val SurfaceCard    = Color(0xFF0D0D12)
private val SurfaceCardHi  = Color(0xFF131319)
private val SurfaceGlass   = Color(0xFF1A1A22).copy(alpha = 0.88f)
private val DividerColor   = Color(0xFF1A1A22)

private val AccentYellow = Color(0xFFFFD480)
private val AccentBlue   = Color(0xFF7EB8F7)
private val AccentGreen  = Color(0xFF8FD6A0)
private val AccentPink   = Color(0xFFF2A0C4)
private val AccentPurple = Color(0xFFC4A8E8)
private val AccentOrange = Color(0xFFFFC27A)
private val AccentCyan   = Color(0xFF6EE2E2)
private val AccentCoral  = Color(0xFFFFA08C)
private val AccentRose   = Color(0xFFFF8FA3)
private val AccentMint   = Color(0xFF7BF0C0)

private val GlassHighlight = Color.White.copy(alpha = 0.04f)
private val GlassBorder    = Color.White.copy(alpha = 0.06f)
private val GlassGlow      = Color.White.copy(alpha = 0.02f)
private val GlassShadow    = Color.Black.copy(alpha = 0.3f)

private val TextPrimary   = Color(0xFFF0F0F5)
private val TextSecondary = Color(0xFFA0A0B8)
private val TextMuted     = Color(0xFF50506A)
private val TextDisabled  = Color(0xFF2A2A38)
private val TextGlow      = Color(0xFFFFE0B2)

// v4.0 动画常量
private const val BREATHING_CYCLE_MS = 4000
private const val ENTRANCE_STIFFNESS = 180f
private val ENTRANCE_SPRING = spring<Float>(dampingRatio = 0.75f, stiffness = ENTRANCE_STIFFNESS)
private val MICRO_SPRING = spring<Float>(dampingRatio = 0.6f, stiffness = 200f)
private val COLOR_SPRINE = spring<Color>(dampingRatio = 0.8f, stiffness = 120f)
private const val GLOW_PULSE_MS = 300
private const val CHART_ANIM_MS = 1800
private const val BURNIN_PERIOD_X_MS = 90000
private const val BURNIN_PERIOD_Y_MS = 75000
private const val PARTICLE_COUNT = 15
private const val STATS_HORIZONTAL_PADDING = 14
private const val STATS_VERTICAL_PADDING = 18
private const val CARD_RADIUS_DP = 22


// ═══════════════════════════════════════════════════════════════
// v4.0 玻璃拟态 Modifier 扩展
// ═══════════════════════════════════════════════════════════════

/**
 * v4.0 玻璃拟态效果— 微光高光 + 顶底渐变 + 微光边框
 */
fun Modifier.glassmorphismV4(
    alpha: Float = 0.04f,
    borderAlpha: Float = 0.06f
): Modifier = this.drawBehind {
    drawRect(color = Color.White.copy(alpha = alpha))
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.03f), Color.Transparent),
            startY = 0f, endY = size.height * 0.3f
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.01f)),
            startY = size.height * 0.7f, endY = size.height
        )
    )
    drawRect(
        color = Color.White.copy(alpha = borderAlpha),
        style = Stroke(width = 0.5.dp.toPx())
    )
}

/**
 * v4.0 呼吸玻璃卡片 Modifier — 缓慢缩放 0.98-1.02 配合玻璃效果
 */
fun Modifier.breathingGlassCardV4(
    breathScale: Float = 1f
): Modifier = this
    .scale(breathScale)
    .glassmorphismV4()

// ═══════════════════════════════════════════════════════════════
// Dashboard 入口
// ═══════════════════════════════════════════════════════════════

/**
 * Compose Multiplatform 共享 UI — Dashboard v4.0
 *
 * 升级亮点：
 *  - 呼吸玻璃拟态系统 v4 (缓慢缩放 + 微光边框)
 *  - 数据讲故事卡片 (自然语言总结)
 *  - 呼吸引导卡片 (4-7-8 呼吸法)
 *  - 心情轮播时间线
 *  - 环境光适配提示
 *  - 智能建议引擎
 *  - 增强粒子背景 (15粒子 + 3种粒子类型)
 *  - Spring 物理动画系统 v4
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


// ═══════════════════════════════════════════════════════════════
// Dashboard 主体屏幕
// ═══════════════════════════════════════════════════════════════

/**
 * Dashboard v4.0 主屏幕 — 日/周视图切换 + 多层增强卡片
 */
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

    val burnInTransition = rememberInfiniteTransition(label = "burnInV4")
    val burnInX by burnInTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(BURNIN_PERIOD_X_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "burnInXV4"
    )
    val burnInY by burnInTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(BURNIN_PERIOD_Y_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "burnInYV4"
    )

    LaunchedEffect(Unit) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date
        val start = today.atTime(LocalTime(0, 0, 0))
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val end = today.atTime(23, 59, 59)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        todayStats = runCatching { repo.queryMinuteStats(start, end) }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            latestFace = runCatching { repo.getLatest() }.getOrNull()
            delay(2000L)
        }
    }

    LaunchedEffect(Unit) {
        weeklySummary = WeeklySummary(
            weekStart = "2026-10-06", weekEnd = "2026-10-12",
            totalSmileCount = 168, avgDailySmile = 28.0f,
            bestDay = "周三", bestDaySmileCount = 42, trend = "+18%"
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
        ParticleBackgroundV4()

        Scaffold(
            topBar = { DashboardTopBarV4(showWeekly = showWeekly, onToggleWeekly = { showWeekly = !showWeekly }) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp)
                    .offset(x = burnInX.dp, y = burnInY.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                if (showWeekly) {
                    item { WeeklyOverviewCardV4(summary = weeklySummary) }
                    item { MoodHeatmapCardV4(heatmapData = moodHeatmap) }
                    item { WeeklyBarChartCardV4(heatmapData = moodHeatmap) }
                    item { WeeklyInsightCardV4(summary = weeklySummary) }
                } else {
                    item { AnimatedDashboardItemV4(delayMs = 0) { RealTimeFaceCardV4(face = latestFace) } }
                    item { AnimatedDashboardItemV4(delayMs = 60) {
                        val smileSum = todayStats.sumOf { it.smileCount.toLong() }
                        val sampleSum = todayStats.sumOf { it.sampleCount.toLong() }
                        val avgSmileVal = if (todayStats.isNotEmpty())
                            todayStats.map { it.smilePercent.toDouble() }.average() else 0.0
                        val eyeStrain = if (todayStats.isNotEmpty()) {
                            val avgEye = todayStats.map {
                                (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f
                            }.average()
                            ((1.0 - avgEye.coerceAtLeast(0.0)) * 100).coerceIn(0.0, 100.0)
                        } else 0.0
                        StatsGridV4(smileSum = smileSum, sampleSum = sampleSum,
                            avgSmile = avgSmileVal, eyeStrain = eyeStrain)
                    } }
                    item { AnimatedDashboardItemV4(delayMs = 120) { AuroraDataStoryCardV4(todayStats = todayStats) } }
                    item { AnimatedDashboardItemV4(delayMs = 180) { BreathingGuideCardV4() } }
                    item { AnimatedDashboardItemV4(delayMs = 240) { MoodCarouselCardV4(todayStats = todayStats) } }
                    item { AnimatedDashboardItemV4(delayMs = 300) { WorkRhythmCardV4(todayStats = todayStats) } }
                    item { AnimatedDashboardItemV4(delayMs = 360) { InsightCardV4(todayStats = todayStats, latestFace = latestFace) } }
                    item { AnimatedDashboardItemV4(delayMs = 420) { TrendChartSectionV4(todayStats = todayStats) } }
                    item { AnimatedDashboardItemV4(delayMs = 480) { AmbientLightAdapterCardV4() } }
                    item { AnimatedDashboardItemV4(delayMs = 540) { SmartSuggestionCardV4(todayStats = todayStats, latestFace = latestFace) } }
                    if (todayStats.isNotEmpty()) {
                        item { AnimatedDashboardItemV4(delayMs = 600) {
                            Text(
                                text = "⏱ 每分锺详情",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } }
                        itemsIndexed(todayStats.reversed()) { index, stat ->
                            AnimatedDashboardItemV4(delayMs = 600 + index * 20) {
                                Column {
                                    MinuteStatRowV4(stat = stat, index = index)
                                    if (index < todayStats.size - 1) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp, color = DividerColor,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 顶部操作栏
// ═══════════════════════════════════════════════════════════════

/**
 * Dashboard v4.0 顶部栏 — 标题 + 日/周切换 + 设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBarV4(showWeekly: Boolean, onToggleWeekly: () -> Unit) {
    val titleColor by animateColorAsState(
        targetValue = if (showWeekly) AccentPurple else TextPrimary,
        animationSpec = COLOR_SPRINE, label = "titleColorV4"
    )

    CenterAlignedTopAppBar(
        title = {
            AnimatedContent(
                targetState = showWeekly,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "titleSwitchV4"
            ) { weekly ->
                Text(
                    text = if (weekly) "📊 本周概览" else "😊 打工人表情追踪",
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = if (weekly) AccentPurple else TextPrimary
                )
            }
        },
        actions = {
            Surface(
                onClick = onToggleWeekly,
                shape = RoundedCornerShape(20.dp),
                color = if (showWeekly) AccentPurple.copy(alpha = 0.12f) else DeepSpace.copy(alpha = 0.6f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (showWeekly) AccentPurple.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f)
                ),
                modifier = Modifier.height(38.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(
                        text = if (showWeekly) "周" else "日", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = if (showWeekly) AccentPurple else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (showWeekly) "→ 日" else "→ 周", fontSize = 11.sp, color = TextMuted)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                onClick = { },
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
            containerColor = Color.Transparent, titleContentColor = TextPrimary
        )
    )
}


// ═══════════════════════════════════════════════════════════════
// v4.0 微光粒子背景 — 深邃宇宙 + 3种粒子类型
// ═══════════════════════════════════════════════════════════════

/**
 * v4.0 粒子背景 — 15粒子 + 3种粒子类型 (漂浮星尘 + 上升光点 + 固定亮点)
 */
@Composable
private fun ParticleBackgroundV4() {
    val particles = remember { generateParticlesV4() }
    val infiniteTransition = rememberInfiniteTransition(label = "particlesV4")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = PI.toFloat() * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "particleTimeV4"
    )

    Canvas(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        particles.forEach { p ->
            val currentAlpha = p.alpha * (0.5f + 0.5f * sin(time * p.speed + p.phase))
            val currentY = when (p.type) {
                ParticleType.FLOATING -> (p.y + time * p.speed * 0.01f) % 1f
                ParticleType.RISING -> (p.y - time * p.speed * 0.02f + 2f) % 1f
                ParticleType.STATIC -> p.y
            }
            val currentX = when (p.type) {
                ParticleType.FLOATING -> (p.x + sin(time * p.speed * 0.5f + p.phase) * 0.02f + 1f) % 1f
                else -> p.x
            }
            drawCircle(
                color = p.color.copy(alpha = currentAlpha * p.intensity),
                radius = p.size,
                center = Offset(currentX * size.width, currentY * size.height)
            )
        }
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

private enum class ParticleType { FLOATING, RISING, STATIC }

private data class ParticleDataV4(
    val x: Float, val y: Float, val size: Float,
    val speed: Float, val phase: Float, val alpha: Float,
    val color: Color, val intensity: Float, val type: ParticleType
)

private fun generateParticlesV4(): List<ParticleDataV4> {
    return List(PARTICLE_COUNT) { i ->
        val type = when (i % 3) {
            0 -> ParticleType.FLOATING
            1 -> ParticleType.RISING
            else -> ParticleType.STATIC
        }
        val color = when (i % 4) {
            0 -> AccentCyan
            1 -> AccentPurple
            2 -> AccentBlue
            else -> AccentMint
        }
        ParticleDataV4(
            x = Random.nextFloat(), y = Random.nextFloat(),
            size = Random.nextFloat() * 2f + 0.5f,
            speed = Random.nextFloat() * 0.3f + 0.1f,
            phase = Random.nextFloat() * PI.toFloat() * 2f,
            alpha = Random.nextFloat() * 0.4f + 0.1f,
            color = color, intensity = if (type == ParticleType.STATIC) 0.3f else 0.6f,
            type = type
        )
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 可见性动画包装器
// ═══════════════════════════════════════════════════════════════

/**
 * v4.0 入场动画 — spring 弹性弹出 + 垂直滑动渐入
 */
@Composable
private fun AnimatedDashboardItemV4(delayMs: Int = 0, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, easing = EaseOutCubic)) + slideInVertically(
            initialOffsetY = { it / 4 }, animationSpec = ENTRANCE_SPRING
        )
    ) { content() }
}

// ═══════════════════════════════════════════════════════════════
// v4.0 动态玻璃拟态卡片容器
// ═══════════════════════════════════════════════════════════════

/**
 * v4.0 玻璃拟态卡片 — 呼吸效果 + 内发光 + 微光边框
 */
@Composable
private fun GlassCardV4(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    isActive: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val breathTransition = rememberInfiniteTransition(label = "glassBreath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.98f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATHING_CYCLE_MS, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "breathScaleV4"
    )
    val effectiveScale = if (isActive) breathScale else 1f

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(effectiveScale),
        colors = CardDefaults.elevatedCardColors(containerColor = SurfaceGlass),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(CARD_RADIUS_DP.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawV4GlassEffects(glowColor)
                .padding(18.dp),
            content = content
        )
    }
}

/**
 * v4.0 玻璃效果绘制 — 微光高光 + 底部微光 + 外发光 + 玻璃边框
 */
private fun Modifier.drawV4GlassEffects(glowColor: Color): Modifier = this.drawBehind {
    drawRect(brush = Brush.verticalGradient(
        colors = listOf(GlassHighlight, Color.Transparent),
        startY = 0f, endY = size.height * 0.3f
    ))
    drawRect(brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, GlassGlow),
        startY = size.height * 0.7f, endY = size.height
    ))
    if (glowColor != Color.Transparent) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(glowColor.copy(alpha = 0.15f), glowColor.copy(alpha = 0.05f)),
                startY = 0f, endY = size.height
            ),
            style = Stroke(width = 1.5.dp.toPx()),
            cornerRadius = CornerRadius(22f, 22f)
        )
    }
    drawRoundRect(
        color = GlassBorder,
        style = Stroke(width = 0.75.dp.toPx()),
        cornerRadius = CornerRadius(22f, 22f)
    )
}


// ═══════════════════════════════════════════════════════════════
// v4.0 实时状态卡 — 双层仪表盘
// ═══════════════════════════════════════════════════════════════

/**
 * 实时表情状态卡 v4.0 — 呼吸光效 + 双层仪表盘 + 情绪动画
 */
@Composable
private fun RealTimeFaceCardV4(face: FaceAnalysis?) {
    val breathingAnim = rememberInfiniteTransition(label = "breathingV4")
    val glowAlpha by breathingAnim.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "glowV4"
    )
    val ringPulse by breathingAnim.animateFloat(
        initialValue = 0.95f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "ringPulseV4"
    )

    val smileProgress = face?.smileProbability?.coerceAtLeast(0f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = smileProgress,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 70f),
        label = "smileSpringV4"
    )
    val animatedInnerRing by animateFloatAsState(
        targetValue = face?.let {
            if (it.isSmiling) 0.9f else if (it.smileProbability > 0.4f) 0.6f else 0.2f
        } ?: 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 90f),
        label = "innerRingV4"
    )

    val accentColor by animateColorAsState(
        targetValue = when {
            animatedProgress >= 0.7f -> AccentYellow
            animatedProgress >= 0.4f -> AccentCyan
            face == null -> TextMuted
            else -> AccentBlue
        },
        animationSpec = COLOR_SPRINE, label = "accentColorV4"
    )

    GlassCardV4(glowColor = accentColor) {
        Column {
            RealTimeHeaderRowV4(face = face, accentColor = accentColor, glowAlpha = glowAlpha, ringPulse = ringPulse)
            Spacer(modifier = Modifier.height(22.dp))
            if (face == null) { RealTimeEmptyStateV4() }
            else { RealTimeFaceContentV4(face = face, animatedProgress = animatedProgress, animatedInnerRing = animatedInnerRing, accentColor = accentColor) }
        }
    }
}

/** 实时状态卡顶部栏 */
@Composable
private fun RealTimeHeaderRowV4(face: FaceAnalysis?, accentColor: Color, glowAlpha: Float, ringPulse: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(12.dp * ringPulse).background(
                brush = Brush.radialGradient(listOf(accentColor.copy(alpha = glowAlpha), Color.Transparent)),
                shape = CircleShape
            ))
            Box(modifier = Modifier.size(6.dp).background(accentColor.copy(alpha = glowAlpha), CircleShape))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "实时状态", style = MaterialTheme.typography.labelLarge, color = TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier
            .background(color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
            .border(0.5.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(5.dp).background(accentColor.copy(alpha = glowAlpha), CircleShape))
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "LIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accentColor, letterSpacing = 2.sp)
            }
        }
    }
}

/** 实时状态空状态 — 摄像头未就绪 */
@Composable
private fun RealTimeEmptyStateV4() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "🎯", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "正在初始化摄像头...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "请将手机前置摄像头对准面部", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** 实时表情数据 + 双层仪表盘 */
@Composable
private fun RealTimeFaceContentV4(face: FaceAnalysis, animatedProgress: Float, animatedInnerRing: Float, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmotionLabelAnimatedV4(face = face)
            AnimatedNumberV4(targetValue = face.smileProbability.coerceAtLeast(0f), fontSize = 36.sp, color = accentColor, suffix = "%")
            Text(text = "微笑概率", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            if (face.leftEyeOpenProbability >= 0f) {
                Spacer(modifier = Modifier.height(4.dp))
                EyeOpenRowV4("L", face.leftEyeOpenProbability)
                Spacer(modifier = Modifier.height(2.dp))
                EyeOpenRowV4("R", face.rightEyeOpenProbability)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        DualRingGaugeV4(progress = animatedProgress, innerProgress = animatedInnerRing, isSmiling = face.isSmiling, size = 100.dp, glowColor = accentColor)
    }
}

/** 情绪分类标签动画 */
@Composable
private fun EmotionLabelAnimatedV4(face: FaceAnalysis) {
    AnimatedContent(
        targetState = face.isSmiling to (face.smileProbability >= 0.4f),
        transitionSpec = {
            fadeIn(tween(400)) scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.7f))
                togetherWith fadeOut(tween(200)) scaleOut(targetScale = 0.8f, animationSpec = tween(200))
        },
        label = "emotionLabelV4"
    ) { (smiling, pleasant) ->
        val (emoji, label, color) = when {
            smiling -> Triple("😊", "微笑中", AccentYellow)
            pleasant -> Triple("🙂", "心情愉悦", AccentCyan)
            face.smileProbability >= 0.2f -> Triple("😐", "平静中性", AccentBlue)
            else -> Triple("😶", "专注/疲劳", TextSecondary)
        }
        Text(text = "$emoji $label", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 双层微笑仪表盘
// ═══════════════════════════════════════════════════════════════

/**
 * v4.0 双层环形仪表盘 — 外环微笑进度 + 内环情绪强度
 */
@Composable
private fun DualRingGaugeV4(progress: Float, innerProgress: Float, isSmiling: Boolean, size: Dp, glowColor: Color) {
    val sweepAngle = progress.coerceIn(0f, 1f) * 360f

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 7.dp.toPx()
            val outerRadius = (this.size.minDimension - strokeW) / 2
            val innerRadius = outerRadius - strokeW - 6.dp.toPx()
            val center = Offset(this.size.width / 2, this.size.height / 2)

            if (progress > 0.05f) {
                drawCircle(color = glowColor.copy(alpha = 0.12f),
                    radius = outerRadius + strokeW / 2 + 6.dp.toPx(), center = center)
            }
            drawArc(color = DividerColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = strokeW, cap = StrokeCap.Round))
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(colors = listOf(glowColor.copy(alpha = 0.2f), glowColor, glowColor.copy(alpha = 0.8f))),
                    startAngle = -90f, sweepAngle = sweepAngle, useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }
            if (sweepAngle > 5f) {
                val angle = Math.toRadians((-90.0 + sweepAngle).toDouble())
                val dotX = center.x + outerRadius * cos(angle).toFloat()
                val dotY = center.y + outerRadius * sin(angle).toFloat()
                drawCircle(color = Color.White, radius = strokeW / 2 + 2.dp.toPx(), center = Offset(dotX, dotY))
            }
            val innerSweep = innerProgress * 360f
            val innerStrokeW = 4.dp.toPx()
            drawArc(color = DividerColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = innerStrokeW, cap = StrokeCap.Round))
            if (innerSweep > 0f) {
                drawArc(color = AccentPurple.copy(alpha = 0.6f), startAngle = -90f, sweepAngle = innerSweep, useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = innerStrokeW, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = if (isSmiling) "😊" else if (progress >= 0.3f) "🙂" else "😐", fontSize = 24.sp)
            if (progress > 0f) {
                Text(text = "${(progress * 100).toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = glowColor)
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 数字滚动动画 + 眼部数据行
// ═══════════════════════════════════════════════════════════════

/**
 * 数字滚动动画 v4.0 — spring 弹性数字过渡
 */
@Composable
private fun AnimatedNumberV4(targetValue: Float, fontSize: androidx.compose.ui.unit.TextUnit, color: Color, suffix: String = "") {
    val currentValue by animateFloatAsState(
        targetValue = targetValue * 100,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "numAnimV4"
    )
    Text(text = "${"%.0f".format(currentValue)}$suffix", fontSize = fontSize, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = (-1).sp)
}

/** 眼部数据行 v4.0 — 渐变进度条 */
@Composable
private fun EyeOpenRowV4(label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium, modifier = Modifier.width(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        val animatedFraction by animateFloatAsState(targetValue = value.coerceIn(0f, 1f), animationSpec = tween(500), label = "eyeBarV4")
        Box(modifier = Modifier.weight(1f).height(5.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(color = Color.White.copy(alpha = 0.05f), size = Size(size.width, size.height), cornerRadius = CornerRadius(2.5f, 2.5f))
                drawRoundRect(
                    brush = Brush.horizontalGradient(colors = listOf(AccentBlue.copy(alpha = 0.6f), AccentBlue)),
                    size = Size(size.width * animatedFraction, size.height),
                    cornerRadius = CornerRadius(2.5f, 2.5f)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "%.0f%%".format(value.coerceIn(0f, 1f) * 100), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(32.dp))
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 统计数字网格 + 微交互
// ═══════════════════════════════════════════════════════════════

/** 统计数字网格 v4.0 — 玻璃拟态 + 按压微交互 + 流光边框 */
@Composable
private fun StatsGridV4(smileSum: Long, sampleSum: Long, avgSmile: Double, eyeStrain: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCardV4(modifier = Modifier.weight(1f), icon = "😊", label = "微笑次数", value = smileSum.toString(), accent = AccentYellow, delayAnim = 0)
            StatCardV4(modifier = Modifier.weight(1f), icon = "📊", label = "采样总数", value = sampleSum.toString(), accent = AccentBlue, delayAnim = 60)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCardV4(modifier = Modifier.weight(1f), icon = "📈", label = "微笑占比", value = "%.1f%%".format(avgSmile), accent = AccentGreen, delayAnim = 120)
            StatCardV4(modifier = Modifier.weight(1f), icon = "👁", label = "眼部疲劳", value = "%.0f%%".format(eyeStrain), accent = AccentPink, delayAnim = 180)
        }
    }
}

/** 单个统计数字卡片 v4.0 — 按压回弹 + 渐变流光顶线 */
@Composable
private fun StatCardV4(modifier: Modifier = Modifier, icon: String, label: String, value: String, accent: Color, delayAnim: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayAnim.toLong()); visible = true }

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MICRO_SPRING, label = "pressV4"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = 0.7f))
    ) {
        ElevatedCard(
            modifier = modifier.scale(pressScale)
                .clickable { pressed = !pressed },
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceCardHi),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                StatCardTopAccentV4(accent = accent)
                Column(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = icon, fontSize = 22.sp)
                    AnimatedNumberV4(targetValue = value.toFloatOrNull() ?: 0f, fontSize = 28.sp, color = accent)
                    Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

/** 卡片顶部渐变流光装饰 */
@Composable
private fun StatCardTopAccentV4(accent: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(2.5.dp).drawBehind {
        drawRect(brush = Brush.horizontalGradient(colors = listOf(accent.copy(alpha = 0.6f), accent.copy(alpha = 0.15f), Color.Transparent)))
    })
}


// ═══════════════════════════════════════════════════════════════
// v4.0 工作节律分析
// ═══════════════════════════════════════════════════════════════

/** 工作节律分析卡片 v4.0 — 自动识别情绪高峰/低谷 + 时间轴 */
@Composable
private fun WorkRhythmCardV4(todayStats: List<MinuteStat>) {
    val (peakHour, troughHour, rhythmInsight) = remember(todayStats) {
        val peak = if (todayStats.isNotEmpty()) {
            todayStats.groupBy { it.minuteKey.substring(11, 13) }
                .mapValues { (_, s) -> s.sumOf { it.smileCount.toLong() } }.maxByOrNull { it.value }?.key
        } else null
        val trough = if (todayStats.isNotEmpty()) {
            todayStats.groupBy { it.minuteKey.substring(11, 13) }
                .mapValues { (_, s) -> s.sumOf { it.smileCount.toLong() } }.filter { it.value > 0 }
                .minByOrNull { it.value }?.key
        } else null
        val insight = when {
            peak == null -> "数据采集中...请先让摄像头运行一段时间"
            peak == trough -> "今天工作节奏平稳，情绪较为均匀"
            else -> "${peak}时是你最开心的时段。${if (trough != null) "${trough}时左右是你的低谷时段。" else ""}在低谷时段可以起身走动、喝水补充能量。"
        }
        Triple(peak, trough, insight)
    }

    GlassCardV4(glowColor = AccentCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkRhythmHeaderV4()
            if (todayStats.isNotEmpty()) { RhythmTimelineV4(stats = todayStats) }
            Text(text = rhythmInsight, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 22.sp)
            WorkRhythmChipsV4(peakHour = peakHour, troughHour = troughHour)
        }
    }
}

@Composable
private fun WorkRhythmHeaderV4() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).background(brush = Brush.linearGradient(listOf(AccentCyan, AccentBlue)), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(text = "⚡", fontSize = 16.sp) }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = "工作节律", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentCyan)
            Text(text = "自动分析你的情绪规律", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun WorkRhythmChipsV4(peakHour: String?, troughHour: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (peakHour != null) { SuggestionChipV4("⏰ ${peakHour}时最开心", AccentYellow) }
        if (troughHour != null && troughHour != peakHour) { SuggestionChipV4("💧 ${troughHour}时注意补水", AccentBlue) }
    }
}

@Composable
private fun SuggestionChipV4(text: String, accent: Color) {
    Surface(color = accent.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, accent.copy(alpha = 0.25f))) {
        Text(text = text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RhythmTimelineV4(stats: List<MinuteStat>) {
    val hourlyData = remember(stats) {
        stats.groupBy { it.minuteKey.substring(11, 13) }.mapValues { (_, st) -> st.sumOf { it.smileCount.toLong() }.toInt() }.toSortedMap()
    }
    val maxVal = hourlyData.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        val barWidth = if (hourlyData.size > 1) size.width / hourlyData.size else size.width
        val chartHeight = size.height
        hourlyData.entries.forEachIndexed { index, (_, count) ->
            val barHeight = (count.toFloat() / maxVal) * chartHeight
            val x = index * barWidth + barWidth * 0.15f
            val w = barWidth * 0.7f
            val y = chartHeight - barHeight
            drawRoundRect(
                color = lerpV4(AccentBlue, AccentYellow, count.toFloat() / maxVal).copy(alpha = 0.7f),
                topLeft = Offset(x, y), size = Size(w, barHeight),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
        drawLine(color = Color.White.copy(alpha = 0.05f), start = Offset(0f, chartHeight), end = Offset(size.width, chartHeight), strokeWidth = 0.5f)
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 智能洞察卡片
// ═══════════════════════════════════════════════════════════════

/** 智能洞察卡片 v4.0 — 情绪分析 + 压力指数 + 多条洞察 */
@Composable
private fun InsightCardV4(todayStats: List<MinuteStat>, latestFace: FaceAnalysis?) {
    val insights = remember(todayStats, latestFace) { generateInsightsV4(todayStats, latestFace) }
    val primaryInsight = insights.firstOrNull() ?: "正在收集数据，稍后将为你提供个性化的工作状态分析。"
    val stressLevel = calculateStressIndexV4(todayStats, latestFace)

    GlassCardV4(glowColor = AccentPurple) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InsightHeaderV4(stressLevel = stressLevel)
            Text(text = primaryInsight, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 22.sp)
            if (insights.size > 1) { InsightSubListV4(insights.drop(1).take(2)) }
        }
    }
}

@Composable
private fun InsightHeaderV4(stressLevel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(38.dp).background(brush = Brush.linearGradient(listOf(AccentPurple, AccentPink)), shape = CircleShape), contentAlignment = Alignment.Center) {
            Text(text = "🧠", fontSize = 17.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = "智能洞察", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentPurple)
            Text(text = "压力指数 $stressLevel", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        Surface(color = AccentPurple.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(0.5.dp, AccentPurple.copy(alpha = 0.25f))) {
            Text(text = "AI 分析", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.sp, color = AccentPurple, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InsightSubListV4(extraInsights: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        extraInsights.forEach { insight ->
            Surface(color = Color.White.copy(alpha = 0.02f), shape = RoundedCornerShape(10.dp)) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Text(text = "·", color = AccentPurple, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = insight, style = MaterialTheme.typography.bodySmall, color = TextMuted, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun calculateStressIndexV4(stats: List<MinuteStat>, face: FaceAnalysis?): String {
    if (stats.isEmpty() && face == null) return "--"
    val avgSmile = stats.map { it.smilePercent.toDouble() }.average()
    val eyeStrainPct = if (stats.isNotEmpty()) {
        val avgEye = stats.map { (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f }.average()
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

private fun generateInsightsV4(stats: List<MinuteStat>, face: FaceAnalysis?): List<String> {
    if (stats.isEmpty() && face == null) return listOf("正在收集数据，稍后将为你提供个性化的工作状态分析。")
    val insights = mutableListOf<String>()
    val avgSmile = stats.map { it.smilePercent.toDouble() }.average()
    val totalSamples = stats.sumOf { it.sampleCount.toLong() }
    val highStrainMinutes = stats.count { stat ->
        val eyeAvg = (stat.avgEyeLeft.coerceAtLeast(0f) + stat.avgEyeRight.coerceAtLeast(0f)) / 2f
        eyeAvg < 0.3f
    }
    val smileVariance = if (stats.size > 1) { val mean = avgSmile; stats.map { (it.smilePercent - mean) * (it.smilePercent - mean) }.average() } else 0.0
    val smileVolatility = kotlin.math.sqrt(smileVariance)

    insights += when {
        face == null -> "摄像头尚未就绪，请将手机前置摄像头对准面部。"
        avgSmile > 60 -> "今天心情不错！微笑占比较高（${"%.0f".format(avgSmile)}%），保持这份好状态 😊"
        avgSmile > 30 -> "微笑频率适中（${"%.0f".format(avgSmile)}%）。记得适时起身活动，眺望远方放松眼睛。"
        highStrainMinutes > 3 -> "检测到 ${highStrainMinutes} 次眼部疲劳信号，建议每 40 分钟做一次眼保健操 👀"
        totalSamples < 10 -> "数据采样较少（$totalSamples），保持手机亮屏运行以获取更准确的分析。"
        else -> "今天整体表情偏中性，工作压力可能偏高。午休时去户外走走晒晒太阳吧 ☀️"
    }
    if (stats.size > 5 && smileVolatility > 20) { insights += "今日情绪波动较大（标准差 ${"%.1f".format(smileVolatility)}），注意保持心态平和。" }
    if (highStrainMinutes in 1..3) { insights += "检测到零星的眼睛干涩信号，试试 20-20-20 护眼法则：每 20 分钟远眺 20 英尺 20 秒。" }
    if (stats.size > 20) {
        val latePeriod = stats.filter { it.minuteKey.substring(11, 13).toInt() >= 15 }
        if (latePeriod.isNotEmpty()) {
            val lateAvg = latePeriod.map { it.smilePercent.toDouble() }.average()
            if (lateAvg < avgSmile * 0.7) { insights += "下午时段微笑频率明显下降，建议在 15:00-16:00 安排短暂休息或茶歇。" }
        }
    }
    return insights
}


// ═══════════════════════════════════════════════════════════════
// v4.0 趋势图表 + 每分钟详情
// ═══════════════════════════════════════════════════════════════

/** 趋势图表卡片 v4.0 — 渐变填充 + 峰值高亮 */
@Composable
private fun TrendChartSectionV4(todayStats: List<MinuteStat>) {
    GlassCardV4 {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(30.dp).background(AccentYellow.copy(alpha = 0.08f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(text = "📉", fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "微笑趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (todayStats.isNotEmpty()) { Text(text = "共 ${todayStats.size} 个数据点", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            EnhancedSmileyChartV4(modifier = Modifier.fillMaxWidth().height(200.dp), data = todayStats)
        }
    }
}

@Composable
private fun EnhancedSmileyChartV4(modifier: Modifier, data: List<MinuteStat>) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "⏳", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "暂无数据", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(text = "摄像头就绪后开始自动记录", color = TextMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
            }
        }
        return
    }
    val lineColor = AccentYellow
    val fillTopColor = AccentYellow.copy(alpha = 0.2f)
    val fillBotColor = AccentYellow.copy(alpha = 0.0f)
    val animationProgress by animateFloatAsState(targetValue = 1f, animationSpec = tween(durationMillis = CHART_ANIM_MS, easing = EaseOutCubic), label = "chartAnimV4")

    Canvas(modifier = modifier) {
        val padding = 20f
        val w = size.width - padding * 2
        val h = size.height - padding * 2
        val maxVal = 100f
        val stepX = if (data.size > 1) w / (data.size - 1) else w
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            val y = padding + h * (1f - fraction)
            drawLine(color = DividerColor, start = Offset(padding, y), end = Offset(padding + w, y), strokeWidth = 0.5f)
        }
        val visibleCount = (data.size * animationProgress).toInt().coerceAtLeast(1)
        val visibleData = data.take(visibleCount)
        if (visibleData.size < 2) return@Canvas
        val path = Path()
        val fillPath = Path()
        visibleData.forEachIndexed { i, stat ->
            val x = padding + i * stepX
            val y = padding + h - (stat.smilePercent / maxVal) * h
            if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, padding + h); fillPath.lineTo(x, y) }
            else {
                val prevX = padding + (i - 1) * stepX
                val prevStat = visibleData[i - 1]
                val prevY = padding + h - (prevStat.smilePercent / maxVal) * h
                val ctrlX1 = prevX + (x - prevX) / 2
                path.cubicTo(ctrlX1, prevY, ctrlX1, y, x, y)
                fillPath.cubicTo(ctrlX1, prevY, ctrlX1, y, x, y)
            }
        }
        fillPath.lineTo(padding + (visibleData.size - 1) * stepX, padding + h)
        fillPath.close()
        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(fillTopColor, fillBotColor), startY = padding, endY = padding + h))
        drawPath(path = path, brush = Brush.verticalGradient(colors = listOf(lineColor, lineColor.copy(alpha = 0.7f)), startY = padding, endY = padding + h), style = Stroke(width = 3f, cap = StrokeCap.Round))
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


/** 每分钟详情行 v4.0 — 点击展开动画 + glow 脉冲 */
@Composable
private fun MinuteStatRowV4(stat: MinuteStat, index: Int) {
    var expanded by remember { mutableStateOf(false) }
    val animatedFraction by animateFloatAsState(targetValue = (stat.smilePercent / 100f).coerceIn(0f, 1f), animationSpec = tween(600), label = "barFillV4")

    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stat.minuteKey.takeLast(5), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(48.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f).height(22.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barH = size.height; val barW = size.width
                    drawRoundRect(color = DividerColor, size = Size(barW, barH), cornerRadius = CornerRadius(barH / 2))
                    if (animatedFraction > 0.01f) {
                        drawRoundRect(brush = Brush.horizontalGradient(colors = listOf(AccentYellow.copy(alpha = 0.3f), AccentYellow)),
                            size = Size(barW * animatedFraction, barH), cornerRadius = CornerRadius(barH / 2))
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${stat.smileCount}次", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AccentYellow, fontSize = 14.sp)
                Text(text = "${stat.sampleCount}采样", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(animationSpec = spring(0.8f, 200f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            MinuteStatDetailV4(stat = stat)
        }
    }
}

@Composable
private fun MinuteStatDetailV4(stat: MinuteStat) {
    Surface(color = Color.White.copy(alpha = 0.02f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            DetailItemV4("左眼", stat.avgEyeLeft, AccentBlue)
            DetailItemV4("右眼", stat.avgEyeRight, AccentBlue)
            DetailItemV4("最高", stat.maxSmile, AccentOrange)
            DetailItemV4("平均", stat.avgSmile, AccentGreen)
        }
    }
}

@Composable
private fun DetailItemV4(label: String, value: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "%.0f%%".format(value.coerceAtLeast(0f) * 100), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 10.sp, color = TextMuted)
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 周视图卡片
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WeeklyOverviewCardV4(summary: WeeklySummary?) {
    GlassCardV4(glowColor = AccentCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WeeklyOverviewHeaderV4(summary = summary)
            if (summary != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    WeeklyStatItemV4("总微笑", "${summary.totalSmileCount}", AccentYellow)
                    WeeklyStatItemV4("日均", "%.0f".format(summary.avgDailySmile), AccentGreen)
                    WeeklyStatItemV4("最佳", summary.bestDay, AccentCoral)
                }
                Surface(color = AccentGreen.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp), border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📈", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "本周微笑频率比上周 ${summary.trend}，继续保持!", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            } else { Text("加载中...", color = TextMuted) }
        }
    }
}

@Composable
private fun WeeklyOverviewHeaderV4(summary: WeeklySummary?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "📅", fontSize = 22.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = "本周概览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (summary != null) { Text(text = "${summary.weekStart} ~ ${summary.weekEnd}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun WeeklyStatItemV4(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = accent, letterSpacing = (-0.5).sp)
        Text(text = label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun MoodHeatmapCardV4(heatmapData: List<DailyMoodHeatmap>) {
    GlassCardV4 {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎨", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "情绪热力图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "本周每日心情颜色", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                heatmapData.forEach { day ->
                    MoodHeatmapDayV4(day = day)
                }
            }
        }
    }
}

@Composable
private fun MoodHeatmapDayV4(day: DailyMoodHeatmap) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val color = lerpV4(AccentPink, AccentYellow, day.smileScore)
        Box(modifier = Modifier.size(38.dp).background(brush = Brush.verticalGradient(colors = listOf(color, color.copy(alpha = 0.6f))), shape = RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, color.copy(alpha = 1f)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center
        ) {
            Text(text = day.dayLabel.take(1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${day.smileCount}", fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun WeeklyBarChartCardV4(heatmapData: List<DailyMoodHeatmap>) {
    GlassCardV4 {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "📊 周度柱状图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (heatmapData.isNotEmpty()) {
                val maxCount = heatmapData.maxOf { it.smileCount }.coerceAtLeast(1)
                Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                    val barWidth = size.width / (heatmapData.size * 2f)
                    val chartHeight = size.height - 20f
                    heatmapData.forEachIndexed { index, day ->
                        val barHeight = (day.smileCount.toFloat() / maxCount) * chartHeight
                        val x = (index * 2 + 0.5f) * barWidth
                        val y = chartHeight - barHeight
                        drawRoundRect(color = lerpV4(AccentPink, AccentYellow, day.smileScore),
                            topLeft = Offset(x, y), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(4f, 4f))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    heatmapData.forEach { day ->
                        Text(text = day.dayLabel.takeLast(2), fontSize = 10.sp, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyInsightCardV4(summary: WeeklySummary?) {
    if (summary == null) return
    GlassCardV4(glowColor = AccentCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💎", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "周度洞察", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentCyan)
                    Text(text = "本周情绪总结", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp)
                }
            }
            val insight = when {
                summary.avgDailySmile > 35 -> "本周你笑得很多！日均 ${"%.0f".format(summary.avgDailySmile)} 次，工作状态阳光积极。"
                summary.avgDailySmile > 20 -> "本周情绪稳定，日均 ${"%.0f".format(summary.avgDailySmile)}$ 次微笑。${summary.bestDay} 状态最佳!"
                else -> "本周微笑偏少（日均 ${"%.0f".format(summary.avgDailySmile)} 次），注意调节压力。"
            }
            Text(text = insight, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 22.sp)
        }
    }
}


// ═══════════════════════════════════════════════════════════════
// v4.0 新增功能卡片 — 数据故事 + 呼吸引导 + 心情轮播 + 环境光 + 智能建议
// ═══════════════════════════════════════════════════════════════

// ─── 1. AuroraDataStoryCard ─────────────────────────────────

/** 数据故事叙述卡片 v4.0 — 用自然语言总结今日数据 */
@Composable
private fun AuroraDataStoryCardV4(todayStats: List<MinuteStat>) {
    val (storyText, highlightColor, emoji) = remember(todayStats) { generateDataStoryV4(todayStats) }

    GlassCardV4(glowColor = highlightColor) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "数据故事", style = MaterialTheme.typography.labelMedium, color = highlightColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = storyText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 20.sp)
            }
        }
    }
}

private fun generateDataStoryV4(stats: List<MinuteStat>): Triple<String, Color, String> {
    if (stats.isEmpty()) return Triple("还没有数据，先微笑一下吧～", AccentCyan, "✨")
    val totalSmiles = stats.sumOf { it.smileCount.toLong() }
    val peakHour = stats.groupBy { it.minuteKey.substring(11, 13) }
        .mapValues { (_, s) -> s.sumOf { it.smileCount.toLong() } }.maxByOrNull { it.value }?.key ?: "--"
    val avgPct = if (stats.isNotEmpty()) stats.map { it.smilePercent.toDouble() }.average() else 0.0
    return when {
        totalSmiles > 50 -> Triple("你今天笑了${totalSmiles}次！午后${peakHour}点是巅峰，笑容如花般绽放 🌸", AccentYellow, "🎉")
        totalSmiles > 20 -> Triple("今天记录了${totalSmiles}次微笑，${peakHour}时最开心。保持这份节奏 💫", AccentCyan, "😊")
        totalSmiles > 0 -> Triple("今天仅有${totalSmiles}次微笑记录，试着在午后听首喜欢的歌吧 🎵", AccentPurple, "🌤")
        else -> Triple("今天还没有微笑记录，深呼吸，让嘴角轻轻上扬～", AccentRose, "💝")
    }
}

// ─── 2. BreathingGuideCard ──────────────────────────────────

/** 呼吸引导卡片 v4.0 — 可调节的呼吸圆环动画 (4-7-8 呼吸法) */
@Composable
private fun BreathingGuideCardV4() {
    var isActive by remember { mutableStateOf(false) }
    var selectedPattern by remember { mutableIntStateOf(0) }
    val patterns = remember { listOf(BreathPattern("4-7-8 放松", 4f, 7f, 8f), BreathPattern("Box 呼吸", 4f, 4f, 4f), BreathPattern("深慢呼吸", 5f, 0f, 5f)) }
    val currentPattern = patterns[selectedPattern]

    val breathTransition = rememberInfiniteTransition(label = "breath")
    val breathProgress by breathTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) (currentPattern.inhale * 1000).toInt() else 4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "breathCircle"
    )
    val scale by animateFloatAsState(targetValue = 0.6f + breathProgress * 0.5f, animationSpec = spring(0.5f, 100f), label = "breathScale")
    val glowAlpha by animateFloatAsState(targetValue = breathProgress, animationSpec = tween(300), label = "glowAlpha")

    GlassCardV4(glowColor = if (isActive) AccentCyan else Color.Transparent) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "🌬 呼吸引导", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                patterns.forEachIndexed { i, p ->
                    PatternChipV4(label = p.name, selected = i == selectedPattern, accent = AccentCyan) { selectedPattern = i; isActive = false }
                }
            }
            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                BreathingCircleCanvasV4(scale = scale, glowAlpha = glowAlpha, isActive = isActive)
                Text(text = if (isActive)breathPhaseText(breathProgress, currentPattern) else "点击开始",
                    fontSize = 12.sp, color = if (isActive) Color.White else TextSecondary, fontWeight = FontWeight.Medium)
            }
            Button(onClick = { isActive = !isActive }, colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) AccentCyan.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.08f),
                contentColor = AccentCyan
            ), shape = RoundedCornerShape(16.dp)) {
                Text(text = if (isActive) "停止呼吸" else "开始呼吸", fontSize = 13.sp)
            }
        }
    }
}

private data class BreathPattern(val name: String, val inhale: Float, val hold: Float, val exhale: Float)

private fun breathPhaseText(progress: Float, pattern: BreathPattern): String {
    val total = pattern.inhale + pattern.hold + pattern.exhale
    return when {
        progress < pattern.inhale / total -> "吸气 ${pattern.inhale}s"
        progress < (pattern.inhale + pattern.hold) -> "屏息 ${pattern.hold}s"
        else -> "呼气 ${pattern.exhale}s"
    }.let { if (pattern.hold <= 0f && it.startsWith("屏息")) "----" else it }
}

@Composable
private fun BreathingCircleCanvasV4(scale: Float, glowAlpha: Float, isActive: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = (size.minDimension / 2) * scale
        val center = Offset(size.width / 2, size.height / 2)
        if (isActive && glowAlpha > 0.1f) {
            drawCircle(color = AccentCyan.copy(alpha = glowAlpha * 0.15f), radius = radius + 12.dp.toPx(), center = center)
            drawCircle(color = AccentCyan.copy(alpha = glowAlpha * 0.08f), radius = radius + 24.dp.toPx(), center = center)
        }
        drawCircle(color = DividerColor, radius = radius, center = center)
        drawCircle(brush = Brush.radialGradient(colors = listOf(AccentCyan.copy(alpha = 0.3f), Color.Transparent)),
            radius = radius * 0.9f, center = center)
        drawCircle(color = AccentCyan.copy(alpha = 0.5f), radius = radius, center = center,
            style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun PatternChipV4(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp), border = BorderStroke(0.5.dp, if (selected) accent.copy(alpha = 0.3f) else Color.Transparent)
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp, color = if (selected) accent else TextSecondary, fontWeight = FontWeight.Medium)
    }
}


// ─── 3. MoodCarouselCard ────────────────────────────────────

/** 心情轮播卡片 v4.0 — LazyRow 展示心情 emoji 时间线 */
@Composable
private fun MoodCarouselCardV4(todayStats: List<MinuteStat>) {
    val moodItems = remember(todayStats) { generateMoodTimelineV4(todayStats) }

    GlassCardV4(glowColor = AccentRose) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💭", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "心情轮播", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentRose)
                    Text(text = "今天的心情片段", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                itemsIndexed(moodItems) { _, item ->
                    MoodBubbleV4(item = item)
                }
            }
        }
    }
}

private data class MoodItem(val emoji: String, val label: String, val time: String, val color: Color)

private fun generateMoodTimelineV4(stats: List<MinuteStat>): List<MoodItem> {
    if (stats.size < 2) {
        return listOf(
            MoodItem("🌅", "开始", "早上", AccentCyan),
            MoodItem("😊", "愉快", "", AccentYellow),
            MoodItem("💪", "专注", "", AccentPurple),
            MoodItem("🌙", "放松", "晚上", AccentRose),
        )
    }
    return stats.filterIndexed { i, _ -> i % kotlin.math.max(1, stats.size / 8) == 0 || i == stats.size - 1 }.map { s ->
        val pct = s.smilePercent / 100f
        when {
            pct > 0.7f -> MoodItem("😄", "很开心", s.minuteKey.takeLast(5), AccentYellow)
            pct > 0.5f -> MoodItem("😊", "开心", s.minuteKey.takeLast(5), AccentGreen)
            pct > 0.3f -> MoodItem("🙂", "还好", s.minuteKey.takeLast(5), AccentCyan)
            pct > 0.1f -> MoodItem("😐", "一般", s.minuteKey.takeLast(5), AccentBlue)
            else -> MoodItem("😶", "平静", s.minuteKey.takeLast(5), TextMuted)
        }
    }.distinctBy { it.label + it.time }.take(8)
}

@Composable
private fun MoodBubbleV4(item: MoodItem) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = MICRO_SPRING, label = "moodScale")

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
        .scale(scale)
        .clickable { pressed = !pressed }
        .padding(4.dp)
    ) {
        Box(modifier = Modifier.size(48.dp).background(color = item.color.copy(alpha = 0.12f), shape = CircleShape)
            .border(0.5.dp, item.color.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center
        ) { Text(text = item.emoji, fontSize = 22.sp) }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = item.label, fontSize = 10.sp, color = item.color, fontWeight = FontWeight.Medium)
        if (item.time.isNotEmpty()) { Text(text = item.time, fontSize = 9.sp, color = TextMuted) }
    }
}

// ─── 4. AmbientLightAdapterCard ─────────────────────────────

/** 环境光适配提示卡片 v4.0 */
@Composable
private fun AmbientLightAdapterCardV4() {
    val currentHour = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour }
    val (tip, accent, emoji) = remember { generateAmbientTip(currentHour) }

    GlassCardV4(glowColor = accent, isActive = false) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "环境光提示", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = tip, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

private fun generateAmbientTip(hour: Int): Triple<String, Color, String> = when {
    hour < 6 -> Triple("夜间低蓝光模式已启动，保护你的眼睛 🌙", AccentPurple, "🌙")
    hour < 10 -> Triple("晨光柔和，适合适度调高屏幕亮度 ☀️", AccentYellow, "🌤")
    hour < 14 -> Triple("正午强光环境下，摄像头识别可能稍有偏差，属正常现象", AccentCyan, "💡")
    hour < 18 -> Triple("下午光线渐暗，注意保持良好照明以保护视力", AccentOrange, "🔆")
    hour < 21 -> Triple("暖光时段，建议开启护眼模式减轻眼部疲劳", AccentRose, "🕯")
    else -> Triple("夜间模式提醒：避免长时间在暗光下使用屏幕 🌛", AccentPurple, "🌙")
}


// ─── 5. SmartSuggestionCard ─────────────────────────────────

/** 智能建议卡片 v4.0 — 基于数据给出 2-3 条个性化建议 */
@Composable
private fun SmartSuggestionCardV4(todayStats: List<MinuteStat>, latestFace: FaceAnalysis?) {
    val suggestions = remember(todayStats, latestFace) { generateSmartSuggestionsV4(todayStats, latestFace) }
    GlassCardV4(glowColor = AccentMint) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmartSuggestionHeaderV4()
            suggestions.forEachIndexed { i, suggestion ->
                SmartSuggestionRowV4(suggestion = suggestion, index = i, accent = listOf(AccentMint, AccentCyan, AccentPurple)[i % 3])
            }
        }
    }
}

@Composable
private fun SmartSuggestionHeaderV4() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(30.dp).background(
            brush = Brush.linearGradient(listOf(AccentMint, AccentGreen)), shape = CircleShape
        ), contentAlignment = Alignment.Center) { Text(text = "🌟", fontSize = 14.sp) }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = "智能建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentMint)
            Text(text = "基于你的数据为你量身推荐", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SmartSuggestionRowV4(suggestion: SmartSuggestion, index: Int, accent: Color) {
    Surface(color = accent.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Text(text = suggestion.emoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = suggestion.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = suggestion.detail, fontSize = 11.sp, color = TextMuted, lineHeight = 16.sp)
            }
        }
    }
}

private data class SmartSuggestion(val emoji: String, val title: String, val detail: String)

private fun generateSmartSuggestionsV4(stats: List<MinuteStat>, face: FaceAnalysis?): List<SmartSuggestion> {
    val results = mutableListOf<SmartSuggestion>()
    if (stats.isEmpty() && face == null) {
        results.add(SmartSuggestion("📱", "开启摄像头", "将手机前置摄像头对准面部，开始追踪你的工作表情"))
        results.add(SmartSuggestion("⏰", "设置提醒", "建议每 45 分钟记录一次，数据越完整分析越准确"))
        return results
    }
    val avgSmile = stats.map { it.smilePercent.toDouble() }.average()
    val highStrain = stats.count { (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f < 0.3f }
    if (avgSmile < 30) results.add(SmartSuggestion("🎶", "听首喜欢的歌", "今日微笑频率偏低，研究表明听喜欢的音乐可以提升情绪"))
    if (avgSmile > 50) results.add(SmartSuggestion("🌈", "分享快乐", "今天心情很棒！不妨把正能量传递给身边的同事"))
    if (highStrain > 3) results.add(SmartSuggestion("👀", "护眼时光", "检测到多次眼部疲劳信号，试试 20-20-20 法则放松眼睛"))
    if (highStrain in 1..3) results.add(SmartSuggestion("🧘", "短暂休息", "起身走动一下，让眼睛离开屏幕远眺 20 秒"))
    if (stats.size > 20) {
        val lateAfternoon = stats.filter { it.minuteKey.substring(11, 13).toInt() in 14..16 }
        if (lateAfternoon.isNotEmpty() && lateAfternoon.map { it.smilePercent.toDouble() }.average() < avgSmile * 0.7) {
            results.add(SmartSuggestion("☕", "下午茶歇", "下午时段微笑频率下降，来杯咖啡或短暂冥想吧"))
        }
    }
    if (face != null && face.smileProbability < 0.2f) {
        results.add(SmartSuggestion("🌤", "你还好吗？", "当前表情偏中性，如果有压力记得及时释放哦"))
    }
    if (results.isEmpty()) results.add(SmartSuggestion("🌱", "保持现状", "各项指标正常，继续保持良好的工作状态"))
    return results.take(3)
}


// ═══════════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════════

/** 线性插值颜色混合 */
private fun lerpV4(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}

