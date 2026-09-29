package com.example.autodial.util

import android.content.Context
import android.util.Log
import com.example.autodial.data.*
import com.example.autodial.service.UssdAccessibilityService
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Full State-Machine Engage Bot Implementation.
 * Manages customer conversations and cumulative payments when normal flow is blocked.
 */
object EngageBotManager {

    private const val TAG = "EngageBot"
    const val SENDER_PREFIX = "ENGAGE BOT"
    const val SESSION_MARKER = "ENGAGE_SESSION:"

    /**
     * Triggered when a normal recommendation fails with "Already Recommended Today".
     */
    suspend fun tryStartFromRecommendationFailure(
        context: Context,
        transaction: Transaction,
        response: String?
    ): Boolean {
        val prefs = PreferenceManager(context)
        if (!prefs.isEngageBotEnabled()) return false
        if (transaction.status == Transaction.STATUS_SUCCESS || transaction.isVerified) return false
        if (!EngageBotTrigger.isAlreadyRecommendedToday(response)) return false

        val phone = normalizePhone(transaction.customerNumber.ifEmpty { transaction.recipientNumber })
        if (phone.isBlank()) return false

        val repository = AppDatabase.getDatabase(context).createRepository()

        // Skip Engage Bot session if the offer has an active fallback offer configured
        val allOffers = repository.allOffers.first()
        val currentOffer = allOffers.find { it.id == transaction.offerId }
        val originalOffer = transaction.originalOfferId?.let { origId -> allOffers.find { it.id == origId } }
        val hasFallbackConfigured = (currentOffer?.isFallbackEnabled == true && currentOffer.fallbackOfferId != null) ||
                                    (originalOffer?.isFallbackEnabled == true && originalOffer.fallbackOfferId != null)
        
        if (hasFallbackConfigured) {
            Log.d(TAG, "Transaction ${transaction.id} has a fallback offer configured. Bypassing Engage Bot session to allow immediate fallback execution.")
            return false
        }
        
        // 1. Prevent multiple prompts for the same transaction
        val existingForTxn = repository.engageSessionDao.findByTransactionId(transaction.id)
        if (existingForTxn != null) {
            Log.d(TAG, "Engage session already exists for transaction ${transaction.id}. Skipping duplicate prompt.")
            return true
        }

        // 2. Use existing session if active, else create new
        var session = findActiveSession(repository, phone)
        if (session == null) {
            session = EngageSession(
                customerNumber = phone,
                customerName = transaction.senderName,
                originalOfferId = transaction.offerId,
                originalOfferName = transaction.offerName.ifBlank { "Bingwa Offer" },
                originalAmount = transaction.amount,
                transactionId = transaction.id,
                state = EngageSession.STATE_WAITING_FOR_CHOICE,
                recordStatus = EngageSession.STATUS_WAITING_FOR_CUSTOMER,
                notes = "[BOT:START] Rec Fail"
            )
            val id = repository.engageSessionDao.insert(session)
            session = session.copy(id = id.toInt())
        } else {
            session = session.copy(
                customerName = if (session.customerName.isBlank()) transaction.senderName else session.customerName,
                state = EngageSession.STATE_WAITING_FOR_CHOICE,
                recordStatus = EngageSession.STATUS_WAITING_FOR_CUSTOMER,
                updatedAt = System.currentTimeMillis(),
                transactionId = transaction.id,
                notes = session.notes + " | [BOT:REACTIVATE]"
            )
            repository.engageSessionDao.update(session)
        }

        sendChoicePrompt(context, session)
        return true
    }

