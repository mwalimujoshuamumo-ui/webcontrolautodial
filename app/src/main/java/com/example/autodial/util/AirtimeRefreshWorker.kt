package com.example.autodial.util

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class AirtimeRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefManager = PreferenceManager(applicationContext)
        if (!prefManager.isAutoRefreshAirtimeEnabled()) {
            return Result.success()
        }

        // If a transaction is actively processing, wait/queue until transaction queue is idle
        var waitAttempts = 0
        while (TransactionQueueManager.isProcessing() && waitAttempts < 10) {
            Log.i("AirtimeRefreshWorker", "Transaction processing active. Queueing airtime auto-refresh...")
            kotlinx.coroutines.delay(5000)
            waitAttempts++
        }

        if (TransactionQueueManager.isProcessing()) {
            Log.w("AirtimeRefreshWorker", "Transaction processing still busy. Retrying worker later.")
            return Result.retry()
        }

        val simSlot = prefManager.getAutoRefreshSimSlot()
        Log.i("AirtimeRefreshWorker", "Executing background airtime refresh for SIM slot: $simSlot")

        try {
            when (simSlot) {
                0 -> refreshSlot(0)
                1 -> refreshSlot(1)
                2 -> {
                    refreshSlot(0)
                    kotlinx.coroutines.delay(3000)
                    refreshSlot(1)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("AirtimeRefreshWorker", "Failed to auto-refresh airtime balance", e)
            return Result.retry()
        }
    }

    private fun refreshSlot(slot: Int) {
        val context = applicationContext
        val prefManager = PreferenceManager(context)
        UssdHelper.sendBackgroundUssd(context, "*144#", slot, allowFallback = false) { response ->
            val balance = UssdHelper.extractBalanceFromResponse(response)
            if (balance != null) {
                prefManager.setAirtimeBalance(slot, balance)
                Log.i("AirtimeRefreshWorker", "Updated SIM $slot balance to KSh $balance")
                com.example.autodial.util.FirebaseDeviceManager.getExistingInstance()?.triggerSync()
            } else {
                Log.w("AirtimeRefreshWorker", "Failed to extract balance from response on SIM $slot: $response")
            }
        }
    }

    companion object {
        private const val WORK_NAME = "AirtimeRefreshWorker"

        fun schedule(context: Context, intervalMins: Int) {
            val workManager = WorkManager.getInstance(context)
            val interval = intervalMins.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<AirtimeRefreshWorker>(
                interval, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
            Log.i("AirtimeRefreshWorker", "Scheduled auto-refresh every $interval mins")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i("AirtimeRefreshWorker", "Cancelled airtime auto-refresh worker")
        }
    }
}
