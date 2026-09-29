package com.example.autodial.util

import android.util.Log
import com.example.autodial.data.Transaction
import com.example.autodial.data.UssdMapWithSteps
import java.util.*

/**
 * Sequential USSD Session Manager
 * Holds the instructions (segments) extracted from the USSD string.
 */
object UssdSessionManager {
    
    // --- Session Identity ---
    var sessionId: String = ""
        private set
    var sessionStartTime: Long = 0
        private set

    // --- State Machine ---
    enum class State {
        IDLE,
        DIALING,
        WAITING_FOR_WINDOW,
        OBSERVING,
        REPLYING,
        COMPLETED,
        FAILED
    }
    var currentState: State = State.IDLE
        private set

    // --- Session Config ---
    var activeTransactionId: Int = -1
    private var segments: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var trail = TreeMap<Int, String>()
    private var currentTransaction: Transaction? = null
    
    // --- Stability ---
    var lastScreenFingerprint: String = ""
    var isAirtimeRefresh: Boolean = false
    var activeSimSlot: Int = 0
    var isTokenPurchase: Boolean = false
    var isTrainingMode: Boolean = false
    var trainingMapId: Int = -1
    var trainingPhoneNumber: String = "0700000000"
    var activeMap: UssdMapWithSteps? = null
    var tokenAmount: Int = 0
    var isUnlimitedPurchase: Boolean = false
    var purchaseDurationDays: Int = 0
    private var baseCode: String = ""
    
    var lastCustomerNumber: String? = null
        private set

    /**
     * Start a fresh USSD session, splitting the USSD code into sequential steps.
     */
    fun startSession(
        ussdCode: String,
        isAirtime: Boolean = false,
        simSlot: Int = 0,
        transactionId: Int = -1,
        isDirectDial: Boolean = false,
        transaction: Transaction? = null,
        isTraining: Boolean = false,
        mapId: Int = -1,
        map: UssdMapWithSteps? = null,
        customTrainingPhone: String = "0700000000"
    ) {
        sessionId = UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()
        currentState = State.DIALING
        
        Log.d("UssdSession", "--- FRESH START: $sessionId ---")

        trail.clear()
        lastScreenFingerprint = ""
        currentIndex = 0
        
        isAirtimeRefresh = isAirtime
        activeSimSlot = simSlot
        isTrainingMode = isTraining
        trainingMapId = mapId
        trainingPhoneNumber = if (customTrainingPhone.isNotEmpty()) customTrainingPhone else "0700000000"
        activeMap = map
        activeTransactionId = transactionId
        currentTransaction = transaction
        lastCustomerNumber = transaction?.customerNumber?.ifEmpty { transaction.recipientNumber }
        
        if (ussdCode.isEmpty()) {
            isActive = false
            currentState = State.IDLE
            return
        }

        // 1. Parsing: USSD is source of steps. 
        if (isDirectDial) {
            baseCode = formatPhone(ussdCode) // Format if it contains numbers
            segments = emptyList()
        } else {
            // Speed Optimization: Fast split for base code extraction
            val starIndex = ussdCode.indexOf('*', 1)
            if (starIndex == -1) {
                baseCode = if (ussdCode.endsWith("#")) ussdCode else "$ussdCode#"
                segments = emptyList()
            } else {
                baseCode = ussdCode.substring(0, starIndex) + "#"
                val interactionPart = ussdCode.substring(starIndex + 1).removeSuffix("#")
                segments = interactionPart.split("*").filter { it.isNotEmpty() }
            }
        }
        
        addToTrail(0, baseCode, "LAUNCH", "Dialing base USSD")
        Log.d("UssdSession", "Base Code: $baseCode | Interactive Steps: $segments")
    }

    var isActive: Boolean
        get() = currentState != State.IDLE && currentState != State.COMPLETED && currentState != State.FAILED
        set(value) { if (!value) currentState = State.IDLE }

    fun updateState(newState: State) {
        Log.d("UssdSession", "STATE: $currentState -> $newState")
        currentState = newState
    }

    fun isEventStale(eventTime: Long): Boolean {
        return eventTime < sessionStartTime
    }

    private fun ensureHash(code: String): String = if (code.endsWith("#")) code else "$code#"

    fun formatPhone(number: String): String {
        if (number.contains("AD-")) return number
        var formatted = number.trim().replace(Regex("[^0-9]"), "")
        if (formatted.startsWith("254")) {
            formatted = "0" + formatted.drop(3)
        } else if (formatted.startsWith("7") || formatted.startsWith("1")) {
            formatted = "0$formatted"
        }
        return formatted
    }

    /**
     * Resolve placeholders (BF, PN) to actual customer data at the moment of use.
     */
    fun resolvePlaceholders(text: String, transaction: Transaction? = null): String {
        val trans = transaction ?: currentTransaction
        val defaultPhone = if (isTrainingMode && trainingPhoneNumber.isNotEmpty()) trainingPhoneNumber else "0700000000"
        val rawPhone = trans?.customerNumber?.ifEmpty { trans.recipientNumber } ?: defaultPhone
        val phone = formatPhone(rawPhone)
        val amount = trans?.amount?.toInt()?.toString() ?: "0"
        val trainingPhone = if (trainingPhoneNumber.isNotEmpty()) trainingPhoneNumber else "0700000000"
        
        val trimmed = text.trim()
        val isPhoneInput = trimmed.matches(Regex("^(?:254|0)?[17][0-9]{8,10}$")) || 
                           (trimmed.length >= 10 && trimmed.all { it.isDigit() })

        if (isPhoneInput && !isTrainingMode) {
            return phone
        }
        
        return text
            .replace("BF", phone, ignoreCase = true)
            .replace("PN", phone, ignoreCase = true)
            .replace("AMT", amount, ignoreCase = true)
            .replace(trainingPhone, phone, ignoreCase = true)
            .replace("0700000000", phone, ignoreCase = true)
    }

    fun addToTrail(index: Int, step: String, label: String, header: String = "", status: String = "SUCCESS") {
        // Store raw data for high-fidelity rendering
        trail[index] = "$step:::$label:::$header:::$status"
    }

    fun addFinalResponse(message: String, status: String = "SUCCESS") {
        trail[999] = "FINAL:::$message:::Final Response:::$status"
    }

    fun getTrail(): String = trail.values.joinToString("*")
    fun getBaseCode(): String = baseCode
    fun getCurrentIndex(): Int = currentIndex
    
    /**
     * Returns the next input segment from the USSD string, resolving placeholders.
     */
    fun peekNextStep(): String? {
        if (currentIndex < segments.size) {
            val rawStep = segments[currentIndex]
            return resolvePlaceholders(rawStep)
        }
        return null
    }

    /**
     * Mark the current segment as successfully submitted.
     */
    fun consumeStep() {
        currentIndex++
    }

    /**
     * Returns true if the current step is the very last interaction defined in the sequence.
     */
    fun isLastStep(): Boolean {
        return segments.isNotEmpty() && currentIndex == segments.size - 1
    }

    fun endSession(success: Boolean = true) {
        Log.d("UssdSession", "--- SESSION ENDED ($success) ---")
        currentState = if (success) State.COMPLETED else State.FAILED
        currentIndex = 0
        currentTransaction = null
        isTrainingMode = false
        trainingMapId = -1
        activeMap = null
        isTokenPurchase = false
        tokenAmount = 0
        isUnlimitedPurchase = false
        purchaseDurationDays = 0
        com.example.autodial.service.UssdAccessibilityService.stopSuppression()
        WakeManager.releaseWakeLock()
    }
}