    /**
     * Handles customer SMS replies based on current state.
     */
    suspend fun handleIncomingSms(context: Context, sender: String, body: String): Boolean {
        val phone = normalizePhone(sender)
        val repository = AppDatabase.getDatabase(context).createRepository()
        val session = findActiveSession(repository, phone) ?: return false

        // Auto-close if the original transaction is already finalized
        val transaction = repository.getTransactionById(session.transactionId)
        if (transaction != null && isTransactionDone(transaction.status)) {
            Log.d(TAG, "Transaction ${transaction.id} already in terminal state ${transaction.status}. Closing session ${session.id}.")
            repository.engageSessionDao.update(session.copy(
                state = EngageSession.STATE_COMPLETED,
                recordStatus = EngageSession.STATUS_COMPLETED,
                notes = session.notes + " | [BOT:AUTO_CLOSE] Txn Done"
            ))
            return false
        }

        val trimmed = body.trim()
        Log.d(TAG, "SMS from $phone in state ${session.state}: $trimmed")

        return when (session.state) {
            EngageSession.STATE_WAITING_FOR_CHOICE -> handleChoiceSelection(context, repository, session, trimmed)
            EngageSession.STATE_WAITING_FOR_OTHER_NUMBER -> handleOtherNumberInput(context, repository, session, trimmed)
            EngageSession.STATE_SCHEDULED_FOR_TOMORROW -> {
                // If they are in scheduled state, they might be trying to set the time
                handleTimeInput(context, repository, session, trimmed)
            }
            EngageSession.STATE_WAITING_FOR_TOPUP -> handleTopUpSelection(context, repository, session, trimmed)
            else -> false
        }
    }

    /**
     * Intercepts M-Pesa payments for top-up logic.
     * Supports both direct completion and cumulative addition.
     */
    suspend fun handleTopUpPayment(
        context: Context,
        customerNumber: String,
        amount: Double,
        transactionCode: String,
        rawMessage: String,
        senderName: String
    ): Boolean {
        val phone = normalizePhone(customerNumber)
        val repository = AppDatabase.getDatabase(context).createRepository()
        
        // Find any active session for this customer
        var session = findActiveSession(repository, phone) ?: return false

        // Auto-close if the original transaction is already finalized
        val transaction = repository.getTransactionById(session.transactionId)
        if (transaction != null && isTransactionDone(transaction.status)) {
            Log.d(TAG, "Transaction ${transaction.id} already in terminal state ${transaction.status}. Closing session ${session.id}.")
            repository.engageSessionDao.update(session.copy(
                state = EngageSession.STATE_COMPLETED,
                recordStatus = EngageSession.STATUS_COMPLETED,
                notes = session.notes + " | [BOT:AUTO_CLOSE] Txn Done"
            ))
            return false
        }

        // NAME VERIFICATION: Ensure the payment name matches the session customer name
        if (!isNameMatch(session.customerName, senderName)) {
            Log.w(TAG, "Top-up name mismatch: Session=${session.customerName}, Payment=$senderName. Blocking top-up.")
            return false
        }

        // Cumulative Calculation: Current + New
        val newCumulative = session.topUpAmount + amount
        val totalPaid = session.originalAmount + newCumulative
        
        Log.i(TAG, "Top-up for $phone: Original=${session.originalAmount}, PrevTopUp=${session.topUpAmount}, New=$amount. Total=$totalPaid")

        val allOffers = repository.allOffers.first().associateBy { it.id }
        val plans = repository.topUpPlanDao.getEnabledPlansOnce()
            .filter { allOffers[it.offerId]?.isEnabled == true }
            .sortedBy { it.requiredAmount }
        
        // Find the HIGHEST enabled plan that totalPaid satisfies (comparing against plan.requiredAmount / agent price)
        val matchedPlan = plans.lastOrNull { 
            val offer = allOffers[it.offerId]
            offer != null && totalPaid >= it.requiredAmount 
        }

        if (matchedPlan != null) {
            val offer = allOffers[matchedPlan.offerId]!!
            val overpayment = totalPaid - matchedPlan.requiredAmount
            val overpaymentNote = if (overpayment > 0.0) {
                "\n[OVERPAYMENT] Excess KSh ${formatAmount(overpayment)} to be reversed by agent."
            } else ""
            
            processTopUpSuccess(context, repository, session, matchedPlan, offer, amount, transactionCode, rawMessage, overpaymentNote)
            return true
        }

        // No plan fully covered: accumulate cumulative and keep waiting indefinitely (no session expiration or standalone bypass)
        val updatedSession = session.copy(
            topUpAmount = newCumulative,
            state = EngageSession.STATE_WAITING_FOR_TOPUP,
            recordStatus = EngageSession.STATUS_WAITING_FOR_TOPUP,
            updatedAt = System.currentTimeMillis(),
            notes = session.notes + " | [BOT:PAY] +$amount (Total $newCumulative)"
        )
        repository.engageSessionDao.update(updatedSession)

        // Find the next higher plan above totalPaid to give a precise target reminder
        val nextPlan = plans.firstOrNull { 
            val offer = allOffers[it.offerId]
            offer != null && it.requiredAmount > totalPaid 
        } ?: plans.firstOrNull()
        
        val remainingMsg = if (nextPlan != null) {
            val offer = allOffers[nextPlan.offerId]
            if (offer != null) {
                val rem = (nextPlan.requiredAmount - totalPaid).coerceAtLeast(0.0)
                "\nPlease send remaining KSh ${formatAmount(rem)} to reach total expected KSh ${formatAmount(nextPlan.requiredAmount)} for ${offer.name}."
            } else {
                "\nPlease send the remaining amount to complete your offer."
            }
        } else {
            "\nPlease send the remaining amount to qualify for an alternative offer."
        }

        sendMessage(context, phone, 
            "✅ Additional payment of KSh ${formatAmount(amount)} received.\n" +
            "Your total payment is now KSh ${formatAmount(totalPaid)}.$remainingMsg"
        )
        
        return true
    }

