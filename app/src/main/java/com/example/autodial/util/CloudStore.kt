package com.example.autodial.util

import android.util.Log
import com.example.autodial.data.Client
import com.example.autodial.data.ClientSms
import com.example.autodial.data.EngageSession
import com.example.autodial.data.MainRepository
import com.example.autodial.data.Transaction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore is the durable store. The phone only keeps a small working set
 * (queued/processing items and live config). History is read from the cloud.
 * Disk persistence is disabled so Firestore does not fill internal storage.
 */
object CloudStore {
    private const val TAG = "CloudStore"
    private const val COL_TRANSACTIONS = "transactions"
    private const val COL_CLIENTS = "clients"
    private const val COL_CLIENT_SMS = "client_sms"
    private const val COL_SESSIONS = "bot_sessions"

    val terminalArchiveStatuses = setOf(
        Transaction.STATUS_SUCCESS,
        Transaction.STATUS_SKIPPED,
        Transaction.STATUS_CANCELLED,
        Transaction.STATUS_RELAYED,
        Transaction.STATUS_BLOCKED,
        Transaction.STATUS_IMPORTED,
        Transaction.STATUS_DISABLED
    )

    private val closableSessionStates = setOf(
        EngageSession.STATE_COMPLETED,
        EngageSession.STATE_FAILED,
        EngageSession.STATE_CANCELLED
    )

    @Volatile
    private var cacheConfigured = false

