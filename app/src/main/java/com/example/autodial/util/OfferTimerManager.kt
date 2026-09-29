package com.example.autodial.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.autodial.data.AppDatabase
import com.example.autodial.data.Offer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object OfferTimerManager {

    private const val ACTION_TIMER_EVENT = "com.example.autodial.OFFER_TIMER_EVENT"
    private const val EXTRA_OFFER_ID = "offer_id"
    private const val EXTRA_ACTION_TYPE = "action_type" // "ENABLE" or "DISABLE"

    fun scheduleAlarms(context: Context, offer: Offer, skipStateSync: Boolean = false) {
        if (!offer.isTimerEnabled) {
            cancelAlarms(context, offer.id)
            return
        }

        // Safety check for exact alarms on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("OfferTimerManager", "Missing SCHEDULE_EXACT_ALARM permission. Timer for ${offer.name} may be delayed.")
            }
        }

        if (!skipStateSync) {
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val shouldBeEnabled = offer.isTimerActive(currentTime)
            
            // Immediately update if state is different
            if (offer.isEnabled != shouldBeEnabled) {
                updateOfferState(context, offer, shouldBeEnabled)
            }
        }

        scheduleAlarm(context, offer.id, offer.timerEnableAt, "ENABLE")
        scheduleAlarm(context, offer.id, offer.timerDisableAt, "DISABLE")
    }

    private fun updateOfferState(context: Context, offer: Offer, enabled: Boolean) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val repo = db.createRepository()
            
            // Update primary offer
            repo.updateOffer(offer.copy(isEnabled = enabled, lastModified = System.currentTimeMillis()))
        }
    }

    fun rescheduleAll(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val repo = db.createRepository()
            val offers = repo.allOffers.first()
            offers.filter { it.isTimerEnabled }.forEach { offer ->
                scheduleAlarms(context, offer)
            }
        }
    }

    private fun scheduleAlarm(context: Context, offerId: Int, timeStr: String, actionType: String) {
        if (timeStr.isEmpty() || !timeStr.contains(":")) {
            Log.w("OfferTimerManager", "Invalid time string for alarm: $timeStr")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, OfferTimerReceiver::class.java).apply {
            action = ACTION_TIMER_EVENT
            putExtra(EXTRA_OFFER_ID, offerId)
            putExtra(EXTRA_ACTION_TYPE, actionType)
        }

        // Use a unique requestCode for each offer/action combination
        val requestCode = offerId * 10 + (if (actionType == "ENABLE") 1 else 2)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val calendar = Calendar.getInstance().apply {
                val parts = timeStr.split(":")
                val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If time is in the past, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // CRASH FIX: Check for exact alarm permission on Android 12+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d("OfferTimerManager", "Scheduled $actionType (INEXACT) for Offer $offerId at ${calendar.time}")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d("OfferTimerManager", "Scheduled $actionType (EXACT) for Offer $offerId at ${calendar.time}")
            }
        } catch (e: Exception) {
            Log.e("OfferTimerManager", "Failed to schedule alarm for $timeStr", e)
        }
    }

    fun cancelAlarms(context: Context, offerId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, OfferTimerReceiver::class.java).apply {
            action = ACTION_TIMER_EVENT
        }

        listOf("ENABLE", "DISABLE").forEach { actionType ->
            val requestCode = offerId * 10 + (if (actionType == "ENABLE") 1 else 2)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    class OfferTimerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TIMER_EVENT) {
                val offerId = intent.getIntExtra(EXTRA_OFFER_ID, -1)
                val actionType = intent.getStringExtra(EXTRA_ACTION_TYPE)
                
                if (offerId != -1 && actionType != null) {
                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch {
                        val db = AppDatabase.getDatabase(context)
                        val repo = db.createRepository()
                        val offers = repo.allOffers.first()
                        val offer = offers.find { it.id == offerId }
                        
                        if (offer != null && offer.isTimerEnabled) {
                            val shouldEnable = actionType == "ENABLE"
                            updateOfferState(context, offer, shouldEnable)
                            Log.i("OfferTimerReceiver", "Offer $offerId (${offer.name}) set to isEnabled=$shouldEnable by Timer")
                            
                            // MUTUAL EXCLUSION: If enabling this offer, disable its switch target.
                            // If disabling this offer, enable its switch target.
                            if (offer.switchOfferId != null) {
                                val targetOffer = offers.find { it.id == offer.switchOfferId }
                                if (targetOffer != null) {
                                    val targetEnabled = !shouldEnable
                                    repo.updateOffer(targetOffer.copy(isEnabled = targetEnabled, lastModified = System.currentTimeMillis()))
                                    Log.i("OfferTimerReceiver", "Automatically set switch target offer ${targetOffer.id} (${targetOffer.name}) to isEnabled=$targetEnabled")
                                }
                            }

                            // Reschedule for next day without overwriting the explicit receiver action
                            scheduleAlarms(context, offer.copy(isEnabled = shouldEnable), skipStateSync = true)
                        }
                    }
                }
            }
        }
    }
}
