package com.example.autodial.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Keeps large app data off internal (/data/data) storage.
 * Prefers a removable SD card; otherwise uses shared external files
 * (Android/data) which Android does not count as internal app data.
 */
object AppStorage {
    const val DB_NAME = "autodial_database"
    private const val TAG = "AppStorage"

    fun dataDir(context: Context): File {
        val candidates = context.getExternalFilesDirs(null)
            ?.filterNotNull()
            ?.filter { isWritableDir(it) }
            .orEmpty()

        val removable = candidates.firstOrNull { dir ->
            Environment.isExternalStorageRemovable(dir) || !Environment.isExternalStorageEmulated(dir)
        }
        val chosen = removable
            ?: candidates.firstOrNull()
            ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "data")

        val dbDir = File(chosen, "db")
        if (!dbDir.exists()) dbDir.mkdirs()
        return dbDir
    }

    fun databaseFile(context: Context): File = File(dataDir(context), DB_NAME)

    fun backupFile(context: Context): File = File(dataDir(context), "$DB_NAME.bak")

    fun databaseSize(context: Context): Long {
        val base = databaseFile(context)
        return listOf("", "-wal", "-shm").sumOf { suffix ->
            val file = File(base.absolutePath + suffix)
            if (file.exists()) file.length() else 0L
        }
    }

    fun migrateFromInternalIfNeeded(context: Context) {
        val dest = databaseFile(context)
        val internal = context.getDatabasePath(DB_NAME)
        try {
            if (internal.exists() && internal.length() > 0) {
                if (!dest.exists() || dest.length() == 0L || internal.lastModified() > dest.lastModified()) {
                    copyDbFamily(internal, dest)
                    Log.d(TAG, "Migrated database to ${dest.absolutePath}")
                }
            }
            val internalBak = context.getDatabasePath("$DB_NAME.db.bak")
            if (internalBak.exists()) {
                val destBak = backupFile(context)
                if (!destBak.exists() || destBak.length() < internalBak.length()) {
                    internalBak.copyTo(destBak, overwrite = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed migrating database off internal storage", e)
            return
        }
        deleteInternalDatabaseFiles(context)
    }

    fun deleteInternalDatabaseFiles(context: Context) {
        val names = listOf(
            DB_NAME,
            "$DB_NAME-wal",
            "$DB_NAME-shm",
            "$DB_NAME-journal",
            "$DB_NAME.db.bak"
        )
        names.forEach { name ->
            try {
                val file = context.getDatabasePath(name)
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "Removed internal $name")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete internal $name", e)
            }
        }
    }

    fun clearCaches(context: Context) {
        try {
            purgeDownloadedApks(context)
        } catch (_: Exception) {}
        try {
            context.cacheDir.deleteRecursively()
        } catch (_: Exception) {}
        try {
            context.externalCacheDir?.deleteRecursively()
        } catch (_: Exception) {}
        context.externalCacheDirs?.forEach { dir ->
            try {
                dir?.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    fun purgeDownloadedApks(context: Context) {
        val dirsToSearch = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(null)
        )
        dirsToSearch.forEach { dir ->
            try {
                dir.walkTopDown()
                    .filter { it.isFile && (it.extension.equals("apk", ignoreCase = true) || it.name.startsWith("update_") || it.name.endsWith(".tmp")) }
                    .forEach { file ->
                        if (file.delete()) {
                            Log.i(TAG, "Deleted stale temp file: ${file.name}")
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning temp files in ${dir.absolutePath}", e)
            }
        }
    }

    fun purgeLegacyDiskCaches(context: Context) {
        try {
            // 1. Delete legacy Firestore LevelDB disk cache directories
            val firestoreDirs = listOf(
                File(context.filesDir, "firestore"),
                File(context.filesDir, "app_firestore"),
                File(context.noBackupFilesDir, "firestore"),
                context.getDir("firestore", Context.MODE_PRIVATE)
            )
            firestoreDirs.forEach { dir ->
                if (dir.exists()) {
                    val deleted = dir.deleteRecursively()
                    Log.i(TAG, "Purged legacy Firestore cache at ${dir.absolutePath}: $deleted")
                }
            }

            // 2. Delete redundant backup files if main DB file exists
            val mainDb = databaseFile(context)
            if (mainDb.exists() && mainDb.length() > 0) {
                val bak = backupFile(context)
                if (bak.exists()) {
                    bak.delete()
                    Log.i(TAG, "Removed duplicate backup file ${bak.absolutePath}")
                }
            }

            // 3. Remove leftover internal DB files
            deleteInternalDatabaseFiles(context)

            // 4. Clear temporary application caches
            clearCaches(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed purging legacy storage", e)
        }
    }

    private fun copyDbFamily(src: File, dest: File) {
        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = File(src.absolutePath + suffix)
            val destSidecar = File(dest.absolutePath + suffix)
            if (sidecar.exists()) {
                sidecar.copyTo(destSidecar, overwrite = true)
            } else if (destSidecar.exists()) {
                destSidecar.delete()
            }
        }
    }

    private fun isWritableDir(dir: File): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            dir.exists() && dir.canWrite()
        } catch (_: Exception) {
            false
        }
    }
}
