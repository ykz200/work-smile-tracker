package com.ykz200.smiletracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.db.SmileDatabase
import com.ykz200.smiletracker.ui.DashboardApp
import java.util.concurrent.TimeUnit

/**
 * Android 端 MainActivity
 *
 * v3.0 — 沉浸式引导体验:
 *  - 深邃宇宙黑 + 流光效果 + 分层入场动画
 *  - 3 步快速引导 (欢迎 → 隐私说明 → 开始使用)
 *  - Canvas 绘制"微笑弧线"抽象 Logo + 旋转光晕
 *  - 流光边框按钮 + 触感反馈 (scaleIn)
 *  - 极光缓慢流动渐变背景
 *
 * 职责:
 *  - 运行时权限请求（摄像头、通知）
 *  - 初始化 RepositoryProvider
 *  - 调度 WorkManager 后台任务
 *  - 拉起前台 Service 保活
 */
class MainActivity : ComponentActivity() {

    private var cameraPermissionGranted by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 AppContextHolder
        AppContextHolder.init(this)

        // 检查权限状态
        cameraPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            notificationPermissionGranted = true
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (cameraPermissionGranted) {
                        SmileTrackerApp(
                            onAllPermissionsGranted = {
                                cameraPermissionGranted = true
                                notificationPermissionGranted = true
                            }
                        )
                    } else {
                        PermissionRequestScreen(onAllPermissionsGranted = {
                            cameraPermissionGranted = true
                            notificationPermissionGranted = true
                        })
                    }
                }
            }
        }
    }
}

/**
 * 主 App 内容 — 双屏切换
 *  - 默认 PortraitCameraScreen (解决人脸难看问题)
 *  - 切换后 DashboardApp (图表报表)
 */
@Composable
private fun SmileTrackerApp(onAllPermissionsGranted: () -> Unit) {
    // 初始化数据库
    LaunchedEffect(Unit) {
        initRepository()
    }

    // 调度后台任务
    LaunchedEffect(Unit) {
        scheduleBackgroundWork()
    }

    // 拉起前台服务
    LaunchedEffect(Unit) {
        startForegroundServiceSafely()
    }

    // 切换状态
    var showDashboard by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = showDashboard,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
        },
        label = "screenSwitch"
    ) { isDashboard ->
        if (isDashboard) {
            DashboardApp()
        } else {
            PortraitCameraScreen(
                onNavigateToDashboard = { showDashboard = true }
            )
        }
    }
}

private fun initRepository() {
    try {
        val driver: SqlDriver = AndroidSqliteDriver(
            schema = SmileDatabase.Schema,
            context = AppContextHolder.get(),
            name = "smile_tracker.db"
        )
        val database = SmileDatabase(driver)
        RepositoryProvider.init(database)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "DB init failed: ${e.message}")
    }
}

private fun scheduleBackgroundWork() {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()
    val workRequest = PeriodicWorkRequestBuilder<SmileyAnalysisWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .addTag("smiley_analysis_worker")
        .build()
    WorkManager.getInstance(AppContextHolder.get())
        .enqueueUniquePeriodicWork("smiley_analysis", ExistingPeriodicWorkPolicy.KEEP, workRequest)
}

private fun startForegroundServiceSafely() {
    try {
        val intent = Intent(AppContextHolder.get(), SmileyAnalysisService::class.java)
        ContextCompat.startForegroundService(AppContextHolder.get(), intent)
    } catch (e: Exception) {
        android.util.Log.w("MainActivity", "Start FGS failed: ${e.message}")
    }
}

/**
 * Application Context 持有器 — 避免各处 this 传参
 */
object AppContextHolder {
    @Volatile
    private var context: android.content.Context? = null

    fun init(ctx: android.content.Context) {
        context = ctx.applicationContext
    }

    fun get(): android.content.Context =
        context ?: throw IllegalStateException("AppContextHolder not initialized")
}

// ═══════════════════════════════════════════════════════════════
// v3.0 — 沉浸式权限引导界面
// ═══════════════════════════════════════════════════════════════

/**
 * 权限请求引导界面 v3.0 — 沉浸式宇宙极光风格
 *
 * 核心升级:
 *  1. 深邃宇宙黑 + 流光效果 + 分层入场动画
 *  2. 3 步快速引导 (欢迎 → 隐私说明 → 开始使用) 带跳过按钮
 *  3. Canvas 绘制"微笑弧线"抽象 Logo + 旋转光晕
 *  4. 流光边框按钮 + 触感反馈 (scaleIn)
 *  5. 极光缓慢流动渐变背景
 *  6. 温暖、有吸引力的文案
 */
