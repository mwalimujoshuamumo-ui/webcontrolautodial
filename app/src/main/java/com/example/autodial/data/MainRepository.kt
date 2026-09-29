package com.example.autodial.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class MainRepository(
    private val offerDao: OfferDao,
    private val transactionDao: TransactionDao,
    private val scheduledTaskDao: ScheduledTaskDao,
    private val notificationTemplateDao: NotificationTemplateDao,
    val contactDao: ContactDao,
    val relayConfigDao: RelayConfigDao,
    val ussdMapDao: UssdMapDao,
    val relayDeviceDao: RelayDeviceDao,
    val engageSessionDao: EngageSessionDao,
    val topUpPlanDao: TopUpPlanDao,
    val clientDao: ClientDao
) {

    val allOffers: Flow<List<Offer>> = offerDao.getAllOffers()
    val activeOffers: Flow<List<Offer>> = offerDao.getActiveOffers()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val recentTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allScheduledTasks: Flow<List<ScheduledTask>> = scheduledTaskDao.getAllScheduledTasks()
    val allTemplates: Flow<List<NotificationTemplate>> = notificationTemplateDao.getAllTemplates()
    val allLinkedDevices: Flow<List<RelayDevice>> = relayDeviceDao.getAllDevicesFlow()
    val allEngageSessions: Flow<List<EngageSession>> = engageSessionDao.getAllSessions()
    val allTopUpPlans: Flow<List<TopUpPlan>> = topUpPlanDao.getAllPlans()
    val allClients: Flow<List<Client>> = clientDao.getAllClients()

    fun getTransactionsToday(): Flow<List<Transaction>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return transactionDao.getTransactionsToday(calendar.timeInMillis)
    }

    fun getTransactionsForDashboard(): Flow<List<Transaction>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return transactionDao.getTransactionsForDashboard(calendar.timeInMillis)
    }

    fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsInRange(start, end)
    }

    suspend fun deleteTransactions(ids: List<Int>) {
        if (ids.isNotEmpty()) {
            transactionDao.deleteTransactionsByIds(ids)
        }
    }

    suspend fun updateTransactionsStatus(ids: List<Int>, status: String): Int {
        return if (ids.isNotEmpty()) {
            transactionDao.updateTransactionsStatus(ids, status)
        } else 0
    }

    suspend fun deleteTransactionsBefore(timestamp: Long): Int {
        return transactionDao.deleteTransactionsBefore(timestamp)
    }

    suspend fun pruneTransactionLogsBefore(timestamp: Long): Int {
        return transactionDao.pruneTransactionLogsBefore(timestamp)
    }

    suspend fun deleteSessionsBefore(timestamp: Long): Int {
        return engageSessionDao.deleteSessionsBefore(timestamp)
    }

    suspend fun deleteUnmatchedTransactionsBefore(timestamp: Long): Int {
        return transactionDao.deleteUnmatchedTransactionsBefore(timestamp)
    }

    suspend fun deleteSmsBefore(timestamp: Long): Int {
        return clientDao.deleteSmsBefore(timestamp)
    }

    suspend fun deleteSettledDebtRecordsBefore(timestamp: Long): Int {
        return clientDao.deleteSettledDebtRecordsBefore(timestamp)
    }

    suspend fun deleteInactiveClientsBefore(timestamp: Long): Int {
        return clientDao.deleteInactiveClientsBefore(timestamp)
    }

    suspend fun findMatchingOffer(amount: Double): Offer? {
        return offerDao.findMatchingOffer(amount)
    }

    suspend fun findMatchingOfferIgnoringStatus(amount: Double): Offer? {
        return offerDao.findMatchingOfferIgnoringStatus(amount)
    }

    suspend fun insertOffer(offer: Offer): Long {
        return offerDao.insertOffer(offer)
    }

    suspend fun updateOffer(offer: Offer) {
        offerDao.updateOffer(offer)
    }

    suspend fun deleteOffer(offer: Offer) {
        offerDao.deleteOffer(offer)
    }

    suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun getTransactionByCode(code: String): Transaction? {
        return transactionDao.getTransactionByCode(code)
    }

    suspend fun getTransactionById(id: Int): Transaction? {
        return transactionDao.getTransactionById(id)
    }

    suspend fun findRecentTransactionByPhone(phone: String): Transaction? {
        return transactionDao.findRecentTransactionByPhone(phone)
    }

    suspend fun getProcessingTransactions(): List<Transaction> {
        return transactionDao.getProcessingTransactions()
    }

    suspend fun getWaitingTransactions(): List<Transaction> {
        return transactionDao.getWaitingTransactions()
    }

    suspend fun insertScheduledTask(task: ScheduledTask): Long {
        return scheduledTaskDao.insertTask(task)
    }

    suspend fun updateScheduledTask(task: ScheduledTask) {
        scheduledTaskDao.updateTask(task)
    }

    suspend fun deleteScheduledTask(task: ScheduledTask) {
        scheduledTaskDao.deleteTask(task)
    }

    // Notification Templates
    suspend fun insertTemplate(template: NotificationTemplate) {
        notificationTemplateDao.insertTemplate(template)
    }

    suspend fun updateTemplate(template: NotificationTemplate) {
        notificationTemplateDao.updateTemplate(template)
    }

    suspend fun deleteTemplate(template: NotificationTemplate) {
        notificationTemplateDao.deleteTemplate(template)
    }

    suspend fun getTemplateByCategory(category: String): NotificationTemplate? {
        return notificationTemplateDao.getTemplateByCategory(category)
    }
}
