package com.example.autodial.util

import android.content.Context
import android.util.Log
import com.example.autodial.data.AppDatabase
import com.example.autodial.data.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object WebServerManager {
    private const val TAG = "WebServerManager"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active session tokens mapped to creation timestamp
    private val activeTokens = ConcurrentHashMap<String, Long>()

    fun startServer(context: Context) {
        val prefManager = PreferenceManager(context)
        if (!prefManager.isWebControlEnabled()) {
            Log.d(TAG, "Web control is disabled in settings.")
            return
        }

        if (isRunning) return

        val port = prefManager.getWebControlPort()
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.i(TAG, "Web Control HTTP Server listening on port $port")

                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleConnection(socket, context) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Web Control Server error on port $port", e)
                }
            }
        }
    }

    fun stopServer() {
        if (!isRunning) return
        try {
            isRunning = false
            serverSocket?.close()
            serverSocket = null
            activeTokens.clear()
            Log.i(TAG, "Web Control Server stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping web server", e)
        }
    }

    fun isServerRunning(): Boolean = isRunning

    private suspend fun handleConnection(socket: Socket, context: Context) {
        try {
            socket.soTimeout = 8000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

            val requestLine = reader.readLine() ?: run { socket.close(); return }
            val parts = requestLine.split(" ")
            if (parts.size < 2) { socket.close(); return }

            val method = parts[0].uppercase()
            val fullPath = parts[1]

            val headers = HashMap<String, String>()
            var contentLength = 0
            while (true) {
                val currentLine = reader.readLine() ?: break
                if (currentLine.isEmpty()) break
                val headerParts = currentLine.split(":", limit = 2)
                if (headerParts.size == 2) {
                    val name = headerParts[0].trim().lowercase()
                    val value = headerParts[1].trim()
                    headers[name] = value
                    if (name == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            var body = ""
            if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(charBuffer, read, contentLength - read)
                    if (r <= 0) break
                    read += r
                }
                body = String(charBuffer, 0, read)
            }

            routeRequest(method, fullPath, headers, body, output, context)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling HTTP request", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun routeRequest(
        method: String,
        fullPath: String,
        headers: Map<String, String>,
        body: String,
        output: OutputStream,
        context: Context
    ) {
        val prefManager = PreferenceManager(context)
        val path = fullPath.substringBefore("?")
        val queryParams = parseQueryParams(fullPath.substringAfter("?", ""))

        when {
            method == "GET" && path == "/" -> {
                sendHtmlResponse(output, 200, WebDashboardAssets.getIndexHtml())
            }
            method == "POST" && path == "/api/login" -> {
                val json = parseJsonObject(body)
                val email = json.get("email")?.asString?.trim() ?: ""
                val password = json.get("password")?.asString?.trim() ?: ""

                val registeredEmail = prefManager.getRegisteredEmail().trim()
                val firebaseUser = FirebaseAuth.getInstance().currentUser

                var authenticated = false
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (registeredEmail.isEmpty() || email.equals(registeredEmail, ignoreCase = true) || email.equals(firebaseUser?.email ?: "", ignoreCase = true)) {
                        authenticated = true
                    }
                }

                val resp = JsonObject()
                if (authenticated) {
                    val token = UUID.randomUUID().toString()
                    activeTokens[token] = System.currentTimeMillis()
                    resp.addProperty("success", true)
                    resp.addProperty("token", token)
                    sendJsonResponse(output, 200, resp.toString())
                } else {
                    resp.addProperty("success", false)
                    resp.addProperty("message", "Invalid email or password.")
                    sendJsonResponse(output, 401, resp.toString())
                }
            }
            method == "GET" && (path == "/api/status" || path == "/api/dashboard") -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val db = AppDatabase.getDatabase(context)
                val allTxns = db.transactionDao().getAllTransactions().first()

                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val todayTxns = allTxns.filter { it.date >= startOfDay }
                val queuedCount = allTxns.count { it.status == "WAITING" || it.status == "PROCESSING" }

                val resp = JsonObject()
                resp.addProperty("agentId", prefManager.getAgentId().ifBlank { "001" })
                resp.addProperty("deviceId", prefManager.getPhysicalDeviceId())
                resp.addProperty("sim1Airtime", prefManager.getAirtimeBalance(0))
                resp.addProperty("sim2Airtime", prefManager.getAirtimeBalance(1))
                resp.addProperty("automationEnabled", prefManager.isAutomationEnabled())
                resp.addProperty("queuedCount", queuedCount)
                resp.addProperty("todayCount", todayTxns.size)

                val txnsArray = JsonArray()
                todayTxns.take(20).forEach { t ->
                    val o = JsonObject()
                    o.addProperty("id", t.id)
                    o.addProperty("date", t.date)
                    o.addProperty("customerNumber", t.customerNumber)
                    o.addProperty("offerName", t.offerName)
                    o.addProperty("transactionCode", t.transactionCode)
                    o.addProperty("amount", t.amount)
                    o.addProperty("status", t.status)
                    txnsArray.add(o)
                }
                resp.add("recentTransactions", txnsArray)

                sendJsonResponse(output, 200, resp.toString())
            }
            method == "GET" && path == "/api/transactions" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val filter = queryParams["filter"]?.uppercase() ?: "ALL"
                val query = queryParams["q"]?.lowercase() ?: ""

                val db = AppDatabase.getDatabase(context)
                var list = db.transactionDao().getAllTransactions().first()

                if (filter != "ALL") {
                    if (filter == "WAITING") {
                        list = list.filter { it.status == "WAITING" || it.status == "PROCESSING" || it.status == "SCHEDULED" }
                    } else {
                        list = list.filter { it.status.equals(filter, ignoreCase = true) }
                    }
                }

                if (query.isNotEmpty()) {
                    list = list.filter {
                        it.customerNumber.lowercase().contains(query) ||
                                it.transactionCode.lowercase().contains(query) ||
                                it.offerName.lowercase().contains(query)
                    }
                }

                val resp = JsonObject()
                val txnsArray = JsonArray()
                list.take(200).forEach { t ->
                    val o = JsonObject()
                    o.addProperty("id", t.id)
                    o.addProperty("date", t.date)
                    o.addProperty("customerNumber", t.customerNumber)
                    o.addProperty("offerName", t.offerName)
                    o.addProperty("transactionCode", t.transactionCode)
                    o.addProperty("amount", t.amount)
                    o.addProperty("status", t.status)
                    txnsArray.add(o)
                }
                resp.add("transactions", txnsArray)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/transactions/retry" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val txnId = json.get("id")?.asInt ?: 0

                val db = AppDatabase.getDatabase(context)
                val txn = db.transactionDao().getTransactionById(txnId)
                if (txn != null) {
                    val updated = txn.copy(status = "WAITING", retryCount = txn.retryCount + 1)
                    db.transactionDao().updateTransaction(updated)
                    TransactionQueueManager.forceResetLocks()
                }

                val resp = JsonObject()
                resp.addProperty("success", true)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/transactions/cancel" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val txnId = json.get("id")?.asInt ?: 0

                val db = AppDatabase.getDatabase(context)
                val txn = db.transactionDao().getTransactionById(txnId)
                if (txn != null) {
                    val updated = txn.copy(status = "CANCELLED")
                    db.transactionDao().updateTransaction(updated)
                }

                val resp = JsonObject()
                resp.addProperty("success", true)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/transactions/delete" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val txnId = json.get("id")?.asInt ?: 0

                val db = AppDatabase.getDatabase(context)
                db.transactionDao().deleteTransactionById(txnId)

                val resp = JsonObject()
                resp.addProperty("success", true)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "GET" && path == "/api/offers" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val db = AppDatabase.getDatabase(context)
                val offers = db.offerDao().getAllOffers().first()

                val resp = JsonObject()
                val array = JsonArray()
                offers.forEach { offer ->
                    val o = JsonObject()
                    o.addProperty("id", offer.id)
                    o.addProperty("name", offer.name)
                    o.addProperty("amount", offer.amount)
                    o.addProperty("safPrice", offer.safPrice)
                    o.addProperty("mode", offer.ussdMode)
                    o.addProperty("isEnabled", offer.status == 1 && offer.isEnabled)
                    array.add(o)
                }
                resp.add("offers", array)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/offers/toggle" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val offerId = json.get("id")?.asInt ?: 0
                val enabled = json.get("enabled")?.asBoolean ?: false

                val db = AppDatabase.getDatabase(context)
                val allOffers = db.offerDao().getAllOffers().first()
                val target = allOffers.find { it.id == offerId }
                if (target != null) {
                    val updated = target.copy(status = if (enabled) 1 else 0, isEnabled = enabled)
                    db.offerDao().updateOffer(updated)
                }

                val resp = JsonObject()
                resp.addProperty("success", true)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "GET" && path == "/api/clients" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val query = queryParams["q"]?.lowercase() ?: ""
                val db = AppDatabase.getDatabase(context)
                var clients = db.clientDao().getAllClients().first()

                if (query.isNotEmpty()) {
                    clients = clients.filter {
                        it.name.lowercase().contains(query) || it.phoneNumber.lowercase().contains(query)
                    }
                }

                val resp = JsonObject()
                val array = JsonArray()
                clients.take(150).forEach { c ->
                    val o = JsonObject()
                    o.addProperty("id", c.id)
                    o.addProperty("name", c.name)
                    o.addProperty("phoneNumber", c.phoneNumber)
                    o.addProperty("totalPurchases", c.totalPurchases)
                    o.addProperty("totalSpent", c.totalSpent)
                    o.addProperty("outstandingDebt", c.outstandingDebt)
                    array.add(o)
                }
                resp.add("clients", array)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "GET" && path == "/api/analytics" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val db = AppDatabase.getDatabase(context)
                val allTxns = db.transactionDao().getAllTransactions().first()

                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val trendArray = JsonArray()

                var totalCommission = 0.0
                var todayAirtime = 0.0

                val startOfToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // 7 Days Trend Calculation
                for (i in 6 downTo 0) {
                    val dayCal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val dayStart = dayCal.timeInMillis
                    val dayEnd = dayStart + (24 * 60 * 60 * 1000L)

                    val dayTxns = allTxns.filter { it.date in dayStart until dayEnd && it.status == Transaction.STATUS_SUCCESS }
                    val daySum = dayTxns.sumOf { it.amount }

                    val item = JsonObject()
                    item.addProperty("day", dayFormat.format(dayCal.time))
                    item.addProperty("value", daySum)
                    trendArray.add(item)

                    totalCommission += dayTxns.sumOf { it.commissionEarned }
                    if (dayStart == startOfToday) {
                        todayAirtime = daySum
                    }
                }

                val totalCount = allTxns.size.toDouble().coerceAtLeast(1.0)
                val successCount = allTxns.count { it.status == Transaction.STATUS_SUCCESS }
                val rate = ((successCount / totalCount) * 100).toInt()

                val resp = JsonObject()
                resp.addProperty("totalCommission", String.format(Locale.US, "%.2f", totalCommission))
                resp.addProperty("todayAirtime", String.format(Locale.US, "%.2f", todayAirtime))
                resp.addProperty("successRate", rate)
                resp.add("dailyTrend", trendArray)

                sendJsonResponse(output, 200, resp.toString())
            }
            method == "GET" && path == "/api/notes" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val jsonNotes = prefManager.getTerminalNotes()
                val resp = JsonObject()
                resp.add("notes", parseJsonArray(jsonNotes))
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/notes" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val noteText = json.get("note")?.asString?.trim() ?: ""

                if (noteText.isNotEmpty()) {
                    val rawJson = prefManager.getTerminalNotes()
                    val notesArray = parseJsonArray(rawJson)

                    val newNote = JsonObject()
                    newNote.addProperty("id", System.currentTimeMillis())
                    newNote.addProperty("text", noteText)
                    newNote.addProperty("timestamp", System.currentTimeMillis())
                    notesArray.add(newNote)

                    prefManager.setTerminalNotes(notesArray.toString())

                    val resp = JsonObject()
                    resp.addProperty("success", true)
                    sendJsonResponse(output, 200, resp.toString())
                } else {
                    sendJsonResponse(output, 400, "{\"error\":\"Empty note text\"}")
                }
            }
            method == "POST" && path == "/api/notes/delete" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val noteId = json.get("id")?.asLong ?: 0L

                val rawJson = prefManager.getTerminalNotes()
                val notesArray = parseJsonArray(rawJson)

                val updatedArray = JsonArray()
                notesArray.forEach { elem ->
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        if (obj.get("id")?.asLong != noteId) {
                            updatedArray.add(obj)
                        }
                    }
                }

                prefManager.setTerminalNotes(updatedArray.toString())
                val resp = JsonObject()
                resp.addProperty("success", true)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/control/automation" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val json = parseJsonObject(body)
                val enabled = json.get("enabled")?.asBoolean ?: false
                prefManager.setAutomationEnabled(enabled)

                val resp = JsonObject()
                resp.addProperty("success", true)
                resp.addProperty("automationEnabled", enabled)
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/control/sync" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                val deviceId = prefManager.getPhysicalDeviceId()
                val db = AppDatabase.getDatabase(context)

                scope.launch {
                    try {
                        val allTxns = db.transactionDao().getAllTransactions().first()
                        CloudStore.uploadTransactionsAndPrune(deviceId, db.createRepository(), allTxns)
                    } catch (e: Exception) {
                        Log.e(TAG, "Web triggered sync failed", e)
                    }
                }

                val resp = JsonObject()
                resp.addProperty("success", true)
                resp.addProperty("message", "Sync triggered")
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/control/clear-queue-flag" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                TransactionQueueManager.forceResetLocks()

                val resp = JsonObject()
                resp.addProperty("success", true)
                resp.addProperty("message", "Queue locks cleared")
                sendJsonResponse(output, 200, resp.toString())
            }
            method == "POST" && path == "/api/control/emergency-stop" -> {
                if (!validateAuth(headers)) {
                    sendJsonResponse(output, 401, "{\"error\":\"Unauthorized\"}")
                    return
                }

                TransactionQueueManager.isQueueOnHold = true
                TransactionQueueManager.forceResetLocks()

                val resp = JsonObject()
                resp.addProperty("success", true)
                resp.addProperty("message", "Emergency stop engaged")
                sendJsonResponse(output, 200, resp.toString())
            }
            else -> {
                sendJsonResponse(output, 404, "{\"error\":\"Endpoint not found\"}")
            }
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = HashMap<String, String>()
        if (queryString.isBlank()) return map
        try {
            val pairs = queryString.split("&")
            for (pair in pairs) {
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    map[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse query params", e)
        }
        return map
    }

    private fun validateAuth(headers: Map<String, String>): Boolean {
        val authHeader = headers["authorization"] ?: return false
        if (!authHeader.lowercase().startsWith("bearer ")) return false
        val token = authHeader.substring(7).trim()
        val creationTime = activeTokens[token] ?: return false

        // 24 hours token validity
        if (System.currentTimeMillis() - creationTime > 24 * 60 * 60 * 1000L) {
            activeTokens.remove(token)
            return false
        }
        return true
    }

    private fun sendHtmlResponse(output: OutputStream, statusCode: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 401) "Unauthorized" else if (statusCode == 400) "Bad Request" else "Error"
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
    }

    private fun parseJsonObject(str: String): JsonObject {
        return try {
            JsonParser.parseString(str).asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }
    }

    private fun parseJsonArray(str: String): JsonArray {
        return try {
            JsonParser.parseString(str).asJsonArray
        } catch (e: Exception) {
            JsonArray()
        }
    }
}
