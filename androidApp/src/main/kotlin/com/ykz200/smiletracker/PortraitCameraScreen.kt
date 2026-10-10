package com.ykz200.smiletracker

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ykz200.smiletracker.domain.FaceAnalysis
import com.ykz200.smiletracker.ui.EmotionProbabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 摄像头检测主界面 — 横屏布局 (左摄像头 + 右数据面板)
 *
 * v3.0 升级:
 *  - HUD风格全面升级: 半透明毛玻璃面板叠加在预览上
 *  - 情绪色环: 人脸周围随情绪变化的光环动画
 *  - 智能引导动画: 初次使用时居中脉冲圈+提示文字淡入淡出
 *  - 眼部放松提醒: 长时间用眼显示 20-20-20 法则提示
 *  - 底部迷你数据条: 微笑概率波形横条
 *  - 深色玻璃质感: 所有叠加UI使用深色毛玻璃背景
 *  - 动画系统: spring物理系统 + 呼吸动画 + 平滑过渡
 *
 * 关键修复:
 *  - 摄像头初始化使用 LaunchedEffect 同步等待实例就绪
 *  - 预览框 9:16 比例 + 偏上居中 (人脸可见)
 *  - 数据面板竖排横条图
 */
@Composable
fun PortraitCameraScreen(
    onNavigateToDashboard: () -> Unit = {},
    enableAnalysis: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ─── 状态管理 ───
    var latestFace by remember { mutableStateOf<FaceAnalysis?>(null) }
    var emotionProbs by remember { mutableStateOf(EmotionProbabilities()) }
    var isLearning by remember { mutableStateOf(true) }
    var learningProgress by remember { mutableFloatStateOf(0f) }
    var baselineSampleCount by remember { mutableIntStateOf(0) }
    var dominantEmotion by remember { mutableStateOf(Pair("中性", 0f)) }
    var cameraReady by remember { mutableStateOf(false) }

    // 预览 View 引用
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var analyzer by remember { mutableStateOf<AndroidCameraAnalyzer?>(null) }

    // v3.0 新增状态
    var showGuidance by remember { mutableStateOf(true) }
    var eyeStrainDetected by remember { mutableStateOf(false) }
    var continuousUseTime by remember { mutableIntStateOf(0) }
    val smileHistory = remember { mutableStateListOf<Float>() }

    // ─── 步骤1: 初始化 MediaPipe ───
    LaunchedEffect(enableAnalysis) {
        if (!enableAnalysis) return@LaunchedEffect
        try {
            val a = AndroidCameraAnalyzer(context)
            analyzer = a

            launch {
                a.diagnosticState.collect { diag ->
                    baselineSampleCount = diag.faceDetected
                }
            }

            launch {
                a.latestFlow.collect { face ->
                    latestFace = face
                    if (baselineSampleCount < 600) {
                        // 学习阶段
                    } else if (isLearning) {
                        isLearning = false
                    }
                    learningProgress = (baselineSampleCount / 600f).coerceIn(0f, 1f)

                    // 记录微笑历史 (用于波形图)
                    val smileVal = face.smileProbability.coerceIn(0f, 1f)
                    smileHistory.add(smileVal)
                    if (smileHistory.size > 60) smileHistory.removeAt(0)

                    // 检测眼部疲劳
                    val eyeClosed = face.leftEyeOpenProbability in 0f..0.3f ||
                            face.rightEyeOpenProbability in 0f..0.3f
                    if (eyeClosed && baselineSampleCount > 100) {
                        eyeStrainDetected = true
                    }

                    emotionProbs = EmotionProbabilities(
                        smile = face.smileProbability.coerceIn(0f, 1f),
                        angry = 0f,
                        neutral = if (face.smileProbability in 0.1f..0.4f) 0.6f else 0.1f,
                        thinking = if (face.headPitch < -5f) 0.3f else 0f,
                        surprise = if (face.smileProbability < 0.05f && face.headPitch > 5f) 0.4f else 0f
                    )
                    dominantEmotion = emotionProbs.dominantEmotion()
                }
            }
        } catch (e: Exception) {
            Log.e("PortraitScreen", "Analyzer init failed: ${e.message}", e)
        }
    }

    // ─── 步骤2: PreviewView 就绪后绑定 ───
    LaunchedEffect(previewView, analyzer) {
        val pv = previewView
        val a = analyzer
        if (pv == null || a == null) return@LaunchedEffect
        try {
            a.bindToLifecycle(lifecycleOwner, pv)
            cameraReady = true
        } catch (e: Exception) {
            Log.e("PortraitScreen", "bind failed: ${e.message}", e)
        }
    }

    // v3.0: 用眼计时器 (模拟连续用眼时间累积)
    LaunchedEffect(cameraReady) {
        if (cameraReady) {
            while (true) {
                delay(1000)
                continuousUseTime++
                // 每20分钟触发提醒
                if (continuousUseTime > 0 && continuousUseTime % 1200 == 0) {
                    eyeStrainDetected = true
                    delay(8000)
                    eyeStrainDetected = false
                }
            }
        }
    }

    // v3.0: 引导动画自动消失
    LaunchedEffect(showGuidance) {
        if (showGuidance) {
            delay(5000)
            showGuidance = false
        }
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose { analyzer?.release() }
    }

    // ═══ 横屏主布局 ═══
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── 左侧 60%: 摄像头预览区 ───
        Box(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
        ) {
            // 摄像头预览: 居中偏上
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(9f / 16f)
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView = this
                        }
                    }
                )
            }

            // ─ 叠加在预览上的 UI ─

            // v3.0: 智能引导动画 (居中脉冲圈)
            AnimatedVisibility(
                visible = showGuidance && !isLearning,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(animationSpec = tween(800)),
                exit = fadeOut(animationSpec = tween(1200))
            ) {
                SmartGuidanceOverlay()
            }

            // v3.0: 情绪色环 (人脸周围光环动画)
            EmotionColorRing(
                emotionName = dominantEmotion.first,
                emotionProb = dominantEmotion.second,
                isActive = !isLearning && dominantEmotion.second > 0.2f,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(9f / 16f)
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
            )

            // v3.0: LIVE 状态指示 — 玻璃质感
            LiveIndicatorV3(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 12.dp)
            )

            // v3.0: 美颜模式按钮 — 玻璃质感
            BeautyButtonV3(
                level = 2,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 42.dp, start = 12.dp)
            )

            // v3.0: 情绪上升趋势提示 — 玻璃质感
            AnimatedVisibility(
                visible = emotionProbs.smile > 0.4f && !isLearning,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                EmotionRisingTagV3()
            }

            // 预览框内: 人脸检测框 + 情绪标签
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(9f / 16f)
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
            ) {
                FaceDetectionOverlayV3(
                    isLearning = isLearning,
                    emotionName = dominantEmotion.first,
                    modifier = Modifier.align(Alignment.Center)
                )

                // 情绪大标签 (人脸下方) — 玻璃质感
                AnimatedVisibility(
                    visible = dominantEmotion.second > 0.3f,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 90.dp),
                    enter = scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                    exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                           fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                ) {
                    DominantEmotionLabelV3(
                        name = dominantEmotion.first,
                        probability = dominantEmotion.second
                    )
                }

                // v3.0: 学习基线提示 — 玻璃质感
                if (isLearning) {
                    LearningBaselineOverlayV3(
                        progress = learningProgress,
                        count = baselineSampleCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                }
            }

            // 诊断叠加
            if (baselineSampleCount == 0) {
                DiagnosticOverlayV3(
                    analyzer = analyzer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            // v3.0: 眼部放松提醒
            AnimatedVisibility(
                visible = eyeStrainDetected,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.9f
                ),
                exit = fadeOut(animationSpec = tween(800)) + scaleOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    targetScale = 0.95f
                )
            ) {
                EyeRelaxReminder(
                    onDismiss = { eyeStrainDetected = false }
                )
            }

            // v3.0: 左下角迷你按钮组 — 玻璃质感
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniCircleButtonV3(
                    label = if (baselineSampleCount < 100) "GO" else "$baselineSampleCount",
                    backgroundColor = Color(0xCC000000)
                )
                MiniCircleButtonV3(
                    label = "D",
                    backgroundColor = Color(0x66000000),
                    onClick = onNavigateToDashboard
                )
            }

            // v3.0: 底部迷你数据条 — 微笑概率波形
            if (!isLearning && smileHistory.size >= 3) {
                BottomMiniWaveBar(
                    data = smileHistory.toList(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 12.dp)
                )
            }
        }

        // ─── 右侧 40%: 数据面板 ───
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部功能按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                MiniCircleButtonV3(
                    label = "F",
                    backgroundColor = Color(0xCC1A1A1A)
                )
            }

            // 积极指数卡 — 玻璃质感
            PositivityIndexCompactV3(
                index = emotionProbs.positivityIndex()
            )

            // 情绪柱状图
            EmotionBarsVerticalV3(
                emotions = emotionProbs.sorted().take(6)
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════
// v3.0 子 UI 组件
// ═══════════════════════════════════════════════════════

/**
 * v3.0: 智能引导动画 — 居中脉冲圈 + 提示文字
 */
@Composable
private fun SmartGuidanceOverlay() {
    val pulseRadius by rememberInfiniteTransition(label = "guidePulse").animateFloat(
        initialValue = 30f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by rememberInfiniteTransition(label = "guideAlpha").animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier.size(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // 脉冲圈
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.4f),
                    radius = pulseRadius,
                    center = center,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.2f),
                    radius = pulseRadius * 0.6f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // 中心圆点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF00E5FF), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 提示文字 — 玻璃质感背景
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0x99000000)
        ) {
            Text(
                text = "面向正前方",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFEEEEEE),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * v3.0: 情绪色环 — 在人脸周围叠加随情绪变化的光环动画
 * 用 Canvas 绘制围绕人脸区域的彩色圆环
 */
@Composable
private fun EmotionColorRing(
    emotionName: String,
    emotionProb: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // 根据情绪选择颜色
    val ringColor = when {
        emotionName == "微笑" -> Color(0xFF4CAF50)
        emotionName == "生气" -> Color(0xFFE91E63)
        emotionName == "思考" -> Color(0xFF7E57C2)
        emotionName == "惊讶" -> Color(0xFFFFC107)
        emotionName == "恐惧" -> Color(0xFFFF5722)
        emotionName == "中性" -> Color(0xFF5C6BC0)
        else -> Color(0xFF00E5FF)
    }

    // 呼吸动画
    val breathe by rememberInfiniteTransition(label = "ringBreathe").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    // 旋转动画
    val rotation by rememberInfiniteTransition(label = "ringRotate").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.6f * emotionProb else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ringAlpha"
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height * 0.42f
        val baseRadius = size.minDimension * 0.2f * breathe

        if (animatedAlpha > 0.01f) {
            // 外环 — 渐变光晕
            drawCircle(
                color = ringColor.copy(alpha = animatedAlpha * 0.3f),
                radius = baseRadius * 1.2f * (1 + emotionProb * 0.3f),
                center = Offset(centerX, centerY),
                style = Stroke(width = 3f)
            )

            // 内环 — 旋转弧线
            val arcRadius = baseRadius * 0.9f
            val sweepAngle = 120f + emotionProb * 60f
            drawArc(
                color = ringColor.copy(alpha = animatedAlpha),
                startAngle = rotation,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerX - arcRadius, centerY - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )

            // 对称的另一段弧
            drawArc(
                color = ringColor.copy(alpha = animatedAlpha * 0.7f),
                startAngle = rotation + 180f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerX - arcRadius, centerY - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // 微光点 (沿圆周分布)
            val dotCount = 3
            for (i in 0 until dotCount) {
                val angle = Math.toRadians((rotation + i * 120f).toDouble())
                val dotX = centerX + arcRadius * kotlin.math.cos(angle).toFloat()
                val dotY = centerY + arcRadius * kotlin.math.sin(angle).toFloat()
                drawCircle(
                    color = ringColor.copy(alpha = animatedAlpha * 0.8f),
                    radius = 2.5f,
                    center = Offset(dotX, dotY)
                )
            }
        }
    }
}

/**
 * v3.0: LIVE 状态指示 — 深色玻璃质感
 */
@Composable
private fun LiveIndicatorV3(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "livePulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "liveAlpha"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xB3000000)
    ) {
        // 微光边框
        Box(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF00E5FF).copy(alpha = pulse),
                                    Color(0xFF00E5FF).copy(alpha = pulse * 0.5f)
                                )
                            ),
                            CircleShape
                        )
                )
                Text(
                    text = "LIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E5FF).copy(alpha = pulse),
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * v3.0: 美颜模式按钮 — 深色玻璃质感
 */
@Composable
private fun BeautyButtonV3(level: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xB31A1A1A)
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFFFC107).copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Canvas 绘制星形图标
            Canvas(modifier = Modifier.size(14.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 * 0.7f
                val color = Color(0xFFFFC107)
                for (i in 0 until 5) {
                    val outerAngle = Math.toRadians((i * 72 - 90).toDouble())
                    val innerAngle = Math.toRadians((i * 72 - 90 + 36).toDouble())
                    val ox = cx + r * kotlin.math.cos(outerAngle).toFloat()
                    val oy = cy + r * kotlin.math.sin(outerAngle).toFloat()
                    val ix = cx + r * 0.4f * kotlin.math.cos(innerAngle).toFloat()
                    val iy = cy + r * 0.4f * kotlin.math.sin(innerAngle).toFloat()
                    if (i == 0) drawLine(color, Offset(ox, oy), Offset(ix, iy), 1.5f, StrokeCap.Round)
                    val prevOuter = Math.toRadians(((i - 1 + 5) % 5 * 72 - 90).toDouble())
                    val pox = cx + r * kotlin.math.cos(prevOuter).toFloat()
                    val poy = cy + r * kotlin.math.sin(prevOuter).toFloat()
                    drawLine(color, Offset(pox, poy), Offset(ix, iy), 1.5f, StrokeCap.Round)
                }
            }
            Text(
                text = "美颜$level",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFC107)
            )
        }
    }
}

