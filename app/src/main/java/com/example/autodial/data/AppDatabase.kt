package com.example.autodial.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.autodial.util.AppStorage

@Database(
    entities = [
        Offer::class,
        Transaction::class,
        ScheduledTask::class,
        NotificationTemplate::class,
        Contact::class,
        RelayConfig::class,
        UssdMap::class,
        UssdMapStep::class,
        UssdMapOption::class,
        RelayDevice::class,
        EngageSession::class,
        TopUpPlan::class,
        Client::class,
        DebtRecord::class,
        ClientSms::class
    ],
    version = 35,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offerDao(): OfferDao
    abstract fun transactionDao(): TransactionDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun notificationTemplateDao(): NotificationTemplateDao
    abstract fun contactDao(): ContactDao
    abstract fun relayConfigDao(): RelayConfigDao
    abstract fun ussdMapDao(): UssdMapDao
    abstract fun relayDeviceDao(): RelayDeviceDao
    abstract fun engageSessionDao(): EngageSessionDao
    abstract fun topUpPlanDao(): TopUpPlanDao
    abstract fun clientDao(): ClientDao

    fun vacuum() {
        this.openHelper.writableDatabase.execSQL("VACUUM")
    }

    fun getDatabaseSize(context: Context): Long {
        return AppStorage.databaseSize(context)
    }

    fun createRepository(): MainRepository = MainRepository(
        offerDao(),
        transactionDao(),
        scheduledTaskDao(),
        notificationTemplateDao(),
        contactDao(),
        relayConfigDao(),
        ussdMapDao(),
        relayDeviceDao(),
        engageSessionDao(),
        topUpPlanDao(),
        clientDao()
    )

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Schema Identity Hashes
        private const val HASH_V35 = "5b64a680faf211faba7db4ab390f5820"
        private const val HASH_V31_V34 = "97e97904e7fe8f072f69e1ba568dd6fd"
        private const val HASH_V28_V30 = "a38c227bf791bbcdce8b9a13b6320573"

        // Upgrade Migrations
        val MIGRATION_1_27 = object : Migration(1, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Defensive migration for old versions
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `clients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `lastTransactionDate` INTEGER, `totalPurchases` INTEGER NOT NULL, `outstandingDebt` REAL NOT NULL, `isBlacklisted` INTEGER NOT NULL, `blacklistedAt` INTEGER, `unblacklistedAt` INTEGER, `smsHistoryCount` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clients_phoneNumber` ON `clients` (`phoneNumber`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `debt_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` INTEGER NOT NULL, `amount` REAL NOT NULL, `date` INTEGER NOT NULL, `type` TEXT NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE TABLE IF NOT EXISTS `client_sms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` INTEGER NOT NULL, `message` TEXT NOT NULL, `date` INTEGER NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE transactions ADD COLUMN processingStartedAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val columnsToAdd = listOf(
                    Triple("isNotified", "INTEGER NOT NULL DEFAULT 0", "0"),
                    Triple("isSwitched", "INTEGER NOT NULL DEFAULT 0", "0"),
                    Triple("originalOfferId", "INTEGER", "NULL"),
                    Triple("originalOfferName", "TEXT", "NULL"),
                    Triple("switchReason", "TEXT", "NULL"),
                    Triple("notes", "TEXT NOT NULL DEFAULT ''", "''"),
                    Triple("processingStartedAt", "INTEGER NOT NULL DEFAULT 0", "0"),
                    Triple("usedUssdType", "TEXT NOT NULL DEFAULT 'MAIN'", "'MAIN'")
                )

                columnsToAdd.forEach { (name, type, _) ->
                    try {
                        val cursor = database.query("PRAGMA table_info(transactions)")
                        var exists = false
                        val nameIndex = cursor.getColumnIndex("name")
                        if (nameIndex >= 0) {
                            while (cursor.moveToNext()) {
                                if (cursor.getString(nameIndex) == name) {
                                    exists = true
                                    break
                                }
                            }
                        }
                        cursor.close()
                        if (!exists) {
                            database.execSQL("ALTER TABLE transactions ADD COLUMN $name $type")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AppDatabase", "Failed to add column $name", e)
                    }
                }
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE engage_sessions ADD COLUMN customerName TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionCode` ON `transactions` (`transactionCode`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE clients ADD COLUMN totalSpent REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V31_V34')")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE offers ADD COLUMN switchOfferId INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Failed to add switchOfferId to offers", e)
                }
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V35')")
            }
        }

        // Downgrade Migrations for Backdating APKs
        val MIGRATION_35_34 = object : Migration(35, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V31_V34')")
            }
        }

        val MIGRATION_34_33 = object : Migration(34, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V31_V34')")
            }
        }

        val MIGRATION_33_32 = object : Migration(33, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V31_V34')")
            }
        }

        val MIGRATION_32_31 = object : Migration(32, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V31_V34')")
            }
        }

        val MIGRATION_31_30 = object : Migration(31, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V28_V30')")
            }
        }

        val MIGRATION_30_29 = object : Migration(30, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V28_V30')")
            }
        }

        val MIGRATION_29_28 = object : Migration(29, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V28_V30')")
            }
        }

        val MIGRATION_28_27 = object : Migration(28, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$HASH_V28_V30')")
            }
        }

        private fun safeBackupDatabase(context: Context) {
            try {
                val dbFile = AppStorage.databaseFile(context)
                if (dbFile.exists() && dbFile.length() > 0) {
                    val bakFile = AppStorage.backupFile(context)
                    // SAFETY: Only create backup if DB size is under 50 MB or backup doesn't exist yet
                    // to prevent duplicating massive DB files on every startup.
                    if (dbFile.length() < 50 * 1024 * 1024L || !bakFile.exists()) {
                        dbFile.copyTo(bakFile, overwrite = true)
                        android.util.Log.d("AppDatabase", "Backed up database to ${bakFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Failed to backup database", e)
            }
        }

        private fun reconcileDatabaseSchema(context: Context, targetVersion: Int, targetIdentityHash: String) {
            val dbFile = AppStorage.databaseFile(context)
            if (!dbFile.exists()) return

            try {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                )
                val currentVersion = db.version

                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `clients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `lastTransactionDate` INTEGER, `totalPurchases` INTEGER NOT NULL, `totalSpent` REAL NOT NULL DEFAULT 0.0, `outstandingDebt` REAL NOT NULL, `isBlacklisted` INTEGER NOT NULL, `blacklistedAt` INTEGER, `unblacklistedAt` INTEGER, `smsHistoryCount` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clients_phoneNumber` ON `clients` (`phoneNumber`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `debt_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` INTEGER NOT NULL, `amount` REAL NOT NULL, `date` INTEGER NOT NULL, `type` TEXT NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `client_sms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` INTEGER NOT NULL, `message` TEXT NOT NULL, `date` INTEGER NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                ensureColumnExists(db, "offers", "switchOfferId", "INTEGER DEFAULT NULL")
                ensureColumnExists(db, "clients", "totalSpent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumnExists(db, "engage_sessions", "customerName", "TEXT NOT NULL DEFAULT ''")

                ensureColumnExists(db, "transactions", "processingStartedAt", "INTEGER NOT NULL DEFAULT 0")
                ensureColumnExists(db, "transactions", "isNotified", "INTEGER NOT NULL DEFAULT 0")
                ensureColumnExists(db, "transactions", "isSwitched", "INTEGER NOT NULL DEFAULT 0")
                ensureColumnExists(db, "transactions", "originalOfferId", "INTEGER")
                ensureColumnExists(db, "transactions", "originalOfferName", "TEXT")
                ensureColumnExists(db, "transactions", "switchReason", "TEXT")
                ensureColumnExists(db, "transactions", "notes", "TEXT NOT NULL DEFAULT ''")
                ensureColumnExists(db, "transactions", "usedUssdType", "TEXT NOT NULL DEFAULT 'MAIN'")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionCode` ON `transactions` (`transactionCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")

                db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$targetIdentityHash')")
                db.version = targetVersion

                db.close()
                android.util.Log.d("AppDatabase", "Reconciled schema from version $currentVersion to $targetVersion")
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error reconciling database schema", e)
            }
        }

        private fun ensureColumnExists(
            db: android.database.sqlite.SQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String
        ) {
            try {
                val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
                var exists = false
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == columnName) {
                            exists = true
                            break
                        }
                    }
                }
                cursor.close()
                if (!exists) {
                    db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Failed to add column $columnName to $tableName", e)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                AppStorage.migrateFromInternalIfNeeded(appContext)
                safeBackupDatabase(appContext)
                reconcileDatabaseSchema(
                    appContext,
                    35,
                    HASH_V35
                )

                val dbPath = AppStorage.databaseFile(appContext)
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    dbPath.absolutePath
                )
                .setJournalMode(
                    if (android.os.Environment.isExternalStorageRemovable(dbPath)) {
                        JournalMode.TRUNCATE
                    } else {
                        JournalMode.WRITE_AHEAD_LOGGING
                    }
                )
                .addMigrations(
                    MIGRATION_1_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30,
                    MIGRATION_30_31,
                    MIGRATION_31_32,
                    MIGRATION_32_33,
                    MIGRATION_33_34,
                    MIGRATION_34_35,
                    MIGRATION_35_34,
                    MIGRATION_34_33,
                    MIGRATION_33_32,
                    MIGRATION_32_31,
                    MIGRATION_31_30,
                    MIGRATION_30_29,
                    MIGRATION_29_28,
                    MIGRATION_28_27
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
