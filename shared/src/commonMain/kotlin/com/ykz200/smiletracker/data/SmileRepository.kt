package com.ykz200.smiletracker.data

import com.ykz200.smiletracker.db.SmileDatabase
import com.ykz200.smiletracker.domain.FaceAnalysis
import com.ykz200.smiletracker.domain.MinuteStat
import com.ykz200.smiletracker.domain.SmileRepository
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
    }

    override suspend fun queryMinuteStats(startMillis: Long, endMillis: Long): List<MinuteStat> {
        return queries.analyzeSmile(startMillis, endMillis)
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
    }

    override suspend fun getLatest(): FaceAnalysis? {
        val row = queries.selectLatestSample().executeAsOneOrNull() ?: return null
        return FaceAnalysis(
            tsMillis = row.id ?: 0L,
            smileProbability = (row.smile_probability).toFloat(),
            leftEyeOpenProbability = (row.left_eye_open_probability).toFloat(),
            rightEyeOpenProbability = (row.right_eye_open_probability).toFloat(),
            headPitch = (row.head_pitch).toFloat(),
            headYaw = (row.head_yaw).toFloat(),
            headRoll = (row.head_roll).toFloat()
        )
    }
}

/** 建库 factory — 各端通过 DriverProvider 注入 */
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