@Composable
private fun PermissionRequestScreen(onAllPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }
    var showContent by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要摄像头权限才能记录表情数据", Toast.LENGTH_LONG).show()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // 入场动画触发
    LaunchedEffect(Unit) { showContent = true }

    // ─── 极光背景 ───
    val auroraProgress by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraProgress"
    )

    // ─── Logo 旋转光晕 ───
    val haloRotation by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloRotation"
    )

    // ─── 分层入场动画 ───
    val stepEnterAnim = fadeIn(tween(500)) + slideInVertically(
        initialOffsetY = { it / 5 },
        animationSpec = tween(500, easing = EaseOutCubic)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050510))
    ) {
        // ─── 极光流动背景 ───
        AuroraBackground(progress = auroraProgress)

        // ─── 主内容区 ───
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(400))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ─── Logo 区域 ───
                AnimatedVisibility(
                    visible = showContent,
                    enter = stepEnterAnim
                ) {
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 旋转光晕 (Canvas 绘制)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val radius = size.minDimension / 2 * 0.9f
                            // 外圈光弧
                            drawArc(
                                color = Color(0xFF6C63FF).copy(alpha = 0.3f),
                                startAngle = haloRotation,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(cx - radius, cy - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = 3f, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Color(0xFFFF6584).copy(alpha = 0.2f),
                                startAngle = haloRotation + 180f,
                                sweepAngle = 90f,
                                useCenter = false,
                                topLeft = Offset(cx - radius * 0.75f, cy - radius * 0.75f),
                                size = Size(radius * 1.5f, radius * 1.5f),
                                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                            )
                            // 内圈点缀
                            drawArc(
                                color = Color(0xFF00D2FF).copy(alpha = 0.25f),
                                startAngle = -haloRotation * 0.5f,
                                sweepAngle = 60f,
                                useCenter = false,
                                topLeft = Offset(cx - radius * 0.5f, cy - radius * 0.5f),
                                size = Size(radius, radius),
                                style = Stroke(width = 2f, cap = StrokeCap.Round)
                            )
                        }
                        // 微笑弧线 Logo (Canvas 绘制)
                        SmileLogo(modifier = Modifier.size(48.dp))
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ─── 步骤内容 ───
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideIn Horizontally(tween(350)) { it / 4 }) togetherWith
                                (fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 4 })
                    },
                    label = "stepContent"
                ) { step ->
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> PrivacyStep()
                        2 -> GetStartedStep()
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── 步骤指示器 ───
                StepIndicator(
                    currentStep = currentStep,
                    totalSteps = 3,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ─── 按钮区域 ───
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(200))
                    },
                    label = "buttonContent"
                ) { step ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (step) {
                            0 -> {
                                // 下一步按钮
                                FlowingBorderButton(
                                    text = "开启微笑之旅",
                                    onClick = { currentStep = 1 }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                SkipButton(onClick = { currentStep = 2 })
                            }
                            1 -> {
                                // 了解了按钮
                                FlowingBorderButton(
                                    text = "我了解，继续",
                                    onClick = { currentStep = 2 }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                SkipButton(onClick = { currentStep = 2 })
                            }
                            2 -> {
                                // 主按钮: 授权并开始
                                FlowingBorderButton(
                                    text = "授权并开始记录",
                                    onClick = {
                                        cameraLauncher.launch(Manifest.permission.CAMERA)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        // 检测是否已授权
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            onAllPermissionsGranted()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                // 已授权入口
                                TextButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            onAllPermissionsGranted()
                                        } else {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text(
                                        "已授权？点这里进入 →",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 13.sp
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

// ═══════════════════════════════════════════════════════════════
// 引导步骤内容
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SmileTracker",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFFFFF),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "你的每一份用心，都值得被看见",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB8B8FF),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "让人脸摄像头记录你的微笑数据\n见证每天的成长与变化",
            fontSize = 14.sp,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun PrivacyStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "隐私为先",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFFFFF),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "数据安全是我们的底线",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFFFB8D0),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 隐私要点卡片
        PrivacyPoint(text = "所有数据只在本地处理", color = Color(0xFF6C63FF))
        Spacer(modifier = Modifier.height(6.dp))
        PrivacyPoint(text = "不会上传或存储任何图像", color = Color(0xFFFF6584))
        Spacer(modifier = Modifier.height(6.dp))
        PrivacyPoint(text = "你随时可以删除所有数据", color = Color(0xFF00D2FF))
    }
}

@Composable
private fun PrivacyPoint(text: String, color: Color) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFFB0B0B0),
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun GetStartedStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "一切就绪",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFFFFF),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "让我们开始记录微笑吧",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB8FFB8),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "授权摄像头权限\n摄像头只用于表情识别，不会保存画面",
            fontSize = 14.sp,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// v3.0 UI 组件
// ═══════════════════════════════════════════════════════════════

/**
 * 极光缓慢流动渐变背景
 */
@Composable
private fun AuroraBackground(progress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 基础深色背景
        drawRect(color = Color(0xFF050510))

        // 极光层 1 — 蓝紫色
        val offset1 = w * 0.3f * kotlin.math.sin(progress * kotlin.math.PI * 2)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6C63FF).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f + offset1, h * 0.3f),
                radius = w * 0.6f
            )
        )

        // 极光层 2 — 粉红色
        val offset2 = w * 0.25f * kotlin.math.cos(progress * kotlin.math.PI * 2 + 1f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF6584).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(w * 0.4f + offset2, h * 0.6f),
                radius = w * 0.55f
            )
        )

        // 极光层 3 — 青蓝色
        val offset3 = w * 0.2f * kotlin.math.sin(progress * kotlin.math.PI * 2 + 2.5f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00D2FF).copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(w * 0.6f + offset3, h * 0.8f),
                radius = w * 0.5f
            )
        )

        // 顶部微光
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF6C63FF).copy(alpha = 0.04f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.4f
            )
        )
    }
}