/**
 * v3.0: 情绪上升趋势提示 — 玻璃质感
 */
@Composable
private fun EmotionRisingTagV3() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xB31A1A1A)
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF4CAF50).copy(alpha = 0.25f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Canvas 绘制笑脸
            Canvas(modifier = Modifier.size(16.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 * 0.8f
                val color = Color(0xFF4CAF50)
                drawCircle(color = color, radius = r, style = Stroke(width = 1.5f))
                drawCircle(color = color, radius = r * 0.15f, Offset(cx - r * 0.3f, cy - r * 0.2f))
                drawCircle(color = color, radius = r * 0.15f, Offset(cx + r * 0.3f, cy - r * 0.2f))
                val smilePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - r * 0.4f, cy + r * 0.1f)
                    quadraticBezierTo(cx, cy + r * 0.5f, cx + r * 0.4f, cy + r * 0.1f)
                }
                drawPath(smilePath, color, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
            }
            Text(
                text = "情绪在上升",
                fontSize = 12.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold
            )
            // 上升箭头 — Canvas 绘制
            Canvas(modifier = Modifier.size(12.dp)) {
                val color = Color(0xFF4CAF50)
                drawLine(color, Offset(size.width / 2, size.height), Offset(size.width / 2, 0f), 2f, StrokeCap.Round)
                drawLine(color, Offset(0f, size.height * 0.3f), Offset(size.width / 2, 0f), 2f, StrokeCap.Round)
                drawLine(color, Offset(size.width, size.height * 0.3f), Offset(size.width / 2, 0f), 2f, StrokeCap.Round)
            }
        }
    }
}