    fun disableDiskCache() {
        if (cacheConfigured) return
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
            cacheConfigured = true
            Log.i(TAG, "Firestore disk cache disabled (memory only)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply memory-only Firestore cache", e)
        }
    }

    private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()

    fun transactionDocId(deviceId: String, transaction: Transaction): String {
        val code = transaction.transactionCode.trim()
        return if (code.isNotEmpty()) "${deviceId}_T_$code" else "${deviceId}_${transaction.id}"
    }

    fun listenTransactions(deviceId: String, limit: Long = 500): Flow<List<Transaction>> = callbackFlow {
        if (deviceId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = db().collection(COL_TRANSACTIONS)
            .whereEqualTo("deviceId", deviceId)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Transaction listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toTransaction() }.orEmpty()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    suspend fun findTransactionByCode(deviceId: String, code: String): Transaction? {
        if (deviceId.isBlank() || code.isBlank()) return null
        return try {
            val snap = db().collection(COL_TRANSACTIONS)
                .whereEqualTo("deviceId", deviceId)
                .whereEqualTo("transactionCode", code)
                .limit(1)
                .get()
                .await()
            snap.documents.firstOrNull()?.toTransaction()
        } catch (e: Exception) {
            Log.w(TAG, "Cloud duplicate lookup failed for $code", e)
            null
        }
    }

    suspend fun uploadTransactionsAndPrune(
        deviceId: String,
        repository: MainRepository,
        transactions: List<Transaction>
    ) {
        if (deviceId.isBlank() || transactions.isEmpty()) return
        val uploadedIds = mutableListOf<Int>()
        transactions.chunked(400).forEach { chunk ->
            val batch = db().batch()
            chunk.forEach { txn ->
                val ref = db().collection(COL_TRANSACTIONS).document(transactionDocId(deviceId, txn))
                batch.set(ref, txn.toCloudMap(deviceId), SetOptions.merge())
            }
            batch.commit().await()
            uploadedIds += chunk.filter { it.status in terminalArchiveStatuses && it.id > 0 }.map { it.id }
        }
        if (uploadedIds.isNotEmpty()) {
            repository.deleteTransactions(uploadedIds)
            Log.i(TAG, "Archived ${uploadedIds.size} transactions to cloud and removed from device")
        }
    }

    suspend fun uploadClients(deviceId: String, clients: List<Client>) {
        if (deviceId.isBlank() || clients.isEmpty()) return
        clients.chunked(400).forEach { chunk ->
            val batch = db().batch()
            chunk.forEach { client ->
                val phoneKey = client.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(12).ifBlank { "c${client.id}" }
                val ref = db().collection(COL_CLIENTS).document("${deviceId}_$phoneKey")
                batch.set(ref, hashMapOf(
                    "deviceId" to deviceId,
                    "roomId" to client.id,
                    "name" to client.name,
                    "phoneNumber" to client.phoneNumber,
                    "dateAdded" to client.dateAdded,
                    "lastTransactionDate" to client.lastTransactionDate,
                    "totalPurchases" to client.totalPurchases,
                    "totalSpent" to client.totalSpent,
                    "outstandingDebt" to client.outstandingDebt,
                    "isBlacklisted" to client.isBlacklisted,
                    "blacklistedAt" to client.blacklistedAt,
                    "unblacklistedAt" to client.unblacklistedAt,
                    "smsHistoryCount" to client.smsHistoryCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    suspend fun uploadSmsAndPrune(
        deviceId: String,
        repository: MainRepository,
        messages: List<ClientSms>
    ) {
        val sent = messages.filter { it.status == ClientSms.STATUS_SENT || it.status == ClientSms.STATUS_FAILED }
        if (deviceId.isBlank() || sent.isEmpty()) return
        sent.chunked(400).forEach { chunk ->
            val batch = db().batch()
            chunk.forEach { sms ->
                val ref = db().collection(COL_CLIENT_SMS).document("${deviceId}_SMS_${sms.id}")
                batch.set(ref, hashMapOf(
                    "deviceId" to deviceId,
                    "roomId" to sms.id,
                    "clientId" to sms.clientId,
                    "message" to sms.message,
                    "date" to sms.date,
                    "type" to sms.type,
                    "status" to sms.status,
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
            }
            batch.commit().await()
        }
        val cutoff = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000)
        repository.deleteSmsBefore(cutoff)
    }

    suspend fun uploadSessionsAndPrune(
        deviceId: String,
        repository: MainRepository,
        sessions: List<EngageSession>
    ) {
        if (deviceId.isBlank() || sessions.isEmpty()) return
        sessions.chunked(400).forEach { chunk ->
            val batch = db().batch()
            chunk.forEach { session ->
                val ref = db().collection(COL_SESSIONS).document("${deviceId}_S_${session.id}")
                batch.set(ref, session.toCloudMap(deviceId), SetOptions.merge())
            }
            batch.commit().await()
        }
        val cutoff = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
        val toDelete = sessions.filter { session ->
            session.state in closableSessionStates &&
                session.nextEligibleDate <= System.currentTimeMillis() &&
                session.updatedAt < cutoff &&
                session.id > 0
        }
        toDelete.forEach { repository.engageSessionDao.delete(it) }
        if (toDelete.isNotEmpty()) {
            Log.i(TAG, "Archived ${toDelete.size} bot sessions to cloud and removed from device")
        }
    }

    fun mergeTransactions(local: List<Transaction>, cloud: List<Transaction>): List<Transaction> {
        val byKey = LinkedHashMap<String, Transaction>()
        (cloud + local).forEach { txn ->
            val key = txn.transactionCode.trim().ifBlank { "id-${txn.id}-${txn.date}" }
            val existing = byKey[key]
            if (existing == null || txn.date >= existing.date) {
                byKey[key] = txn
            }
        }
        return byKey.values.sortedByDescending { it.date }
    }

    private fun Transaction.toCloudMap(deviceId: String): HashMap<String, Any?> = hashMapOf(
        "deviceId" to deviceId,
        "roomId" to id,
        "date" to date,
        "amount" to amount,
        "offerName" to offerName,
        "offerId" to offerId,
        "customerNumber" to customerNumber,
        "recipientNumber" to recipientNumber,
        "senderName" to senderName,
        "network" to network,
        "status" to status,
        "transactionCode" to transactionCode,
        "simUsed" to simUsed,
        "commissionEarned" to commissionEarned,
        "safPrice" to safPrice,
        "tokenDeducted" to tokenDeducted,
        "finalResponse" to finalResponse,
        "executionTrail" to executionTrail,
        "ussdDialedCode" to ussdDialedCode,
        "originalOfferName" to originalOfferName,
        "originalOfferId" to originalOfferId,
        "switchReason" to switchReason,
        "isSwitched" to isSwitched,
        "isVerified" to isVerified,
        "isNotified" to isNotified,
        "isFallbackExecuted" to isFallbackExecuted,
        "retryCount" to retryCount,
        "usedUssdType" to usedUssdType,
        "rawMessage" to rawMessage,
        "verificationSms" to verificationSms,
        "notes" to notes,
        "processingTimeMs" to processingTimeMs,
        "processingStartedAt" to processingStartedAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun EngageSession.toCloudMap(deviceId: String): HashMap<String, Any?> = hashMapOf(
        "deviceId" to deviceId,
        "sessionId" to id,
        "customerNumber" to customerNumber,
        "customerName" to customerName,
        "state" to state,
        "recordStatus" to recordStatus,
        "originalAmount" to originalAmount,
        "topUpAmount" to topUpAmount,
        "ussdResult" to ussdResult,
        "notes" to notes,
        "originalOfferId" to originalOfferId,
        "originalOfferName" to originalOfferName,
        "transactionId" to transactionId,
        "nextEligibleDate" to nextEligibleDate,
        "createdAt" to createdAt,
        "updatedAtSession" to updatedAt,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toTransaction(): Transaction? {
        return try {
            Transaction(
                id = number("roomId")?.toInt() ?: 0,
                date = number("date")?.toLong() ?: 0L,
                amount = number("amount")?.toDouble() ?: 0.0,
                offerName = string("offerName"),
                offerId = number("offerId")?.toInt() ?: 0,
                senderName = string("senderName"),
                customerNumber = string("customerNumber"),
                recipientNumber = string("recipientNumber"),
                network = string("network"),
                simUsed = number("simUsed")?.toInt() ?: 0,
                status = string("status").ifBlank { Transaction.STATUS_SUCCESS },
                transactionCode = string("transactionCode"),
                commissionEarned = number("commissionEarned")?.toDouble() ?: 0.0,
                safPrice = number("safPrice")?.toDouble() ?: 0.0,
                processingTimeMs = number("processingTimeMs")?.toLong() ?: 0L,
                rawMessage = string("rawMessage"),
                ussdDialedCode = string("ussdDialedCode"),
                executionTrail = string("executionTrail"),
                finalResponse = string("finalResponse"),
                verificationSms = string("verificationSms"),
                tokenDeducted = boolean("tokenDeducted"),
                isVerified = boolean("isVerified"),
                isFallbackExecuted = boolean("isFallbackExecuted"),
                retryCount = number("retryCount")?.toInt() ?: 0,
                usedUssdType = string("usedUssdType").ifBlank { "MAIN" },
                isNotified = boolean("isNotified"),
                processingStartedAt = number("processingStartedAt")?.toLong() ?: 0L,
                isSwitched = boolean("isSwitched"),
                originalOfferId = number("originalOfferId")?.toInt(),
                originalOfferName = getString("originalOfferName"),
                switchReason = getString("switchReason"),
                notes = string("notes")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to map transaction ${id}", e)
            null
        }
    }

    private fun DocumentSnapshot.number(field: String): Number? = get(field) as? Number
    private fun DocumentSnapshot.string(field: String): String = getString(field) ?: ""
    private fun DocumentSnapshot.boolean(field: String): Boolean = getBoolean(field) ?: false
}
