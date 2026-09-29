package com.example.autodial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offers")
data class Offer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val amount: Double = 0.0,
    val safPrice: Double = 0.0, // Official Safaricom Price
    val network: String = "",
    val ussdCode: String = "",
    val simSelection: Int = 0,
    val status: Int = 1,
    val category: String = CATEGORY_DATA, // Data, Minutes, SMS, Other
    val isEnabled: Boolean = true,
    val commission: Double = 0.0,
    val paymentMode: String = MODE_BOTH, // TILL, PAYBILL, BOTH
    val ussdMode: String = USSD_SIMPLE, // SIMPLE, ADVANCED
    
    // Execution Settings
    val executionDelayMs: Long = 0,
    val retryAttempts: Int = 0,
    val timeoutMs: Long = 10000,
    
    val notes: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val timesUsed: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastUsedDate: Long = 0,
    val avgExecutionTimeMs: Long = 0,

    // Alternative USSD configuration
    val mainStartTime: String = "00:00",
    val mainEndTime: String = "23:59",
    
    val altUssdCode: String = "",
    val altStartTime: String = "",
    val altEndTime: String = "",

    // Fallback Plan
    val isFallbackEnabled: Boolean = false,
    val fallbackOfferId: Int? = null,

    // Timer Settings
    val isTimerEnabled: Boolean = false,
    val timerEnableAt: String = "00:00",
    val timerDisableAt: String = "23:59",
    val switchOfferId: Int? = null
) {
    fun isAltConfigured(): Boolean = altUssdCode.isNotEmpty()

    fun isTimerActive(currentTime: String): Boolean {
        if (!isTimerEnabled) return true
        
        val currentMin = parseTimeToMinutes(currentTime) ?: return true
        val enableMin = parseTimeToMinutes(timerEnableAt) ?: 0
        val disableMin = parseTimeToMinutes(timerDisableAt) ?: 1439 // 23:59 default
        
        return if (enableMin <= disableMin) {
            currentMin >= enableMin && currentMin < disableMin
        } else {
            currentMin >= enableMin || currentMin < disableMin
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int? {
        if (timeStr.isEmpty() || !timeStr.contains(":")) return null
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    fun getActiveConfig(currentTime: String): String {
        if (!isAltConfigured()) return "MAIN"
        
        return if (isTimeInRange(currentTime, altStartTime, altEndTime)) {
            "ALT"
        } else {
            "MAIN"
        }
    }

    private fun isTimeInRange(current: String, start: String, end: String): Boolean {
        if (start.isEmpty() || end.isEmpty()) return false
        
        return if (start <= end) {
            current > start && current < end
        } else {
            current > start || current < end
        }
    }

    companion object {
        const val CATEGORY_DATA = "Data"
        const val CATEGORY_MINUTES = "Minutes"
        const val CATEGORY_SMS = "SMS"
        const val CATEGORY_OTHER = "Other"

        const val MODE_TILL = "Till Number"
        const val MODE_PAYBILL = "Paybill"
        const val MODE_BOTH = "Both"

        const val USSD_SIMPLE = "Simple"
        const val USSD_ADVANCED = "Advanced"
    }
}
