package com.example.autodial.util

import android.content.Context
import android.util.Log
import com.example.autodial.data.AppDatabase
import com.example.autodial.data.RelayDevice
import com.example.autodial.data.Transaction
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * CONNECT Main Device Server
 * Orchestrates multi-device relay network with smart routing and real-time sync.
 */
object RelayServer {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val clientHandlers = ConcurrentHashMap<String, ClientHandler>()
    private var isRunning = false
    
    private val _activeDevices = MutableStateFlow<List<RelayDevice>>(emptyList())
    val activeDevices = _activeDevices.asStateFlow()

    fun startServer(context: Context) {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(8888)
                Log.d("RelayServer", "Server started on port 8888")
                
                // Active monitoring of devices
                launch { monitorDevices(context) }

                while (isRunning) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        socket.soTimeout = 300000 // 5 minutes timeout to handle jitter/backgrounding
                        val handler = ClientHandler(socket, context)
                        scope.launch { handler.run() }
                    }
                }
            } catch (e: Exception) {
                Log.e("RelayServer", "Server error", e)
            }
        }
    }

    private suspend fun monitorDevices(context: Context) {
        while (isRunning) {
            delay(30000) // Check every 30 seconds
            val now = System.currentTimeMillis()
            val db = AppDatabase.getDatabase(context)
            val all = db.relayDeviceDao().getAllDevicesFlow().first()
            
            all.forEach { device ->
                // Mark as Inactive only after 10 minutes of silence
                if (device.status == "ACTIVE" && now - device.lastSeen > 600000) {
                    db.relayDeviceDao().updateConnectionStatus(device.id, false, "INACTIVE")
                }
            }
            refreshActiveDevices(context)
        }
    }

    fun stopServer() {
        isRunning = false
        serverSocket?.close()
        clientHandlers.values.forEach { it.stop() }
        clientHandlers.clear()
        _activeDevices.value = emptyList()
    }

    private class ClientHandler(val socket: Socket, val context: Context) {
        private var writer: OutputStreamWriter? = null
        private var reader: BufferedReader? = null
        private var deviceId: String? = null
        private var isAlive = true

        suspend fun run() {
            try {
                writer = OutputStreamWriter(socket.getOutputStream())
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                
                while (isAlive) {
                    val line = withContext(Dispatchers.IO) { reader?.readLine() } ?: break
                    val msg = Gson().fromJson(line, RelayMessage::class.java)
                    handleMessage(msg)
                }
            } catch (e: Exception) {
                Log.e("RelayServer", "Handler error for $deviceId", e)
            } finally {
                cleanup()
            }
        }

        private suspend fun handleMessage(msg: RelayMessage) {
            when (msg.type) {
                RelayMessage.TYPE_HANDSHAKE -> {
                    val handshake = Gson().fromJson(msg.payload, HandshakePayload::class.java)
                    deviceId = handshake.autodialId
                    clientHandlers[handshake.autodialId] = this
                    
                    val db = AppDatabase.getDatabase(context)
                    val existing = db.relayDeviceDao().getDeviceById(handshake.autodialId)
                    if (existing == null) {
                        db.relayDeviceDao().insertDevice(RelayDevice(
                            deviceName = handshake.deviceName,
                            role = "LINKED",
                            connectionType = "HOTSPOT",
                            ipAddress = socket.inetAddress.hostAddress ?: "",
                            model = handshake.model,
                            androidVersion = handshake.androidVersion,
                            autodialId = handshake.autodialId,
                            isConnected = true,
                            status = "ACTIVE",
                            lastSeen = System.currentTimeMillis()
                        ))
                    } else {
                        db.relayDeviceDao().updateDevice(existing.copy(
                            ipAddress = socket.inetAddress.hostAddress ?: "",
                            isConnected = true,
                            status = "ACTIVE",
                            lastSeen = System.currentTimeMillis()
                        ))
                    }
                    
                    // Send Handshake Response
                    val response = HandshakePayload(
                        deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL,
                        model = android.os.Build.MODEL,
                        androidVersion = android.os.Build.VERSION.RELEASE,
                        autodialId = PreferenceManager(context).getUniqueDeviceId()
                    )
                    send(RelayMessage(RelayMessage.TYPE_HANDSHAKE_RESPONSE, Gson().toJson(response)))
                    
                    refreshActiveDevices(context)
                }
                RelayMessage.TYPE_RESULT_UPDATE -> {
                    val result = Gson().fromJson(msg.payload, ResultUpdatePayload::class.java)
                    val db = AppDatabase.getDatabase(context)
                    db.transactionDao().getTransactionByCode(result.transactionCode)?.let { trans ->
                        val success = result.status == Transaction.STATUS_SUCCESS
                        db.transactionDao().updateTransaction(trans.copy(
                            status = result.status,
                            finalResponse = result.finalResponse
                        ))
                        
                        // Update device stats
                        deviceId?.let { id ->
                            val device = db.relayDeviceDao().getDeviceById(id)
                            device?.let { d ->
                                db.relayDeviceDao().updateDevice(d.copy(
                                    totalReceived = d.totalReceived + 1,
                                    totalCompleted = d.totalCompleted + if (success) 1 else 0,
                                    totalFailed = d.totalFailed + if (success) 0 else 1,
                                    lastSeen = System.currentTimeMillis()
                                ))
                            }
                        }
                    }
                    refreshActiveDevices(context)
                }
                RelayMessage.TYPE_HEARTBEAT -> {
                    deviceId?.let { id ->
                        val db = AppDatabase.getDatabase(context)
                        db.relayDeviceDao().getDeviceById(id)?.let { d ->
                            db.relayDeviceDao().updateDevice(d.copy(lastSeen = System.currentTimeMillis(), status = "ACTIVE", isConnected = true))
                        }
                    }
                }
            }
        }

        fun send(msg: RelayMessage) {
            scope.launch {
                try {
                    val json = Gson().toJson(msg)
                    withContext(Dispatchers.IO) {
                        writer?.write(json + "\n")
                        writer?.flush()
                    }
                } catch (e: Exception) {
                    Log.e("RelayServer", "Send failed to $deviceId", e)
                    cleanup()
                }
            }
        }

        private fun cleanup() {
            isAlive = false
            deviceId?.let { 
                clientHandlers.remove(it)
                scope.launch {
                    val db = AppDatabase.getDatabase(context)
                    db.relayDeviceDao().getDeviceById(it)?.let { d -> 
                        db.relayDeviceDao().updateConnectionStatus(d.id, false, "DISCONNECTED") 
                        refreshActiveDevices(context)
                    }
                }
            }
            try { socket.close() } catch (_: Exception) {}
        }

        fun stop() { isAlive = false; cleanup() }
    }

    private fun refreshActiveDevices(context: Context) {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val all = db.relayDeviceDao().getAllDevicesFlow().first()
            _activeDevices.value = all
        }
    }

    /**
     * Least Busy + Availability Check Routing
     */
    suspend fun routeTransaction(transaction: Transaction, context: Context): Boolean {
        val db = AppDatabase.getDatabase(context)
        val allDevices = db.relayDeviceDao().getAllDevicesFlow().first()
        
        // 1. Filter eligible processors
        val amountStr = transaction.amount.toInt().toString()
        val eligibleDevices = allDevices.filter { device ->
            device.status == "ACTIVE" && device.automationEnabled &&
            (device.relayAmounts.isEmpty() || device.relayAmounts.split(",").map { it.trim() }.contains(amountStr))
        }
        
        if (eligibleDevices.isEmpty()) return false
        
        // 2. Select Least Busy: min(received - completed)
        val target = eligibleDevices.minByOrNull { it.totalReceived - it.totalCompleted } ?: return false
        
        // 3. Dispatch
        val handler = clientHandlers[target.autodialId]
        if (handler != null) {
            handler.send(RelayMessage(RelayMessage.TYPE_TRANSACTION_DATA, Gson().toJson(transaction)))
            Log.d("RelayServer", "Routed KSh ${transaction.amount} to ${target.deviceName}")
            return true
        }
        
        return false
    }

    /**
     * Broadcast config updates (e.g. amounts changed on Main)
     */
    fun pushConfigUpdate(deviceId: String, amounts: String, enabled: Boolean) {
        val handler = clientHandlers[deviceId]
        if (handler != null) {
            val payload = ConfigUpdatePayload(amounts, enabled)
            handler.send(RelayMessage(RelayMessage.TYPE_CONFIG_UPDATE, Gson().toJson(payload)))
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("RelayServer", "IP retrieval error", ex)
        }
        return "127.0.0.1"
    }
}