    private fun isNameMatch(expected: String, actual: String): Boolean {
        if (expected.isBlank() || expected.equals("UNKNOWN (MASKED)", true)) return true
        val expParts = expected.uppercase().split(Regex("\\s+")).filter { it.length > 2 }
        val actUpper = actual.uppercase()
        
        // If any part of the expected name is in the actual name, we consider it a match
        // M-Pesa names are often truncated or rearranged
        for (part in expParts) {
            if (actUpper.contains(part)) return true
        }
        return false
    }

    /**
     * Sessions never expire; they remain in waiting/failed state indefinitely until top-up plan matches.
     */
    fun cleanupExpiredSessions(context: Context) {
        // No-op: Sessions remain active waiting for cumulative top-ups indefinitely.
    }

    private suspend fun processTopUpSuccess(
        context: Context,
        repository: MainRepository,
        session: EngageSession,
        plan: TopUpPlan,
        offer: Offer,
        lastAmount: Double,
        code: String,
        raw: String,
        overpaymentNote: String
    ) {
        val totalPaid = session.originalAmount + session.topUpAmount + lastAmount
        
        val overpaymentSms = if (overpaymentNote.isNotEmpty()) {
            "\n(Note: Excess payment will be reversed by the agent.)"
        } else ""

        sendMessage(context, session.customerNumber,
            "✅ Additional payment of KSh ${formatAmount(lastAmount)} received.\n" +
            "Your total payment is now KSh ${formatAmount(totalPaid)}.\n" +
            "Your ${offer.name} alternative offer has been identified.\n" +
            "⚙️ Processing your offer now. Please wait...$overpaymentSms"
        )

        val updated = session.copy(
            state = EngageSession.STATE_PROCESSING_TOPUP_OFFER,
            recordStatus = EngageSession.STATUS_PROCESSING,
            topUpAmount = session.topUpAmount + lastAmount,
            topUpPlanId = plan.id,
            selectedOfferId = offer.id,
            updatedAt = System.currentTimeMillis(),
            notes = session.notes + " | [BOT:SUCCESS_TOPUP]" + overpaymentNote
        )
        repository.engageSessionDao.update(updated)

        val originalTxn = repository.getTransactionById(session.transactionId)
        val dropNotes = "\n[BOT:TOPUP_OK] Code: $code | Paid Total KSh $totalPaid (Required Agent Price: KSh ${plan.requiredAmount})" + overpaymentNote
        if (originalTxn != null) {
            repository.updateTransaction(originalTxn.copy(
                amount = offer.amount,
                offerName = offer.name,
                offerId = offer.id,
                senderName = formatBotName(originalTxn.senderName),
                status = Transaction.STATUS_WAITING,
                safPrice = offer.safPrice,
                notes = originalTxn.notes + dropNotes,
                rawMessage = originalTxn.rawMessage + "\n[BOT_TOPUP] $code | $lastAmount"
            ))
        } else {
            val txnId = repository.insertTransaction(
                Transaction(
                    amount = offer.amount,
                    offerName = offer.name,
                    offerId = offer.id,
                    senderName = formatBotName(session.customerName),
                    customerNumber = session.customerNumber,
                    status = Transaction.STATUS_WAITING,
                    transactionCode = code,
                    rawMessage = "$SESSION_MARKER${updated.id}|TOPUP|$raw",
                    safPrice = offer.safPrice,
                    notes = "[BOT:TOPUP_NEW] Session ${updated.id}" + dropNotes
                )
            )
            repository.engageSessionDao.update(updated.copy(transactionId = txnId.toInt()))
        }
        TransactionQueueManager.processNext(context)
    }