/**
 * v3.0: 人脸检测框 — 精致角点 + 呼吸动画
 */
@Composable
private fun FaceDetectionOverlayV3(
    isLearning: Boolean,
    emotionName: String,
    modifier: Modifier = Modifier
) {
    val breathe by rememberInfiniteTransition(label = "faceBreathe").animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "faceScale"
    )

    val ringColor = when {
        emotionName == "微笑" -> Color(0xFF4CAF50)
        emotionName == "生气" -> Color(0xFFE91E63)
        emotionName == "思考" -> Color(0xFF7E57C2)
        emotionName == "惊讶" -> Color(0xFFFFC107)
        emotionName == "中性" -> Color(0xFF5C6BC0)
        else -> if (isLearning) Color(0xFF4FC3F7) else Color(0xFF00E5FF)
    }

    Box(
        modifier = modifier.size(180.dp * breathe),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val cornerLen = r * 0.22f
            val strokeW = 2.5f

            // 四角装饰 (L形)
            val corners = listOf(
                // 左上
                listOf(Offset(0f, cornerLen), Offset(0f, 0f), Offset(cornerLen, 0f)),
                // 右上
                listOf(Offset(size.width - cornerLen, 0f), Offset(size.width, 0f), Offset(size.width, cornerLen)),
                // 左下
                listOf(Offset(0f, size.height - cornerLen), Offset(0f, size.height), Offset(cornerLen, size.height)),
                // 右下
                listOf(Offset(size.width - cornerLen, size.height), Offset(size.width, size.height), Offset(size.width, size.height - cornerLen))
            )

            corners.forEach { path ->
                drawLine(ringColor, path[0], path[1], strokeW, cap = StrokeCap.Round)
                drawLine(ringColor, path[1], path[2], strokeW, cap = StrokeCap.Round)
            }

            // 内圈虚线 (淡淡的引导圈)
            drawCircle(
                color = ringColor.copy(alpha = 0.15f),
                radius = r * 0.7f,
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(width = 1f)
            )
        }
    }
}

