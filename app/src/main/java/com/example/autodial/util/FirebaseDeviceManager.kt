package com.example.autodial.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.autodial.ui.DashboardStats
import com.example.autodial.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Manages Firebase Firestore device registration and heartbeat.
 * Tracks device health, metadata, and status using a permanent physicalDeviceId.
 */
class FirebaseDeviceManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val repository: MainRepository
) {
    companion object {
        @Volatile
        private var instance: FirebaseDeviceManager? = null

        fun getInstance(context: Context, preferenceManager: PreferenceManager, repository: MainRepository): FirebaseDeviceManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseDeviceManager(context, preferenceManager, repository).also { instance = it }
            }
        }
        
        fun getExistingInstance(): FirebaseDeviceManager? = instance
    }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var transactionSyncJob: Job? = null
    private var sessionSyncJob: Job? = null
    private var offersSyncJob: Job? = null
    private var settingsListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    private var lastStats: DashboardStats? = null
    private var isSyncInProgress = false

    init {
        try {
            setupConnectivityListener()
        } catch (e: Exception) {
            Log.e("FirebaseDeviceManager", "Connectivity listener init failed", e)
        }
    }

    private fun setupConnectivityListener() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i("FirebaseDeviceManager", "Internet returned. Triggering sync.")
                triggerSync()
            }
        })
    }

    /**
     * Registers the device and starts the periodic heartbeat.
     */
    fun start() {
        Log.i("FirebaseDeviceManager", "Starting Firebase device registration cycle")
        scope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null && (currentUser.isEmailVerified || currentUser.isAnonymous)) {
                    // registerOrUpdate(online = true)
                    startHeartbeat()
                    startTransactionObserver()
                    startSessionObserver()
                    startSettingsListener()
                } else {
                    Log.d("FirebaseDeviceManager", "Start deferred: User not fully authenticated or verified")
                }
            } catch (e: Exception) {
                Log.e("FirebaseDeviceManager", "Registration cycle crash prevented", e)
            }
        }
    }

    /**
     * Signs up a new user with email and password.
     */
    suspend fun signUp(name: String, phone: String, email: String, password: String): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                user?.sendEmailVerification()
                
                // Update profile name
                val profileUpdates = userProfileChangeRequest {
                    displayName = name
                }
                user?.updateProfile(profileUpdates)
                
                val now = Date()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

                preferenceManager.setUserName(name)
                preferenceManager.setAgentPhone(phone)
                preferenceManager.setRegisteredEmail(email)
                preferenceManager.setRegistrationDate(dateStr)
                preferenceManager.setRegistrationTime(timeStr)
                preferenceManager.setUserRegistered(true)
                
                registerOrUpdate(online = true)
                continuation.resume(Result.success(true))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Signs in an existing user.
     */
    suspend fun signIn(email: String, password: String): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                preferenceManager.setUserName(user?.displayName ?: "")
                preferenceManager.setRegisteredEmail(email)
                preferenceManager.setUserRegistered(true)
                
                registerOrUpdate(online = true)
                continuation.resume(Result.success(true))
            }
            .addOnFailureListener { e ->
                continuation.resume(Result.failure(e))
            }
    }

    /**
     * Sends a password reset email.
     */
    fun sendPasswordReset(email: String, onComplete: (Boolean) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun isEmailVerified(): Boolean {
        try {
            auth.currentUser?.reload()
            return auth.currentUser?.isEmailVerified == true
        } catch (e: Exception) {
            Log.e("FirebaseDeviceManager", "Email verification check failed", e)
            return false
        }
    }

    fun resendVerification() {
        auth.currentUser?.sendEmailVerification()
    }

    suspend fun getIdToken(forceRefresh: Boolean = false): String? = suspendCancellableCoroutine { continuation ->
        val user = auth.currentUser
        if (user == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        user.getIdToken(forceRefresh).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result?.token)
            } else {
                Log.e("FirebaseDeviceManager", "Failed to get ID token", task.exception)
                continuation.resume(null)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        heartbeatJob?.cancel()
        transactionSyncJob?.cancel()
        sessionSyncJob?.cancel()
        settingsListener?.remove()
        preferenceManager.setUserRegistered(false)
        setOffline()
    }

    /**
     * Ensures the user is signed in to Firebase anonymously if not already signed in.
     * Legacy support for anonymous access if required.
     */
    suspend fun ensureAuthenticated(): Boolean = suspendCancellableCoroutine { continuation ->
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d("FirebaseDeviceManager", "Already authenticated as: ${currentUser.uid}")
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        Log.i("FirebaseDeviceManager", "Performing anonymous sign-in...")
        val task = auth.signInAnonymously()
        
        task.addOnSuccessListener { result ->
            if (continuation.isActive) {
                Log.d("FirebaseDeviceManager", "Anonymous sign-in successful: ${result.user?.uid}")
                continuation.resume(true)
            }
        }.addOnFailureListener { e ->
            if (continuation.isActive) {
                Log.e("FirebaseDeviceManager", "Anonymous sign-in failed", e)
                continuation.resume(false)
            }
        }
    }

    /**
     * Creates or updates the device document in Firestore using the permanent physicalDeviceId.
     */
    /**
     * Creates a one-time token backup code and moves tokens to cloud.
     */
    fun createTokenBackup(amount: Int, unlimitedExpiry: Long, onResult: (Result<String>) -> Unit) {
        val physicalId = preferenceManager.getPhysicalDeviceId()
        val code = "BK-" + java.util.UUID.randomUUID().toString().take(6).uppercase()
        val remainingTimeMs = maxOf(0L, unlimitedExpiry - System.currentTimeMillis())

        // Save locally first for instant, non-blocking fallback execution
        preferenceManager.saveLocalBackup(code, amount, unlimitedExpiry)
        preferenceManager.setTokens(0)
        preferenceManager.setUnlimitedExpiry(0)

        // Return backup code IMMEDIATELY (0ms delay to UI)
        onResult(Result.success(code))

        // Asynchronously update Firestore in background without blocking UI thread
        val backupRef = db.collection("token_backups").document(code)
        val data = hashMapOf(
            "amount" to amount,
            "unlimitedExpiry" to unlimitedExpiry,
            "remainingTimeMs" to remainingTimeMs,
            "code" to code,
            "deviceId" to physicalId,
            "restored" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        backupRef.set(data)
            .addOnSuccessListener {
                val deviceUpdates = mapOf<String, Any>(
                    "tokenBalance" to 0,
                    "unlimitedExpiry" to 0L,
                    "isUnlimited" to false
                )
                db.collection("devices").document(physicalId)
                    .set(deviceUpdates, SetOptions.merge())
            }
            .addOnFailureListener { e ->
                Log.w("FirebaseDeviceManager", "Cloud backup write failed (local backup active): ${e.message}")
            }
    }

    fun uploadOfferBackup(code: String, json: String, onComplete: (Boolean) -> Unit) {
        val physicalId = preferenceManager.getPhysicalDeviceId()
        val data = hashMapOf(
            "code" to code,
            "deviceId" to physicalId,
            "content" to json,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours expiry
        )
        
        db.collection("offer_backups").document(code)
            .set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun downloadOfferBackup(code: String, onResult: (String?) -> Unit) {
        db.collection("offer_backups").document(code).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val expiresAt = doc.getLong("expiresAt") ?: 0L
                    if (System.currentTimeMillis() < expiresAt) {
                        onResult(doc.getString("content"))
                    } else {
                        onResult(null) // Expired
                    }
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    /**
     * Restores tokens from a one-time backup code.
     * Returns Pair(units, durationMs)
     */
    fun restoreFromBackup(code: String, onResult: (Result<Pair<Int, Long>>) -> Unit) {
        val backupRef = db.collection("token_backups").document(code)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(backupRef)
            if (!snapshot.exists()) {
                throw Exception("Invalid backup code")
            }

            val isRestored = snapshot.getBoolean("restored") == true
            if (isRestored) {
                throw Exception("This code has already been used")
            }

            val amount = (snapshot.getLong("amount") ?: 0L).toInt()
            val storedRemainingTimeMs = snapshot.getLong("remainingTimeMs")
            val durationMs = if (storedRemainingTimeMs != null) {
                maxOf(0L, storedRemainingTimeMs)
            } else {
                val expiry = snapshot.getLong("unlimitedExpiry") ?: 0L
                maxOf(0L, expiry - System.currentTimeMillis())
            }

            transaction.update(backupRef, "restored", true)
            transaction.update(backupRef, "restoredAt", FieldValue.serverTimestamp())
            transaction.update(backupRef, "restoredBy", preferenceManager.getPhysicalDeviceId())

            Pair(amount, durationMs)
        }.addOnSuccessListener { result ->
            onResult(Result.success(result))
        }.addOnFailureListener { cloudEx ->
            val localBackup = preferenceManager.getLocalBackup(code)
            if (localBackup != null) {
                preferenceManager.markLocalBackupUsed(code)
                val durationMs = maxOf(0L, localBackup.second - System.currentTimeMillis())
                onResult(Result.success(Pair(localBackup.first, durationMs)))
            } else {
                onResult(Result.failure(cloudEx))
            }
        }
    }

    /**
     * Forcefully fetches the latest authoritative balance from Firestore.
     */
    fun refreshAuthoritativeBalance(onComplete: (Int, Long) -> Unit = { _, _ -> }) {
        val physicalId = preferenceManager.getPhysicalDeviceId()
        db.collection("devices").document(physicalId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val tokens = (doc.data?.get("tokenBalance") as? Long)?.toInt() ?: 0
                    val expiry = (doc.data?.get("unlimitedExpiry") as? Long) ?: 0L
                    
                    preferenceManager.setTokens(tokens)
                    preferenceManager.setUnlimitedExpiry(expiry)
                    onComplete(tokens, expiry)
                }
            }
    }

    /**
     * Restores token balance and active plan from a specific device ID.
     * Callback receives (success, tokens, remainingExpiryMs)
     */
    fun restoreAccountData(id: String, onResult: (Boolean, Int, Long) -> Unit) {
        val formattedId = id.trim().uppercase()
        if (formattedId.isEmpty()) {
            onResult(false, 0, 0L)
            return
        }
        db.collection("devices").document(formattedId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val tokens = (doc.data?.get("tokenBalance") as? Long)?.toInt() ?: 0
                    val expiry = (doc.data?.get("unlimitedExpiry") as? Long) ?: 0L
                    val remainingMs = maxOf(0L, expiry - System.currentTimeMillis())
                    
                    preferenceManager.setTokens(tokens)
                    if (expiry > System.currentTimeMillis()) {
                        preferenceManager.setUnlimitedExpiry(expiry)
                    }
                    triggerSync()
                    onResult(true, tokens, remainingMs)
                } else {
                    onResult(false, 0, 0L)
                }
            }
            .addOnFailureListener {
                onResult(false, 0, 0L)
            }
    }

    /**
     * Redeems a promocode and reports back to admin.
     */
    fun redeemPromocode(code: String, onResult: (Result<Int>) -> Unit) {
        val physicalId = preferenceManager.getPhysicalDeviceId()
        val promocodeRef = db.collection("promo_codes").document(code)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(promocodeRef)
            
            if (!snapshot.exists()) {
                throw Exception("Invalid promocode. Please check and try again.")
            }
            
            val status = snapshot.getString("status") ?: "inactive"
            if (status == "redeemed" || status == "expired") {
                throw Exception("This promocode has already been used or has expired.")
            }
            if (status != "active") {
                throw Exception("This promocode is currently inactive.")
            }

            val maxUses = (snapshot.getLong("maxUses") ?: 1L).toInt()
            val redemptionCount = (snapshot.getLong("redemptionCount") ?: 0L).toInt()
            if (redemptionCount >= maxUses) {
                transaction.update(promocodeRef, "status", "redeemed")
                throw Exception("Promocode has reached maximum uses.")
            }
            
            val tokenType = snapshot.getString("tokenType") ?: "unit"
            val tokenAmount = (snapshot.getLong("tokenAmount") ?: 0L).toInt()
            
            when (tokenType) {
                "unit" -> preferenceManager.addTokens(tokenAmount)
                "time" -> {
                    val durationHours = (snapshot.getLong("durationHours") ?: 0L).toInt()
                    val currentExpiry = maxOf(preferenceManager.getUnlimitedExpiry(), System.currentTimeMillis())
                    val newExpiry = currentExpiry + (durationHours.toLong() * 60 * 60 * 1000)
                    preferenceManager.setUnlimitedExpiry(newExpiry)
                }
                "offer" -> {
                    // Specific logic for unlocking a premium offer if needed
                    // For now we treat it as adding extra tokens
                    preferenceManager.addTokens(tokenAmount)
                }
            }
            
            // Update the promocode status and usage
            val newCount = redemptionCount + 1
            transaction.update(promocodeRef, "redemptionCount", newCount)
            if (newCount >= maxUses) {
                transaction.update(promocodeRef, "status", "redeemed")
            }
            transaction.update(promocodeRef, "lastRedeemedBy", physicalId)
            transaction.update(promocodeRef, "lastRedeemedAt", FieldValue.serverTimestamp())
            
            // Record usage in a sub-collection for audit
            val usageData = hashMapOf(
                "deviceId" to physicalId,
                "timestamp" to FieldValue.serverTimestamp(),
                "agentName" to preferenceManager.getUserName()
            )
            db.collection("promo_codes").document(code).collection("usages").add(usageData)
            
            tokenAmount
        }.addOnSuccessListener { value ->
            logEvent("Successfully redeemed promocode: $code", "info")
            onResult(Result.success(value))
        }.addOnFailureListener { e ->
            logEvent("Failed to redeem promocode: $code - ${e.message}", "warning")
            onResult(Result.failure(e))
        }
    }

    private fun startSettingsListener() {
        settingsListener?.remove()
        settingsListener = db.collection("system").document("settings")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirebaseDeviceManager", "Settings listener failed", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val globalEnabled = snapshot.getBoolean("globalAutomationEnabled") ?: true
                    preferenceManager.setGlobalAutomationEnabled(globalEnabled)
                    
                    Log.d("FirebaseDeviceManager", "Global Settings Sync: Automation=$globalEnabled")
                }
            }
    }

    fun logEvent(message: String, type: String = "info") {
        val currentUser = auth.currentUser ?: return
        val logData = hashMapOf(
            "agentId" to currentUser.uid,
            "agentName" to (currentUser.displayName ?: preferenceManager.getUserName()),
            "message" to message,
            "type" to type,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("agent_logs").add(logData)
            .addOnFailureListener { e ->
                Log.e("FirebaseDeviceManager", "Failed to log event", e)
            }
    }

    private fun registerOrUpdate(online: Boolean) {
        scope.launch {
            val physicalId = preferenceManager.getPhysicalDeviceId()
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w("FirebaseDeviceManager", "Registration skipped: User not authenticated")
                return@launch
            }

            val data = getDeviceData(online, currentUser.uid)
            
            db.collection("devices").document(physicalId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FirebaseDeviceManager", "Permanent device $physicalId registration successful")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseDeviceManager", "Device registration failed", e)
                }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(60_000) // 1 minute heartbeat as requested
                updateHeartbeat()
            }
        }
        
        // Setup Persistent Background Heartbeat
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<HeartbeatWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
        ).build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "FirebaseHeartbeat",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun startTransactionObserver() {
        transactionSyncJob?.cancel()
        transactionSyncJob = scope.launch {
            repository.allTransactions.collect { transactions ->
                transactions.forEach { transaction ->
                    syncTransactionToCloud(transaction)
                }
            }
        }
    }

    private fun startSessionObserver() {
        sessionSyncJob?.cancel()
        sessionSyncJob = scope.launch {
            repository.allEngageSessions.collect { sessions ->
                sessions.forEach { session ->
                    syncSessionToCloud(session)
                }
            }
        }
    }

    private fun startOffersObserver() {
        offersSyncJob?.cancel()
        offersSyncJob = scope.launch {
            repository.allOffers.collect { offers ->
                offers.forEach { offer ->
                    syncOfferToCloud(offer)
                }
            }
        }
    }

    fun syncOfferToCloud(offer: Offer) {
        try {
            val physicalId = preferenceManager.getPhysicalDeviceId()
            val docId = "${physicalId}_O_${offer.id}"

            val data = hashMapOf(
                "deviceId" to physicalId,
                "offerId" to offer.id,
                "name" to offer.name,
                "description" to offer.description,
                "amount" to offer.amount,
                "safPrice" to offer.safPrice,
                "network" to offer.network,
                "ussdCode" to offer.ussdCode,
                "simSelection" to offer.simSelection,
                "status" to offer.status,
                "category" to offer.category,
                "isEnabled" to offer.isEnabled,
                "commission" to offer.commission,
                "paymentMode" to offer.paymentMode,
                "ussdMode" to offer.ussdMode,
                "executionDelayMs" to offer.executionDelayMs,
                "retryAttempts" to offer.retryAttempts,
                "timeoutMs" to offer.timeoutMs,
                "notes" to offer.notes,
                "dateCreated" to offer.dateCreated,
                "lastModified" to offer.lastModified,
                "timesUsed" to offer.timesUsed,
                "successCount" to offer.successCount,
                "failureCount" to offer.failureCount,
                "lastUsedDate" to offer.lastUsedDate,
                "avgExecutionTimeMs" to offer.avgExecutionTimeMs,
                "mainStartTime" to offer.mainStartTime,
                "mainEndTime" to offer.mainEndTime,
                "altUssdCode" to offer.altUssdCode,
                "altStartTime" to offer.altStartTime,
                "altEndTime" to offer.altEndTime,
                "isFallbackEnabled" to offer.isFallbackEnabled,
                "fallbackOfferId" to offer.fallbackOfferId,
                "isTimerEnabled" to offer.isTimerEnabled,
                "timerEnableAt" to offer.timerEnableAt,
                "timerDisableAt" to offer.timerDisableAt,
                "switchOfferId" to offer.switchOfferId,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("offers").document(docId)
                .set(data, SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.e("FirebaseDeviceManager", "Failed to sync offer ${offer.id}", e)
                }
        } catch (e: Exception) {
            Log.e("FirebaseDeviceManager", "syncOfferToCloud crash prevented", e)
        }
    }

    fun syncTransactionToCloud(transaction: Transaction) {
        try {
            val physicalId = preferenceManager.getPhysicalDeviceId()
            val docId = "${physicalId}_${transaction.id}"

            val data = hashMapOf(
                "deviceId" to physicalId,
                "roomId" to transaction.id,
                "date" to transaction.date,
                "amount" to transaction.amount,
                "offerName" to transaction.offerName,
                "customerNumber" to transaction.customerNumber,
                "senderName" to transaction.senderName,
                "status" to transaction.status,
                "transactionCode" to transaction.transactionCode,
                "simUsed" to transaction.simUsed,
                "commissionEarned" to transaction.commissionEarned,
                "safPrice" to transaction.safPrice,
                "tokenDeducted" to transaction.tokenDeducted,
                "finalResponse" to transaction.finalResponse,
                "executionTrail" to transaction.executionTrail,
                "originalOfferName" to transaction.originalOfferName,
                "switchReason" to transaction.switchReason,
                "isVerified" to transaction.isVerified,
                "rawMessage" to transaction.rawMessage,
                "notes" to transaction.notes,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            db.collection("transactions").document(docId)
                .set(data, SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.e("FirebaseDeviceManager", "Failed to sync transaction ${transaction.id}", e)
                }
        } catch (e: Exception) {
            Log.e("FirebaseDeviceManager", "syncTransactionToCloud crash prevented", e)
        }
    }

    fun syncSessionToCloud(session: EngageSession) {
        val physicalId = preferenceManager.getPhysicalDeviceId()
        val docId = "${physicalId}_S_${session.id}"
        
        val data = hashMapOf(
            "deviceId" to physicalId,
            "sessionId" to session.id,
            "customerNumber" to session.customerNumber,
            "customerName" to session.customerName,
            "state" to session.state,
            "recordStatus" to session.recordStatus,
            "originalAmount" to session.originalAmount,
            "topUpAmount" to session.topUpAmount,
            "ussdResult" to session.ussdResult,
            "notes" to session.notes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        
        db.collection("bot_sessions").document(docId)
            .set(data, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e("FirebaseDeviceManager", "Failed to sync session ${session.id}", e)
            }
    }

    fun triggerSync() {
        if (isSyncInProgress) return
        scope.launch {
            try {
                isSyncInProgress = true
                updateHeartbeat()
                delay(2000) // Debounce
            } finally {
                isSyncInProgress = false
            }
        }
    }

    fun syncStats(stats: DashboardStats) {
        try {
            if (lastStats == stats) return
            lastStats = stats
            triggerSync()
        } catch (e: Exception) {
            Log.e("FirebaseDeviceManager", "syncStats crash prevented", e)
        }
    }

    private fun updateHeartbeat() {
        val physicalId = preferenceManager.getPhysicalDeviceId()
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            scope.launch { if (ensureAuthenticated()) registerOrUpdate(true) }
            return
        }

        val updates = mutableMapOf<String, Any>(
            "lastSeen" to FieldValue.serverTimestamp(),
            "online" to true,
            "automationEnabled" to preferenceManager.isAutomationEnabled(),
            "tokenBalance" to preferenceManager.getTokens(),
            "authUid" to currentUser.uid,
            "agentId" to preferenceManager.getAgentId(),
            "relaySimSlot" to preferenceManager.getRelaySimSlot(),
            "notifSimSlot" to preferenceManager.getNotificationSimSlot(),
            "relayTarget" to preferenceManager.getRelayTargetNumber(),
            "userName" to (currentUser.displayName ?: preferenceManager.getUserName()),
            "userEmail" to (currentUser.email ?: preferenceManager.getRegisteredEmail()),
            "agentPhone" to preferenceManager.getAgentPhone(),
            "appStatus" to if (com.example.autodial.util.TransactionQueueManager.isProcessing()) "ACTIVE" else "IDLE",
            "airtimeSim1" to preferenceManager.getAirtimeBalance(0),
            "airtimeSim2" to preferenceManager.getAirtimeBalance(1),
            "ussdResponseDelay" to preferenceManager.getUssdResponseDelay(),
            "simpleTimeout" to preferenceManager.getSimpleModeTimeout(),
            "advancedTimeout" to preferenceManager.getAdvancedModeTimeout(),
            "isUnlimited" to preferenceManager.isUnlimitedActive(),
            "unlimitedExpiry" to preferenceManager.getUnlimitedExpiry(),
            "accessibilityEnabled" to UssdHelper.isAccessibilityServiceEnabled(context)
        )
        
        lastStats?.let { stats ->
            updates["totalTransactions"] = stats.totalCount
            updates["successfulTransactions"] = stats.doneCount
            updates["failedTransactions"] = stats.failedCount
            updates["queuedTransactions"] = stats.queuedCount
            updates["unmatchedTransactions"] = stats.unmatchedCount
            updates["skippedTransactions"] = stats.skippedCount
            updates["scheduledTransactions"] = stats.scheduledCount
            updates["totalCommission"] = stats.totalCommission
            updates["totalOffers"] = stats.totalOffers
            updates["activeOffers"] = stats.activeOffers
            updates["successRate"] = stats.rate
        }
        
        db.collection("devices").document(physicalId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.w("FirebaseDeviceManager", "Heartbeat update failed, re-registering...", e)
                registerOrUpdate(true)
            }
    }

    fun setOffline() {
        heartbeatJob?.cancel()
        val physicalId = preferenceManager.getPhysicalDeviceId()
        db.collection("devices").document(physicalId)
            .update("online", false)
    }

    private fun getDeviceData(online: Boolean, authUid: String): Map<String, Any> {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
        
        val data = mutableMapOf<String, Any>(
            "deviceId" to preferenceManager.getPhysicalDeviceId(),
            "hardwareId" to preferenceManager.getPhysicalDeviceId(),
            "agentId" to preferenceManager.getAgentId(),
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "androidVersion" to Build.VERSION.RELEASE,
            "appVersion" to (packageInfo?.versionName ?: "1.3.3"),
            "packageName" to context.packageName,
            "online" to online,
            "lastSeen" to FieldValue.serverTimestamp(),
            "automationEnabled" to preferenceManager.isAutomationEnabled(),
            "tokenBalance" to preferenceManager.getTokens(),
            "authUid" to authUid,
            "relaySimSlot" to preferenceManager.getRelaySimSlot(),
            "notifSimSlot" to preferenceManager.getNotificationSimSlot(),
            "relayTarget" to preferenceManager.getRelayTargetNumber(),
            "userName" to preferenceManager.getUserName(),
            "userEmail" to preferenceManager.getRegisteredEmail(),
            "agentPhone" to preferenceManager.getAgentPhone(),
            "registrationDate" to preferenceManager.getRegistrationDate(),
            "registrationTime" to preferenceManager.getRegistrationTime(),
            "appStatus" to if (com.example.autodial.util.TransactionQueueManager.isProcessing()) "ACTIVE" else "IDLE",
            "airtimeSim1" to preferenceManager.getAirtimeBalance(0),
            "airtimeSim2" to preferenceManager.getAirtimeBalance(1),
            "ussdResponseDelay" to preferenceManager.getUssdResponseDelay(),
            "simpleTimeout" to preferenceManager.getSimpleModeTimeout(),
            "advancedTimeout" to preferenceManager.getAdvancedModeTimeout(),
            "isUnlimited" to preferenceManager.isUnlimitedActive(),
            "unlimitedExpiry" to preferenceManager.getUnlimitedExpiry(),
            "accessibilityEnabled" to UssdHelper.isAccessibilityServiceEnabled(context)
        )

        lastStats?.let { stats ->
            data["totalTransactions"] = stats.totalCount
            data["successfulTransactions"] = stats.doneCount
            data["failedTransactions"] = stats.failedCount
            data["successRate"] = stats.rate
        }

        return data
    }
}
