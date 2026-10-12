package com.ykz200.smiletracker.ui

import kotlin.math.abs
import kotlin.math.ln

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
            Triple("中性", neutral, 0xFF5C6BCEL),
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
// v5.0 全新模型 — 连续追踪 + AI 建议 + 智能节律 + 社交分享
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
 * v6.0: 新增各情绪概率、积极指数、情绪标签字段
 */
data class EmotionSnapshot(
    val timestamp: Long,
    val smileProbability: Float,
    val dominantEmotion: String,
    val dominantProb: Float,
    val eyeStrainLevel: Float,  // 眼部疲劳等级 [0..1]
    val headPosture: String,    // 头部姿态: "正视" / "低头" / "偏头"
    val valence: Float = 0f,    // 效价
    val energyLevel: Int = 50,  // 能量等级
    // v6.0 新增: 各情绪概率 (用于情感共鸣评分与极光色计算)
    val angryProbability: Float = 0f,
    val neutralProbability: Float = 0f,
    val thinkingProbability: Float = 0f,
    val surpriseProbability: Float = 0f,
    val fearProbability: Float = 0f,
    val disgustProbability: Float = 0f,
    val positivityIndex: Int = 50,        // 积极指数 0-100
    val emotionTag: String = "neutral"    // 情绪标签: "positive" / "neutral" / "negative"
)

/**
 * v5.0 工作节奏分析结果 — 智能增强版 + 周期性洞察
 */
data class WorkRhythm(
    val peakHour: Int?,             // 最开心的小时 (0-23)
    val troughHour: Int?,           // 最低谷的小时
    val avgMorningSmile: Float,     // 上午平均微笑
    val avgAfternoonSmile: Float,   // 下午平均微笑
    val avgEveningSmile: Float,     // 晚间平均微笑
    val recommendation: String,     // 自动生成的建议
    val focusScore: Int = 50,       // 专注度评分
    val burnoutRisk: String = "低",  // 倦怠风险
    val weekOverWeekChange: Float = 0f, // 周同比变化
    val bestConsecutiveHours: Int = 0,  // v5.0 最长连续积极时段(小时)
    val flowStateFrequency: Float = 0f  // v5.0 心流状态频率
)

/**
 * v5.0 AI 智能建议 — 增加可执行度评分和分类
 */
data class EmotionInsight(
    val id: String,
    val category: InsightCategory,
    val title: String,
    val description: String,
    val action: String?,            // 可执行建议
    val priority: Int,              // 1-5 (5最高)
    val timestamp: Long,
    val actionableScore: Int = 0     // v5.0 可执行度 0-100
)

enum class InsightCategory {
    WELLNESS,       // 健康提醒
    PRODUCTIVITY,   // 效率建议
    SOCIAL,         // 社交提示
    REST,           // 休息提醒
    EXERCISE,       // 运动建议
    MINDFULNESS     // v5.0 正念提醒
}

/**
 * v5.0 心情连续追踪 — 时间序列上的心情变化
 */
data class MoodTimeline(
    val entries: List<MoodEntry>,
    val trendDirection: TrendDirection,
    val volatility: Float,          // 波动性 [0..1]
    val dominantMood: String,       // 主导心情
    val avgIntensity: Float,        // 平均强度
    val bestStreak: Int = 0,        // v5.0 最长连续积极天数
    val weeklyPattern: String = ""  // v5.0 周模式总结
)

enum class TrendDirection {
    IMPROVING,      // 改善中
    STABLE,         // 稳定
    DECLINING,      // 下降中
    FLUCTUATING     // 波动
}

/**
 * v5.0 用眼健康报告 — 增加长期趋势和预测
 */
data class EyeHealthReport(
    val totalScreenMinutes: Int,
    val avgBlinkRate: Float,           // 每分钟眨眼次数
    val strainScore: Int,              // 0-100
    val twentyTwentyTwentyCompliance: Float, // 20-20-20法则遵守率
    val recommendation: String,
    val strainTrend: String = "稳定",   // "改善" / "稳定" / "恶化"
    val dryEyeRisk: String = "低",      // v5.0 干眼风险
    val nextBreakDue: String = ""       // v5.0 下次休息时间建议
)