/**
 * v3.0: 主导情绪标签 — 玻璃质感
 */
@Composable
private fun DominantEmotionLabelV3(name: String, probability: Float) {
    val emotionColor = when (name) {
        "微笑" -> Color(0xFF4CAF50)
        "生气" -> Color(0xFFE91E63)
        "中性" -> Color(0xFF5C6BC0)
        "思考" -> Color(0xFF7E57C2)
        "惊讶" -> Color(0xFFFFC107)
        else -> Color(0xFF00E5FF)
    }

    val emotionIcon: String = when (name) {
        "微笑" -> ":)"
        "生气" -> ">:("
        "中性" -> ":|"
        "思考" -> "?.?"
        "惊讶" -> ":O"
        else -> ":|"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC1A1A1A)
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = emotionColor.copy(alpha = 0.25f),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.5f)
                    )
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 情绪图标 (Canvas 绘制)
            Canvas(modifier = Modifier.size(24.dp)) {
                val color = emotionColor
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 * 0.8f
                drawCircle(color = color, radius = r, style = Stroke(width = 1.5f))
                // 眼睛
                drawCircle(color = color, radius = r * 0.12f, Offset(cx - r * 0.3f, cy - r * 0.15f))
                drawCircle(color = color, radius = r * 0.12f, Offset(cx + r * 0.3f, cy - r * 0.15f))
                // 嘴 (根据情绪)
                if (name == "微笑") {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - r * 0.3f, cy + r * 0.2f)
                        quadraticBezierTo(cx, cy + r * 0.5f, cx + r * 0.3f, cy + r * 0.2f)
                    }
                    drawPath(path, color, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
                } else {
                    drawLine(color, Offset(cx - r * 0.2f, cy + r * 0.25f), Offset(cx + r * 0.2f, cy + r * 0.25f), 1.2f, StrokeCap.Round)
                }
            }

            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = emotionColor
            )
            Text(
                text = "${(probability * 100).toInt()}%",
                fontSize = 13.sp,
                color = emotionColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * v3.0: 学习基线提示 — 玻璃质感
 */
@Composable
private fun LearningBaselineOverlayV3(
    progress: Float,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC0A0A0A)
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF4FC3F7).copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "学习表情基线中...",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFEEEEEE)
            )
            Text(
                text = "请面对摄像头保持自然状态",
                fontSize = 11.sp,
                color = Color(0xFF999999)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "learningProgress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF4FC3F7), Color(0xFF00E5FF))
                                )
                            )
                    )
                }
                Text(
                    text = "$count",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
            }
        }
    }
}

