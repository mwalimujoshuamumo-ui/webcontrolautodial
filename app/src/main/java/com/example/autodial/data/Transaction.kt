package com.example.autodial.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index("transactionCode"),
        Index("date")
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val amount: Double,
    val offerName: String = "",
    val offerId: Int = 0,
    val senderName: String = "",
    val customerNumber: String = "", // Formatted as 07... or 01...
    val recipientNumber: String = "", // For Quick Dial/Scheduler
    val network: String = "",
    val simUsed: Int = 0,
    val status: String, // SUCCESS, FAILED, PROCESSING, SCHEDULED, SKIPPED, CANCELLED, WAITING
    val transactionCode: String = "", // M-PESA Code
    val commissionEarned: Double = 0.0,
    val safPrice: Double = 0.0, // For airtime usage calculation
    val processingTimeMs: Long = 0,
    val rawMessage: String = "", // For audit trail
    val ussdDialedCode: String = "", // e.g. *188#
    val executionTrail: String = "", // e.g. "8 - Gift a friend | 1 - Confirm"
    val finalResponse: String = "", // Last USSD screen text
    val verificationSms: String = "", // Incoming verification message from Safaricom
    val tokenDeducted: Boolean = false, // Track if a unit has been charged
    val isVerified: Boolean = false, // Blue tick for confirmation SMS
    val isFallbackExecuted: Boolean = false,
    val retryCount: Int = 0,
    val usedUssdType: String = "MAIN", // MAIN or ALT
    val isNotified: Boolean = false, // Prevents duplicate SMS notifications
    val processingStartedAt: Long = 0, // For 10s recovery tracking

    // Switch Offer Tracking
    val isSwitched: Boolean = false,
    val originalOfferId: Int? = null,
    val originalOfferName: String? = null,
    val switchReason: String? = null,

    val notes: String = "" // Internal notes for tracking bot/system status
) {
    companion object {
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_WAITING = "WAITING"
        const val STATUS_DISABLED = "DISABLED"
        const val STATUS_UNMATCHED = "UNMATCHED"
        const val STATUS_RELAYED = "RELAYED"
        const val STATUS_BLOCKED = "BLOCKED"
        const val STATUS_IMPORTED = "IMPORTED"
    }
}
