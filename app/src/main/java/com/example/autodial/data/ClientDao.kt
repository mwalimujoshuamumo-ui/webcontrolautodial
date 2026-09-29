package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getClientByPhone(phone: String): Client?

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientById(id: Int): Client?

    @Query("SELECT * FROM clients WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%'")
    fun searchClients(query: String): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY totalPurchases DESC")
    fun getClientsByActivity(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY totalSpent DESC")
    fun getClientsBySpending(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY lastTransactionDate DESC")
    fun getClientsByRecency(): Flow<List<Client>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client): Int

    @Delete
    suspend fun deleteClient(client: Client): Int

    @Query("DELETE FROM clients WHERE id IN (:ids)")
    suspend fun deleteClients(ids: List<Int>): Int

    // Debt Records
    @Query("SELECT * FROM debt_records WHERE clientId = :clientId ORDER BY date DESC")
    fun getDebtRecordsForClient(clientId: Int): Flow<List<DebtRecord>>

    @Insert
    suspend fun insertDebtRecord(record: DebtRecord): Long

    @Query("SELECT SUM(amount) FROM debt_records WHERE clientId = :clientId AND type = 'DEBT'")
    suspend fun getTotalDebt(clientId: Int): Double?

    @Query("SELECT SUM(amount) FROM debt_records WHERE clientId = :clientId AND type = 'PAYMENT'")
    suspend fun getTotalPayments(clientId: Int): Double?

    // SMS History
    @Query("SELECT * FROM client_sms WHERE clientId = :clientId ORDER BY date DESC")
    fun getSmsHistoryForClient(clientId: Int): Flow<List<ClientSms>>

    @Insert
    suspend fun insertClientSms(sms: ClientSms): Long

    @Update
    suspend fun updateClientSms(sms: ClientSms): Int

    @Query("DELETE FROM client_sms WHERE date < :timestamp")
    suspend fun deleteSmsBefore(timestamp: Long): Int

    @Query("DELETE FROM debt_records WHERE date < :timestamp AND clientId NOT IN (SELECT id FROM clients WHERE outstandingDebt > 0)")
    suspend fun deleteSettledDebtRecordsBefore(timestamp: Long): Int

    @Query("DELETE FROM clients WHERE (lastTransactionDate < :timestamp OR (lastTransactionDate IS NULL AND dateAdded < :timestamp)) AND outstandingDebt <= 0.0")
    suspend fun deleteInactiveClientsBefore(timestamp: Long): Int

    // Stats
    @Query("SELECT COUNT(*) FROM clients")
    fun getClientCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clients WHERE isBlacklisted = 1")
    fun getBlacklistedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clients WHERE outstandingDebt > 0")
    fun getDebtorsCount(): Flow<Int>
}