/**
 * v3.0: 迷你圆形按钮 — 玻璃质感
 */
@Composable
private fun MiniCircleButtonV3(
    label: String,
    backgroundColor: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.drawBehind {
                drawCircle(
                    color = Color(0xFF888888).copy(alpha = 0.15f),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 1f)
                )
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFEEEEEE),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * v3.0: 诊断叠加层 — 玻璃质感
 */
@Composable
private fun DiagnosticOverlayV3(
    analyzer: AndroidCameraAnalyzer?,
    modifier: Modifier = Modifier
) {
    if (analyzer == null) {
        Box(modifier = modifier) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xCC1A1A1A)
            ) {
                Text(
                    text = "等待初始化...",
                    fontSize = 11.sp,
                    color = Color(0xFFFF8A65),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        return
    }
    val diag by analyzer.diagnosticState.collectAsState()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xDD0E0E0E)
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFFFC107).copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "[诊断]",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107)
            )
            Text(
                text = if (diag.detectorCreated) "[OK] 检测器已创建" else "[--] 检测器未创建",
                fontSize = 10.sp,
                color = if (diag.detectorCreated) Color(0xFF81C784) else Color(0xFFFF5252)
            )
            Text(
                text = if (diag.cameraBound) "[OK] 摄像头已绑定" else "[--] 摄像头未绑定",
                fontSize = 10.sp,
                color = if (diag.cameraBound) Color(0xFF81C784) else Color(0xFFFF8A65)
            )
            Text(
                text = "帧:${diag.frameReceived} 推理:${diag.inferenceDone} 人脸:${diag.faceDetected}",
                fontSize = 10.sp,
                color = Color(0xFFBBBBBB)
            )
            if (diag.lastError != null) {
                Text(
                    text = "WARN: ${diag.lastError}",
                    fontSize = 9.sp,
                    color = Color(0xFFFF5252)
                )
            }
        }
    }
}