/**
 * 微笑弧线 Logo — Canvas 绘制抽象弧线
 */
@Composable
private fun SmileLogo(modifier: Modifier = Modifier) {
    val logoProgress by rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulse"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val baseRadius = size.minDimension / 2 * 0.7f
        val pulseScale = 1f + logoProgress * 0.08f
        val r = baseRadius * pulseScale

        // 主体微笑弧线 (宽弧)
        drawArc(
            color = Color(0xFF6C63FF),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r * 0.8f),
            size = Size(r * 2, r * 2),
            style = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        )

        // 小眼睛 — 左点
        drawCircle(
            color = Color(0xFFFF6584),
            radius = size.minDimension * 0.06f,
            center = Offset(cx - r * 0.35f, cy - r * 0.15f)
        )

        // 小眼睛 — 右点
        drawCircle(
            color = Color(0xFFFF6584),
            radius = size.minDimension * 0.06f,
            center = Offset(cx + r * 0.45f, cy - r * 0.15f)
        )

        // 点缀星光点
        drawCircle(
            color = Color(0xFF00D2FF).copy(alpha = 0.6f),
            radius = size.minDimension * 0.03f,
            center = Offset(cx + r * 0.1f, cy - r * 0.55f)
        )
    }
}

/**
 * 流光边框按钮 — 流动渐变 + 触感 scaleIn 反馈
 */
@Composable
private fun FlowingBorderButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val flowProgress by rememberInfiniteTransition(label = "flow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowProgress"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A1A))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 流光边框
        Canvas(modifier = Modifier.fillMaxSize().padding(1.5.dp)) {
            val borderBrush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF6C63FF),
                    0.25f to Color(0xFFFF6584),
                    0.5f to Color(0xFF00D2FF),
                    0.75f to Color(0xFF6C63FF),
                    1.0f to Color(0xFF6C63FF)
                ),
                center = Offset(size.width / 2 + (flowProgress - 0.5f) * size.width * 0.5f, size.height / 2)
            )
            // 简化流光效果: 使用线性渐变流动
            val flowBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF6C63FF),
                    Color(0xFFFF6584),
                    Color(0xFF00D2FF),
                    Color(0xFF6C63FF)
                ),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2)
            )
            drawRoundRect(
                brush = flowBrush,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                style = Stroke(width = 2.5f)
            )
        }

        // 按钮文字
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFFFFF)
        )
    }
}

/**
 * 步骤指示器 — 圆点指示当前步骤
 */
@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dotWidth"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = tween(300),
                label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isActive) Color(0xFF6C63FF).copy(alpha = alpha)
                        else Color(0xFF8E8E93).copy(alpha = alpha)
                    )
            )
        }
    }
}

/**
 * 跳过按钮
 */
@Composable
private fun SkipButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            "跳过引导",
            color = Color(0xFF6C63FF).copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
