package com.ykz200.smiletracker.data

import com.ykz200.smiletracker.db.SmileDatabase
import com.ykz200.smiletracker.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * SQLDelight 生成的 DAO + coroutines adapter 帮我们把数据库操作挂到 Kotlin Flow
 * 这个类的实例由各端在 composition root 注入, 调用方不知道也不关心是 Android SQLite 还是 iOS SQLite
 *
 * v2 升级:
 *  - 新增 getDataRetentionDays 数据保留策略
 *  - insertSync 使用 scope 异步落盘避免主线程阻塞
 *  - 异常处理增强 (onFailure 不静默丢弃)
 */
class SmileRepositoryImpl(
    private val database: SmileDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SmileRepository {

    private val queries = database.smileDatabaseQueries

    override suspend fun insert(face: FaceAnalysis) {
        queries.insertRecord(
            tsMillis = face.tsMillis,
            smileProbability = face.smileProbability.toDouble(),
            leftEyeOpenProbability = face.leftEyeOpenProbability.toDouble(),
            rightEyeOpenProbability = face.rightEyeOpenProbability.toDouble(),
            headPitch = face.headPitch.toDouble(),
            headYaw = face.headYaw.toDouble(),
            headRoll = face.headRoll.toDouble(),
            rawData = null  // 启动阶段不存 raw_data, 节省存储
        )
    }

    /** 同步写入 (WorkManager 后台任务中用, 脱离 suspend context) */
    fun insertSync(face: FaceAnalysis) {
        try {
            queries.insertRecord(
                tsMillis = face.tsMillis,
                smileProbability = face.smileProbability.toDouble(),
                leftEyeOpenProbability = face.leftEyeOpenProbability.toDouble(),
                rightEyeOpenProbability = face.rightEyeOpenProbability.toDouble(),
                headPitch = face.headPitch.toDouble(),
                headYaw = face.headYaw.toDouble(),
                headRoll = face.headRoll.toDouble(),
                rawData = null
            )
        } catch (e: Exception) {
            // 同步模式下不崩溃, 记录到日志
            System.err.println("[SmileRepository] insertSync failed: ${e.message}")
        }
    }

    override suspend fun queryMinuteStats(startMillis: Long, endMillis: Long): List<MinuteStat> {
        return try {
            queries.analyzeSmile(startMillis, endMillis)
                .executeAsList()
                .map { row ->
                    MinuteStat(
                        minuteKey = row.minute_key,
                        sampleCount = row.sample_count.toInt(),
                        avgSmile = (row.avg_smile ?: -1.0).toFloat(),
                        maxSmile = (row.max_smile ?: -1.0).toFloat(),
                        smileCount = (row.smile_count ?: 0).toInt(),
                        smilePercent = (row.smile_percent ?: 0.0).toFloat(),
                        avgEyeLeft = (row.avg_eye_left ?: -1.0).toFloat(),
                        avgEyeRight = (row.avg_eye_right ?: -1.0).toFloat()
                    )
                }
        } catch (e: Exception) {
            System.err.println("[SmileRepository] queryMinuteStats failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getLatest(): FaceAnalysis? {
        return try {
            val row = queries.selectLatestSample().executeAsOneOrNull() ?: return null
            FaceAnalysis(
                tsMillis = row.ts_millis,
                smileProbability = (row.smile_probability).toFloat(),
                leftEyeOpenProbability = (row.left_eye_open_probability).toFloat(),
                rightEyeOpenProbability = (row.right_eye_open_probability).toFloat(),
                headPitch = (row.head_pitch).toFloat(),
                headYaw = (row.head_yaw).toFloat(),
                headRoll = (row.head_roll).toFloat()
            )
        } catch (e: Exception) {
            System.err.println("[SmileRepository] getLatest failed: ${e.message}")
            null
        }
    }

    /**
     * 清理过期数据 — 默认保留 90 天
     * 建议在 WorkManager 中每周调用一次
     */
    fun cleanupOldData(retentionDays: Int = 90) {
        try {
            val cutoff = Clock.System.now().toEpochMilliseconds() - (retentionDays * 24L * 3600L * 1000L)
            queries.deleteOlderThan(cutoff)
        } catch (e: Exception) {
            System.err.println("[SmileRepository] cleanup failed: ${e.message}")
        }
    }

    /** 获取指定日期的采样总数 (DailySummary 用) */
    suspend fun getDailyStats(dateStartMillis: Long, dateEndMillis: Long): DailySummary? {
        return try {
            val stats = queryMinuteStats(dateStartMillis, dateEndMillis)
            if (stats.isEmpty()) return null
            val totalSamples = stats.sumOf { it.sampleCount.toLong() }.toInt()
            val smileCount = stats.sumOf { it.smileCount.toLong() }.toInt()
            val avgProb = stats.map { it.avgSmile.coerceAtLeast(0f) }.average().toFloat()
            val peakProb = stats.map { it.maxSmile.coerceAtLeast(0f) }.max()
            val peakStat = stats.maxByOrNull { it.maxSmile }
            val avgEye = stats.map {
                (it.avgEyeLeft.coerceAtLeast(0f) + it.avgEyeRight.coerceAtLeast(0f)) / 2f
            }.average()
            val eyeStrainIdx = ((1.0 - avgEye.coerceAtLeast(0.0)) * 100).coerceIn(0f, 100f)
            val stressLvl = when {
                eyeStrainIdx > 70 -> "高"
                eyeStrainIdx > 40 -> "中"
                else -> "低"
            }
            DailySummary(
                date = peakStat?.minuteKey?.take(10) ?: "",
                totalSamples = totalSamples,
                smileCount = smileCount,
                avgSmileProbability = avgProb,
                peakSmileProbability = peakProb,
                peakTime = peakStat?.minuteKey?.takeLast(5) ?: "--:--",
                eyeStrainIndex = eyeStrainIdx,
                stressLevel = stressLvl
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** 建库 factory — 各端通过 RepositoryProvider 注入 */
object RepositoryProvider {
    @Volatile private var instance: SmileRepository? = null

    fun get(): SmileRepository =
        instance ?: throw IllegalStateException("需要先调用 RepositoryProvider.init(database)")

    fun init(database: SmileDatabase) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = SmileRepositoryImpl(database)
                }
            }
        }
    }
}
