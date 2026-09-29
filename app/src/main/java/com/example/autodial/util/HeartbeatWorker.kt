package com.example.autodial.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autodial.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class HeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val preferenceManager = PreferenceManager(applicationContext)
        val physicalId = preferenceManager.getPhysicalDeviceId()
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("HeartbeatWorker", "User not authenticated. Skipping heartbeat.")
            return Result.retry()
        }

        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = database.createRepository()
            val todayTxns = repository.getTransactionsToday().first()
            val totalToday = todayTxns.size
            val doneCount = todayTxns.count { it.status == com.example.autodial.data.Transaction.STATUS_SUCCESS }
            val failedCount = todayTxns.count { it.status == com.example.autodial.data.Transaction.STATUS_FAILED }
            val successRate = if (totalToday > 0) (doneCount.toFloat() / totalToday * 100).toInt() else 0

            val updates = mapOf(
                "lastSeen" to FieldValue.serverTimestamp(),
                "online" to true,
                "automationEnabled" to preferenceManager.isAutomationEnabled(),
                "tokenBalance" to preferenceManager.getTokens(),
                "authUid" to currentUser.uid,
                "agentId" to preferenceManager.getAgentId(),
                "agentPhone" to preferenceManager.getAgentPhone(),
                "userName" to (currentUser.displayName ?: preferenceManager.getUserName()),
                "userEmail" to (currentUser.email ?: preferenceManager.getRegisteredEmail()),
                "appStatus" to if (TransactionQueueManager.isProcessing()) "ACTIVE" else "IDLE",
                "totalTransactions" to totalToday,
                "successfulTransactions" to doneCount,
                "failedTransactions" to failedCount,
                "successRate" to successRate,
                "airtimeSim1" to preferenceManager.getAirtimeBalance(0),
                "airtimeSim2" to preferenceManager.getAirtimeBalance(1),
                "ussdResponseDelay" to preferenceManager.getUssdResponseDelay(),
                "simpleTimeout" to preferenceManager.getSimpleModeTimeout(),
                "advancedTimeout" to preferenceManager.getAdvancedModeTimeout(),
                "isUnlimited" to preferenceManager.isUnlimitedActive(),
                "unlimitedExpiry" to preferenceManager.getUnlimitedExpiry(),
                "accessibilityEnabled" to UssdHelper.isAccessibilityServiceEnabled(applicationContext),
                "batteryOptimizationIgnored" to if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    pm.isIgnoringBatteryOptimizations(applicationContext.packageName)
                } else true
            )
            
            val isAccEnabled = UssdHelper.isAccessibilityServiceEnabled(applicationContext)
            if (!isAccEnabled && preferenceManager.isAutomationEnabled()) {
                sendAccessibilityAlertNotification(applicationContext)
            }

            db.collection("devices").document(physicalId)
                .update(updates)
                .await()
                
            Log.d("HeartbeatWorker", "Heartbeat signal sent to Firebase for $physicalId")
            return Result.success()
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Failed to send heartbeat", e)
            return Result.retry()
        }
    }

    private fun sendAccessibilityAlertNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "AccessibilityAlertChannel"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Accessibility Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when Accessibility Service is disabled"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 1001, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle("⚠️ Accessibility Service Disconnected")
                .setContentText("Tap to re-enable AutoDial Accessibility Service for automation.")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(9001, notification)
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Failed to send accessibility alert notification", e)
        }
    }
}
