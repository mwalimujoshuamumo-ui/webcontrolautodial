package com.example.autodial.util

import android.content.Context
import android.util.Log
import com.example.autodial.data.*
import kotlinx.coroutines.flow.first

object ClientManager {

    suspend fun upsertClientFromTransaction(context: Context, transaction: Transaction): Boolean {
        val phone = transaction.customerNumber
        if (phone.isBlank() || phone == "Unknown") return false

        val database = AppDatabase.getDatabase(context)
        val clientDao = database.clientDao()
        val prefManager = PreferenceManager(context)

        val existing = clientDao.getClientByPhone(phone)
        if (existing != null) {
            val updated = existing.copy(
                name = if (existing.name.isBlank() || existing.name == "UNKNOWN") transaction.senderName else existing.name,
                lastTransactionDate = transaction.date,
                totalPurchases = existing.totalPurchases + 1,
                totalSpent = existing.totalSpent + transaction.amount
            )
            clientDao.updateClient(updated)
            
            if (prefManager.isAutosaveContactsEnabled()) {
                val prefix = prefManager.getAutosavePrefix()
                ContactsHelper.saveContact(context, updated.name, updated.phoneNumber, prefix)
            }
            
            Log.d("ClientManager", "Updated client: ${updated.phoneNumber}")
            return false
        } else {
            val newClient = Client(
                name = transaction.senderName,
                phoneNumber = phone,
                dateAdded = transaction.date,
                lastTransactionDate = transaction.date,
                totalPurchases = 1,
                totalSpent = transaction.amount
            )
            clientDao.insertClient(newClient)
            
            if (prefManager.isAutosaveContactsEnabled()) {
                val prefix = prefManager.getAutosavePrefix()
                ContactsHelper.saveContact(context, newClient.name, newClient.phoneNumber, prefix)
            }
            
            Log.d("ClientManager", "Created new client: ${newClient.phoneNumber}")
            return true
        }
    }

    suspend fun checkEligibility(context: Context, transaction: Transaction): Pair<Boolean, String?> {
        val phone = transaction.customerNumber
        if (phone.isBlank() || phone == "Unknown") return Pair(true, null)

        val database = AppDatabase.getDatabase(context)
        val clientDao = database.clientDao()

        val client = clientDao.getClientByPhone(phone) ?: return Pair(true, null)

        if (client.isBlacklisted) {
            return Pair(false, "BLACKLISTED")
        }

        if (client.outstandingDebt > 0) {
            return Pair(false, "DEBT")
        }

        return Pair(true, null)
    }

    suspend fun recordDebt(context: Context, clientId: Int, amount: Double, notes: String = "") {
        val database = AppDatabase.getDatabase(context)
        val clientDao = database.clientDao()

        val record = DebtRecord(
            clientId = clientId,
            amount = amount,
            type = DebtRecord.TYPE_DEBT,
            notes = notes
        )
        clientDao.insertDebtRecord(record)
        updateClientBalance(context, clientId)
        
        val client = clientDao.getClientById(clientId)
        if (client != null) {
            sendDebtNotification(context, client, amount, "DEBT")
        }
    }

    suspend fun recordPayment(context: Context, clientId: Int, amount: Double, notes: String = "", transactionId: Int = -1) {
        val database = AppDatabase.getDatabase(context)
        val clientDao = database.clientDao()
        val repository = database.createRepository()

        val record = DebtRecord(
            clientId = clientId,
            amount = amount,
            type = DebtRecord.TYPE_PAYMENT,
            notes = notes
        )
        clientDao.insertDebtRecord(record)
        updateClientBalance(context, clientId)

        val client = clientDao.getClientById(clientId)
        if (client != null) {
            sendDebtNotification(context, client, amount, "PAYMENT")
            
            // LINK TO TRANSACTION: If this payment came from a specific transaction
            if (transactionId != -1) {
                repository.getTransactionById(transactionId)?.let { trans ->
                    repository.updateTransaction(trans.copy(
                        status = Transaction.STATUS_SUCCESS,
                        offerName = "Debt Payment",
                        finalResponse = "Payment applied to debt. Remaining: KSh ${client.outstandingDebt.toInt()}"
                    ))
                }
            }

            // AUTO-UNBLOCK: If debt is cleared, unblock pending transactions
            if (client.outstandingDebt <= 0) {
                val blockedTransactions = repository.allTransactions.first().filter { 
                    it.customerNumber == client.phoneNumber && it.status == Transaction.STATUS_BLOCKED 
                }
                
                blockedTransactions.forEach { trans ->
                    repository.updateTransaction(trans.copy(
                        status = Transaction.STATUS_WAITING,
                        finalResponse = "Unblocked: Debt Cleared"
                    ))
                }
                
                if (blockedTransactions.isNotEmpty()) {
                    TransactionQueueManager.processNext(context)
                }
            }
        }
    }

    private suspend fun updateClientBalance(context: Context, clientId: Int) {
        val database = AppDatabase.getDatabase(context)
        val clientDao = database.clientDao()

        val totalDebt = clientDao.getTotalDebt(clientId) ?: 0.0
        val totalPayments = clientDao.getTotalPayments(clientId) ?: 0.0
        val balance = totalDebt - totalPayments

        val client = clientDao.getClientById(clientId)
        if (client != null) {
            clientDao.updateClient(client.copy(outstandingDebt = if (balance < 0) 0.0 else balance))
        }
    }

    private suspend fun sendDebtNotification(context: Context, client: Client, amount: Double, type: String) {
        val message = when (type) {
            "DEBT" -> "Dear ${client.name.split(" ")[0]}, your current outstanding balance is KSh ${client.outstandingDebt.toInt()}. Please clear your balance before making another purchase. Thank you."
            "PAYMENT" -> {
                if (client.outstandingDebt <= 0) {
                    "Dear ${client.name.split(" ")[0]}, your outstanding balance has been fully cleared. You may now make purchases normally. Thank you."
                } else {
                    "Dear ${client.name.split(" ")[0]}, we have received your payment of KSh ${amount.toInt()}. Your remaining outstanding balance is KSh ${client.outstandingDebt.toInt()}."
                }
            }
            else -> ""
        }

        if (message.isNotBlank()) {
            SmsSender.sendEngageMessage(context, client.phoneNumber, message)
            
            // Record SMS in history
            val database = AppDatabase.getDatabase(context)
            database.clientDao().insertClientSms(ClientSms(
                clientId = client.id,
                message = message,
                type = ClientSms.TYPE_DEBT,
                status = ClientSms.STATUS_SENT
            ))
        }
    }

    suspend fun sendBroadcastSms(context: Context, clientIds: List<Int>, message: String, notes: String = "") {
        val database = AppDatabase.getDatabase(context)
        val clientDao = database.clientDao()

        val fullMessage = if (notes.isNotBlank()) "$message\n[Note: $notes]" else message

        clientIds.forEach { id ->
            val client = clientDao.getClientById(id)
            if (client != null) {
                try {
                    SmsSender.sendEngageMessage(context, client.phoneNumber, fullMessage)
                    clientDao.insertClientSms(ClientSms(
                        clientId = client.id,
                        message = fullMessage,
                        type = ClientSms.TYPE_BROADCAST,
                        status = ClientSms.STATUS_SENT
                    ))
                } catch (e: Exception) {
                    clientDao.insertClientSms(ClientSms(
                        clientId = client.id,
                        message = fullMessage,
                        type = ClientSms.TYPE_BROADCAST,
                        status = ClientSms.STATUS_FAILED
                    ))
                }
            }
        }
    }
}