    /**
     * Handle results of USSD executions triggered by the bot.
     */
    suspend fun onOfferExecutionResult(
        context: Context,
        transaction: Transaction,
        success: Boolean,
        response: String?
    ): Boolean {
        if (!transaction.rawMessage.contains(SESSION_MARKER)) return false

        val repository = AppDatabase.getDatabase(context).createRepository()
        val sessionId = parseSessionId(transaction.rawMessage) ?: return false
        val session = repository.engageSessionDao.getById(sessionId) ?: return false

        if (success) {
            sendMessage(context, session.customerNumber, "🎉 Offer activated successfully!\nYour ${transaction.offerName} has been activated.\nThank you for using our service.")
            repository.engageSessionDao.update(session.copy(
                state = EngageSession.STATE_COMPLETED,
                recordStatus = EngageSession.STATUS_COMPLETED,
                ussdResult = response.orEmpty(),
                updatedAt = System.currentTimeMillis(),
                notes = session.notes + " | [BOT:SUCCESS]"
            ))
            repository.updateTransaction(transaction.copy(
                notes = transaction.notes + "\n[BOT:COMPLETE] SUCCESS"
            ))
        } else {
            sendMessage(context, session.customerNumber, "❌ Offer activation failed.\nWe received your payment, but we were unable to activate the offer.\nPlease try again or contact support.")
            repository.engageSessionDao.update(session.copy(
                state = EngageSession.STATE_FAILED,
                recordStatus = EngageSession.STATUS_FAILED,
                ussdResult = response.orEmpty(),
                updatedAt = System.currentTimeMillis(),
                notes = session.notes + " | [BOT:FAILED]"
            ))
            repository.updateTransaction(transaction.copy(
                notes = transaction.notes + "\n[BOT:COMPLETE] FAILED"
            ))
        }
        return true
    }

