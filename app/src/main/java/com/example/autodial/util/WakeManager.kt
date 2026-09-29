package com.example.autodial.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

object WakeManager {
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    @SuppressLint("InvalidWakeLockTag")
    fun wakeAndUnlock(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager

            // 1. Ensure screen wakes up if display is off
            try {
                if (!powerManager.isInteractive) {
                    if (screenWakeLock == null || !screenWakeLock!!.isHeld) {
                        @Suppress("DEPRECATION")
                        screenWakeLock = powerManager.newWakeLock(
                            PowerManager.FULL_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                            "AutoDial:ScreenWakeLock"
                        )
                        screenWakeLock?.acquire(2 * 60 * 1000L) // 2 minutes screen wake
                        Log.d("WakeManager", "Screen woken up from off state")
                    }
                }
            } catch (e: Exception) {
                Log.e("WakeManager", "Error acquiring screen wake lock", e)
            }

            // 2. Keep CPU awake for transaction processing
            try {
                if (cpuWakeLock == null || !cpuWakeLock!!.isHeld) {
                    cpuWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "AutoDial:PartialWakeLock"
                    )
                    cpuWakeLock?.acquire(5 * 60 * 1000L) // 5 minutes CPU lock
                    Log.d("WakeManager", "CPU Partial WakeLock acquired")
                }
            } catch (e: Exception) {
                Log.e("WakeManager", "Error acquiring CPU wake lock", e)
            }

            // 3. Launch LockScreenWakeActivity ONLY if Keyguard is ACTUALLY locked
            if (keyguardManager?.isKeyguardLocked == true) {
                try {
                    val intent = Intent(context, LockScreenWakeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(intent)
                    Log.d("WakeManager", "Launched LockScreenWakeActivity to dismiss keyguard")
                } catch (e: Exception) {
                    Log.e("WakeManager", "Error launching LockScreenWakeActivity", e)
                }
            }

        } catch (e: Exception) {
            Log.e("WakeManager", "Error in wakeAndUnlock", e)
        }
    }

    fun releaseWakeLock() {
        try {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
                screenWakeLock = null
                Log.d("WakeManager", "Screen wake lock released")
            }
        } catch (e: Exception) {
            Log.e("WakeManager", "Error releasing screen wake lock", e)
        }

        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
                cpuWakeLock = null
                Log.d("WakeManager", "CPU wake lock released")
            }
        } catch (e: Exception) {
            Log.e("WakeManager", "Error releasing CPU wake lock", e)
        }
    }
}
