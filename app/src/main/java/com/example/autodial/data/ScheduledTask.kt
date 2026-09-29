package com.example.autodial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val offerId: Int,
    val offerName: String,
    val targetNumber: String,
    val scheduledTime: Long,
    val status: String = STATUS_PENDING, // PENDING, COMPLETED, FAILED
    val repeatMode: String = REPEAT_ONCE, // ONCE, DAILY, DAYS
    val selectedDays: String = "", // e.g. "1,2,3" for Mon, Tue, Wed
    val isEnabled: Boolean = true,
    val lastExecuted: Long = 0,
    val notes: String = ""
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"

        const val REPEAT_ONCE = "Once"
        const val REPEAT_DAILY = "Daily"
        const val REPEAT_DAYS = "Selected Days"
    }
}
