package com.example.autodial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_templates")
data class NotificationTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // COMPLETE, FAILED, UNAVAILABLE
    val body: String
) {
    companion object {
        const val CAT_COMPLETE = "Complete"
        const val CAT_FAILED = "Failed"
        const val CAT_UNAVAILABLE = "Unavailable Offer"
    }
}
