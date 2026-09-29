package com.example.autodial.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relay_configs")
data class RelayConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val simSlot: Int = 0,
    val targetNumbers: String = "", // Comma-separated
    val offerIds: String = "", // Comma-separated, empty for all
    val isEnabled: Boolean = true
)