    private suspend fun handleChoiceSelection(context: Context, repository: MainRepository, session: EngageSession, input: String): Boolean {
        val updatedSession = session.copy(
            notes = session.notes + "\n[BOT:CHOICE] $input",
            updatedAt = System.currentTimeMillis()
        )
        repository.engageSessionDao.update(updatedSession)

        return when (input) {
            "1" -> {
                repository.engageSessionDao.update(updatedSession.copy(
                    state = EngageSession.STATE_WAITING_FOR_OTHER_NUMBER,
                    recordStatus = EngageSession.STATUS_WAITING_FOR_CUSTOMER
                ))
                sendMessage(context, session.customerNumber, "📱 Please enter the phone number you would like us to recommend.")
                true
            }
            "2" -> {
                repository.engageSessionDao.update(updatedSession.copy(
                    state = EngageSession.STATE_SCHEDULED_FOR_TOMORROW,
                    recordStatus = EngageSession.STATUS_WAITING_FOR_CUSTOMER
                ))
                sendMessage(context, session.customerNumber, "📅 You selected Recommend tomorrow.\nWhat time would you like the recommendation processed?\nExample: 12:00 AM")
                true
            }
            "3" -> {
                val allOffers = repository.allOffers.first().associateBy { it.id }
                val plans = repository.topUpPlanDao.getEnabledPlansOnce()
                    .filter { allOffers[it.offerId]?.isEnabled == true }
                    .sortedBy { allOffers[it.offerId]?.amount ?: 0.0 }
                
                if (plans.isEmpty()) {
                    sendMessage(context, session.customerNumber, "💰 No Top Up Plans available at the moment.")
                    return true
                }
                repository.engageSessionDao.update(updatedSession.copy(
                    state = EngageSession.STATE_WAITING_FOR_TOPUP,
                    recordStatus = EngageSession.STATUS_WAITING_FOR_TOPUP
                ))
                sendTopUpPlansPrompt(context, repository, plans, updatedSession)
                true
            }
            else -> {
                sendMessage(context, session.customerNumber, "⚠️ Invalid option.\nPlease reply with:\n1️⃣ Recommend another number\n2️⃣ Recommend tomorrow\n3️⃣ Top up for an alternative offer")
                true
            }
        }
    }

    private suspend fun handleOtherNumberInput(context: Context, repository: MainRepository, session: EngageSession, input: String): Boolean {
        val phone = input.filter { it.isDigit() }
        if (phone.length < 9) {
            sendMessage(context, session.customerNumber, "⚠️ Invalid phone number.\nPlease enter a valid customer number, for example: 0712345678")
            return true
        }

        val formatted = UssdSessionManager.formatPhone(phone)
        sendMessage(context, session.customerNumber, "✅ Number $formatted received.\nWe will use this number for the recommendation.")

        val offer = repository.allOffers.first().find { it.id == session.originalOfferId } ?: return true
        
        val updated = session.copy(
            otherNumber = formatted,
            state = EngageSession.STATE_PROCESSING_OTHER_NUMBER,
            recordStatus = EngageSession.STATUS_PROCESSING,
            notes = session.notes + "\n[BOT:ALT_NUM] $formatted",
            updatedAt = System.currentTimeMillis()
        )
        repository.engageSessionDao.update(updated)

        val originalTxn = repository.getTransactionById(session.transactionId)
        if (originalTxn != null) {
            repository.updateTransaction(originalTxn.copy(
                customerNumber = formatted,
                senderName = formatBotName(originalTxn.senderName),
                status = Transaction.STATUS_WAITING,
                notes = originalTxn.notes + "\n[BOT:ALT_NUM] $formatted. Retrying...",
                rawMessage = originalTxn.rawMessage + "\n[BOT_ALT_NUM] $formatted"
            ))
        } else {
            val txnId = repository.insertTransaction(
                Transaction(
                    amount = offer.amount,
                    offerName = offer.name,
                    offerId = offer.id,
                    senderName = formatBotName(session.customerName),
                    customerNumber = formatted,
                    status = Transaction.STATUS_WAITING,
                    simUsed = offer.simSelection,
                    transactionCode = "ENGAGE-OTHER-${session.id}",
                    rawMessage = "$SESSION_MARKER${updated.id}|OTHER|$formatted",
                    notes = "[BOT:ALT_NUM] Session ${updated.id}"
                )
            )
            repository.engageSessionDao.update(updated.copy(transactionId = txnId.toInt()))
        }
        TransactionQueueManager.processNext(context)
        return true
    }

