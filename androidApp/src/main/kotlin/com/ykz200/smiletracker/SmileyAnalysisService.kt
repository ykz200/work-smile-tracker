package com.ykz200.smiletracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Android 前台 Service - 保活
 * 以 Camera foregroundServiceType 权限保证 Worker + Activity 不被系统杀掉
 */
class SmileyAnalysisService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val note: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmileTracker 正在记录")
            .setContentText("工作日 " + "on" + " 上班时表情追踪中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, note)
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SmileTracker 前台服务",
                NotificationManager.IMPORTANCE_LOW)
            val mgr = getSystemService(NotificationManager::class.java)
            mgr?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "smile_tracker_foreground"
        const val NOTIF_ID = 4242
    }
}
