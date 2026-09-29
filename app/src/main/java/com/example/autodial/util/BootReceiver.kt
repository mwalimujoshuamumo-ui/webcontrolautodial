package com.example.autodial.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.autodial.service.RelayService
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            Log.i("BootReceiver", "Boot action received: $action")
            if (action == Intent.ACTION_BOOT_COMPLETED || 
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                
                Log.i("BootReceiver", "Device rebooted / package updated. Initializing background services...")

                // 1. Force reset any stale processing locks leftover from pre-reboot
                try {
                    TransactionQueueManager.forceResetLocks()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error resetting locks", e)
                }

                // 2. Reschedule offer timers
                try {
                    OfferTimerManager.rescheduleAll(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling offer timers", e)
                }

                // 3. Enforce WorkManager periodic tasks
                try {
                    scheduleWorkers(context)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error setting up workers", e)
                }

                // 4. Verify Accessibility Service Status
                try {
                    val prefManager = PreferenceManager(context)
                    val isAccEnabled = UssdHelper.isAccessibilityServiceEnabled(context)
                    if (!isAccEnabled && prefManager.isAutomationEnabled()) {
                        Log.w("BootReceiver", "Accessibility service is OFF after reboot. Alerting user...")
                        showAccessibilityAlertNotification(context)
                    } else {
                        Log.i("BootReceiver", "Accessibility service is ON and active.")
                    }

                    // 5. Start Relay Service if CONNECT role was set
                    if (prefManager.getDeviceRole() == "MAIN" || prefManager.getDeviceRole() == "LINKED") {
                        try {
                            val relayIntent = Intent(context, RelayService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(relayIntent)
                            } else {
                                context.startService(relayIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("BootReceiver", "Error starting RelayService on boot", e)
                        }
                    }

                    // 6. Resume transaction queue processing if automation is on
                    if (prefManager.isAutomationEnabled() && prefManager.isGlobalAutomationEnabled()) {
                        TransactionQueueManager.processNext(context)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error during boot initialization checks", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Critical reboot trigger failure", e)
        }
    }

    private fun scheduleWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // ScheduledTaskWorker (every 15 min)
        val periodicRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(
            15, TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            "ScheduledTaskWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        // Midnight Reset Worker
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val delay = midnight.timeInMillis - now.timeInMillis
        val midnightRequest = PeriodicWorkRequestBuilder<MidnightWorker>(
            24, TimeUnit.HOURS
        ).setInitialDelay(delay, TimeUnit.MILLISECONDS)
         .build()

        workManager.enqueueUniquePeriodicWork(
            "MidnightResetWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            midnightRequest
        )
    }

    private fun showAccessibilityAlertNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "AccessibilityAlertChannel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Accessibility Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when Accessibility Service is disabled"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1001, settingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("⚠️ Accessibility Service Disconnected")
                .setContentText("Tap to re-enable AutoDial Accessibility Service for automation.")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(9001, notification)
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to show accessibility alert notification", e)
        }
    }
}