/**
 * v3.0: 眼部放松提醒 — 20-20-20 法则
 */
@Composable
private fun EyeRelaxReminder(onDismiss: () -> Unit) {
    // 倒计时
    var countdown by remember { mutableIntStateOf(20) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        delay(1000)
        onDismiss()
    }

    // 呼吸脉冲
    val breathe by rememberInfiniteTransition(label = "relaxBreathe").animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "relaxScale"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE60D0D0D)
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF4FC3F7).copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style = Stroke(width = 1.5f)
                    )
                }
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 眼睛图标 (Canvas 绘制)
            Canvas(
                modifier = Modifier
                    .size(36.dp * breathe)
            ) {
                val color = Color(0xFF4FC3F7)
                val cx = size.width / 2
                val cy = size.height / 2
                val w = size.width * 0.35f
                val h = size.height * 0.2f
                // 左眼
                drawOval(
                    color = color,
                    topLeft = Offset(cx - w * 1.2f, cy - h / 2),
                    size = Size(w, h),
                    style = Stroke(width = 2f)
                )
                // 右眼
                drawOval(
                    color = color,
                    topLeft = Offset(cx + w * 0.2f, cy - h / 2),
                    size = Size(w, h),
                    style = Stroke(width = 2f)
                )
            }

            Text(
                text = "20-20-20 护眼法则",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4FC3F7)
            )

            Text(
                text = "请看向20英尺外",
                fontSize = 13.sp,
                color = Color(0xFFEEEEEE),
                textAlign = TextAlign.Center
            )

            Text(
                text = "持续 ${countdown}s...",
                fontSize = 12.sp,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center
            )

            // 进度圆环
            val progress = (20 - countdown) / 20f
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 3f
                    val radius = (size.minDimension - strokeW) / 2
                    // 背景圆
                    drawCircle(
                        color = Color(0xFF1E1E1E),
                        radius = radius,
                        style = Stroke(width = strokeW)
                    )
                    // 进度弧
                    drawArc(
                        color = Color(0xFF4FC3F7),
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = Offset(
                            (size.width - radius * 2) / 2,
                            (size.height - radius * 2) / 2
                        ),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            // 关闭按钮
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier.clickable { onDismiss() }
            ) {
                Text(
                    text = "知道了",
                    fontSize = 12.sp,
                    color = Color(0xFFBBBBBB),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * v3.0: 底部迷你数据条 — 微笑概率波形
 */
@Composable
private fun BottomMiniWaveBar(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    val waveColor = Color(0xFF00E5FF)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0x80000000)
    ) {
        Box(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 标签
                Text(
                    text = "微笑",
                    fontSize = 10.sp,
                    color = Color(0xFF888888),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(24.dp)
                )

                // 波形图
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (data.size < 2) return@Canvas

                    val maxPoints = 40
                    val step = if (data.size > maxPoints) data.size / maxPoints else 1
                    val displayData = data.filterIndexed { index, _ -> index % step == 0 }.take(maxPoints)

                    val w = size.width
                    val h = size.height
                    val baseline = h * 0.85f

                    // 基线
                    drawLine(
                        color = Color(0xFF222222),
                        start = Offset(0f, baseline),
                        end = Offset(w, baseline),
                        strokeWidth = 0.5f
                    )

                    // 波形填充
                    val path = androidx.compose.ui.graphics.Path()
                    val wavePath = androidx.compose.ui.graphics.Path()

                    displayData.forEachIndexed { index, value ->
                        val x = (index.toFloat() / (displayData.size - 1).coerceAtLeast(1)) * w
                        val y = baseline - value * (h * 0.7f)

                        if (index == 0) {
                            path.moveTo(x, y)
                            wavePath.moveTo(x, baseline)
                            wavePath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            wavePath.lineTo(x, y)
                        }
                    }

                    // 闭合填充路径
                    if (displayData.isNotEmpty()) {
                        val lastX = w
                        wavePath.lineTo(lastX, baseline)
                        wavePath.close()

                        // 半透明填充
                        drawPath(
                            wavePath,
                            color = waveColor.copy(alpha = 0.1f)
                        )

                        // 波形线
                        drawPath(
                            path,
                            waveColor,
                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                        )

                        // 末端点
                        val lastValue = displayData.last()
                        val lastY = baseline - lastValue * (h * 0.7f)
                        drawCircle(
                            color = waveColor,
                            radius = 3f,
                            center = Offset(lastX, lastY)
                        )
                        drawCircle(
                            color = waveColor.copy(alpha = 0.3f),
                            radius = 6f,
                            center = Offset(lastX, lastY)
                        )
                    }
                }

                // 当前值
                val currentValue = data.lastOrNull() ?: 0f
                Text(
                    text = "${(currentValue * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = waveColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(28.dp)
                )
            }
        }
    }
}