    private suspend fun handleTimeInput(context: Context, repository: MainRepository, session: EngageSession, input: String): Boolean {
        val time = parseTime(input)
        if (time == null) {
            sendMessage(context, session.customerNumber, "⚠️ Invalid time.\nPlease enter a valid time, for example:\n12:00 AM\n10:30 AM\n8:00 PM")
            return true
        }

        val nextDay = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val scheduledTask = ScheduledTask(
            offerId = session.originalOfferId,
            offerName = session.originalOfferName,
            targetNumber = session.customerNumber,
            scheduledTime = nextDay,
            notes = "[BOT:SCHED] Session ${session.id}"
        )
        repository.insertScheduledTask(scheduledTask)

        repository.engageSessionDao.update(session.copy(
            state = EngageSession.STATE_SCHEDULED_FOR_TOMORROW,
            recordStatus = EngageSession.STATUS_SCHEDULED,
            nextEligibleDate = nextDay,
            updatedAt = System.currentTimeMillis(),
            notes = session.notes + "\n[BOT:SCHED] $input"
        ))

        sendMessage(context, session.customerNumber, "✅ Recommendation scheduled successfully.\n📅 Date: Tomorrow\n⏰ Time: ${input.uppercase()}\n\nThe recommendation will be processed automatically.")
        return true
    }

    private suspend fun handleTopUpSelection(context: Context, repository: MainRepository, session: EngageSession, input: String): Boolean {
        val allOffers = repository.allOffers.first().associateBy { it.id }
        val plans = repository.topUpPlanDao.getEnabledPlansOnce()
            .filter { allOffers[it.offerId]?.isEnabled == true }
            .sortedBy { it.requiredAmount }
        val index = input.toIntOrNull()?.minus(1)
        
        if (index == null || index !in plans.indices) {
            sendTopUpPlansPrompt(context, repository, plans, session)
            return true
        }

        val plan = plans[index]
        val offer = allOffers[plan.offerId] ?: return true
        
        val remaining = (plan.requiredAmount - (session.originalAmount + session.topUpAmount)).coerceAtLeast(0.0)
        
        sendMessage(context, session.customerNumber, 
            "✅ You selected the ${offer.name} upgrade.\n" +
            "Total Expected: ${offer.name} at KSh ${formatAmount(plan.requiredAmount)}\n" +
            "Previously Paid: KSh ${formatAmount(session.originalAmount + session.topUpAmount)}\n" +
            "👉 Please top up the remaining KSh ${formatAmount(remaining)} to activate.\n" +
            "You can send it now via M-PESA. No need to reply."
        )
        
        repository.engageSessionDao.update(session.copy(
            topUpPlanId = plan.id,
            updatedAt = System.currentTimeMillis()
        ))
        
        return true
    }

    private fun sendChoicePrompt(context: Context, session: EngageSession) {
        sendMessage(context, session.customerNumber, 
            "✅ We have received your KSh ${formatAmount(session.originalAmount)} payment.\n" +
            "Your number has already been recommended today.\n" +
            "Please choose an option:\n" +
            "1️⃣ Recommend another number\n" +
            "2️⃣ Recommend tomorrow\n" +
            "3️⃣ Top up for an alternative offer\n\n" +
            "Reply with 1, 2 or 3."
        )
    }

    private suspend fun sendTopUpPlansPrompt(context: Context, repository: MainRepository, plans: List<TopUpPlan>, session: EngageSession) {
        val allOffers = repository.allOffers.first().associateBy { it.id }
        val currentPaid = session.originalAmount + session.topUpAmount
        
        val prompt = buildString {
            append("💰 You can top up to get an alternative offer.\n")
            if (currentPaid > 0) {
                append("Already paid: KSh ${formatAmount(currentPaid)}\n\n")
            }
            append("Available options:\n")
            plans.forEachIndexed { i, plan ->
                val offer = allOffers[plan.offerId]
                if (offer != null) {
                    val diff = plan.requiredAmount - currentPaid
                    if (diff > 0) {
                        append("${i + 1}️⃣ KSh ${formatAmount(diff)} more for ${offer.name} (Total Expected: ${offer.name} at KSh ${formatAmount(plan.requiredAmount)})\n")
                    } else if (abs(diff) < 0.1) {
                        append("${i + 1}️⃣ Ready! Total Expected: ${offer.name} at KSh ${formatAmount(plan.requiredAmount)}\n")
                    }
                }
            }
            append("\nReply with 1, 2, etc., or simply send the top-up amount directly.")
        }
        sendMessage(context, session.customerNumber, prompt)
    }

