package com.example.autodial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "top_up_plans")
data class TopUpPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val requiredAmount: Double,
    val offerId: Int,
    val isEnabled: Boolean = true,
    val dateCreated: Long = System.currentTimeMillis()
)
