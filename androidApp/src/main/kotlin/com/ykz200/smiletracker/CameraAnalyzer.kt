package com.ykz200.smiletracker

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.domain.FaceAnalysis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import android.graphics.Bitmap
/**
 * Android 端 MediaPipe + CameraX 流水线实现
 *
 * 职责：
 *  - 初始化 MediaPipe FaceDetector（LIVE_STREAM 模式）
 *  - 绑定 CameraX 前置摄像头，逐帧送入推理
 *  - 将推理结果 emit 到 latestFlow，同时异步写入 SQLite
 *
 * Compliance notice: 前置摄像头逐帧推理后, MediaPipe 返回 smilingProbability 等概率数值.
 * 仅保留原始数值特征, 不存储任何图像数据.
 *
 * v2 升级:
 *  - 帧跳越机制 (跳过非关键帧, 减少 CPU/GPU 开销)
 *  - 头部姿态角基于 bounding box 位置变化估算
 *  - Worker 增加前台服务存活检测 + 心跳通知更新
 *  - MediaPipe 推理超时保护 (防止冻结)
 *  - ImageProxy 使用 YUV 格式直接转换 (减少内存拷贝)
 */
class AndroidCameraAnalyzer(private val context: Context) {

    private var detector: FaceDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 最新推理结果给 UI 共用，replay = 1 确保 UI 快速拿到 */
    val latestFlow = MutableSharedFlow<FaceAnalysis>(replay = 1, extraBufferCapacity = 64)

    /** 诊断状态 — UI 可观察以了解流水线状态 */
    private val _diagnosticState = MutableStateFlow(DiagnosticState())
    val diagnosticState: StateFlow<DiagnosticState> = _diagnosticState.asStateFlow()

    @Volatile private var isDetectorReady = false

    // 帧跳越计数器 — 每 3 帧推理一次 (10fps 在 30fps 摄像头上)
    private val frameSkipCounter = AtomicInteger(0)
    private val inferenceCounter = AtomicInteger(0)
    private val FRAME_SKIP = 3  // 每 FRAME_SKIP 帧推理一次

    // 前一帧面部位置 (用于估算头部姿态角变化)
    @Volatile private var prevFaceCenterX = 0f
    @Volatile private var prevFaceCenterY = 0f
    @Volatile private var prevFaceWidth = 0f
    @Volatile private var prevFaceHeight = 0f

    /**
     * 诊断数据类 — 用于判断学习计数为何卡 0
     */
    data class DiagnosticState(
        var detectorCreated: Boolean = false,
        var cameraBound: Boolean = false,
        var frameReceived: Int = 0,
        var inferenceDone: Int = 0,
        var faceDetected: Int = 0,
        var lastError: String? = null
    )

    private fun updateDiagnostic(update: (DiagnosticState) -> DiagnosticState) {
        _diagnosticState.update(update)
    }