    private suspend fun findActiveSession(repository: MainRepository, phone: String): EngageSession? {
        val suffix = if (phone.length >= 9) phone.takeLast(9) else phone
        return repository.engageSessionDao.findActiveByPhoneSuffix(suffix, EngageSession.ACTIVE_STATES.toList())
    }

    private fun sendMessage(context: Context, phone: String, body: String) {
        SmsSender.sendEngageMessage(context, phone, body)
    }

    fun normalizePhone(number: String): String {
        return UssdSessionManager.formatPhone(number.filter { it.isDigit() || it == '+' })
    }

    private fun formatBotName(originalName: String): String {
        val base = if (originalName.contains(" - ")) originalName.substringAfter(" - ") else originalName
        val first = base.trim().split(" ").firstOrNull()?.lowercase() ?: "customer"
        return "bot - $first"
    }

    private fun formatAmount(amount: Double): String = if (amount == amount.toLong().toDouble()) amount.toLong().toString() else String.format(Locale.US, "%.2f", amount)

    private fun parseTime(input: String): Pair<Int, Int>? {
        try {
            val upper = input.uppercase().trim()
            val format = SimpleDateFormat("h:mm a", Locale.US)
            val date = format.parse(upper) ?: return null
            val cal = Calendar.getInstance().apply { time = date }
            return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseSessionId(raw: String): Int? {
        if (!raw.contains(SESSION_MARKER)) return null
        val after = raw.substringAfter(SESSION_MARKER)
        val idPart = after.takeWhile { it.isDigit() }
        return idPart.toIntOrNull()
    }

    private fun isTransactionDone(status: String): Boolean {
        return status == Transaction.STATUS_SUCCESS || 
               status == Transaction.STATUS_RELAYED || 
               status == Transaction.STATUS_CANCELLED || 
               status == Transaction.STATUS_SKIPPED ||
               status == Transaction.STATUS_IMPORTED
    }

    suspend fun processDueScheduled(context: Context) {
        val repository = AppDatabase.getDatabase(context).createRepository()
        val due = repository.engageSessionDao.getDueScheduledSessions(EngageSession.STATE_SCHEDULED_FOR_TOMORROW, System.currentTimeMillis())
        for (session in due) {
            val offer = repository.allOffers.first().find { it.id == session.originalOfferId } ?: continue
            
            val originalTxn = repository.getTransactionById(session.transactionId)
            if (originalTxn != null) {
                repository.updateTransaction(originalTxn.copy(
                    status = Transaction.STATUS_WAITING,
                    senderName = formatBotName(originalTxn.senderName),
                    notes = originalTxn.notes + "\n[BOT:SCHED_RUN]",
                    date = System.currentTimeMillis() // Move to top of today's queue
                ))
                repository.engageSessionDao.update(session.copy(
                    state = EngageSession.STATE_PROCESSING_OTHER_NUMBER,
                    recordStatus = EngageSession.STATUS_PROCESSING,
                    notes = session.notes + " | [BOT:SCHED_RUN]"
                ))
            } else {
                val txnId = repository.insertTransaction(Transaction(
                    amount = offer.amount,
                    offerName = offer.name,
                    offerId = offer.id,
                    senderName = formatBotName(session.customerName),
                    customerNumber = session.customerNumber,
                    status = Transaction.STATUS_WAITING,
                    simUsed = offer.simSelection,
                    transactionCode = "ENGAGE-SCHED-${session.id}",
                    rawMessage = "$SESSION_MARKER${session.id}|SCHED",
                    notes = "[BOT:SCHED_RUN] Session ${session.id}"
                ))
                repository.engageSessionDao.update(session.copy(
                    state = EngageSession.STATE_PROCESSING_OTHER_NUMBER,
                    recordStatus = EngageSession.STATUS_PROCESSING,
                    transactionId = txnId.toInt(),
                    notes = session.notes + " | [BOT:SCHED_RUN]"
                ))
            }
        }
        if (due.isNotEmpty()) TransactionQueueManager.processNext(context)
    }
}
