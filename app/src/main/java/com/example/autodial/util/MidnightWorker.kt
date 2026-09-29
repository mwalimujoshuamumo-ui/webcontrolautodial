package com.example.autodial.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autodial.data.AppDatabase

class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = database.createRepository()
            val prefManager = PreferenceManager(applicationContext)

            if (prefManager.isAutoCleanupEnabled()) {
                val days = prefManager.getRetentionDays()
                val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                
                Log.i("MidnightWorker", "Starting auto-cleanup for data older than $days days...")
                
                val deletedTxns = repository.deleteTransactionsBefore(cutoff)
                val deletedSms = repository.deleteSmsBefore(cutoff)
                val deletedSessions = repository.deleteSessionsBefore(cutoff)
                repository.deleteSettledDebtRecordsBefore(cutoff)
                
                if (prefManager.isPruningEnabled()) {
                    repository.pruneTransactionLogsBefore(cutoff)
                    repository.deleteInactiveClientsBefore(cutoff)
                }

                val totalDeleted = deletedTxns + deletedSms + deletedSessions
                val dbSize = database.getDatabaseSize(applicationContext)
                
                // CONDITIONAL VACUUM: Only vacuum if substantial rows were deleted or DB > 50 MB
                if (totalDeleted >= 500 || dbSize >= 50 * 1024 * 1024L) {
                    database.vacuum()
                    Log.i("MidnightWorker", "Database vacuumed (deleted $totalDeleted records, size $dbSize bytes).")
                } else {
                    Log.i("MidnightWorker", "Auto-cleanup complete ($totalDeleted records removed). Vacuum skipped.")
                }
            }

            try {
                AppStorage.clearCaches(applicationContext)
                AppStorage.purgeDownloadedApks(applicationContext)
                Log.i("MidnightWorker", "App cache and downloaded APKs cleared.")
            } catch (e: Exception) {
                Log.e("MidnightWorker", "Failed to clear cache", e)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("MidnightWorker", "Failed midnight reset", e)
            Result.failure()
        }
    }
}