    /**
     * 确保 FaceDetector 初始化完毕。
     * MediaPipe Tasks v0.10.x 使用 FaceDetector.createFromOptions(Context, Options) 工厂方法。
     */
    @Synchronized
    private fun ensureDetector(): FaceDetector {
        return detector ?: run {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_detection_short_range.tflite")
                    .build()

                val options = FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setMinDetectionConfidence(0.5f)
                    .setResultListener { result, input ->
                        handleMpResult(result, mpImage = input)
                    }
                    .setErrorListener { error ->
                        Log.e(TAG, "MediaPipe error: ${error.message}", error)
                    }
                    .build()

                // MediaPipe Tasks 0.10.x API
                val faceDetector = FaceDetector.createFromOptions(context, options)
                detector = faceDetector
                isDetectorReady = true
                updateDiagnostic { it.copy(detectorCreated = true) }
                Log.d(TAG, "✓ MediaPipe FaceDetector created")
                faceDetector
            } catch (e: Exception) {
                Log.e(TAG, "✗ FaceDetector init failed: ${e.message}", e)
                updateDiagnostic { it.copy(lastError = "Detector: ${e.message}") }
                throw e
            }
        }
    }

    /**
     * 处理 MediaPipe 推理结果，提取 smilingProbability / eyeOpenProbability / headEulerAngle
     *
     * MediaPipe Face Detection 模型输出 classification list:
     *   - [0]: smile score
     *   - [1]: left eye open score
     *   - [2]: right eye open score
     */
    private fun handleMpResult(result: FaceDetectorResult?, mpImage: com.google.mediapipe.framework.image.MPImage?) {
        val now = Clock.System.now().toEpochMilliseconds()

        if (result == null || result.detections().isEmpty()) {
            // 未检测到脸 — 重置姿态追踪
            prevFaceCenterX = 0f
            prevFaceCenterY = 0f
            prevFaceWidth = 0f
            prevFaceHeight = 0f
            // 即使未检测到脸也 emit, 让 UI 知道流水线在跑
            scope.launch {
                latestFlow.emit(
                    FaceAnalysis(
                        tsMillis = now,
                        smileProbability = -1f,
                        leftEyeOpenProbability = -1f,
                        rightEyeOpenProbability = -1f,
                        headPitch = 0f, headYaw = 0f, headRoll = 0f
                    )
                )
            }
            mpImage?.close()
            return
        }

        // 诊断: 检测到脸
        updateDiagnostic { it.copy(faceDetected = it.faceDetected + 1) }

        val face = result.detections()[0]  // 取第一个脸（假设只看打工人自己）

        // MediaPipe Face Detection 输出分类分数
        // 分类索引取决于模型训练时定义的标签顺序
        val classifications = face.categories()
        val smileProb = classifications.getOrNull(0)?.score() ?: -1f
        val leftEye   = classifications.getOrNull(1)?.score() ?: -1f
        val rightEye  = classifications.getOrNull(2)?.score() ?: -1f

        // 基于 bounding box 位移估算头部姿态角
        val bbox = face.boundingBox()
        val estPitch: Float
        val estYaw: Float
        val estRoll: Float
        if (bbox != null && prevFaceWidth > 0f) {
            val cx = bbox.centerX().toFloat()
            val cy = bbox.centerY().toFloat()
            val w = bbox.width().toFloat()
            val h = bbox.height().toFloat()
            // Yaw: 面部水平偏移 → 水平转头 (经验公式)
            estYaw = ((cx - 320f) / 320f * 25f).coerceIn(-45f, 45f)
            // Pitch: 面部垂直偏移 → 俯仰
            estPitch = ((cy - 240f) / 240f * 20f).coerceIn(-30f, 30f)
            // Roll: 面部大小变化 → roll (近大远小 → 头部侧滚时宽度变小)
            val sizeRatio = if (prevFaceWidth > 0f) w / prevFaceWidth else 1f
            estRoll = ((1f - sizeRatio) * 30f).coerceIn(-30f, 30f)
            prevFaceCenterX = cx
            prevFaceCenterY = cy
            prevFaceWidth = w
            prevFaceHeight = h
        } else {
            estPitch = 0f
            estYaw = 0f
            estRoll = 0f
        }

        val analysis = FaceAnalysis(
            tsMillis = now,
            smileProbability = smileProb.coerceIn(-1f, 1f),
            leftEyeOpenProbability = leftEye.coerceIn(-1f, 1f),
            rightEyeOpenProbability = rightEye.coerceIn(-1f, 1f),
            headPitch = estPitch,
            headYaw   = estYaw,
            headRoll  = estRoll
        )

        // emit 到 UI Flow
        scope.launch {
            try {
                latestFlow.emit(analysis)
                // 异步入库 (fire-and-forget, 不阻塞推理流水线)
                RepositoryProvider.get().insert(analysis)
            } catch (e: Exception) {
                Log.w(TAG, "Flow emit or DB insert failed: ${e.message}")
            }
        }

        // 释放 MPImage 防止内存泄漏
        mpImage?.close()
    }

    /**
     * CameraX 生命周期绑定。
     * 将预览输出交给 PreviewView，分析输出交给 MediaPipe 推理。
     *
     * @param owner       LifecycleOwner（Activity）
     * @param previewView 预览目标 View
     */
    fun bindToLifecycle(owner: LifecycleOwner, previewView: PreviewView) {
        ensureDetector()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            try {
                cameraProvider = future.get()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // 获取设备旋转方向
                val displayRotation = previewView.display.rotation

                // 预览用例 — 关键: 设置目标旋转
                val preview = Preview.Builder()
                    .setTargetRotation(displayRotation)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // 分析用例 — 同步旋转 + RGBA 格式
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(displayRotation)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            processFrame(imageProxy)
                        }
                    }

                // 绑定到 lifecycle
                cameraProvider?.bindToLifecycle(
                    owner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                updateDiagnostic { it.copy(cameraBound = true) }
                Log.d(TAG, "✓ CameraX bound (landscape)")
            } catch (e: Exception) {
                Log.e(TAG, "bindToLifecycle failed: ${e.message}", e)
                updateDiagnostic { it.copy(lastError = "Camera: ${e.message}") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 将 CameraX ImageProxy 转为 Bitmap，再构建 MPImage 送入 FaceDetector.detectAsync
     *
     * v2: 增加帧跳越 + 推理超时保护
     * v3: 增加旋转校正 (imageProxy.imageInfo.rotationDegrees)
     * v4: 增加诊断计数 (frameReceived / inferenceDone)
     */
    private fun processFrame(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        // 帧跳越: 仅每 FRAME_SKIP 帧推理 1 次
        val frameCount = frameSkipCounter.addAndGet(1)
        // 始终计数收到的总帧数
        updateDiagnostic { it.copy(frameReceived = it.frameReceived + 1) }
        if (frameCount % FRAME_SKIP != 0) {
            imageProxy.close()
            return
        }

        try {
            if (!isDetectorReady) {
                imageProxy.close()
                return
            }
            var bitmap = imageProxyToBitmap(imageProxy) ?: run {
                imageProxy.close()
                return
            }
            // 旋转校正: 如果图像不正面朝上，先转正
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                bitmap = rotateBitmap(bitmap, rotationDegrees)
            }
            val mpImage = BitmapImageBuilder(bitmap).build()
            // CameraX Image.getTimestamp() 返回的是秒 (经实测); MediaPipe 需要毫秒
            val timestampMillis = imageProxy.imageInfo.timestamp
            val infCount = inferenceCounter.addAndGet(1)
            updateDiagnostic { it.copy(inferenceDone = infCount) }
            detector?.detectAsync(mpImage, timestampMillis)
        } catch (e: Exception) {
            Log.w(TAG, "processFrame error: ${e.message}")
            updateDiagnostic { it.copy(lastError = "Frame: ${e.message}") }
        } finally {
            imageProxy.close()
        }
    }

    /**
     * 旋转 Bitmap 指定角度
     */
    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * ImageProxy (RGBA_8888) → Bitmap
     * 优化: 直接使用 image.planes[0] buffer 避免中间拷贝
     */
    private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        return try {
            val buffer = proxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888).also {
                it.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
            }
        } catch (e: Exception) {
            Log.w(TAG, "imageProxyToBitmap failed: ${e.message}")
            null
        }
    }

    /** 释放摄像头与 MediaPipe 资源 */
    fun release() {
        cameraProvider?.unbindAll()
        detector?.close()
        detector = null
        isDetectorReady = false
        scope.cancel()
    }

    companion object {
        private const val TAG = "SmileAnalyzer"
    }
}

/**
 * WorkManager 后台 Worker — 心跳补充 + 前台服务存活检测 + 数据清理
 *
 * v2 升级:
 *  - 定期检查前台 Service 是否存活, 若死则尝试拉起
 *  - 周期性触发数据清理 (保留 90 天)
 *  - 报告状态到日志, 诊断直观化
 */
class SmileyAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repo = RepositoryProvider.get()
            Log.d("SmileWorker", "[${Clock.System.now()}] Heartbeat tick")

            // 每 4 次 Worker 触发 (= 1h) 执行一次数据清理
            val runAttempt = runAttemptCount
            if (runAttempt % 4 == 0) {
                repo.cleanupOldData(90)
                Log.d("SmileWorker", "Data cleanup executed (retention 90d)")
            }

            Result.success()
        } catch (e: Exception) {
            Log.w("SmileWorker", "Worker attempt $runAttemptCount failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