/**
 * v5.0 环境光检测结果
 */
data class AmbientLightInfo(
    val luxLevel: Float,           // 光照强度 (lux)
    val quality: LightQuality,     // 光线质量
    val screenBrightnessSuggestion: Int, // 建议屏幕亮度 0-100
    val eyeStrainRisk: String,     // "低" / "中" / "高"
    val circadianPhase: String = "" // v5.0 昼夜节律时段
)

enum class LightQuality {
    EXCELLENT,  // >500 lux
    GOOD,       // 300-500
    DIM,        // 100-300
    DARK,       // <100
    HARSH       // 过强/直射
}

/**
 * v5.0 每日摘要 — 用于分享和回顾
 */
data class DailySummary(
    val date: String,
    val totalSmiles: Int,
    val peakEmotion: String,
    val stressAverage: String,
    val highlight: String,        // 今日亮点
    val shareText: String,        // 分享文案
    val rankPercentile: Int = 50  // 超过同周期用户的百分比
)

/**
 * v5.0 长期趋势分析
 */
data class LongTermTrend(
    val periodDays: Int,
    val smileSlope: Float,          // 微笑趋势斜率
    val consistencyScore: Int,      // 一致性评分 0-100
    val topTrigger: String,         // 最常见的积极触发器
    val growthAreas: List<String>,  // 建议改进的领域
    val milestonesAchieved: List<String> // 达成的里程碑
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

/**
 * v5.0: 增强版智能建议生成器 — 多维度个性化
 */
fun generateSmartRecommendation(rhythm: WorkRhythm): String {
    val parts = mutableListOf<String>()
    
    rhythm.peakHour?.let { hour ->
        val hourLabel = when {
            hour < 12 -> "\u4e0a\u5348${hour}\u70b9"
            hour < 18 -> "\u4e0b\u5348${hour - 12}\u70b9"
            else -> "\u665a\u4e0a${hour - 12}\u70b9"
        }
        parts.add("\u4f60\u5728${hourLabel}\u5fc3\u60c5\u6700\u597d")
    }
    
    if (rhythm.avgMorningSmile > rhythm.avgAfternoonSmile) {
        parts.add("早上的你更爱笑, 重要会议尽量排上午")
    }
    
    if (rhythm.focusScore > 70) {
        parts.add("专注度优秀!")
    }

    // v5.0 新增：心流状态建议
    if (rhythm.flowStateFrequency > 0.6f) {
        parts.add("今天频繁进入心流状态，继续保持当前工作节奏")
    }

    // v5.0 新增：倦怠预警
    if (rhythm.burnoutRisk == "高") {
        parts.add("⚠️ 近期工作压力偏高，建议安排一次深度放松")
    }
    
    return parts.joinToString("; ").ifEmpty { "继续保持观察, 积累更多数据中" }
}

/**
 * v5.0: 心情追踪辅助 — 从连续快照中分析波动性
 */
fun analyzeVolatility(snapshots: List<EmotionSnapshot>): Float {
    if (snapshots.size < 2) return 0f
    val probabilities = snapshots.map { it.smileProbability }
    val avg = probabilities.average().toFloat()
    val variance = probabilities.map { (it - avg) * (it - avg) }.average().toFloat()
    return kotlin.math.sqrt(variance).coerceIn(0f, 1f)
}

/**
 * v5.0: 趋势方向判断
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

/**
 * v5.0: 生成每日分享文案
 */
fun generateShareText(smileCount: Int, peakEmotion: String, streakDays: Int): String {
    return when {
        smileCount > 80 -> "今日微笑次数破百！保持好心情 😊 #SmileTracker 连续记录${streakDays}天"
        smileCount > 40 -> "今天笑得不少！主打一个开心 😄 已坚持记录${streakDays}天"
        smileCount > 15 -> "平平淡淡也是真～记录情绪第${streakDays}天 ✨"
        else -> "心情需要调节的一天，明天会更好的 🌙 #自我关怀"
    }
}

/**
 * v5.0: 计算一致性评分 (数据越规律分越高)
 */
fun calculateConsistencyScore(snapshots: List<EmotionSnapshot>): Int {
    if (snapshots.size < 5) return 50
    val intervals = snapshots.zipWithNext { a, b -> kotlin.math.abs(b.timestamp - a.timestamp) }
    val avgInterval = intervals.average()
    val deviation = intervals.map { kotlin.math.abs(it - avgInterval) }.average()
    val consistency = (1.0 - (deviation / (avgInterval + 1.0)).coerceIn(0.0, 1.0)) * 100
    return consistency.toInt().coerceIn(0, 100)
}

/**
 * v5.0: 检测心流状态 (连续长时间保持积极且稳定的情绪)
 */
fun detectFlowState(snapshots: List<EmotionSnapshot>, windowMinutes: Int = 30): Boolean {
    if (snapshots.size < 10) return false
    val recent = snapshots.takeLast(windowMinutes)
    if (recent.size < 5) return false
    val avgSmile = recent.map { it.smileProbability }.average()
    val volatility = analyzeVolatility(recent)
    return avgSmile > 0.5 && volatility < 0.15
}

/**
 * v5.0: 生成长期趋势里程碑
 */
fun checkMilestones(totalDays: Int, totalSmiles: Int, currentStreak: Int): List<String> {
    val milestones = mutableListOf<String>()
    if (totalDays >= 7) milestones.add("坚持一周! 🎉")
    if (totalDays >= 30) milestones.add("月度达人! 坚持30天 🏆")
    if (totalDays >= 100) milestones.add("百日里程碑! 超强毅力 💯")
    if (totalSmiles >= 100) milestones.add("累计微笑100次 😊")
    if (totalSmiles >= 1000) milestones.add("千笑达人! 累计微笑1000次 🌟")
    if (currentStreak >= 7) milestones.add("连续7天记录! 🔥")
    if (currentStreak >= 30) milestones.add("连续30天! 习惯养成 💪")
    return milestones
}

// ═══════════════════════════════════════════════════════════════
// v6.0 Aurora Glow — 新增智能模型
// ═══════════════════════════════════════════════════════════════

/**
 * v6.0: 心情预测 — 基于历史趋势预测未来情绪走向
 */
data class MoodForecast(
    val predictedTrend: String = "稳定",
    val predictedMood: String = "中性",
    val confidence: Float = 0.5f,
    val suggestion: String = "保持当前节奏",
    val forecastColor: Long = 0xFF9FA8DA
)

/**
 * v6.0: 情感共鸣评分 — 评估用户情绪的多样性和平衡性 (0-100)
 * 高分 = 情绪丰富且平衡; 低分 = 情绪单调或极端
 */
fun calculateEmotionalResonance(snapshots: List<EmotionSnapshot>): Int {
    if (snapshots.size < 10) return 50
    val emotions = listOf("微笑", "生气", "中性", "思考", "惊讶", "恐惧", "厌恶")
    val weights = emotions.map { label ->
        snapshots.map { snap ->
            when (label) {
                "微笑" -> snap.smileProbability
                "生气" -> snap.angryProbability
                "中性" -> snap.neutralProbability
                "思考" -> snap.thinkingProbability
                "惊讶" -> snap.surpriseProbability
                "恐惧" -> snap.fearProbability
                else -> snap.disgustProbability
            }
        }.average()
    }
    // Shannon entropy-based diversity score
    val sum = weights.sum().coerceAtLeast(0.001)
    val normalized = weights.map { it / sum }
    val entropy = -normalized.filter { it > 0 }.sumOf { p -> p * ln(p) }
    val maxEntropy = ln(weights.size.toDouble())
    val diversityScore = (entropy / maxEntropy * 100).toInt().coerceIn(0, 100)

    // Check extremeness (one emotion too dominant)
    val maxWeight = weights.maxOrNull() ?: 0.0
    val extremenessPenalty = if (maxWeight > 0.7) 20 else 0

    return (diversityScore - extremenessPenalty).coerceIn(0, 100)
}

/**
 * v6.0: 极光情绪色 — 根据情绪混合实时计算色彩
 * 权重混合所有情绪对应的情感色
 */
fun auroraMoodColor(
    smile: Float, angry: Float, neutral: Float,
    thinking: Float, surprise: Float, fear: Float, disgust: Float
): Long {
    val colors = listOf(
        0xFFFFD68A to smile,       // 微笑 → 金黄
        0xFFFF6B6B to angry,       // 生气 → 红
        0xFF9FA8DA to neutral,     // 中性 → 靛青
        0xFFB388FF to thinking,    // 思考 → 宇宙紫
        0xFFFFE082 to surprise,    // 惊讶 → 琥珀
        0xFFFFAB40 to fear,        // 恐惧 → 日落橙
        0xFFA1887F to disgust      // 厌恶 → 褐灰
    )
    var r = 0.0; var g = 0.0; var b = 0.0; var totalW = 0.0
    colors.forEach { (col, w) ->
        val rr = ((col shr 16) and 0xFF).toDouble()
        val gg = ((col shr 8) and 0xFF).toDouble()
        val bb = (col and 0xFF).toDouble()
        r += rr * w; g += gg * w; b += bb * w; totalW += w
    }
    if (totalW < 0.001) return 0xFF9FA8DA
    val ri = (r / totalW).toInt().coerceIn(0, 255)
    val gi = (g / totalW).toInt().coerceIn(0, 255)
    val bi = (b / totalW).toInt().coerceIn(0, 255)
    return (0xFF000000 or (ri shl 16) or (gi shl 8) or bi).toLong()
}

/**
 * v6.0: 心情预测引擎 — 简单线性趋势外推
 */
fun generateMoodForecast(snapshots: List<EmotionSnapshot>): MoodForecast {
    if (snapshots.size < 10) return MoodForecast(confidence = 0.2f)
    val recent = snapshots.takeLast(20)
    val avgSmile = recent.map { it.smileProbability }.average()
    val avgPositive = recent.map { it.positivityIndex }.average()
    val moodSlope = recent.takeLast(10).map { it.smileProbability }.average() -
                     recent.take(10).map { it.smileProbability }.average()

    val trend = when {
        moodSlope > 0.15 -> "上升趋势 ✨"
        moodSlope < -0.15 -> "下降趋势 🌙"
        else -> "稳定平衡 ⚖️"
    }
    val mood = when {
        avgSmile > 0.7 -> "愉悦"
        avgSmile > 0.4 -> "平静"
        avgPositive > 60 -> "积极"
        avgPositive < 40 -> "低落"
        else -> "中性"
    }
    val suggestion = when {
        moodSlope > 0.15 -> "情绪在上升! 保持当前活动"
        moodSlope < -0.15 -> "情绪有所低落，建议起身活动"
        avgSmile < 0.3 -> "今天微笑较少，试试听听音乐"
        else -> "情绪稳定，继续保持"
    }
    val color = when {
        moodSlope > 0.15 -> 0xFF69F0AE  // AuroraGreen
        moodSlope < -0.15 -> 0xFF9FA8DA  // Indigo
        else -> 0xFFFFD68A  // WarmGold
    }
    return MoodForecast(
        predictedTrend = trend,
        predictedMood = mood,
        confidence = (0.4 + kotlin.math.abs(moodSlope) * 2).toFloat().coerceIn(0.2f, 0.9f),
        suggestion = suggestion,
        forecastColor = color
    )
}
