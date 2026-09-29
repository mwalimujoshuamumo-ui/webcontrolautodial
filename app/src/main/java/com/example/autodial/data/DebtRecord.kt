package com.example.autodial.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "debt_records",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DebtRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val type: String, // "DEBT", "PAYMENT"
    val notes: String = ""
) {
    companion object {
        const val TYPE_DEBT = "DEBT"
        const val TYPE_PAYMENT = "PAYMENT"
    }
}
