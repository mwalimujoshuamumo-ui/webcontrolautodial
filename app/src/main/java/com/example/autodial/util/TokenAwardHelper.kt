package com.example.autodial.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.regex.Pattern

object TokenAwardHelper {

    private val TARGET_NUMBER = "0700822857"
    private val POSITIVE_KEYWORDS = listOf(
        "successfully", "transferred", "completed", "you have transferred",
        "confirmed", "sent to", "awarded"
    )
    private val NEGATIVE_KEYWORDS = listOf(
        "sorry", "insufficient", "failed", "error", "not enough", "unavailable",
        "cancelled", "invalid"
    )

    /**
     * Processes a USSD response string and awards tokens if verification passes.
     * Returns true if units were awarded.
     */
    fun processResponse(context: Context, response: String, expectedTokens: Int): Boolean {
        val lowerResponse = response.lowercase()
        val prefManager = PreferenceManager(context)
        
        val isTargetMatch = (lowerResponse.contains(TARGET_NUMBER) || lowerResponse.contains("4005721")) &&
                            POSITIVE_KEYWORDS.any { lowerResponse.contains(it) }

        if (isTargetMatch && !NEGATIVE_KEYWORDS.any { lowerResponse.contains(it) }) {
            
            if (UssdSessionManager.isUnlimitedPurchase && UssdSessionManager.purchaseDurationDays > 0) {
                val currentExpiry = maxOf(prefManager.getUnlimitedExpiry(), System.currentTimeMillis())
                val newExpiry = currentExpiry + (UssdSessionManager.purchaseDurationDays * 24L * 60 * 60 * 1000)
                prefManager.setUnlimitedExpiry(newExpiry)
                Log.d("TokenAwardHelper", "Awarded ${UssdSessionManager.purchaseDurationDays} unlimited days for match")
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Airtime Transferred! Unlimited Plan Activated Successfully!", Toast.LENGTH_LONG).show()
                }
            } else {
                prefManager.addTokens(expectedTokens)
                Log.d("TokenAwardHelper", "Awarded $expectedTokens tokens for match")
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Airtime Transferred! Units Awarded Successfully!", Toast.LENGTH_LONG).show()
                }
            }
            return true
        } else {
            Log.d("TokenAwardHelper", "Verification failed. TargetMatch=$isTargetMatch. Response: $response")
            
            Handler(Looper.getMainLooper()).post {
                if (NEGATIVE_KEYWORDS.any { lowerResponse.contains(it) }) {
                    Toast.makeText(context, "Purchase Failed. Please check balance.", Toast.LENGTH_LONG).show()
                }
            }
            return false
        }
    }

    /**
     * Checks if there is a pending STK payment and awards tokens/unlimited plan immediately.
     */
    fun awardPendingStkPayment(context: Context): Boolean {
        val prefManager = PreferenceManager(context)
        val orderId = prefManager.getPendingOrderId()
        if (orderId.isBlank()) return false

        val units = prefManager.getPendingPaymentUnits()
        val isUnlimited = prefManager.isPendingUnlimited()
        val durationDays = prefManager.getPendingDurationDays()

        if (isUnlimited) {
            val currentExpiry = maxOf(prefManager.getUnlimitedExpiry(), System.currentTimeMillis())
            val newExpiry = currentExpiry + (durationDays * 24L * 60 * 60 * 1000)
            prefManager.setUnlimitedExpiry(newExpiry)
            Log.d("TokenAwardHelper", "Awarded $durationDays unlimited days for pending STK payment")
            
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "M-PESA Payment Successful! Unlimited Plan Activated!", Toast.LENGTH_LONG).show()
            }
        } else {
            prefManager.addTokens(units)
            Log.d("TokenAwardHelper", "Awarded $units units for pending STK payment")
            
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "M-PESA Payment Successful! $units Units Awarded!", Toast.LENGTH_LONG).show()
            }
        }

        prefManager.clearPendingPayment()
        return true
    }
}
