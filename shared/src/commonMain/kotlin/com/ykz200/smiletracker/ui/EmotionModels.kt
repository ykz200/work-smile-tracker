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

    /** 情绪能量等级 (0=极度低落, 100=极度兴奋) */
    fun energyLevel(): Int {
        val activation = surprise + angry + smile * 0.5f
        val deactivation = neutral + fear * 0.3f
        return ((activation - deactivation * 0.3f).coerceIn(0f, 1f) * 100).toInt()
    }

    /** 情绪效价 (-1负面 ~ +1正面) */
    val valence: Float
        get() = (smile * 0.8f + surprise * 0.3f - angry * 0.7f - fear * 0.5f - disgust * 0.6f)
            .coerceIn(-1f, 1f)

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
// v4.0 全新模型 — 连续追踪 + AI 建议 + 智能节律
// ═══════════════════════════════════════════════════════════════

/**
 * 心情日记条目 — 手动记录的瞬时心情
 */
data class MoodEntry(
    val timestamp: Long,
    val emoji: String,          // 心情 emoji
    val label: String,          // 标签: "开心" / "平静" / "焦虑" / "疲惫"
    val intensity: Int,         // 强度 1-5
    val note: String? = null,   // 可选备注
    val trigger: String? = null // 触发因素: "工作" / "休息" / "运动" / "社交"
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
    val headPosture: String,    // 头部姿态: "正视" / "低头" / "偏头"
    val valence: Float = 0f,    // 效价
    val energyLevel: Int = 50   // 能量等级
)

/**
 * v4.0 工作节奏分析结果 — 智能增强版
 */
data class WorkRhythm(
    val peakHour: Int?,             // 最开心的小时 (0-23)
    val troughHour: Int?,           // 最低谷的小时
    val avgMorningSmile: Float,     // 上午平均微笑
    val avgAfternoonSmile: Float,   // 下午平均微笑
    val avgEveningSmile: Float,     // 晚间平均微笑 (v4.0新增)
    val recommendation: String,     // 自动生成的建议
    val focusScore: Int = 50,       // 专注度评分 (v4.0新增)
    val burnoutRisk: String = "低",  // 倦怠风险 (v4.0新增)
    val weekOverWeekChange: Float = 0f // 周同比变化 (v4.0新增)
)

/**
 * v4.0 AI 智能建议
 */
data class EmotionInsight(
    val id: String,
    val category: InsightCategory,
    val title: String,
    val description: String,
    val action: String?,            // 可执行建议
    val priority: Int,              // 1-5 (5最高)
    val timestamp: Long
)

enum class InsightCategory {
    WELLNESS,       // 健康提醒
    PRODUCTIVITY,   // 效率建议
    SOCIAL,         // 社交提示
    REST,           // 休息提醒
    EXERCISE        // 运动建议
}

/**
 * v4.0 心情连续追踪 — 时间序列上的心情变化
 */
data class MoodTimeline(
    val entries: List<MoodEntry>,
    val trendDirection: TrendDirection,
    val volatility: Float,          // 波动性 [0..1]
    val dominantMood: String,       // 主导心情
    val avgIntensity: Float         // 平均强度
)

enum class TrendDirection {
    IMPROVING,      // 改善中
    STABLE,         // 稳定
    DECLINING,      // 下降中
    FLUCTUATING     // 波动
}

/**
 * v4.0 用眼健康报告
 */
data class EyeHealthReport(
    val totalScreenMinutes: Int,
    val avgBlinkRate: Float,           // 每分钟眨眼次数
    val strainScore: Int,              // 0-100
    val twentyTwentyTwentyCompliance: Float, // 20-20-20法则遵守率
    val recommendation: String,
    val strainTrend: String = "稳定"    // "改善" / "稳定" / "恶化"
)

/**
 * v4.0 环境光检测结果
 */
data class AmbientLightInfo(
    val luxLevel: Float,           // 光照强度 (lux)
    val quality: LightQuality,     // 光线质量
    val screenBrightnessSuggestion: Int, // 建议屏幕亮度 0-100
    val eyeStrainRisk: String      // "低" / "中" / "高"
)

enum class LightQuality {
    EXCELLENT,  // >500 lux
    GOOD,       // 300-500
    DIM,        // 100-300
    DARK,       // <100
    HARSH       // 过强/直射
}

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

/**
 * v4.0: 根据智能节律数据生成个性化建议
 */
fun generateSmartRecommendation(rhythm:WorkRhythm): String {
    val parts = mutableListOf<String>()
    
    rhythm.peakHour?.let { hour ->
        val timeLabel = when {
            hour < 12 -> "上午${hour}点"
            hour < 18 -> "下午${hour - 12}点"
            else -> "晚上${hour - 12}点"
        }
        parts.add("你在$timeLabel心情最好")
    }
    
    if (rhythm.avgMorningSmile > rhythm.avgAfternoonSmile) {
        parts.add("早上的你更爱笑, 重要会议尽量排上午")
    }
    
    if (rhythm.focusScore > 70) {
        parts.add("专注度优秀!")
    }
    
    return parts.joinToString("; ").ifEmpty { "继续保持观察, 积累更多数据中" }
}

/**
 * v4.0: 心情追踪辅助 — 从连续快照中分析波动性
 */
fun analyzeVolatility(snapshots: List<EmotionSnapshot>): Float {
    if (snapshots.size < 2) return 0f
    val probabilities = snapshots.map { it.smileProbability }
    val avg = probabilities.average().toFloat()
    val variance = probabilities.map { (it - avg) * (it - avg) }.average().toFloat()
    return kotlin.math.sqrt(variance).coerceIn(0f, 1f)
}

/**
 * v4.0: 趋势方向判断
 */
fun determineTrend(snapshots: List<EmotionSnapshot>): TrendDirection {
    if (snapshots.size < 3) return TrendDirection.STABLE
    val recent = snapshots.takeLast(3).map { it.smileProbability }.average()
    val earlier = snapshots.dropLast(3).takeLast(3).map { it.smileProbability }.average()
    val diff = recent - earlier
    val volatility = analyzeVolatility(snapshots)
    
    return when {
        volatility > 0.3f -> TrendDirection.FLUCTUATING
        diff > 0.1f -> TrendDirection.IMPROVING
        diff < -0.1f -> TrendDirection.DECLINING
        else -> TrendDirection.STABLE
    }
}
