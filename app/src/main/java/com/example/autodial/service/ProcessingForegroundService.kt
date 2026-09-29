package com.example.autodial.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autodial.MainActivity
import com.example.autodial.util.WakeManager

class ProcessingForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "ProcessingChannel"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            try {
                val intent = Intent(context, ProcessingForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("ProcessingService", "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ProcessingForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("ProcessingService", "Failed to stop service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannel()
            WakeManager.wakeAndUnlock(this)

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AutoDial Processing")
                .setContentText("Processing active transaction...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(mainPendingIntent)
                .setOngoing(true)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.e("ProcessingService", "startForeground failed", e)
            }
        } catch (e: Exception) {
            Log.e("ProcessingService", "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Processing Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active background transaction processing"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
