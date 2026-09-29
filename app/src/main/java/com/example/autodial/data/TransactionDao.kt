package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT 500")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startOfDay ORDER BY date DESC LIMIT 500")
    fun getTransactionsToday(startOfDay: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startOfDay OR status = 'FAILED' OR status = 'WAITING' ORDER BY date DESC LIMIT 500")
    fun getTransactionsForDashboard(startOfDay: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :start AND date <= :end ORDER BY date DESC LIMIT 500")
    fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int): Int

    @Query("SELECT * FROM transactions WHERE transactionCode = :code LIMIT 1")
    suspend fun getTransactionByCode(code: String): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Int): Transaction?

    @Query("SELECT * FROM transactions WHERE customerNumber LIKE '%' || :phone || '%' ORDER BY date DESC LIMIT 1")
    suspend fun findRecentTransactionByPhone(phone: String): Transaction?

    @Query("SELECT * FROM transactions WHERE status = 'PROCESSING'")
    suspend fun getProcessingTransactions(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE status = 'WAITING' ORDER BY date ASC")
    suspend fun getWaitingTransactions(): List<Transaction>

    @Query("DELETE FROM transactions WHERE date < :timestamp AND status NOT IN ('WAITING', 'PROCESSING', 'SCHEDULED')")
    suspend fun deleteTransactionsBefore(timestamp: Long): Int

    @Query("UPDATE transactions SET rawMessage = '', executionTrail = '', finalResponse = '', verificationSms = '' WHERE date < :timestamp AND status NOT IN ('WAITING', 'PROCESSING', 'SCHEDULED') AND (rawMessage != '' OR executionTrail != '' OR finalResponse != '' OR verificationSms != '')")
    suspend fun pruneTransactionLogsBefore(timestamp: Long): Int

    @Query("DELETE FROM transactions WHERE status = 'UNMATCHED' AND date < :timestamp")
    suspend fun deleteUnmatchedTransactionsBefore(timestamp: Long): Int

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<Int>): Int

    @Query("UPDATE transactions SET status = :status WHERE id IN (:ids)")
    suspend fun updateTransactionsStatus(ids: List<Int>, status: String): Int
}
