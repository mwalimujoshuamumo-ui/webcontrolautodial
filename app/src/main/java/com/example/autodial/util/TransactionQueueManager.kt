package com.example.autodial.util

import android.content.Context
import android.util.Log
import com.example.autodial.data.*
import com.example.autodial.service.UssdAccessibilityService
import com.example.autodial.util.ClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Transaction Execution Router
 * Routes to SIMPLE (Pre-composed Dial) or ADVANCED (Sequential Step) engines.
 */
object TransactionQueueManager {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("QueueManager", "Queue processing CRASHED: ${throwable.message}", throwable)
        processingLock.set(false)
        isWaitingForDismissal = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private val processingLock = AtomicBoolean(false)
    var isWaitingForDismissal = false
        private set
    private var lastWaitingForDismissalTime = 0L
    
    // Blue Badge immediate verification tracking
    var lastDismissedPhoneSuffix: String? = null
        private set
    var lastDismissalTimestamp: Long = 0
        private set

    private val lastProcessTime = AtomicLong(0L)

    /**
     * When true, prevents the queue from automatically starting on app launch.
     * Must be cleared by user action (e.g. clicking "Process All").
     */
    var isQueueOnHold = false

    fun resetProcessingLock() {
        processingLock.set(false)
        isWaitingForDismissal = false
        // stopForeground(context) - Needs context, maybe handle it in UssdAccessibilityService
    }

    fun forceResetLocks() {
        processingLock.set(false)
        isWaitingForDismissal = false
        lastWaitingForDismissalTime = 0L
        UssdSessionManager.endSession(false)
    }

    fun stopForeground(context: Context) {
        com.example.autodial.service.ProcessingForegroundService.stop(context)
    }

    fun markAsDismissed(phone: String? = null) {
        Log.d("QueueManager", "USSD window dismissed confirmed for phone: $phone")
        isWaitingForDismissal = false
        lastWaitingForDismissalTime = 0L
        
        if (phone != null) {
            val clean = phone.replace(Regex("[^0-9]"), "")
            lastDismissedPhoneSuffix = if (clean.length >= 9) clean.takeLast(9) else clean
            lastDismissalTimestamp = System.currentTimeMillis()
        }
    }

    fun clearHold() {
        isQueueOnHold = false
    }

    fun isProcessing(): Boolean {
        // SAFETY: If stuck waiting for dismissal for > 30s, auto-clear
        if (isWaitingForDismissal && lastWaitingForDismissalTime > 0 && 
            System.currentTimeMillis() - lastWaitingForDismissalTime > 30000) {
            isWaitingForDismissal = false
            lastWaitingForDismissalTime = 0
            Log.w("QueueManager", "CLEARED: Stale dismissal lock (30s timeout)")
        }
        return processingLock.get() || UssdSessionManager.isActive || isWaitingForDismissal
    }

