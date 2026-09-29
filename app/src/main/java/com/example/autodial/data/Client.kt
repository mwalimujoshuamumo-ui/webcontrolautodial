package com.example.autodial.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val phoneNumber: String, // Unique identifier
    val dateAdded: Long = System.currentTimeMillis(),
    val lastTransactionDate: Long? = null,
    val totalPurchases: Int = 0,
    val totalSpent: Double = 0.0,
    val outstandingDebt: Double = 0.0,
    val isBlacklisted: Boolean = false,
    val blacklistedAt: Long? = null,
    val unblacklistedAt: Long? = null,
    val smsHistoryCount: Int = 0
)
