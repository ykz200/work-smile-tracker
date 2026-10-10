package com.ykz200.smiletracker.ui

/**
 * 情绪概率 — 对应输出的 Emotion 模型结果
 * 每种情绪一个 Float [0..1]
 */
data class EmotionProbabilities(
    val smile: Float = 0f,     // 微笑
    val angry: Float = 0f,     // 生气
    val neutral: Float = 0f,   // 冷漠/中性
    val thinking: Float = 0f,  // 思考
    val surprise: Float = 0f,  // 惊讶
    val fear: Float = 0f,      // 恐惧
    val disgust: Float = 0f    // 厌恶
) {
    /** 返回当前最高情绪的标签+概率 */
    fun dominantEmotion(): Pair<String, Float> {
        val emotions = listOf(
            "微笑" to smile,
            "生气" to angry,
            "中性" to neutral,
            "思考" to thinking,
            "惊讶" to surprise,
            "恐惧" to fear,
            "厌恶" to disgust
        )
        return emotions.maxByOrNull { it.second } ?: ("中性" to 0f)
    }

    /** 积极指数: 正面情绪 - 负面情绪 (0..100) */
    fun positivityIndex(): Int {
        val positive = smile
        val negative = angry + fear + disgust
        return ((positive - negative).coerceIn(-1f, 1f) * 50 + 50).toInt().coerceIn(0, 100)
    }

    /** 所有情绪排序 (由高到低)，附带颜色 */
    fun sorted(): List<Triple<String, Float, Long>> {
        return listOf(
            Triple("微笑", smile, 0xFF4CAF50L),
            Triple("生气", angry, 0xFFE91E63L),
            Triple("中性", neutral, 0xFF5C6BC0L),
            Triple("思考", thinking, 0xFF7E57C2L),
            Triple("惊讶", surprise, 0xFFFFC107L),
            Triple("恐惧", fear, 0xFFFF5722L),
            Triple("厌恶", disgust, 0xFF795548L)
        ).sortedByDescending { it.second }
    }
}

/**
 * 应用主状态 — 分为 学习中 / 实时检测 两种模式
 */
enum class AppState {
    LEARNING_BASELINE,    // 正在学习基线 (前 60 秒)
    LIVE_DETECTING       // 实时检测中
}

/** 简单的情绪上升趋势 (本次 vs 上次) */
data class EmotionTrend(
    val rising: Boolean,
    val delta: Float
)

// ═══════════════════════════════════════════════════════════════
// v3.0 新增 — 心情日记 + 情绪趋势分析 + 工作节律
// ═══════════════════════════════════════════════════════════════

/**
 * 心情日记条目 — 手动记录的瞬时心情
 */
data class MoodEntry(
    val timestamp: Long,
    val emoji: String,          // 心情 emoji
    val label: String,          // 标签: "开心" / "平静" / "焦虑" / "疲惫"
    val intensity: Int,         // 强度 1-5
    val note: String? = null    // 可选备注
)

/**
 * 情绪快照 — 某个时刻的完整情绪状态 (用于趋势分析)
 */
data class EmotionSnapshot(
    val timestamp: Long,
    val smileProbability: Float,
    val dominantEmotion: String,
    val dominantProb: Float,
    val eyeStrainLevel: Float,  // 眼部疲劳等级 [0..1]
    val headPosture: String     // 头部姿态: "正视" / "低头" / "偏头"
)

/**
 * v3.0 工作节奏分析结果
 */
data class WorkRhythm(
    val peakHour: Int?,         // 最开心的小时 (0-23)
    val troughHour: Int?,      // 最低谷的小时
    val avgMorningSmile: Float, // 上午平均微笑
    val avgAfternoonSmile: Float, // 下午平均微笑
    val recommendation: String  // 自动生成的建议
)

/**
 * 辅助函数: 根据头部角度估算姿态
 */
fun estimatePosture(pitch: Float, yaw: Float, roll: Float): String {
    return when {
        pitch < -10 -> "低头专注"
        pitch > 8 -> "仰望思考"
        yaw > 15 -> "右侧注视"
        yaw < -15 -> "左侧注视"
        kotlin.math.abs(roll) > 10 -> "侧头思考"
        else -> "正视前方"
    }
}