    fun processNext(context: Context, immediate: Boolean = false) {
        scope.launch {
            val now = System.currentTimeMillis()
            // GLOBAL RECOVERY: If stuck in any state for > 60s, force clear all locks
            val isStale = (processingLock.get() || isWaitingForDismissal || UssdSessionManager.isActive) && 
                         (lastProcessTime.get() > 0 && now - lastProcessTime.get() > 60000)
            
            if (isStale) {
                processingLock.set(false)
                isWaitingForDismissal = false
                UssdSessionManager.isActive = false
                Log.w("QueueManager", "RECOVERED: Forced reset of ALL stale locks (60s timeout)")
            }

            if (UssdSessionManager.isActive || isWaitingForDismissal) {
                Log.d("QueueManager", "Session active or waiting for dismissal, skipping.")
                return@launch
            }

            // AUTO-CLEAR HOLD: If user triggers an immediate/manual run, resume automated processing for the rest
            if (immediate) isQueueOnHold = false

            if (isQueueOnHold && !immediate) {
                Log.d("QueueManager", "Queue is currently ON HOLD. User must trigger manual process.")
                return@launch
            }
            
            if (!processingLock.compareAndSet(false, true)) return@launch
            lastProcessTime.set(now)
            
            WakeManager.wakeAndUnlock(context)
            
            // Start Foreground Service to prevent Deep Sleep
            com.example.autodial.service.ProcessingForegroundService.start(context)

            try {
                val prefManager = PreferenceManager(context)
                
                // Stabilizing Cooldown Delay: Let the device cellular modem cycle down and stabilize.
                if (!immediate) {
                    val cooldown = prefManager.getQueueCooldownDelay()
                    if (cooldown > 0) delay(cooldown)
                } 

                val database = AppDatabase.getDatabase(context)
                val repository = database.createRepository()
                
            // 1. STALE RECOVERY: Find and fail any transactions stuck in PROCESSING for > 30s
            val activeProcessing = repository.getProcessingTransactions()
            val stuckTransactions = activeProcessing.filter { 
                it.processingStartedAt > 0 && (System.currentTimeMillis() - it.processingStartedAt > 30000)
            }
            
            if (stuckTransactions.isNotEmpty()) {
                Log.w("QueueManager", "CLEANUP: Found ${stuckTransactions.size} transactions stuck in PROCESSING. Failing them.")
                stuckTransactions.forEach { txn ->
                    repository.updateTransaction(txn.copy(
                        status = Transaction.STATUS_FAILED,
                        finalResponse = "Execution Timeout (Stuck)"
                    ))
                }
            }

            // 2. STRICT SERIAL PROCESSING RULE: Never launch if another is ALREADY processing (after cleanup)
            val stillProcessing = repository.getProcessingTransactions()
            if (stillProcessing.isNotEmpty()) {
                Log.d("QueueManager", "BLOCK: ${stillProcessing.size} transaction(s) still in status PROCESSING. Holding queue.")
                processingLock.set(false)
                return@launch
            }
                
                val waitingTransactions = repository.getWaitingTransactions()
                val offers = repository.allOffers.first()
                val isAccessibilityEnabled = UssdHelper.isAccessibilityServiceEnabled(context)

                val queuedTransaction = waitingTransactions
                    .find { txn ->
                        val isQuickDial = txn.senderName == "QUICK DIAL" || txn.senderName == "MANUAL RUN"
                        val offer = offers.find { it.id == txn.offerId }
                        
                        // Eligibility for immediate processing:
                        // 1. Simple Mode: Always runs if automation is on (or quick dial)
                        // 2. Advanced Mode: Runs ONLY if Accessibility is ON
                        // 3. Unmatched/Broken: Runs to be marked as such
                        
                        val mode = offer?.ussdMode ?: Offer.USSD_SIMPLE
                        val canRun = if (mode == Offer.USSD_ADVANCED) isAccessibilityEnabled else true
                        
                        if (isQuickDial) {
                            canRun
                        } else {
                            prefManager.isAutomationEnabled() && prefManager.isGlobalAutomationEnabled() && canRun
                        }
                    }

                if (queuedTransaction != null) {
                    val isQuickDial = queuedTransaction.senderName == "QUICK DIAL" || queuedTransaction.senderName == "MANUAL RUN"
                    val isUnlimited = prefManager.isUnlimitedActive()
                    val units = prefManager.getTokens()

                    if (!isQuickDial && (units <= 0 && !isUnlimited)) {
                        Log.d("QueueManager", "Transaction ${queuedTransaction.id} failed: No units.")
                        repository.updateTransaction(queuedTransaction.copy(
                            status = Transaction.STATUS_FAILED,
                            finalResponse = "No Token"
                        ))
                        processingLock.set(false)
                        stopForeground(context)
                        processNext(context)
                        return@launch
                    }

                    // 1. ELIGIBILITY CHECK: Blacklist and Debt
                    val (isEligible, reason) = ClientManager.checkEligibility(context, queuedTransaction)
                    if (!isEligible) {
                        Log.d("QueueManager", "Transaction ${queuedTransaction.id} blocked. Reason: $reason")
                        
                        val finalResponse = when (reason) {
                            "BLACKLISTED" -> "Blocked: Blacklisted"
                            "DEBT" -> "Blocked: Outstanding Debt"
                            else -> "Blocked: Restricted"
                        }
                        
                        val blockedTransaction = queuedTransaction.copy(
                            status = Transaction.STATUS_BLOCKED,
                            finalResponse = finalResponse
                        )
                        repository.updateTransaction(blockedTransaction)

                        processingLock.set(false)
                        processNext(context)
                        return@launch
                    }

                    val offer = offers.find { it.id == queuedTransaction.offerId }
                    
                    if (offer != null && offer.isEnabled) {
                        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                        val activeConfig = offer.getActiveConfig(currentTime)
                        
                        // 2. CONNECT Pro: Try to route to Linked Processors first if on Main Device
                        if (prefManager.getDeviceRole() == "MAIN") {
                            val routed = RelayServer.routeTransaction(queuedTransaction, context)
                            if (routed) {
                            val relayedTransaction = queuedTransaction.copy(
                                status = Transaction.STATUS_RELAYED,
                                finalResponse = "Relayed to Linked Processor",
                                processingStartedAt = System.currentTimeMillis()
                            )
                            repository.updateTransaction(relayedTransaction)
                                processingLock.set(false)
                                processNext(context)
                                return@launch
                            }
                        }

                        val isManual = queuedTransaction.senderName == "QUICK DIAL" || queuedTransaction.senderName == "MANUAL RUN"
                        val effectiveSim = if (isManual) queuedTransaction.simUsed else offer.simSelection

                        repository.updateTransaction(queuedTransaction.copy(
                            status = Transaction.STATUS_PROCESSING, 
                            usedUssdType = activeConfig,
                            simUsed = effectiveSim,
                            processingStartedAt = System.currentTimeMillis()
                        ))
                        WakeManager.wakeAndUnlock(context)

                        if (offer.ussdMode == Offer.USSD_ADVANCED) {
                            // ADVANCED MODE: Sequential step-by-step engine
                            isWaitingForDismissal = true
                            lastWaitingForDismissalTime = System.currentTimeMillis()
                            
                            // Trigger Keyboard Suppression immediately
                            UssdAccessibilityService.startSuppression()
                            
                            val ussd = if (activeConfig == "ALT") offer.altUssdCode else offer.ussdCode
                            val activeMap = repository.ussdMapDao.getActiveMapWithStepsForOffer(offer.id)

                            UssdSessionManager.startSession(
                                ussdCode = ussd, 
                                transactionId = queuedTransaction.id,
                                simSlot = effectiveSim,
                                transaction = queuedTransaction,
                                map = activeMap
                            )
                            
                            // RECORD INITIAL TRAIL (STEP 0)
                            repository.updateTransaction(queuedTransaction.copy(
                                status = Transaction.STATUS_PROCESSING, 
                                executionTrail = UssdSessionManager.getTrail(),
                                simUsed = effectiveSim, 
                                processingStartedAt = System.currentTimeMillis()
                            ))

                            UssdHelper.dialUssd(context, UssdSessionManager.getBaseCode(), effectiveSim)
                        } else {
                            // SIMPLE MODE: Stealth Background Dial
                            val template = if (activeConfig == "ALT") offer.altUssdCode else offer.ussdCode
                            val phone = queuedTransaction.customerNumber.ifEmpty { queuedTransaction.recipientNumber }
                            val finalUssd = template
                                .replace("BF", phone, ignoreCase = true)
                                .replace("PN", phone, ignoreCase = true)
                                .replace("AMT", queuedTransaction.amount.toInt().toString(), ignoreCase = true)
                            
                            UssdHelper.sendBackgroundUssd(context, finalUssd, effectiveSim) { response ->
                                scope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    val repo = db.createRepository()
                                    
                                    if (response != null) {
                                        val lower = response.lowercase()
                                        val isFailure = lower.contains("sorry") || 
                                                       lower.contains("insufficient") || 
                                                       lower.contains("sufficient") ||
                                                       lower.contains("failed") || 
                                                       lower.contains("invalid") ||
                                                       lower.contains("top-up") ||
                                                       EngageBotTrigger.isAlreadyRecommendedToday(response)
                                        
                                        var shouldDeductToken = false
                                        if (!isFailure) {
                                            // TOKEN DEDUCTION FIX: Deduct token for successful background USSD
                                            val isAutomated = queuedTransaction.senderName != "MANUAL RUN" && queuedTransaction.senderName != "QUICK DIAL"
                                            if (isAutomated && !queuedTransaction.tokenDeducted && offer.id != 0) {
                                                if (prefManager.useToken()) {
                                                    shouldDeductToken = true
                                                }
                                            }
                                        }

                                        val isAutomated = queuedTransaction.senderName != "MANUAL RUN" && queuedTransaction.senderName != "QUICK DIAL"
                                        var fallbackInitiated = false
                                        
                                        // TARGETED FALLBACK: Alternative/Fallback offer runs ONLY when initial offer was "Already Recommended Today"
                                        val isAlreadyRec = EngageBotTrigger.isAlreadyRecommendedToday(response)
                                        
                                        if (isFailure && isAutomated && isAlreadyRec && offer.isFallbackEnabled && offer.fallbackOfferId != null && !queuedTransaction.isFallbackExecuted) {
                                            val fallbackOffer = repo.allOffers.first().find { it.id == offer.fallbackOfferId }
                                            if (fallbackOffer != null) {
                                                repo.updateTransaction(queuedTransaction.copy(
                                                    offerId = fallbackOffer.id,
                                                    offerName = fallbackOffer.name,
                                                    status = Transaction.STATUS_WAITING,
                                                    isFallbackExecuted = true,
                                                    simUsed = fallbackOffer.simSelection,
                                                    finalResponse = response,
                                                    notes = (queuedTransaction.notes + "\n[FALLBACK] Initial offer '${offer.name}' already recommended today ($response). Retrying with alternative '${fallbackOffer.name}'.").trim()
                                                ))
                                                fallbackInitiated = true
                                            }
                                        }

                                        if (!fallbackInitiated) {
                                            val oldResponse = queuedTransaction.finalResponse
                                            val isAlternativeOrFallback = queuedTransaction.isFallbackExecuted || queuedTransaction.isSwitched || queuedTransaction.originalOfferId != null
                                            val isNegativeResponse = response.lowercase().let {
                                                it.contains("insufficient") || it.contains("sorry") || it.contains("invalid") || it.contains("failed") || EngageBotTrigger.isAlreadyRecommendedToday(it)
                                            }
                                            val alreadySucceeded = queuedTransaction.status == Transaction.STATUS_SUCCESS || queuedTransaction.isVerified
                                            val effectiveFailure = if (alreadySucceeded) false else (isFailure && !(isAlternativeOrFallback && !isNegativeResponse))

                                            val fallbackNote = if (queuedTransaction.isFallbackExecuted) {
                                                "\n[FALLBACK EXECUTION] Final Status: ${if (effectiveFailure) Transaction.STATUS_FAILED else Transaction.STATUS_SUCCESS} | Last Response: $response"
                                            } else if (queuedTransaction.isSwitched || queuedTransaction.originalOfferId != null) {
                                                "\n[ALTERNATIVE EXECUTION] Final Status: ${if (effectiveFailure) Transaction.STATUS_FAILED else Transaction.STATUS_SUCCESS} | Last Response: $response"
                                            } else ""

                                            val newNotes = if (oldResponse.isNotBlank() && oldResponse != response) {
                                                (queuedTransaction.notes + "\n[HISTORY] Initial Response: $oldResponse" + fallbackNote).trim()
                                            } else {
                                                (queuedTransaction.notes + fallbackNote).trim()
                                            }

                                            val updated = queuedTransaction.copy(
                                                status = if (alreadySucceeded || !effectiveFailure) Transaction.STATUS_SUCCESS else Transaction.STATUS_FAILED,
                                                finalResponse = if (alreadySucceeded && isNegativeResponse) queuedTransaction.finalResponse else response,
                                                notes = newNotes,
                                                commissionEarned = if (alreadySucceeded || !effectiveFailure) offer.commission else 0.0,
                                                safPrice = if (alreadySucceeded || !effectiveFailure) offer.safPrice else 0.0,
                                                simUsed = effectiveSim, 
                                                tokenDeducted = queuedTransaction.tokenDeducted || shouldDeductToken,
                                                isVerified = queuedTransaction.isVerified,
                                                verificationSms = queuedTransaction.verificationSms
                                            )
                                            repo.updateTransaction(updated)

                                            // CONNECT Reporting
                                            if (updated.senderName.startsWith("RELAY:")) {
                                                RelayClient.reportResult(updated.transactionCode, updated.status, response)
                                            }

                                            val engaged = if (isFailure && !alreadySucceeded) {
                                                val isEngageTxn = updated.senderName.startsWith(EngageBotManager.SENDER_PREFIX) ||
                                                    updated.rawMessage.contains(EngageBotManager.SESSION_MARKER)
                                                if (!isEngageTxn) {
                                                    EngageBotManager.tryStartFromRecommendationFailure(context, updated, response)
                                                } else false
                                            } else {
                                                false
                                            }
                                            if (!engaged) {
                                                EngageBotManager.onOfferExecutionResult(
                                                    context, updated, !isFailure, response
                                                )
                                            }
                                        }
                                    }
                                    
                                    processingLock.set(false)
                                    isWaitingForDismissal = false
                                    if (!isProcessing()) stopForeground(context)
                                    delay(1200)
                                    processNext(context)
                                }
                            }
                        }
                    } else {
                        repository.updateTransaction(queuedTransaction.copy(status = Transaction.STATUS_DISABLED))
                        processingLock.set(false)
                        processNext(context) 
                    }
                } else {
                    processingLock.set(false)
                    stopForeground(context)
                }
            } catch (e: CancellationException) {
                processingLock.set(false)
                isWaitingForDismissal = false
                Log.i("QueueManager", "Queue processing cancelled")
            } catch (e: Exception) {
                Log.e("QueueManager", "Queue processing error", e)
                processingLock.set(false)
                isWaitingForDismissal = false
                stopForeground(context)
            }
        }
    }
}
