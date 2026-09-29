package com.example.autodial.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.autodial.data.AppDatabase
import com.example.autodial.data.*
import com.example.autodial.util.EngageBotManager
import com.example.autodial.util.EngageBotTrigger
import com.example.autodial.util.MpesaParser
import com.example.autodial.util.PreferenceManager
import com.example.autodial.util.ClientManager
import com.example.autodial.util.MpesaDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
                com.example.autodial.util.WakeManager.wakeAndUnlock(context)
                val preferenceManager = PreferenceManager(context)
                
                val messages = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return
                
                val fullMessage = messages.mapNotNull { it.displayMessageBody }.joinToString("")
                val senderNumber = messages[0].originatingAddress ?: "Unknown"

                Log.d("SmsReceiver", "SMS received from: $senderNumber")

                val verifiedSenders = preferenceManager.getVerifiedSenders()
                val incomingDigits = senderNumber.replace(Regex("[^0-9]"), "")
                val incomingNormalized = if (incomingDigits.length >= 9) incomingDigits.takeLast(9) else ""
                
                val isVerifiedSender = verifiedSenders.any { whitelisted -> 
                    val w = whitelisted.trim()
                    // 1. Direct String Match (handles "MPESA")
                    if (w.equals(senderNumber.trim(), ignoreCase = true)) return@any true
                    
                    // 2. STRICT NUMERIC CHECK: Strip all non-digits from BOTH sides
                    val wDigits = w.replace(Regex("[^0-9]"), "")
                    if (wDigits.length >= 9 && incomingNormalized.isNotEmpty()) {
                        val wNormalized = wDigits.takeLast(9)
                        return@any wNormalized == incomingNormalized
                    }
                    false
                }
                
                if (isVerifiedSender) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            processMpesaMessage(context, fullMessage, senderNumber, isVerifiedSender)
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error processing message", e)
                        } finally {
                            try {
                                pendingResult.finish()
                            } catch (e: Exception) {
                                Log.e("SmsReceiver", "Error finishing pending result", e)
                            }
                        }
                    }
                } else {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            var handledAsCarrier = false
                            val isCarrierOrConfirmation = senderNumber.contains("SAFARICOM", ignoreCase = true) || 
                                                          senderNumber.contains("Safaricom", ignoreCase = true) ||
                                                          senderNumber.equals("SUCCESS", ignoreCase = true) ||
                                                          senderNumber.matches(Regex("(?i).*444.*|.*144.*|.*BINGWA.*|.*CARRIER.*|.*NETWORK.*|.*SYSTEM.*")) ||
                                                          fullMessage.lowercase().contains("success") ||
                                                          fullMessage.lowercase().contains("confirmed") ||
                                                          fullMessage.lowercase().contains("bought") ||
                                                          fullMessage.lowercase().contains("purchased")
                            
                            if (isCarrierOrConfirmation) {
                                handledAsCarrier = verifyCarrierConfirmation(context, fullMessage, senderNumber)
                            }
                            
                            if (!handledAsCarrier && preferenceManager.isEngageBotEnabled()) {
                                val handled = EngageBotManager.handleIncomingSms(context, senderNumber, fullMessage)
                                if (handled) {
                                    Log.i("SmsReceiver", "Engage Bot handled SMS from $senderNumber")
                                }
                            } else if (!handledAsCarrier) {
                                Log.d("SmsReceiver", "Unverified sender number: $senderNumber. Ignoring SMS.")
                            }
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error in async SMS processing", e)
                        } finally {
                            try {
                                pendingResult.finish()
                            } catch (e: Exception) {
                                Log.e("SmsReceiver", "Error finishing pending result", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Fatal error in onReceive", e)
        }
    }

    private suspend fun processMpesaMessage(context: Context, message: String, originalSender: String, isVerifiedSender: Boolean = false) {
        val database = AppDatabase.getDatabase(context)
        val repository = database.createRepository()
        val prefManager = PreferenceManager(context)

        // Check if there is a pending STK Push payment awaiting M-PESA confirmation
        if (prefManager.getPendingOrderId().isNotBlank()) {
            val awarded = com.example.autodial.util.TokenAwardHelper.awardPendingStkPayment(context)
            if (awarded) {
                Log.i("SmsReceiver", "Pending STK Push payment awarded via incoming M-PESA SMS")
            }
        }

        // Handle Airtime Transfer Confirmation as a high-priority edge case
        val isAirtimeConfirm = (message.contains("0700822857") || message.contains("4005721")) && 
                               (message.contains("transferred", true) || message.contains("successfully", true))
        
        if (isAirtimeConfirm) {
            // We can't know the exact token amount from the SMS easily without a match to a pending request,
            // but we can try to award based on amount if we parse it.
            val amountPattern = java.util.regex.Pattern.compile("(?:KSH|Ksh|ksh)\\s?([0-9,.]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            val matcher = amountPattern.matcher(message)
            if (matcher.find()) {
                val amount = matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                // Match amount to a token package
                val pkg = when(amount.toInt()) {
                    10 -> 100
                    25 -> 300
                    40 -> 600
                    99 -> 1500
                    180 -> 3000
                    280 -> 5000
                    500 -> 10000
                    15 -> -1 // Unlimited 1 Day
                    // etc.
                    else -> 0
                }
                
                if (pkg > 0) {
                    prefManager.addTokens(pkg)
                    showToast(context, "Airtime Transferred! $pkg Units Awarded via SMS confirmation.")
                } else if (pkg == -1) {
                    val currentExpiry = maxOf(prefManager.getUnlimitedExpiry(), System.currentTimeMillis())
                    prefManager.setUnlimitedExpiry(currentExpiry + (1 * 24L * 60 * 60 * 1000))
                    showToast(context, "Airtime Transferred! Unlimited Plan Activated via SMS confirmation.")
                }
            }
            return
        }

        val details = MpesaParser.parse(message)
        if (details == null) {
            Log.w("SmsReceiver", "Failed to parse message content: $message")
            return
        }

        // Intercept M-Pesa top-up payments for active Engage Bot sessions (Auto Top-Up without reply)
        if (details.isReceived && details.amount > 0) {
            val handledAsTopUp = EngageBotManager.handleTopUpPayment(
                context = context,
                customerNumber = details.customerNumber,
                amount = details.amount,
                transactionCode = details.transactionCode,
                rawMessage = message,
                senderName = details.sender
            )
            if (handledAsTopUp) {
                Log.i("SmsReceiver", "Engage Bot successfully intercepted top-up payment for ${details.customerNumber} (Amount: ${details.amount})")
                return
            }
        }

        val existing = repository.getTransactionByCode(details.transactionCode)
        if (existing != null) {
            Log.w("SmsReceiver", "Duplicate M-PESA code detected: ${details.transactionCode}. Ignoring.")
            return
        }

        val initialTransaction = Transaction(
            amount = details.amount,
            senderName = details.sender.uppercase().trim(),
            customerNumber = details.customerNumber,
            status = Transaction.STATUS_PROCESSING,
            transactionCode = details.transactionCode,
            rawMessage = message,
            notes = if (isVerifiedSender) "[VERIFIED FORWARD] Received via $originalSender" else ""
        )
        val transactionId = repository.insertTransaction(initialTransaction)
        
        if (details.isReceived) {
            ClientManager.upsertClientFromTransaction(context, initialTransaction.copy(id = transactionId.toInt()))
        }
        
        val matchedOffer = repository.findMatchingOfferIgnoringStatus(details.amount)
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        
        var effectiveOffer = matchedOffer
        var switchApplied = false
        if (matchedOffer != null && (!matchedOffer.isEnabled || !matchedOffer.isTimerActive(currentTime))) {
            if (matchedOffer.switchOfferId != null) {
                val allOffers = repository.allOffers.first()
                val switchOffer = allOffers.find { it.id == matchedOffer.switchOfferId }
                if (switchOffer != null && switchOffer.isEnabled && switchOffer.isTimerActive(currentTime)) {
                    effectiveOffer = switchOffer
                    switchApplied = true
                    Log.i("SmsReceiver", "REDIRECT: Switched from ${matchedOffer.name} to ${switchOffer.name} due to timer/status.")
                }
            }
        }
        
        var finalStatus = when {
            !details.isReceived -> {
                val isAutodialRecipient = details.recipient.contains("AUTODIAL") || 
                                         details.recipient.contains("TORONTO DATA OFFERS") ||
                                         message.contains("4005721") ||
                                         message.contains("0700822857")
                
                if (isAutodialRecipient) {
                    // If it's an airtime transfer to the admin number, award tokens locally as well
                    // to ensure immediate availability if USSD callback missed it.
                    if (message.lowercase().contains("transferred") || message.lowercase().contains("sent to")) {
                        // We don't have the expected token count here easily, but we can try to parse the amount
                        // or just rely on the USSD flow. For now, let's mark it success.
                        Log.d("SmsReceiver", "Airtime transfer confirmation detected for Autodial.")
                    }
                    Transaction.STATUS_SUCCESS
                } else {
                    Transaction.STATUS_UNMATCHED
                }
            }
            details.isMasked -> Transaction.STATUS_SKIPPED
            effectiveOffer == null -> Transaction.STATUS_UNMATCHED
            !effectiveOffer.isEnabled || !effectiveOffer.isTimerActive(currentTime) -> Transaction.STATUS_DISABLED
            else -> Transaction.STATUS_WAITING
        }

        val updatedTransaction = initialTransaction.copy(
            id = transactionId.toInt(),
            offerName = effectiveOffer?.name ?: "Unmatched",
            offerId = effectiveOffer?.id ?: 0,
            status = finalStatus,
            simUsed = effectiveOffer?.simSelection ?: 0,
            isSwitched = switchApplied,
            originalOfferId = if (switchApplied) matchedOffer?.id else null,
            originalOfferName = if (switchApplied) matchedOffer?.name else null,
            switchReason = if (switchApplied) "Timer Switch" else null,
            notes = if (switchApplied) initialTransaction.notes + "\n[SWITCH] Redirected from ${matchedOffer?.name} to ${effectiveOffer?.name} due to Timer schedule." else initialTransaction.notes
        )
        repository.updateTransaction(updatedTransaction)

        if (finalStatus == Transaction.STATUS_WAITING) {
            com.example.autodial.util.TransactionQueueManager.processNext(context)
        }
    }

    private suspend fun verifyCarrierConfirmation(context: Context, message: String, sender: String): Boolean {
        val database = AppDatabase.getDatabase(context)
        val repository = database.createRepository()
        
        // 1. Extract phone number from message (handling spaces/dashes and half-masked numbers with asterisks like 7912670****)
        // Prefix (254 or 0) is optional to fully support 7..., 1..., 07..., 01..., 254..., 2541... and asterisks *
        val phonePattern = java.util.regex.Pattern.compile("(?:254|0)?([17][0-9*\\s-]{5,15})")
        val matcher = phonePattern.matcher(message)
        
        val foundNumbers = mutableListOf<String>()
        while (matcher.find()) {
            val raw = matcher.group(1) ?: ""
            // Remove asterisks, spaces, dashes to get the prefix/digits available (matching customer number first numbers)
            val cleanDigits = raw.replace(Regex("[^0-9]"), "")
            if (cleanDigits.length >= 4) {
                foundNumbers.add(cleanDigits)
            }
        }
        
        val candidateTransactions = mutableSetOf<Transaction>()

        if (foundNumbers.isNotEmpty()) {
            foundNumbers.forEach { prefix ->
                val recent = repository.findRecentTransactionByPhone(prefix)
                if (recent != null) {
                    candidateTransactions.add(recent)
                }
            }
        }

        // Fallback: If no phone match found, look for the most recent unverified transaction
        if (candidateTransactions.isEmpty()) {
            val allRecent = repository.allTransactions.first()
            val recentUnverified = allRecent.firstOrNull { 
                !it.isVerified && (it.status == Transaction.STATUS_PROCESSING || it.status == Transaction.STATUS_WAITING || it.status == Transaction.STATUS_SUCCESS)
            }
            if (recentUnverified != null) {
                candidateTransactions.add(recentUnverified)
            }
        }

        if (candidateTransactions.isEmpty()) return false

        var transactionMatched = false
        candidateTransactions.forEach { recent ->
            // CRITICAL RULE: Any incoming message after Blue Tick verification is NOT valued/ignored.
            if (recent.isVerified) {
                Log.d("SmsReceiver", "Transaction ${recent.id} already has Blue Tick verification. Ignoring incoming SMS from $sender.")
                return@forEach
            }

            if (recent.status == Transaction.STATUS_PROCESSING || recent.status == Transaction.STATUS_WAITING || recent.status == Transaction.STATUS_SUCCESS) {
                transactionMatched = true
                
                // Determine if this is a failure message or success with positive override
                val lowerMsg = message.lowercase()
                val isPositiveOverride = lowerMsg.contains("success") || 
                                         lowerMsg.contains("confirmed") || 
                                         lowerMsg.contains("bought") || 
                                         lowerMsg.contains("purchased") || 
                                         lowerMsg.contains("completed") || 
                                         lowerMsg.contains("credited") || 
                                         lowerMsg.contains("received") ||
                                         lowerMsg.contains("transferred") ||
                                         lowerMsg.contains("enjoy")

                val isNegative = !isPositiveOverride && (
                                 EngageBotTrigger.isAlreadyRecommendedToday(message) || 
                                 lowerMsg.contains("failed") || 
                                 lowerMsg.contains("sorry") ||
                                 lowerMsg.contains("insufficient") ||
                                 lowerMsg.contains("invalid") ||
                                 lowerMsg.contains("unable") ||
                                 lowerMsg.contains("unsuccessful")
                                 )
                
                val executionTypeDesc = if (recent.isFallbackExecuted) "Fallback Execution" else "Original Execution"
                val customerFirstNumbers = if (recent.customerNumber.length >= 7) recent.customerNumber.take(7) else recent.customerNumber

                if (isNegative) {
                    val updated = recent.copy(
                        status = Transaction.STATUS_FAILED,
                        isVerified = false,
                        verificationSms = message,
                        notes = (recent.notes + "\n[CARRIER / $executionTypeDesc] Negative confirmation received from $sender: $message").trim()
                    )
                    repository.updateTransaction(updated)
                } else {
                    val updated = recent.copy(
                        status = Transaction.STATUS_SUCCESS,
                        isVerified = true,
                        verificationSms = message,
                        notes = (recent.notes + "\n[BLUETIK / $executionTypeDesc] Verified via Carrier SMS from $sender matching customer '$customerFirstNumbers': $message").trim()
                    )
                    repository.updateTransaction(updated)
                }
            }
        }
        return transactionMatched
    }


    private fun showToast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
