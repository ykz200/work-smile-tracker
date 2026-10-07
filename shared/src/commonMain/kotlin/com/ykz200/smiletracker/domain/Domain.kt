package com.ykz200.smiletracker.domain

/**
 * 平台无关的面部表情分析结果
 * MediaPipe Face Detection 的标准输出包装
 */
data class FaceAnalysis(
    val tsMillis: Long,
    /** 微笑概率, -1.0f 表示未检测到人脸 */
    val smileProbability: Float,
    /** 左眼睁开概率 */
    val leftEyeOpenProbability: Float,
    /** 右眼睁开概率 */
    val rightEyeOpenProbability: Float,
    /** 头部姿态角 (度) */
    val headPitch: Float,
    val headYaw: Float,
    val headRoll: Float,
    /** 此次采样分钟内是否被记为"微笑" smileProbability >= 0.7 */
    val isSmiling: Boolean = smileProbability >= 0.7f
)

/**
 * 每分钟聚合统计结果 - 由 domain 层计算, UI 直接用
 */
data class MinuteStat(
    val minuteKey: String,          // "2026-10-07T15:30:00" 格式
    val sampleCount: Int,
    val avgSmile: Float,            // 该分钟内平均的微笑概率 [0..1]
    val maxSmile: Float,
    val smileCount: Int,            // 该分钟内 "微笑次数" (smileProb >= 0.7)
    val smilePercent: Float,        // 该分钟内时长占比 (%)
    val avgEyeLeft: Float,
    val avgEyeRight: Float
)

/**
 * 平台无关的抽象接口 — 由各端 (Android / iOS) 实现
 * 这是 KMP 的 expect/actual 模式 "顺手写" 版本, 用 interface + 注入更简单
 */
interface CameraAnalyzer {
    /** 启动前置摄像头 + MediaPipe 流水线 */
    suspend fun start()
    /** 停止摄像头, 释放资源 */
    suspend fun stop()
    /** 每秒接收一次最新的 FaceAnalysis (冷流, 热状态通过 callback) */
    suspend fun analysisFlow(): kotlinx.coroutines.flow.Flow<FaceAnalysis>
}

/**
 * 存储库抽象接口
 */
interface SmileRepository {
    suspend fun insert(face: FaceAnalysis)
    /** 查询指定时间窗口内, 每分钟聚合统计 */
    suspend fun queryMinuteStats(startMillis: Long, endMillis: Long): List<MinuteStat>
    /** 获取最近一条采样 (用于 Dashboard 实时显示 "当前状态") */
    suspend fun getLatest(): FaceAnalysis?
}
