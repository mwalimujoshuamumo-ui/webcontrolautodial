package com.example.autodial.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.autodial.data.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * CONNECT Linked Device Client
 * Syncs with Main device and reports local execution results.
 */
object RelayClient {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var isRunning = false
    
    private var heartbeatJob: Job? = null
    private var lastIp: String? = null
    
    private val _connectedMainName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val connectedMainName = _connectedMainName.asStateFlow()

    fun connectToMain(ip: String, context: Context) {
        lastIp = ip
        if (isRunning) stopConnection()
        isRunning = true
        scope.launch {
            while (isRunning) {
                try {
                    Log.d("RelayClient", "Connecting to Main: $ip")
                    val socket = Socket(ip, 8888)
                    socket.soTimeout = 300000 // 5 minutes timeout
                    RelayClient.socket = socket
                    writer = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    
                    val pref = PreferenceManager(context)
                    pref.setLastConnectedIp(ip)
                    
                    sendHandshake(context)
                    startHeartbeat()
                    
                    while (isRunning) {
                        val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                        val msg = Gson().fromJson(line, RelayMessage::class.java)
                        handleMessage(msg, context)
                    }
                } catch (e: Exception) {
                    Log.e("RelayClient", "Connection dropped. Retrying in 5s...", e)
                    stopHeartbeat()
                    delay(5000)
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isRunning) {
                delay(10000)
                sendMessage(RelayMessage(RelayMessage.TYPE_HEARTBEAT, ""))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendHandshake(context: Context) {
        val pref = PreferenceManager(context)
        val payload = HandshakePayload(
            deviceName = Build.MANUFACTURER + " " + Build.MODEL,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            autodialId = pref.getUniqueDeviceId()
        )
        sendMessage(RelayMessage(RelayMessage.TYPE_HANDSHAKE, Gson().toJson(payload)))
    }

    private fun handleMessage(msg: RelayMessage, context: Context) {
        when (msg.type) {
            RelayMessage.TYPE_HANDSHAKE_RESPONSE -> {
                val handshake = Gson().fromJson(msg.payload, HandshakePayload::class.java)
                _connectedMainName.value = handshake.deviceName
                Log.d("RelayClient", "Linked to Main: ${handshake.deviceName}")
            }
            RelayMessage.TYPE_TRANSACTION_DATA -> {
                val transaction = Gson().fromJson(msg.payload, Transaction::class.java)
                processIncomingTransaction(transaction, context)
            }
            RelayMessage.TYPE_CONFIG_UPDATE -> {
                val payload = Gson().fromJson(msg.payload, ConfigUpdatePayload::class.java)
                val pref = PreferenceManager(context)
                pref.setRelayAmounts(payload.relayAmounts)
                pref.setAutomationEnabled(payload.automationEnabled)
                Log.d("RelayClient", "Sync Complete: Amounts=${payload.relayAmounts}")
            }
        }
    }

    private fun processIncomingTransaction(transaction: Transaction, context: Context) {
        scope.launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val repository = database.createRepository()
                
                // DEDUPLICATION: Check if this transaction code already exists locally
                if (transaction.transactionCode.isNotEmpty()) {
                    val existing = repository.getTransactionByCode(transaction.transactionCode)
                    if (existing != null) {
                        Log.d("RelayClient", "Duplicate transaction detected (Code: ${transaction.transactionCode}). Skipping relay execution.")
                        return@launch
                    }
                }

                // Client Name Extraction for incoming relays
                val details = MpesaParser.parse(transaction.rawMessage)
                val clientName = details?.sender?.uppercase() ?: "RELAY"

                // 1. Local Offer Matching - Ensures Linked device uses its own SIM and USSD settings
                // Strictly use amount to match the local offer and its associated maps
                val matchingOffer = repository.findMatchingOfferIgnoringStatus(transaction.amount)
                
                if (matchingOffer == null) {
                    Log.w("RelayClient", "No local offer found for relayed amount: ${transaction.amount}. Transaction will be recorded as Unmatched.")
                }

                val localTrans = transaction.copy(
                    id = 0,
                    status = Transaction.STATUS_WAITING,
                    offerId = matchingOffer?.id ?: 0,
                    offerName = matchingOffer?.name ?: "Relayed: No Match (KSh ${transaction.amount})",
                    senderName = "RELAY: $clientName",
                    date = System.currentTimeMillis()
                )
                
                val insertedId = database.transactionDao().insertTransaction(localTrans)
                Log.d("RelayClient", "Relayed task (KSh ${transaction.amount}) from Main added to queue (ID: $insertedId). Matched local offer: ${matchingOffer?.name ?: "NONE"}")
                
                // Sync Client for contacts acquire even on relay
                ClientManager.upsertClientFromTransaction(context, localTrans.copy(id = insertedId.toInt()))

                // 2. Add to queue for sequential processing
                if (TransactionQueueManager.isProcessing()) {
                    Log.d("RelayClient", "Relayed transaction queued. Waiting for active session to finish.")
                }
                TransactionQueueManager.processNext(context, immediate = true)
            } catch (e: Exception) {
                Log.e("RelayClient", "Local injection failed", e)
            }
        }
    }

    fun reportResult(transactionCode: String, status: String, response: String) {
        val payload = ResultUpdatePayload(transactionCode, status, response)
        sendMessage(RelayMessage(RelayMessage.TYPE_RESULT_UPDATE, Gson().toJson(payload)))
    }

    private fun sendMessage(msg: RelayMessage) {
        if (!isRunning) return
        scope.launch {
            try {
                val json = Gson().toJson(msg)
                withContext(Dispatchers.IO) {
                    writer?.write(json + "\n")
                    writer?.flush()
                }
            } catch (e: Exception) {
                Log.e("RelayClient", "Send failed", e)
            }
        }
    }

    fun stopConnection() {
        isRunning = false
        stopHeartbeat()
        scope.launch {
            socket?.close()
            socket = null
        }
    }
}
