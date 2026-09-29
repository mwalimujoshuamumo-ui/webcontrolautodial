package com.example.autodial.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autodial.data.AppDatabase
import com.example.autodial.data.MainRepository
import com.example.autodial.data.ScheduledTask
import com.example.autodial.data.Transaction
import kotlinx.coroutines.flow.first

class ScheduledTaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = database.createRepository()
        val prefManager = PreferenceManager(applicationContext)

        // Engage Bot: run any "recommend tomorrow" sessions that are due
        try {
            EngageBotManager.processDueScheduled(applicationContext)
            EngageBotManager.cleanupExpiredSessions(applicationContext)
        } catch (e: Exception) {
            Log.e("ScheduledWorker", "Engage Bot scheduled processing error", e)
        }

        if (!prefManager.isAutomationEnabled()) return Result.success()

        val now = System.currentTimeMillis()
        val allScheduled = database.scheduledTaskDao().getAllScheduledTasks().first()
        
        val dueTasks = allScheduled.filter { 
            it.isEnabled && it.scheduledTime <= now && it.lastExecuted < (now - 60000) 
        }

        for (task in dueTasks) {
            if (prefManager.getTokens() <= 0 && !prefManager.isUnlimitedActive()) {
                Log.w("ScheduledWorker", "No tokens to process task ${task.id}")
                repository.updateScheduledTask(task.copy(isEnabled = false, notes = "Insufficient Tokens"))
                continue
            }

            val offer = repository.allOffers.first().find { it.id == task.offerId }
            if (offer != null) {
                val transaction = Transaction(
                    amount = offer.amount,
                    offerName = offer.name,
                    offerId = offer.id,
                    customerNumber = task.targetNumber,
                    status = Transaction.STATUS_WAITING,
                    senderName = "SCHEDULED",
                    rawMessage = "Scheduled execution: ${offer.name}"
                )
                val transId = repository.insertTransaction(transaction)

                Log.d("ScheduledWorker", "Task ${task.id} injected into queue with ID $transId")
                TransactionQueueManager.processNext(applicationContext)

                if (task.repeatMode == ScheduledTask.REPEAT_ONCE) {
                    repository.updateScheduledTask(task.copy(status = ScheduledTask.STATUS_COMPLETED, isEnabled = false, lastExecuted = now))
                } else {
                    val nextTime = calculateNextOccurrence(task.scheduledTime, task.repeatMode, task.selectedDays)
                    repository.updateScheduledTask(task.copy(scheduledTime = nextTime, lastExecuted = now))
                }
            } else {
                repository.updateScheduledTask(task.copy(isEnabled = false, notes = "Offer not found"))
            }
        }

        return Result.success()
    }

    private fun calculateNextOccurrence(current: Long, mode: String, days: String): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = current
        
        // Ensure we move to future
        while (cal.timeInMillis <= System.currentTimeMillis()) {
            when (mode) {
                ScheduledTask.REPEAT_DAILY -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                ScheduledTask.REPEAT_DAYS -> {
                    val selected = days.split(",").mapNotNull { it.toIntOrNull() }
                    if (selected.isEmpty()) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    } else {
                        var found = false
                        for (i in 1..7) {
                            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            if (selected.contains(cal.get(java.util.Calendar.DAY_OF_WEEK))) {
                                found = true
                                break
                            }
                        }
                        if (!found) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
                else -> { // Once
                    return current
                }
            }
        }
        return cal.timeInMillis
    }
}