/**
 * v3.0: 积极指数卡 — 玻璃质感
 */
@Composable
private fun PositivityIndexCompactV3(index: Int) {
    val indexColor = when {
        index >= 70 -> Color(0xFF4CAF50)
        index >= 40 -> Color(0xFFFFC107)
        else -> Color(0xFF5C6BC0)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC141414)
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    drawRoundRect(
                        color = indexColor.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 1f)
                    )
                }
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "积极指数",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$index",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = indexColor
                )
            }
            // 右侧进度环
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                val animatedIndex by animateFloatAsState(
                    targetValue = index / 100f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "positivityAnim"
                )
                CircularProgressIndicator(
                    progress = { animatedIndex },
                    modifier = Modifier.size(48.dp),
                    color = indexColor,
                    trackColor = Color(0xFF1E1E1E),
                    strokeWidth = 4.dp,
                )
                Text(
                    text = "$index",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * v3.0: 情绪柱状图 — 玻璃质感
 */
@Composable
private fun EmotionBarsVerticalV3(
    emotions: List<Triple<String, Float, Long>>
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC141414)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            emotions.forEach { (name, prob, color) ->
                val isTop = name == emotions.first().first
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = name,
                        fontSize = 11.sp,
                        color = if (isTop) Color(color.toUInt()) else Color(0xFFCCCCCC),
                        fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(28.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .padding(horizontal = 4.dp)
                    ) {
                        val barFraction by animateFloatAsState(
                            targetValue = prob.coerceIn(0f, 1f),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "barAnim"
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val h = size.height
                            val w = size.width
                            // 背景
                            drawRoundRect(
                                color = Color(0xFF1A1A1A),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(h / 2)
                            )
                            // 填充条
                            if (barFraction > 0.01f) {
                                val barBrush = Brush.horizontalGradient(
                                    listOf(
                                        Color(color.toUInt()).copy(alpha = 0.6f),
                                        Color(color.toUInt())
                                    )
                                )
                                drawRoundRect(
                                    brush = barBrush,
                                    size = Size(w * barFraction, h),
                                    cornerRadius = CornerRadius(h / 2)
                                )
                                // 高光
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.08f),
                                    size = Size(w * barFraction, h * 0.4f),
                                    cornerRadius = CornerRadius(h / 2)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${(prob * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = if (isTop) Color(color.toUInt()) else Color(0xFF888888),
                        fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(28.dp)
                    )
                }
            }
        }
    }
}
