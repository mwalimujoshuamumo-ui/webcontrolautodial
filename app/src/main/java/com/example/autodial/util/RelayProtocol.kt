package com.example.autodial.util

import com.example.autodial.data.Transaction

/**
 * CONNECT Protocol Message Wrapper
 */
data class RelayMessage(
    val type: String, // HANDSHAKE, HANDSHAKE_RESPONSE, CONFIG_UPDATE, TRANSACTION_DATA, RESULT_UPDATE, HEARTBEAT
    val payload: String, // JSON string of the object
    val senderId: String = ""
) {
    companion object {
        const val TYPE_HANDSHAKE = "HANDSHAKE"
        const val TYPE_HANDSHAKE_RESPONSE = "HANDSHAKE_RESPONSE"
        const val TYPE_CONFIG_UPDATE = "CONFIG_UPDATE"
        const val TYPE_TRANSACTION_DATA = "TRANSACTION_DATA"
        const val TYPE_RESULT_UPDATE = "RESULT_UPDATE"
        const val TYPE_HEARTBEAT = "HEARTBEAT"
    }
}

data class HandshakePayload(
    val deviceName: String,
    val model: String,
    val androidVersion: String,
    val autodialId: String
)

data class ConfigUpdatePayload(
    val relayAmounts: String,
    val automationEnabled: Boolean
)

data class ResultUpdatePayload(
    val transactionCode: String,
    val status: String,
    val finalResponse: String
)
