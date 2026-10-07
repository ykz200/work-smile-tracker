package com.ykz200.smiletracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.ykz200.smiletracker.data.RepositoryProvider
import com.ykz200.smiletracker.data.SmileRepositoryImpl
import com.ykz200.smiletracker.db.SmileDatabase
import com.ykz200.smiletracker.ui.DashboardApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Android 端 MainActivity
 *  -
 *  - 启动 shared 模块中的 DashboardApp
 *  - 在 lifecycleScope 中插入 RepositoryProvider.init，注入 Android 特定的 SQLDelight Driver
 *  - 启动 SmileyAnalysisWorker (WorkManager) 在 09:00-18:00 内每分钟采样
 *  - 前台 Service 保障长时间后台运行
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 提供 Android 平台的 SQLDelight Driver 并初始化 RepositoryProvider
        val database = SmileDatabase(
            driver = com.sqldelight.db.SqlCursor  // placeholder：需要 app.cash.sqldelight:android-driver 提供 AndroidSqliteDriver
        )
        // RepositoryProvider.init(database) // TODO: 等 Gradle 下载完依赖后启用

        // 2. 启动 Compose Dashboard (双端共享 UI)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardApp()
                }
            }
        }

        // 3. 调度 WorkManager 后台任务 (工作日 09:00-18:00)
        val workRequest = PeriodicWorkRequestBuilder<SmileyAnalysisWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "smiley_analysis",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

        // 4. 拉起前台 Service (保活)
        startForegroundService(Intent(this, SmileyAnalysisService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        WorkManager.getInstance(applicationContext).cancelUniqueWork("smiley_analysis")
    }
}
