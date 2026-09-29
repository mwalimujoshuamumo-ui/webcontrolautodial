package com.example.autodial.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "client_sms",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ClientSms(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val message: String,
    val date: Long = System.currentTimeMillis(),
    val type: String, // "DEBT_NOTIFICATION", "BROADCAST", "GENERAL"
    val status: String // "PENDING", "SENT", "FAILED"
) {
    companion object {
        const val TYPE_DEBT = "DEBT_NOTIFICATION"
        const val TYPE_BROADCAST = "BROADCAST"
        const val TYPE_GENERAL = "GENERAL"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
    }
}
