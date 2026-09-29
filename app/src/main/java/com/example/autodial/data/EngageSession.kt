package com.example.autodial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "engage_sessions")
data class EngageSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerNumber: String,
    val customerName: String = "",
    val originalOfferId: Int,
    val originalOfferName: String = "",
    val transactionId: Int = 0,
    val failureReason: String = "Already recommended today",
    val state: String = STATE_WAITING_FOR_CHOICE,
    val recordStatus: String = STATUS_WAITING_FOR_CUSTOMER,
    val customerChoice: String = "",
    val otherNumber: String = "",
    val originalAmount: Double = 0.0,
    val topUpAmount: Double = 0.0,
    val topUpPlanId: Int = 0,
    val selectedOfferId: Int = 0,
    val ussdResult: String = "",
    val nextEligibleDate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
) {
    companion object {
        const val STATE_RECOMMENDATION_FAILED = "RECOMMENDATION_FAILED"
        const val STATE_WAITING_FOR_CHOICE = "WAITING_FOR_CHOICE"
        const val STATE_WAITING_FOR_OTHER_NUMBER = "WAITING_FOR_OTHER_NUMBER"
        const val STATE_PROCESSING_OTHER_NUMBER = "PROCESSING_OTHER_NUMBER"
        const val STATE_WAITING_FOR_TOPUP = "WAITING_FOR_TOPUP"
        const val STATE_TOPUP_RECEIVED = "TOPUP_RECEIVED"
        const val STATE_PROCESSING_TOPUP_OFFER = "PROCESSING_TOPUP_OFFER"
        const val STATE_COMPLETED = "COMPLETED"
        const val STATE_FAILED = "FAILED"
        const val STATE_CANCELLED = "CANCELLED"
        const val STATE_SCHEDULED_FOR_TOMORROW = "SCHEDULED_FOR_TOMORROW"

        const val STATUS_PENDING = "Pending"
        const val STATUS_WAITING_FOR_CUSTOMER = "Waiting for customer"
        const val STATUS_WAITING_FOR_TOPUP = "Waiting for top-up"
        const val STATUS_PROCESSING = "Processing"
        const val STATUS_COMPLETED = "Completed"
        const val STATUS_FAILED = "Failed"
        const val STATUS_CANCELLED = "Cancelled"
        const val STATUS_SCHEDULED = "Scheduled"

        val ACTIVE_STATES = setOf(
            STATE_RECOMMENDATION_FAILED,
            STATE_WAITING_FOR_CHOICE,
            STATE_WAITING_FOR_OTHER_NUMBER,
            STATE_PROCESSING_OTHER_NUMBER,
            STATE_WAITING_FOR_TOPUP,
            STATE_TOPUP_RECEIVED,
            STATE_PROCESSING_TOPUP_OFFER,
            STATE_SCHEDULED_FOR_TOMORROW
        )
    }
}
