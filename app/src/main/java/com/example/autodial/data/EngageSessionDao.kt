package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EngageSessionDao {
    @Query("SELECT * FROM engage_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<EngageSession>>

    @Query("SELECT * FROM engage_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): EngageSession?

    @Query(
        """
        SELECT * FROM engage_sessions 
        WHERE customerNumber LIKE '%' || :phoneSuffix || '%' 
          AND state IN (:states)
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun findActiveByPhoneSuffix(phoneSuffix: String, states: List<String>): EngageSession?

    @Query(
        """
        SELECT * FROM engage_sessions 
        WHERE state = :state AND nextEligibleDate > 0 AND nextEligibleDate <= :now
        ORDER BY nextEligibleDate ASC
        """
    )
    suspend fun getDueScheduledSessions(state: String, now: Long): List<EngageSession>

    @Query("SELECT * FROM engage_sessions WHERE transactionId = :transactionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByTransactionId(transactionId: Int): EngageSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: EngageSession): Long

    @Update
    suspend fun update(session: EngageSession)

    @Delete
    suspend fun delete(session: EngageSession)

    @Query("DELETE FROM engage_sessions WHERE createdAt < :timestamp AND state NOT IN ('RECOMMENDATION_FAILED', 'WAITING_FOR_CHOICE', 'WAITING_FOR_OTHER_NUMBER', 'PROCESSING_OTHER_NUMBER', 'WAITING_FOR_TOPUP', 'TOPUP_RECEIVED', 'PROCESSING_TOPUP_OFFER', 'SCHEDULED_FOR_TOMORROW')")
    suspend fun deleteSessionsBefore(timestamp: Long): Int
}
