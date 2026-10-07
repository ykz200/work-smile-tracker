package com.ykz200.smiletracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.domain.FaceAnalysis
import com.ykz200.smiletracker.domain.MinuteStat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.*

/**
 * Compose Multiplatform 共享 UI — Dashboard 大屏
 * 同时运行在 iOS 和 Android, 不需要写两套
 */
@Composable
fun DashboardApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        DashboardScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen() {
    val repo = remember { RepositoryProvider.get() }
    var latestFace by remember { mutableStateOf<FaceAnalysis?>(null) }
    var todayStats by remember { mutableStateOf<List<MinuteStat>>(emptyList()) }

    // 启动时拉一次今日统计数据
    LaunchedEffect(Unit) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val start = now.date.atTime(LocalTime(0, 0, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val end = now.date.atTime(23, 59, 59).toInstant(TimeZone.currentZone()).toEpochMilliseconds()
        todayStats = runCatching { repo.queryMinuteStats(start, end) }.getOrDefault(emptyList())
    }

    // 实时刷新最后一条采样 (每 1.5 秒)
    LaunchedEffect(Unit) {
        while (isActive) {
            latestFace = runCatching { repo.getLatest() }.getOrNull()
            delay(1500L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("打工人表情追踪", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── 实时状态卡 ──
            RealTimeFaceCard(face = latestFace)

            Spacer(modifier = Modifier.height(16.dp))

            // ── 今日统计数字 ──
            val smileSum = todayStats.sumOf { it.smileCount.toLong() }
            val sampleSum = todayStats.sumOf { it.sampleCount.toLong() }
            val avgSmileOfToday = if (todayStats.isNotEmpty())
                todayStats.map { it.smilePercent.toDouble() }.average()
            else 0.0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NumberCard(modifier = Modifier.weight(1f), label = "今日微笑次数", value = smileSum.toString(), accent = Color(0xFFFFD54F))
                NumberCard(modifier = Modifier.weight(1f), label = "今日采样总数", value = sampleSum.toString(), accent = Color(0xFF90CAF9))
                NumberCard(modifier = Modifier.weight(1f), label = "时长占比", value = "%.1f%%".format(avgSmileOfToday), accent = Color(0xFFA5D6A7))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 折线图：每分钟微笑占比 ──
            SmileyLineChart(modifier = Modifier.fillMaxWidth().height(200.dp), data = todayStats)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "每分钟详细",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(todayStats.reversed()) { stat ->
                    MinuteStatRow(stat)
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
private fun RealTimeFaceCard(face: FaceAnalysis?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("实时状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            Spacer(modifier = Modifier.height(6.dp))
            if (face == null) {
                Text("未检测到人脸 · 正在启动摄像头...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (face.isSmiling) "😊 微笑中" else "😶 中性",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "概率 %.0f%%".format(face.smileProbability * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberCard(modifier: Modifier, label: String, value: String, accent: Color) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = accent)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun SmileyLineChart(modifier: Modifier, data: List<MinuteStat>) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        return
    }
    val lineColor = Color(0xFFFFD54F)
    val dotColor = Color(0xFFFFF9C4)
    val primary = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val maxVal = 100f  // smilePercent 0..100%
        val stepX = if (data.size > 1) w / (data.size - 1) else w
        val path = Path()
        data.forEachIndexed { i, stat ->
            val x = i * stepX
            val y = h - (stat.smilePercent / maxVal) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // 画折线
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        // 画节点
        data.forEachIndexed { i, stat ->
            val x = i * stepX
            val y = h - (stat.smilePercent / maxVal) * h
            drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
            drawCircle(color = dotColor, radius = 6f, center = Offset(x, y), style = Stroke(width = 1.5f))
        }
    }
}

@Composable
private fun MinuteStatRow(stat: MinuteStat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stat.minuteKey.takeLast(5), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.width(50.dp))
        Spacer(modifier = Modifier.width(8.dp))
        // 进度条 Smile %
        Box(modifier = Modifier.weight(1f).height(14.dp)) {
            val fraction = (stat.smilePercent / 100f).coerceIn(0f, 1f)
            val color = lerp(Color(0xFF455A64), Color(0xFFFFD54F), fraction)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barH = size.height
                val barW = size.width
                // 背景
                drawRoundRect(color = Color(0xFF263238), size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2))
                // 前景
                drawRoundRect(color = color, size = androidx.compose.ui.geometry.Size(barW * fraction, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${stat.smileCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFFFFD54F))
            Text("${stat.sampleCount} samples", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction
    )
}
