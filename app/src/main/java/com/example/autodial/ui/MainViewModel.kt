package com.example.autodial.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.example.autodial.data.*
import com.example.autodial.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

data class DashboardStats(
    val doneCount: Int = 0,
    val failedCount: Int = 0,
    val queuedCount: Int = 0,
    val unmatchedCount: Int = 0,
    val skippedCount: Int = 0,
    val scheduledCount: Int = 0,
    val verifiedCount: Int = 0,
    val unverifiedCount: Int = 0,
    val disabledCount: Int = 0,
    val totalCommission: Double = 0.0,
    val totalCount: Int = 0,
    val rate: Int = 0,
    val totalOffers: Int = 0,
    val activeOffers: Int = 0,
    val simpleOffers: Int = 0,
    val directOffers: Int = 0,
    val advancedOffers: Int = 0
)

enum class CommissionFilter { THREE_DAYS, SEVEN_DAYS, THIS_MONTH, THREE_MONTHS }

data class CommissionSummary(
    val todayCommission: Double,
    val todayDiffPercent: Int,
    val totalCommission: Double,
    val periodDiffPercent: Int,
    val monthTotal: Double,
    val trendData: List<Pair<String, Double>>,
    val todayAirtime: Double,
    val yesterdayAirtime: Double
)

class MainViewModel(
    application: Application,
    private val repository: MainRepository,
    private val mpesaRepository: MpesaRepository,
    private val preferenceManager: PreferenceManager,
    val firebaseDeviceManager: FirebaseDeviceManager
) : AndroidViewModel(application) {

    data class UnifiedTask(
        val type: String,
        val title: String,
        val subtitle: String,
        val time: Long,
        val originalObject: Any
    )

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        try {
            when (key) {
                "tokens" -> _tokens.value = preferenceManager.getTokens()
                "unlimited_expiry" -> _isUnlimitedActive.value = preferenceManager.isUnlimitedActive()
                "app_theme" -> _appTheme.value = preferenceManager.getAppTheme()
                "ussd_response_delay" -> _ussdResponseDelay.value = preferenceManager.getUssdResponseDelay()
                "queue_cooldown_delay" -> _queueCooldownDelay.value = preferenceManager.getQueueCooldownDelay()
                "simple_timeout" -> _simpleModeTimeout.value = preferenceManager.getSimpleModeTimeout()
                "advanced_timeout" -> _advancedModeTimeout.value = preferenceManager.getAdvancedModeTimeout()
                "unique_device_id" -> _uniqueDeviceId.value = preferenceManager.getUniqueDeviceId()
                "agent_id" -> _agentId.value = preferenceManager.getAgentId()
                "engage_bot_enabled" -> _isEngageBotEnabled.value = preferenceManager.isEngageBotEnabled()
                "engage_exact_topup_match" -> _isEngageExactTopUpMatch.value = preferenceManager.isEngageExactTopUpMatch()
                "keyboard_suppression" -> _isKeyboardSuppressionEnabled.value = preferenceManager.isKeyboardSuppressionEnabled()
                "automation_enabled" -> _isAutomationEnabled.value = preferenceManager.isAutomationEnabled()
                "global_automation_enabled" -> _isGlobalAutomationEnabled.value = preferenceManager.isGlobalAutomationEnabled()
                "announcement_active" -> _announcementActive.value = preferenceManager.isAnnouncementActive()
                "announcement_title" -> _announcementTitle.value = preferenceManager.getAnnouncementTitle()
                "announcement_message" -> _announcementMessage.value = preferenceManager.getAnnouncementMessage()
                "data_hidden" -> _isDataHidden.value = preferenceManager.isDataHidden()
                "user_registered" -> _isUserRegistered.value = preferenceManager.isUserRegistered()
                "user_name" -> _userName.value = preferenceManager.getUserName()
                "registered_email" -> _userEmail.value = preferenceManager.getRegisteredEmail()
                "autosave_contacts" -> _isAutosaveEnabled.value = preferenceManager.isAutosaveContactsEnabled()
                "autosave_prefix" -> _autosavePrefix.value = preferenceManager.getAutosavePrefix()
                "airtime_sim_0" -> _airtimeSim1.value = preferenceManager.getAirtimeBalance(0)
                "airtime_sim_1" -> _airtimeSim2.value = preferenceManager.getAirtimeBalance(1)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error in preference listener for key $key", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        preferenceManager.getContext().getSharedPreferences("autodial_prefs", android.content.Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private val _databaseSize = MutableStateFlow(0L)
    val databaseSize: StateFlow<Long> = _databaseSize.asStateFlow()

    data class StorageStats(val totalSize: Long = 0, val deletedAmount: Long = 0, val lastBalance: Long = 0)
    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _verifiedSenders = MutableStateFlow(preferenceManager.getVerifiedSenders())
    val verifiedSenders: StateFlow<Set<String>> = _verifiedSenders.asStateFlow()

    val allTransactions: LiveData<List<Transaction>> = repository.allTransactions.flowOn(Dispatchers.Default).asLiveData()
    
    private val _midnightTrigger = MutableStateFlow(System.currentTimeMillis())
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayTransactions: LiveData<List<Transaction>> = _midnightTrigger.flatMapLatest {
        repository.getTransactionsForDashboard()
    }.flowOn(Dispatchers.Default).asLiveData()
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: LiveData<DashboardStats> = _midnightTrigger.flatMapLatest {
        repository.getTransactionsToday()
    }.map { list ->
        val done = list.filter { it.status == Transaction.STATUS_SUCCESS }.size
        val failed = list.filter { it.status == Transaction.STATUS_FAILED }.size
        val queued = list.filter { it.status == Transaction.STATUS_WAITING || it.status == Transaction.STATUS_PROCESSING }.size
        val unmatched = list.filter { it.status == Transaction.STATUS_UNMATCHED }.size
        val skipped = list.filter { it.status == Transaction.STATUS_SKIPPED }.size
        val scheduled = list.filter { it.status == Transaction.STATUS_SCHEDULED }.size
        val verified = list.filter { it.isVerified }.size
        val unverified = list.filter { !it.isVerified }.size
        val disabled = list.filter { it.status == Transaction.STATUS_DISABLED }.size
        val commission = list.sumOf { it.commissionEarned }
        val total = done + failed
        val rate = if (total > 0) (done * 100 / total) else 0

        DashboardStats(
            doneCount = done,
            failedCount = failed,
            queuedCount = queued,
            unmatchedCount = unmatched,
            skippedCount = skipped,
            scheduledCount = scheduled,
            verifiedCount = verified,
            unverifiedCount = unverified,
            disabledCount = disabled,
            totalCommission = commission,
            totalCount = list.size,
            rate = rate
        )
    }.flowOn(Dispatchers.Default).asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayHourlyAirtimeUsage: LiveData<List<Pair<Int, Double>>> = _midnightTrigger.flatMapLatest {
        repository.getTransactionsToday()
    }.map { list ->
        val successful = list.filter { it.status == Transaction.STATUS_SUCCESS || it.isVerified }
        val hourlyMap = DoubleArray(24) { 0.0 }
        
        val calendar = Calendar.getInstance()
        successful.forEach { txn ->
            calendar.timeInMillis = txn.date
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..23) {
                hourlyMap[hour] += txn.amount
            }
        }
        
        hourlyMap.mapIndexed { hour, sum -> Pair(hour, sum) }
    }.flowOn(Dispatchers.Default).asLiveData()
    val recentTransactions: LiveData<List<Transaction>> = repository.recentTransactions.flowOn(Dispatchers.Default).asLiveData()
    val allOffers: LiveData<List<Offer>> = repository.allOffers.flowOn(Dispatchers.Default).asLiveData()
    val allScheduledTasks: LiveData<List<ScheduledTask>> = repository.allScheduledTasks.flowOn(Dispatchers.Default).asLiveData()
    val allTemplates: LiveData<List<NotificationTemplate>> = repository.allTemplates.flowOn(Dispatchers.Default).asLiveData()
    val allContacts: LiveData<List<Contact>> = repository.contactDao.getAllContacts().flowOn(Dispatchers.Default).asLiveData()
    val allRelayConfigs: LiveData<List<RelayConfig>> = repository.relayConfigDao.getAllConfigs().flowOn(Dispatchers.Default).asLiveData()
    val allActiveMaps: LiveData<List<UssdMap>> = repository.ussdMapDao.getAllActiveMapsFlow().flowOn(Dispatchers.Default).asLiveData()
    val allTopUpPlans: LiveData<List<TopUpPlan>> = repository.allTopUpPlans.flowOn(Dispatchers.Default).asLiveData()
    val allEngageSessions: LiveData<List<EngageSession>> = repository.allEngageSessions.flowOn(Dispatchers.Default).asLiveData()

    private val _transactionDateFilter = MutableStateFlow("All")
    val transactionDateFilter: StateFlow<String> = _transactionDateFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredTransactions: LiveData<List<Transaction>> = _transactionDateFilter.flatMapLatest { filter ->
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (filter) {
            "Today" -> repository.getTransactionsInRange(calendar.timeInMillis, System.currentTimeMillis())
            "Yesterday" -> {
                val end = calendar.timeInMillis - 1
                val start = end - (24 * 60 * 60 * 1000) + 1
                repository.getTransactionsInRange(start, end)
            }
            "This Week" -> {
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)
                repository.getTransactionsInRange(sevenDaysAgo, System.currentTimeMillis())
            }
            else -> repository.allTransactions
        }
    }.flowOn(Dispatchers.Default).asLiveData()

    fun setTransactionDateFilter(filter: String) {
        _transactionDateFilter.value = filter
    }

    private val _isUserRegistered = MutableStateFlow(preferenceManager.isUserRegistered())
    val isUserRegistered: StateFlow<Boolean> = _isUserRegistered.asStateFlow()

    private val _userName = MutableStateFlow(preferenceManager.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(preferenceManager.getRegisteredEmail())
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _mpesaLoading = MutableStateFlow(false)
    val mpesaLoading: StateFlow<Boolean> = _mpesaLoading.asStateFlow()

    private val _isWaitingForMpesa = MutableStateFlow(false)
    val isWaitingForMpesa: StateFlow<Boolean> = _isWaitingForMpesa.asStateFlow()

    private val _mpesaError = MutableStateFlow<String?>(null)
    val mpesaError: StateFlow<String?> = _mpesaError.asStateFlow()

    private val _mpesaStatusMessage = MutableStateFlow("Enter M-PESA PIN on your phone prompt")
    val mpesaStatusMessage: StateFlow<String> = _mpesaStatusMessage.asStateFlow()

    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _commissionSummary = MutableStateFlow<CommissionSummary?>(null)
    val commissionSummary = _commissionSummary.asStateFlow()

    private val _activeCommissionFilter = MutableStateFlow(CommissionFilter.THIS_MONTH)
    val activeCommissionFilter = _activeCommissionFilter.asStateFlow()

    private val _airtimeSim1 = MutableStateFlow(preferenceManager.getAirtimeBalance(0))
    val airtimeSim1: StateFlow<String> = _airtimeSim1.asStateFlow()

    private val _airtimeSim2 = MutableStateFlow(preferenceManager.getAirtimeBalance(1))
    val airtimeSim2: StateFlow<String> = _airtimeSim2.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<String?>(null)
    val scanResult: StateFlow<String?> = _scanResult.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _currentTrainingMapId = MutableStateFlow(-1)
    val currentTrainingMapId: StateFlow<Int> = _currentTrainingMapId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val trainingSteps: StateFlow<List<UssdStepWithOptions>> = _currentTrainingMapId.flatMapLatest { mapId ->
        if (mapId == -1) flowOf(emptyList())
        else repository.ussdMapDao.getMapWithStepsFlow(mapId).map { it?.steps ?: emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAutosaveEnabled = MutableStateFlow(preferenceManager.isAutosaveContactsEnabled())
    val isAutosaveEnabled: StateFlow<Boolean> = _isAutosaveEnabled.asStateFlow()

    private val _autosavePrefix = MutableStateFlow(preferenceManager.getAutosavePrefix())
    val autosavePrefix: StateFlow<String> = _autosavePrefix.asStateFlow()

    private val _isAutoCleanupEnabled = MutableStateFlow(preferenceManager.isAutoCleanupEnabled())
    val isAutoCleanupEnabled: StateFlow<Boolean> = _isAutoCleanupEnabled.asStateFlow()

    private val _retentionDays = MutableStateFlow(preferenceManager.getRetentionDays())
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    private val _isPruningEnabled = MutableStateFlow(preferenceManager.isPruningEnabled())
    val isPruningEnabled: StateFlow<Boolean> = _isPruningEnabled.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    fun setCommissionFilter(filter: CommissionFilter) {
        _activeCommissionFilter.value = filter
    }

    init {
        try {
            preferenceManager.getContext().getSharedPreferences("autodial_prefs", android.content.Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(preferenceListener)
            
            checkAuthState()
            checkPendingPayment()
            refreshDatabaseSize()
            
            OfferTimerManager.rescheduleAll(getApplication())

            viewModelScope.launch {
                while(true) {
                    val now = System.currentTimeMillis()
                    val nextMidnight = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    
                    val delayTime = nextMidnight - now + 1000 // 1 sec buffer
                    if (delayTime > 0) delay(delayTime)
                    else delay(60000)
                    
                    _midnightTrigger.value = System.currentTimeMillis()
                    Log.d("MainViewModel", "Dashboard triggered midnight reset.")
                }
            }

            viewModelScope.launch(Dispatchers.Default) {
                combine(repository.allTransactions, _activeCommissionFilter) { transactions, filter ->
                    calculateCommissionSummary(transactions, filter)
                }.collect { summary ->
                    _commissionSummary.value = summary
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Critical initialization failure", e)
        }
    }

    private fun calculateCommissionSummary(transactions: List<Transaction>, filter: CommissionFilter): CommissionSummary {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filterStart = when (filter) {
            CommissionFilter.THREE_DAYS -> todayStart - (2 * 24 * 60 * 60 * 1000)
            CommissionFilter.SEVEN_DAYS -> todayStart - (6 * 24 * 60 * 60 * 1000)
            CommissionFilter.THIS_MONTH -> {
                Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            }
            CommissionFilter.THREE_MONTHS -> {
                Calendar.getInstance().apply { add(Calendar.MONTH, -2); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            }
        }

        val filteredList = transactions.filter { it.date >= filterStart }
        val totalCommission = filteredList.sumOf { it.commissionEarned }
        val todayCommission = transactions.filter { it.date >= todayStart }.sumOf { it.commissionEarned }
        
        val monthTotal = transactions.filter { 
            it.date >= Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis 
        }.sumOf { it.commissionEarned }

        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000)
        val yesterdayCommission = transactions.filter { it.date in yesterdayStart until todayStart }.sumOf { it.commissionEarned }
        val todayDiff = if (yesterdayCommission > 0) ((todayCommission - yesterdayCommission) / yesterdayCommission * 100).toInt() else 0

        val todayAirtime = transactions.filter { it.date >= todayStart }.sumOf { it.safPrice }
        val yesterdayAirtime = transactions.filter { it.date in yesterdayStart until todayStart }.sumOf { it.safPrice }

        val trendData = mutableListOf<Pair<String, Double>>()
        val dayFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        
        val loopCount = when (filter) {
            CommissionFilter.THREE_DAYS -> 2
            CommissionFilter.SEVEN_DAYS -> 6
            CommissionFilter.THIS_MONTH -> {
                val cal = Calendar.getInstance()
                cal.get(Calendar.DAY_OF_MONTH) - 1
            }
            CommissionFilter.THREE_MONTHS -> 84 // ~12 weeks
        }

        if (filter == CommissionFilter.THREE_MONTHS) {
            val weekFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            for (i in 11 downTo 0) {
                val wStart = todayStart - (i * 7 * 24 * 60 * 60 * 1000L)
                val wEnd = wStart + (7 * 24 * 60 * 60 * 1000L)
                val weekComm = transactions.filter { it.date in wStart until wEnd }.sumOf { it.commissionEarned }
                trendData.add(weekFormat.format(Date(wStart)) to weekComm)
            }
        } else {
            for (i in loopCount downTo 0) {
                val dStart = todayStart - (i * 24 * 60 * 60 * 1000L)
                val dEnd = dStart + (24 * 60 * 60 * 1000L)
                val dayComm = transactions.filter { it.date in dStart until dEnd }.sumOf { it.commissionEarned }
                trendData.add(dayFormat.format(Date(dStart)) to dayComm)
            }
        }

        return CommissionSummary(
            todayCommission = todayCommission,
            todayDiffPercent = todayDiff,
            totalCommission = totalCommission,
            periodDiffPercent = 0,
            monthTotal = monthTotal,
            trendData = trendData,
            todayAirtime = todayAirtime,
            yesterdayAirtime = yesterdayAirtime
        )
    }

    private val _tokens = MutableStateFlow(preferenceManager.getTokens())
    val tokens: StateFlow<Int> = _tokens.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _isUnlimitedActive = MutableStateFlow(preferenceManager.isUnlimitedActive())
    val isUnlimitedActive: StateFlow<Boolean> = _isUnlimitedActive.asStateFlow()

    private val _appTheme = MutableStateFlow(preferenceManager.getAppTheme())
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _ussdResponseDelay = MutableStateFlow(preferenceManager.getUssdResponseDelay())
    val ussdResponseDelay: StateFlow<Long> = _ussdResponseDelay.asStateFlow()

    private val _queueCooldownDelay = MutableStateFlow(preferenceManager.getQueueCooldownDelay())
    val queueCooldownDelay: StateFlow<Long> = _queueCooldownDelay.asStateFlow()

    private val _simpleModeTimeout = MutableStateFlow(preferenceManager.getSimpleModeTimeout())
    val simpleModeTimeout: StateFlow<Int> = _simpleModeTimeout.asStateFlow()

    private val _advancedModeTimeout = MutableStateFlow(preferenceManager.getAdvancedModeTimeout())
    val advancedModeTimeout: StateFlow<Int> = _advancedModeTimeout.asStateFlow()

    private val _uniqueDeviceId = MutableStateFlow(preferenceManager.getUniqueDeviceId())
    val uniqueDeviceId: StateFlow<String> = _uniqueDeviceId.asStateFlow()

    private val _agentId = MutableStateFlow(preferenceManager.getAgentId())
    val agentId: StateFlow<String> = _agentId.asStateFlow()

    private val _isEngageBotEnabled = MutableStateFlow(preferenceManager.isEngageBotEnabled())
    val isEngageBotEnabled: StateFlow<Boolean> = _isEngageBotEnabled.asStateFlow()

    private val _isEngageExactTopUpMatch = MutableStateFlow(preferenceManager.isEngageExactTopUpMatch())
    val isEngageExactTopUpMatch: StateFlow<Boolean> = _isEngageExactTopUpMatch.asStateFlow()

    private val _isKeyboardSuppressionEnabled = MutableStateFlow(preferenceManager.isKeyboardSuppressionEnabled())
    val isKeyboardSuppressionEnabled: StateFlow<Boolean> = _isKeyboardSuppressionEnabled.asStateFlow()

    private val _isAutomationEnabled = MutableStateFlow(preferenceManager.isAutomationEnabled())
    val isAutomationEnabled: StateFlow<Boolean> = _isAutomationEnabled.asStateFlow()

    private val _isGlobalAutomationEnabled = MutableStateFlow(preferenceManager.isGlobalAutomationEnabled())
    val isGlobalAutomationEnabled: StateFlow<Boolean> = _isGlobalAutomationEnabled.asStateFlow()

    private val _announcementActive = MutableStateFlow(preferenceManager.isAnnouncementActive())
    val announcementActive: StateFlow<Boolean> = _announcementActive.asStateFlow()

    private val _announcementTitle = MutableStateFlow(preferenceManager.getAnnouncementTitle())
    val announcementTitle: StateFlow<String> = _announcementTitle.asStateFlow()

    private val _announcementMessage = MutableStateFlow(preferenceManager.getAnnouncementMessage())
    val announcementMessage: StateFlow<String> = _announcementMessage.asStateFlow()


    private val _isDataHidden = MutableStateFlow(preferenceManager.isDataHidden())
    val isDataHidden: StateFlow<Boolean> = _isDataHidden.asStateFlow()

    val waitingTransactionCount: LiveData<Int> = allTransactions.map { list ->
        list.filter { it.status == Transaction.STATUS_WAITING }.size
    }

    val units: StateFlow<Int> = tokens

    val unifiedScheduledTasks: LiveData<List<UnifiedTask>> = combine(allScheduledTasks.asFlow(), allEngageSessions.asFlow()) { tasks, sessions ->
        val manual = tasks.map {
            UnifiedTask("MANUAL", it.offerName, it.targetNumber, it.scheduledTime, it)
        }
        val bots = sessions.filter { it.state == "WAITING" || it.state == "SCHEDULED" }.map {
            UnifiedTask("BOT", it.originalOfferName, it.customerNumber, it.nextEligibleDate, it)
        }
        (manual + bots).sortedBy { it.time }
    }.asLiveData()

    fun updateAccessibilityStatus(enabled: Boolean) {
        _isAccessibilityEnabled.value = enabled
    }

    fun toggleDataVisibility() {
        val newState = !preferenceManager.isDataHidden()
        preferenceManager.setDataHidden(newState)
        _isDataHidden.value = newState
    }

    fun markAnnouncementAsRead(title: String, message: String) {
        val hash = (title + message).hashCode().toString()
        preferenceManager.setLastAnnouncementHash(hash)
    }

    fun checkAuthState() {
        viewModelScope.launch {
            _isUserRegistered.value = preferenceManager.isUserRegistered()
            _userEmail.value = preferenceManager.getRegisteredEmail()
            _userName.value = preferenceManager.getUserName()
            _isEmailVerified.value = firebaseDeviceManager.isEmailVerified()
        }
    }

    fun refreshTokens() {
        _tokens.value = preferenceManager.getTokens()
        _isUnlimitedActive.value = preferenceManager.isUnlimitedActive()
    }

    fun logout() {
        firebaseDeviceManager.signOut()
        _isUserRegistered.value = false
    }

    fun forgotPassword(email: String) {
        firebaseDeviceManager.sendPasswordReset(email) {}
    }

    fun signIn(email: String, pass: String) {
        viewModelScope.launch { firebaseDeviceManager.signIn(email, pass) }
    }

    fun signUp(name: String, phone: String, email: String, pass: String) {
        viewModelScope.launch { firebaseDeviceManager.signUp(name, phone, email, pass) }
    }

    fun resendVerification() {
        firebaseDeviceManager.resendVerification()
    }

    fun triggerStkPush(phone: String, packageName: String, price: Int, units: Int, isUnlimited: Boolean, duration: Int, context: android.content.Context) {
        if (_mpesaLoading.value || _isWaitingForMpesa.value) return

        viewModelScope.launch {
            _mpesaLoading.value = true
            _mpesaStatusMessage.value = "Sending STK Push prompt..."
            val finalPhone = if (phone.isNotBlank()) phone else preferenceManager.getAgentPhone()
            
            // Native Daraja Call
            val result = mpesaRepository.initiateStkPush(
                phone = finalPhone,
                amount = price,
                accountRef = packageName
            )
            
            result.onSuccess { response ->
                if (response.responseCode == "0") {
                    // Persist state for recovery
                    preferenceManager.setPendingOrderId(response.checkoutRequestId)
                    preferenceManager.setPendingPackageId(packageName)
                    preferenceManager.setPendingPaymentUnits(units)
                    preferenceManager.setPendingUnlimited(isUnlimited)
                    preferenceManager.setPendingDurationDays(duration)

                    _isWaitingForMpesa.value = true
                    _mpesaLoading.value = false
                    _mpesaStatusMessage.value = "STK Push sent! Please enter your M-PESA PIN on your phone."
                    startPaymentPolling(response.checkoutRequestId, units, isUnlimited, duration, context)
                } else {
                    _mpesaLoading.value = false
                    _mpesaStatusMessage.value = response.customerMessage
                    android.widget.Toast.makeText(context, response.customerMessage, android.widget.Toast.LENGTH_LONG).show()
                }
            }.onFailure { 
                _mpesaLoading.value = false 
                _mpesaStatusMessage.value = "STK Initiation Failed: ${it.message}"
                android.widget.Toast.makeText(context, "STK Initiation Failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPaymentPolling(checkoutRequestId: String, unitsToAdd: Int, isUnlimited: Boolean, durationDays: Int, context: android.content.Context) {
        viewModelScope.launch {
            var attempts = 0
            while (attempts < 120) { // Poll for up to 10 minutes
                delay(4000)
                attempts++
                
                // If payment was already cleared/awarded by SMS Receiver in the background
                if (preferenceManager.getPendingOrderId().isBlank()) {
                    _isWaitingForMpesa.value = false
                    refreshTokens()
                    return@launch
                }

                _mpesaStatusMessage.value = "Waiting for M-PESA PIN entry..."

                mpesaRepository.queryStkStatus(checkoutRequestId).onSuccess { status ->
                    if (status.resultCode == "0") {
                        // Success! PIN entered and payment processed.
                        _mpesaStatusMessage.value = "PIN entered! Payment confirmed."
                        awardTokensLocally(unitsToAdd, isUnlimited, durationDays, context)
                        return@launch
                    } else if (status.resultCode != null && status.resultCode != "1") {
                        // Final state but not success (e.g. Cancelled 1032, Timeout 1037)
                        _isWaitingForMpesa.value = false
                        preferenceManager.clearPendingPayment()
                        val msg = status.resultDesc ?: "Payment Cancelled or Failed"
                        _mpesaStatusMessage.value = msg
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }.onFailure {
                    Log.w("MainViewModel", "Polling error for $checkoutRequestId: ${it.message}")
                }
            }
            _isWaitingForMpesa.value = false
            android.widget.Toast.makeText(context, "Waiting for confirmation... Check your balance in a few minutes.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun awardTokensLocally(units: Int, isUnlimited: Boolean, durationDays: Int, context: android.content.Context) {
        viewModelScope.launch {
            TokenAwardHelper.awardPendingStkPayment(context)
            _isWaitingForMpesa.value = false
            refreshTokens()
        }
    }

    private fun checkPendingPayment() {
        val orderId = preferenceManager.getPendingOrderId()
        if (orderId.isNotBlank()) {
            _isWaitingForMpesa.value = true
            startPaymentPolling(
                checkoutRequestId = orderId,
                unitsToAdd = preferenceManager.getPendingPaymentUnits(),
                isUnlimited = preferenceManager.isPendingUnlimited(),
                durationDays = preferenceManager.getPendingDurationDays(),
                context = getApplication()
            )
        }
    }

    fun logInfo(message: String) = firebaseDeviceManager.logEvent(message, "info")
    fun logWarning(message: String) = firebaseDeviceManager.logEvent(message, "warning")
    fun logError(message: String) = firebaseDeviceManager.logEvent(message, "error")

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insertTransaction(transaction) }
    }

    fun applyTransactionToDebt(transaction: Transaction, context: android.content.Context) {
        viewModelScope.launch {
            val client = repository.clientDao.getClientByPhone(transaction.customerNumber)
            client?.let { ClientManager.recordPayment(context, it.id, transaction.amount, "Auto", transaction.id) }
        }
    }

    fun addVerifiedSender(number: String) {
        preferenceManager.addVerifiedSender(number)
        _verifiedSenders.value = preferenceManager.getVerifiedSenders()
    }

    fun removeVerifiedSender(number: String) {
        preferenceManager.removeVerifiedSender(number)
        _verifiedSenders.value = preferenceManager.getVerifiedSenders()
    }

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        preferenceManager.setAppTheme(theme)
    }

    fun setUssdResponseDelay(delay: Long) {
        preferenceManager.setUssdResponseDelay(delay)
        _ussdResponseDelay.value = delay
    }

    fun setQueueCooldownDelay(delay: Long) {
        preferenceManager.setQueueCooldownDelay(delay)
        _queueCooldownDelay.value = delay
    }

    fun setSimpleTimeout(seconds: Int) {
        preferenceManager.setSimpleModeTimeout(seconds)
        _simpleModeTimeout.value = seconds
    }

    fun setAdvancedTimeout(seconds: Int) {
        preferenceManager.setAdvancedModeTimeout(seconds)
        _advancedModeTimeout.value = seconds
    }


    fun saveOffer(offer: Offer) {
        viewModelScope.launch {
            if (offer.id == 0) {
                val newId = repository.insertOffer(offer)
                OfferTimerManager.scheduleAlarms(getApplication(), offer.copy(id = newId.toInt()))
            } else {
                repository.updateOffer(offer)
                OfferTimerManager.scheduleAlarms(getApplication(), offer)
            }
        }
    }

    fun deleteOffer(offer: Offer) {
        viewModelScope.launch {
            repository.deleteOffer(offer)
            OfferTimerManager.cancelAlarms(getApplication(), offer.id)
        }
    }

    fun addScheduledTask(task: ScheduledTask) {
        viewModelScope.launch { repository.insertScheduledTask(task) }
    }

    fun updateScheduledTask(task: ScheduledTask) {
        viewModelScope.launch { repository.updateScheduledTask(task) }
    }

    fun deleteScheduledTask(task: ScheduledTask) {
        viewModelScope.launch { repository.deleteScheduledTask(task) }
    }

    fun saveTemplate(template: NotificationTemplate) {
        viewModelScope.launch { if (template.id == 0) repository.insertTemplate(template) else repository.updateTemplate(template) }
    }

    fun deleteTemplate(template: NotificationTemplate) {
        viewModelScope.launch { repository.deleteTemplate(template) }
    }

    fun saveUssdMap(map: UssdMap) {
        viewModelScope.launch { if (map.id == 0) repository.ussdMapDao.insertMap(map) else repository.ussdMapDao.updateMap(map) }
    }

    fun deleteUssdMap(mapId: Int) {
        viewModelScope.launch { repository.ussdMapDao.getMapById(mapId)?.let { repository.ussdMapDao.deleteMap(it) } }
    }

    val allLinkedDevices: LiveData<List<RelayDevice>> = repository.allLinkedDevices.asLiveData()
    
    fun saveRelayDeviceConfig(id: Int, amounts: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.relayDeviceDao.updateDeviceConfig(id, amounts, enabled)
            allLinkedDevices.value?.find { it.id == id }?.let { RelayServer.pushConfigUpdate(it.autodialId, amounts, enabled) }
        }
    }

    fun disconnectDevice(device: RelayDevice) {
        viewModelScope.launch { repository.relayDeviceDao.updateConnectionStatus(device.id, false, "DISCONNECTED") }
    }

    fun deleteDevice(device: RelayDevice) {
        viewModelScope.launch { repository.relayDeviceDao.deleteDevice(device) }
    }

    fun setEngageBotEnabled(enabled: Boolean) {
        preferenceManager.setEngageBotEnabled(enabled)
        _isEngageBotEnabled.value = enabled
    }

    fun setEngageExactTopUpMatch(exact: Boolean) {
        preferenceManager.setEngageExactTopUpMatch(exact)
        _isEngageExactTopUpMatch.value = exact
    }

    fun setKeyboardSuppressionEnabled(enabled: Boolean) {
        preferenceManager.setKeyboardSuppressionEnabled(enabled)
        _isKeyboardSuppressionEnabled.value = enabled
    }

    fun setAutoCleanupEnabled(enabled: Boolean) { 
        preferenceManager.setAutoCleanupEnabled(enabled)
        _isAutoCleanupEnabled.value = enabled
    }
    fun setRetentionDays(days: Int) { 
        preferenceManager.setRetentionDays(days)
        _retentionDays.value = days
    }
    fun setPruningEnabled(enabled: Boolean) { 
        preferenceManager.setPruningEnabled(enabled)
        _isPruningEnabled.value = enabled
    }
    fun setAutosaveEnabled(enabled: Boolean) {
        preferenceManager.setAutosaveContactsEnabled(enabled)
        _isAutosaveEnabled.value = enabled
    }
    fun setAutosavePrefix(prefix: String) {
        preferenceManager.setAutosavePrefix(prefix)
        _autosavePrefix.value = prefix
    }

    fun performEmergencyCleanup(days: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _mpesaLoading.value = true
            try {
                val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                repository.deleteTransactionsBefore(cutoff)
                repository.deleteSmsBefore(cutoff)
                repository.deleteSessionsBefore(cutoff)
                repository.pruneTransactionLogsBefore(cutoff)
                repository.deleteSettledDebtRecordsBefore(cutoff)
                repository.deleteInactiveClientsBefore(cutoff)
                
                AppDatabase.getDatabase(getApplication()).vacuum()
                AppStorage.clearCaches(getApplication())
                refreshDatabaseSize()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Emergency cleanup failed", e)
            } finally {
                _mpesaLoading.value = false
            }
        }
    }
    fun performQuickCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            fixStuckTransactions()
            AppStorage.clearCaches(getApplication())
            refreshDatabaseSize()
        }
    }
    fun performDeepCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            _mpesaLoading.value = true
            try {
                val days = preferenceManager.getRetentionDays()
                val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                
                repository.deleteTransactionsBefore(cutoff)
                repository.deleteSmsBefore(cutoff)
                repository.deleteSessionsBefore(cutoff)
                repository.pruneTransactionLogsBefore(cutoff)
                repository.deleteSettledDebtRecordsBefore(cutoff)
                repository.deleteInactiveClientsBefore(cutoff)
                
                AppDatabase.getDatabase(getApplication()).vacuum()
                AppStorage.clearCaches(getApplication())
                refreshDatabaseSize()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Deep cleanup failed", e)
            } finally {
                _mpesaLoading.value = false
            }
        }
    }
    fun optimizeStorage() { performQuickCleanup() }
    fun compactDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(getApplication()).vacuum()
            refreshDatabaseSize()
        }
    }
    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.autodial.util.AppStorage.clearCaches(getApplication())
            } catch (e: Exception) {
                Log.e("MainViewModel", "Cache clear failed", e)
            }
        }
    }
    fun deepCleanDatabase(days: Int) { performEmergencyCleanup(days) }
    fun clearOldTransactions(days: Int) { performEmergencyCleanup(days) }

    fun toggleAutomation(context: Context) { 
        val newState = !preferenceManager.isAutomationEnabled()
        preferenceManager.setAutomationEnabled(newState)
        _isAutomationEnabled.value = newState
    }
    suspend fun getMapWithSteps(mapId: Int): UssdMapWithSteps? = repository.ussdMapDao.getMapWithStepsFlow(mapId).first()
    fun approveMap(map: UssdMap) { viewModelScope.launch { repository.ussdMapDao.updateMap(map.copy(status = "ACTIVE")) } }
    fun getTrainingPhoneNumber(): String = preferenceManager.getTrainingPhoneNumber()
    fun setTrainingPhoneNumber(number: String) = preferenceManager.setTrainingPhoneNumber(number)

    fun clearTraining() {
        _currentTrainingMapId.value = -1
        UssdSessionManager.endSession(false)
    }
    fun startMapTraining(offer: Offer, context: Context, existingMapId: Int = -1) {
        viewModelScope.launch(Dispatchers.Main) {
            val mapId = if (existingMapId == -1) {
                // 1. Create a new UssdMap entry immediately
                val newMap = UssdMap(
                    name = offer.name + " Map",
                    startingCode = offer.ussdCode.substringBefore("*") + "#",
                    ussdTemplate = offer.ussdCode,
                    status = UssdMap.STATUS_TRAINING,
                    offerId = offer.id
                )
                withContext(Dispatchers.IO) {
                    repository.ussdMapDao.insertMap(newMap).toInt()
                }
            } else {
                // 1b. Reset existing map status to TRAINING and clear old steps/options to replace them cleanly
                withContext(Dispatchers.IO) {
                    val steps = repository.ussdMapDao.getStepsForMap(existingMapId)
                    steps.forEach { repository.ussdMapDao.deleteOptionsForStep(it.id) }
                    repository.ussdMapDao.deleteStepsForMap(existingMapId)
                    repository.ussdMapDao.getMapById(existingMapId)?.let {
                        repository.ussdMapDao.updateMap(it.copy(status = UssdMap.STATUS_TRAINING, lastModified = System.currentTimeMillis()))
                    }
                }
                existingMapId
            }
            
            _currentTrainingMapId.value = mapId

            // 2. Start session with the new ID and training phone number
            val trainingPhone = preferenceManager.getTrainingPhoneNumber()
            UssdSessionManager.startSession(
                ussdCode = offer.ussdCode,
                simSlot = offer.simSelection,
                isTraining = true,
                mapId = mapId,
                customTrainingPhone = trainingPhone
            )
            // Initial dial to start training
            UssdHelper.dialUssd(context, UssdSessionManager.getBaseCode(), offer.simSelection)
        }
    }
    
    fun relearnMap(mapId: Int, context: Context) {
        viewModelScope.launch {
            val map = repository.ussdMapDao.getMapById(mapId)
            val offer = repository.allOffers.first().find { it.id == map?.offerId }
            
            if (offer != null) {
                // SEAMLESS: Just restart training on the same map
                startMapTraining(offer, context, existingMapId = mapId)
            }
        }
    }
    fun approveMapById(mapId: Int) { 
        viewModelScope.launch { 
            repository.ussdMapDao.getMapById(mapId)?.let { 
                repository.ussdMapDao.updateMap(it.copy(status = "ACTIVE")) 
            } 
        } 
    }
    data class OfferExportWrapper(
        val offers: List<Offer>,
        val activeMaps: List<com.example.autodial.data.UssdMap>?,
        val mapSteps: List<com.example.autodial.data.UssdMapStep>?,
        val mapOptions: List<com.example.autodial.data.UssdMapOption>?
    )

    fun exportOffersToCloud(context: Context, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                // ENSURE AUTHENTICATED OR SIGN IN ANONYMOUSLY BEFORE DISPATCHING TO FIRESTORE
                firebaseDeviceManager.ensureAuthenticated()
                
                val offers = repository.allOffers.first()
                val activeMaps = repository.ussdMapDao.getAllActiveMapsWithSteps()
                val exportedMaps = activeMaps.map { it.map }
                val exportedSteps = activeMaps.flatMap { it.steps.map { s -> s.step } }
                val exportedOptions = activeMaps.flatMap { it.steps.flatMap { s -> s.options } }
                
                val wrapper = OfferExportWrapper(offers, exportedMaps, exportedSteps, exportedOptions)
                val json = com.google.gson.Gson().toJson(wrapper)
                val code = "LIN-" + UUID.randomUUID().toString().take(6).uppercase()
                firebaseDeviceManager.uploadOfferBackup(code, json) { success ->
                    onComplete(if (success) code else null)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "exportOffersToCloud failed", e)
                onComplete(null)
            }
        }
    }
    fun importOffersFromCloud(code: String, context: Context) {
        viewModelScope.launch {
            firebaseDeviceManager.downloadOfferBackup(code) { json ->
                if (json != null) {
                    try {
                        val wrapper = try {
                            com.google.gson.Gson().fromJson(json, OfferExportWrapper::class.java)
                        } catch (e: Exception) {
                            val type = object : com.google.gson.reflect.TypeToken<List<Offer>>() {}.type
                            val fallbackOffers: List<Offer> = com.google.gson.Gson().fromJson(json, type)
                            OfferExportWrapper(fallbackOffers, null, null, null)
                        }
                        
                        viewModelScope.launch(Dispatchers.IO) {
                            var imported = 0
                            var collisionCount = 0
                            var mappingUpdates = 0
                            val existingOffers = repository.allOffers.first()
                            val oldIdToNewId = mutableMapOf<Int, Int>()
                            
                            wrapper.offers.forEach { rawOffer ->
                                // SANITIZE: Provide defaults for missing fields in old JSON formats
                                val offer = rawOffer.copy(
                                    timerEnableAt = rawOffer.timerEnableAt ?: "00:00",
                                    timerDisableAt = rawOffer.timerDisableAt ?: "23:59",
                                    ussdMode = rawOffer.ussdMode ?: Offer.USSD_SIMPLE,
                                    mainStartTime = rawOffer.mainStartTime ?: "00:00",
                                    mainEndTime = rawOffer.mainEndTime ?: "23:59",
                                    category = rawOffer.category ?: Offer.CATEGORY_DATA,
                                    paymentMode = rawOffer.paymentMode ?: Offer.MODE_BOTH
                                )
                                val existing = existingOffers.find { it.amount == offer.amount }
                                val time = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                
                                if (existing != null) {
                                    collisionCount++
                                    oldIdToNewId[rawOffer.id] = existing.id
                                    // UPGRADE: Overwrite local offer with new features from import
                                    val updatedOffer = existing.copy(
                                        name = offer.name,
                                        description = offer.description,
                                        ussdCode = offer.ussdCode,
                                        simSelection = offer.simSelection,
                                        commission = offer.commission,
                                        ussdMode = offer.ussdMode,
                                        isTimerEnabled = offer.isTimerEnabled,
                                        timerEnableAt = offer.timerEnableAt,
                                        timerDisableAt = offer.timerDisableAt,
                                        altUssdCode = offer.altUssdCode,
                                        altStartTime = offer.altStartTime,
                                        altEndTime = offer.altEndTime,
                                        isFallbackEnabled = offer.isFallbackEnabled,
                                        fallbackOfferId = offer.fallbackOfferId,
                                        notes = (existing.notes + "\n[UPGRADE] Updated features from cloud backup ($time)").trim()
                                    )
                                    repository.updateOffer(updatedOffer)

                                    val existingMap = repository.ussdMapDao.getActiveMapForOffer(existing.id)
                                    val importedMap = wrapper.activeMaps?.find { it.offerId == offer.id }
                                    
                                    if (existingMap == null && importedMap != null) {
                                        processImportedMapping(offer.id, existing.id, wrapper)
                                        mappingUpdates++
                                    }
                                    return@forEach
                                }
                                
                                val newOfferId = repository.insertOffer(offer.copy(id = 0, notes = "[IMPORT] New offer added from backup ($time)"))
                                oldIdToNewId[rawOffer.id] = newOfferId.toInt()
                                imported++
                                processImportedMapping(offer.id, newOfferId.toInt(), wrapper)
                            }

                            // Re-link fallback offer IDs to correct local offer IDs
                            relinkImportedFallbacks(wrapper.offers, oldIdToNewId)

                            withContext(Dispatchers.Main) {
                                val msg = "Imported $imported new offers. $collisionCount collisions ($mappingUpdates mappings recovered)."
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Cloud Import parse failed", e)
                        android.widget.Toast.makeText(context, "Cloud Import Failed: Corrupt data", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Cloud Import Failed: Invalid or expired code", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun processImportedMapping(oldOfferId: Int, newOfferId: Int, wrapper: OfferExportWrapper) {
        wrapper.activeMaps?.find { it.offerId == oldOfferId }?.let { map ->
            val oldMapId = map.id
            val newMapId = repository.ussdMapDao.insertMap(map.copy(id = 0, offerId = newOfferId))
            
            wrapper.mapSteps?.filter { it.mapId == oldMapId }?.forEach { step ->
                val oldStepId = step.id
                val newStepId = repository.ussdMapDao.insertStep(step.copy(id = 0, mapId = newMapId.toInt()))
                
                wrapper.mapOptions?.filter { it.stepId == oldStepId }?.forEach { option ->
                    repository.ussdMapDao.insertOption(option.copy(id = 0, stepId = newStepId.toInt()))
                }
            }
        }
    }

    fun exportOffers(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val offers = repository.allOffers.first()
                val activeMaps = repository.ussdMapDao.getAllActiveMapsWithSteps()
                val exportedMaps = activeMaps.map { it.map }
                val exportedSteps = activeMaps.flatMap { it.steps.map { s -> s.step } }
                val exportedOptions = activeMaps.flatMap { it.steps.flatMap { s -> s.options } }
                
                val wrapper = OfferExportWrapper(offers, exportedMaps, exportedSteps, exportedOptions)
                val json = com.google.gson.Gson().toJson(wrapper)
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Export failed", e)
            }
        }
    }
    private suspend fun relinkImportedFallbacks(
        importedOffers: List<Offer>,
        oldIdToNewId: Map<Int, Int>
    ) {
        val currentLocalOffers = repository.allOffers.first()
        
        importedOffers.forEach { rawOffer ->
            if (rawOffer.isFallbackEnabled || rawOffer.fallbackOfferId != null) {
                val localOfferId = oldIdToNewId[rawOffer.id] ?: return@forEach
                val localOffer = currentLocalOffers.find { it.id == localOfferId } ?: return@forEach
                
                val targetFallbackOldId = rawOffer.fallbackOfferId
                var resolvedLocalFallbackId: Int? = null
                
                if (targetFallbackOldId != null) {
                    resolvedLocalFallbackId = oldIdToNewId[targetFallbackOldId]
                    
                    if (resolvedLocalFallbackId == null) {
                        val exportedTarget = importedOffers.find { it.id == targetFallbackOldId }
                        if (exportedTarget != null) {
                            resolvedLocalFallbackId = currentLocalOffers.find { it.amount == exportedTarget.amount }?.id
                        }
                    }
                }
                
                if (resolvedLocalFallbackId != localOffer.fallbackOfferId) {
                    repository.updateOffer(localOffer.copy(
                        isFallbackEnabled = rawOffer.isFallbackEnabled && resolvedLocalFallbackId != null,
                        fallbackOfferId = resolvedLocalFallbackId
                    ))
                }
            }
        }
    }

    fun importOffers(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    val wrapper = try {
                        com.google.gson.Gson().fromJson(json, OfferExportWrapper::class.java)
                    } catch (e: Exception) {
                        val type = object : com.google.gson.reflect.TypeToken<List<Offer>>() {}.type
                        val fallbackOffers: List<Offer> = com.google.gson.Gson().fromJson(json, type)
                        OfferExportWrapper(fallbackOffers, null, null, null)
                    }
                    
                    var imported = 0
                    var collisionCount = 0
                    var mappingUpdates = 0
                    val existingOffers = repository.allOffers.first()
                    val oldIdToNewId = mutableMapOf<Int, Int>()
                    
                    wrapper.offers.forEach { rawOffer ->
                        // SANITIZE: Provide defaults for missing fields in old JSON formats
                        val offer = rawOffer.copy(
                            timerEnableAt = rawOffer.timerEnableAt ?: "00:00",
                            timerDisableAt = rawOffer.timerDisableAt ?: "23:59",
                            ussdMode = rawOffer.ussdMode ?: Offer.USSD_SIMPLE,
                            mainStartTime = rawOffer.mainStartTime ?: "00:00",
                            mainEndTime = rawOffer.mainEndTime ?: "23:59",
                            category = rawOffer.category ?: Offer.CATEGORY_DATA,
                            paymentMode = rawOffer.paymentMode ?: Offer.MODE_BOTH
                        )
                        val existing = existingOffers.find { it.amount == offer.amount }
                        val time = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                        
                        if (existing != null) {
                            collisionCount++
                            oldIdToNewId[rawOffer.id] = existing.id
                            // UPGRADE: Overwrite local offer with new features from import
                            val updatedOffer = existing.copy(
                                name = offer.name,
                                description = offer.description,
                                ussdCode = offer.ussdCode,
                                simSelection = offer.simSelection,
                                commission = offer.commission,
                                ussdMode = offer.ussdMode,
                                isTimerEnabled = offer.isTimerEnabled,
                                timerEnableAt = offer.timerEnableAt,
                                timerDisableAt = offer.timerDisableAt,
                                altUssdCode = offer.altUssdCode,
                                altStartTime = offer.altStartTime,
                                altEndTime = offer.altEndTime,
                                isFallbackEnabled = offer.isFallbackEnabled,
                                fallbackOfferId = offer.fallbackOfferId,
                                notes = (existing.notes + "\n[UPGRADE] Updated features from backup file ($time)").trim()
                            )
                            repository.updateOffer(updatedOffer)

                            val existingMap = repository.ussdMapDao.getActiveMapForOffer(existing.id)
                            val importedMap = wrapper.activeMaps?.find { it.offerId == offer.id }
                            
                            if (existingMap == null && importedMap != null) {
                                processImportedMapping(offer.id, existing.id, wrapper)
                                mappingUpdates++
                            }
                            return@forEach
                        }
                        
                        val newOfferId = repository.insertOffer(offer.copy(id = 0, notes = "[IMPORT] New offer added from backup ($time)"))
                        oldIdToNewId[rawOffer.id] = newOfferId.toInt()
                        imported++
                        processImportedMapping(offer.id, newOfferId.toInt(), wrapper)
                    }

                    // Re-link fallback offer IDs to correct local offer IDs
                    relinkImportedFallbacks(wrapper.offers, oldIdToNewId)
                    
                    withContext(Dispatchers.Main) {
                        val msg = "Imported $imported new offers. $collisionCount collisions ($mappingUpdates mappings recovered)."
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to read file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Import failed", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    fun setSelectedCategory(category: String) { _selectedCategory.value = category }
    fun toggleOffer(offer: Offer, enabled: Boolean) { viewModelScope.launch { repository.updateOffer(offer.copy(isEnabled = enabled)) } }
    fun isPriceDuplicate(amount: Double): Boolean {
        return allOffers.value?.any { it.amount == amount } ?: false
    }
    fun setTokenIdFromSim(slot: Int, context: Context) {
        UssdHelper.sendBackgroundUssd(context, "*100*4*1#", slot) { response ->
            if (response != null) {
                // Parse phone number from response (e.g., "Your number is 254712345678")
                val phonePattern = java.util.regex.Pattern.compile("(?:254|0)?([17][0-9]{8})")
                val matcher = phonePattern.matcher(response)
                if (matcher.find()) {
                    val rawPhone = matcher.group(0) ?: ""
                    val formatted = UssdSessionManager.formatPhone(rawPhone)
                    preferenceManager.setUniqueDeviceId(formatted)
                    android.widget.Toast.makeText(context, "Agent ID updated to $formatted", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    fun restoreWithId(id: String, context: Context) {
        viewModelScope.launch {
            firebaseDeviceManager.restoreAccountData(id) { success, tokens, remainingMs ->
                if (success) {
                    refreshTokens()
                    firebaseDeviceManager.triggerSync()
                    val msg = buildRestoredString(tokens, remainingMs)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "Invalid Device ID", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun scheduleTask(task: ScheduledTask) { viewModelScope.launch { repository.insertScheduledTask(task) } }
    fun updateEngageSessionTime(session: EngageSession, time: Long) {
        viewModelScope.launch {
            repository.engageSessionDao.update(session.copy(nextEligibleDate = time))
        }
    }
    fun deleteEngageSession(session: EngageSession) { viewModelScope.launch { repository.engageSessionDao.delete(session) } }
    fun deleteContact(contact: Contact) { viewModelScope.launch { repository.contactDao.deleteContact(contact) } }
    fun processAllUnmatched(context: Context) {
        viewModelScope.launch {
            val transactions = repository.allTransactions.first().filter { it.status == Transaction.STATUS_UNMATCHED }
            transactions.forEach { txn ->
                val offer = repository.findMatchingOffer(txn.amount)
                if (offer != null) {
                    repository.updateTransaction(txn.copy(
                        status = Transaction.STATUS_WAITING,
                        offerName = offer.name,
                        offerId = offer.id
                    ))
                }
            }
            TransactionQueueManager.processNext(context)
        }
    }
    fun runOfferManually(offer: Offer, phone: String, context: Context, forcedSim: Int) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = offer.amount,
                offerName = offer.name,
                offerId = offer.id,
                senderName = "MANUAL RUN",
                customerNumber = phone,
                status = Transaction.STATUS_WAITING,
                simUsed = forcedSim,
                usedUssdType = "MAIN"
            )
            val id = repository.insertTransaction(transaction)
            TransactionQueueManager.processNext(context, immediate = true)
        }
    }
    fun deleteRelayConfig(config: RelayConfig) { viewModelScope.launch { repository.relayConfigDao.deleteConfig(config) } }
    fun toggleRelayConfig(config: RelayConfig, enabled: Boolean) { viewModelScope.launch { repository.relayConfigDao.updateConfig(config.copy(isEnabled = enabled)) } }
    fun saveRelayConfig(config: RelayConfig) { viewModelScope.launch { if (config.id == 0) repository.relayConfigDao.insertConfig(config) else repository.relayConfigDao.updateConfig(config) } }
    fun buyTokens(amount: Int, unitsToAdd: Int, isUnlimited: Boolean, durationDays: Int, simSlot: Int, context: Context) {
        // Hardcoded admin number for token purchases as requested
        val adminPhone = "0700822857"
        val ussd = "*140*$amount*$adminPhone#"
        
        viewModelScope.launch {
            com.example.autodial.util.UssdSessionManager.startSession(ussdCode = ussd, simSlot = simSlot, isDirectDial = true)
            com.example.autodial.util.UssdSessionManager.isTokenPurchase = true
            com.example.autodial.util.UssdSessionManager.tokenAmount = unitsToAdd
            com.example.autodial.util.UssdSessionManager.isUnlimitedPurchase = isUnlimited
            com.example.autodial.util.UssdSessionManager.purchaseDurationDays = durationDays

            UssdHelper.sendBackgroundUssd(context, ussd, simSlot) { response ->
                if (response != null) {
                    val awarded = com.example.autodial.util.TokenAwardHelper.processResponse(context, response, unitsToAdd)
                    if (awarded) {
                        refreshTokens()
                    } else {
                        android.widget.Toast.makeText(context, "Airtime transfer initiated: $response", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    fun redeemPromocode(code: String, context: Context) {
        viewModelScope.launch {
            firebaseDeviceManager.redeemPromocode(code) { result ->
                result.onSuccess { tokens ->
                    refreshTokens()
                    android.widget.Toast.makeText(context, "Redeemed $tokens units!", android.widget.Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    android.widget.Toast.makeText(context, "Redeem failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    fun manualSync() {
        firebaseDeviceManager.triggerSync()
    }
    fun backupTokensToCloud(context: Context, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            _isBackingUp.value = true
            try {
                val amount = preferenceManager.getTokens()
                val expiry = preferenceManager.getUnlimitedExpiry()
                
                // INSTANT NON-BLOCKING BACKUP CODE GENERATION
                firebaseDeviceManager.createTokenBackup(amount, expiry) { result ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _isBackingUp.value = false
                        result.onSuccess { code ->
                            refreshTokens()
                            android.widget.Toast.makeText(context, "Backup code generated: $code", android.widget.Toast.LENGTH_LONG).show()
                            onComplete(code)
                        }.onFailure { e ->
                            android.widget.Toast.makeText(context, "Backup failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            onComplete(null)
                        }
                    }
                }

                // Background fire-and-forget cloud authentication and sync
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        firebaseDeviceManager.ensureAuthenticated()
                        firebaseDeviceManager.triggerSync()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Background sync warning: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "backupTokensToCloud failed", e)
                withContext(Dispatchers.Main) {
                    _isBackingUp.value = false
                    android.widget.Toast.makeText(context, "Backup error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    onComplete(null)
                }
            }
        }
    }

    private fun buildRestoredString(units: Int, durationMs: Long): String {
        val hasUnits = units > 0
        val hasTime = durationMs > 0
        
        return when {
            hasUnits && hasTime -> {
                val hours = durationMs / (1000 * 60 * 60)
                val timeStr = if (hours >= 24) "${hours / 24} days" else "$hours hours"
                "Successfully restored $units units and $timeStr unlimited plan!"
            }
            hasUnits -> "Successfully restored $units units!"
            hasTime -> {
                val hours = durationMs / (1000 * 60 * 60)
                val timeStr = if (hours >= 24) "${hours / 24} days" else "$hours hours"
                "Successfully restored $timeStr unlimited plan!"
            }
            else -> "Account restored (No active units or time)."
        }
    }

    fun restoreTokensFromBackupCode(code: String, context: Context, onComplete: (Boolean, String) -> Unit = {_,_ ->}) {
        viewModelScope.launch {
            _isRestoring.value = true
            try {
                val rawCode = code.trim().uppercase()
                if (rawCode.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _isRestoring.value = false
                        val msg = "Please enter a valid backup code"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        onComplete(false, msg)
                    }
                    return@launch
                }
                val finalCode = if (!rawCode.startsWith("BK-")) "BK-$rawCode" else rawCode
                
                // Instant Fast-Path: Check local backup store first (0ms delay)
                val localBackup = preferenceManager.getLocalBackup(finalCode)
                if (localBackup != null) {
                    preferenceManager.markLocalBackupUsed(finalCode)
                    if (localBackup.first > 0) preferenceManager.addTokens(localBackup.first)
                    val dur = maxOf(0L, localBackup.second - System.currentTimeMillis())
                    if (dur > 0) {
                        val curr = maxOf(preferenceManager.getUnlimitedExpiry(), System.currentTimeMillis())
                        preferenceManager.setUnlimitedExpiry(curr + dur)
                    }
                    refreshTokens()
                    _isRestoring.value = false
                    val msg = buildRestoredString(localBackup.first, dur) + " (Restored)"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    onComplete(true, msg)
                    
                    // Asynchronously update cloud backup state in background
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            firebaseDeviceManager.restoreFromBackup(finalCode) {}
                        } catch (e: Exception) { Log.w("MainViewModel", "Background cloud restore sync: ${e.message}") }
                    }
                    return@launch
                }

                // Cloud-Path: Attempt cloud restore for new/transferred device
                firebaseDeviceManager.restoreFromBackup(finalCode) { result ->
                    viewModelScope.launch(Dispatchers.Main) {
                        result.onSuccess { pair ->
                            _isRestoring.value = false
                            val restoredUnits = pair.first
                            val durationMs = pair.second

                            if (restoredUnits > 0) {
                                preferenceManager.addTokens(restoredUnits)
                            }
                            if (durationMs > 0) {
                                val currentExpiry = maxOf(preferenceManager.getUnlimitedExpiry(), System.currentTimeMillis())
                                preferenceManager.setUnlimitedExpiry(currentExpiry + durationMs)
                            }
                            refreshTokens()
                            firebaseDeviceManager.triggerSync()

                            val msg = buildRestoredString(restoredUnits, durationMs)
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            onComplete(true, msg)
                        }.onFailure { err ->
                            // Fallback 1: System Device ID restore
                            firebaseDeviceManager.restoreAccountData(rawCode) { success, tokens, remainingMs ->
                                viewModelScope.launch(Dispatchers.Main) {
                                    _isRestoring.value = false
                                    if (success) {
                                        refreshTokens()
                                        val msg = buildRestoredString(tokens, remainingMs) + " (System Restored)"
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                        onComplete(true, msg)
                                    } else {
                                        val msg = "Restore failed: Invalid code or Device ID not found"
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                        onComplete(false, msg)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isRestoring.value = false
                    val msg = "Restore error: ${e.message}"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    onComplete(false, msg)
                }
            }
        }
    }

    fun initiateMpesaPayment(phone: String, packageName: String, price: Int, unitsToAdd: Int, isUnlimited: Boolean, durationDays: Int, context: Context) {
        triggerStkPush(phone, packageName, price, unitsToAdd, isUnlimited, durationDays, context)
    }
    fun updateTransactionNotes(id: Int, notes: String) { viewModelScope.launch { repository.getTransactionById(id)?.let { repository.updateTransaction(it.copy(notes = notes)) } } }
    fun retryTransactions(ids: List<Int>, context: Context) { 
        viewModelScope.launch { 
            TransactionQueueManager.forceResetLocks()
            ids.forEach { id -> 
                repository.getTransactionById(id)?.let { 
                    repository.updateTransaction(it.copy(
                        status = Transaction.STATUS_WAITING,
                        processingStartedAt = 0L
                    )) 
                } 
            }
            TransactionQueueManager.processNext(context, immediate = true)
        } 
    }
    fun deleteTransactions(ids: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTransactions(ids)
        }
    }
    fun runAlternativeOffer(txn: Transaction, offer: Offer, context: Context) {
        viewModelScope.launch {
            repository.updateTransaction(txn.copy(
                status = Transaction.STATUS_WAITING,
                offerId = offer.id,
                offerName = offer.name,
                isSwitched = true,
                originalOfferId = txn.offerId,
                originalOfferName = txn.offerName,
                switchReason = "USER_ALT_SELECTION"
            ))
            TransactionQueueManager.processNext(context, immediate = true)
        }
    }
    fun runOfferWithAltNumber(txn: Transaction, phone: String, context: Context) {
        viewModelScope.launch {
            repository.updateTransaction(txn.copy(
                status = Transaction.STATUS_WAITING,
                customerNumber = phone,
                notes = txn.notes + "\n[MANUAL] Number overridden by user: $phone"
            ))
            TransactionQueueManager.processNext(context, immediate = true)
        }
    }
    fun deleteMap(mapId: Int) { viewModelScope.launch { repository.ussdMapDao.getMapById(mapId)?.let { repository.ussdMapDao.deleteMap(it) } } }
    fun updateTransactionsStatus(ids: List<Int>, status: String) { 
        viewModelScope.launch(Dispatchers.IO) { 
            if (ids.isNotEmpty()) {
                repository.updateTransactionsStatus(ids, status)
                if (status == Transaction.STATUS_WAITING) {
                    TransactionQueueManager.processNext(getApplication(), immediate = true)
                }
            }
        } 
    }
    fun processAllWaiting(context: Context) {
        viewModelScope.launch {
            TransactionQueueManager.clearHold()
            TransactionQueueManager.processNext(context, immediate = true)
        }
    }
    fun refreshAirtime(slot: Int, context: Context) {
        val label = when (slot) {
            0 -> "SIM 1"
            1 -> "SIM 2"
            else -> "SIM 1 & SIM 2"
        }
        android.widget.Toast.makeText(context, "Refreshing $label airtime balance...", android.widget.Toast.LENGTH_SHORT).show()

        if (slot == 2) {
            refreshSingleSimSlot(0, context) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    refreshSingleSimSlot(1, context)
                }
            }
        } else {
            refreshSingleSimSlot(slot, context)
        }
    }

    private fun refreshSingleSimSlot(slot: Int, context: Context, onComplete: (() -> Unit)? = null) {
        UssdHelper.sendBackgroundUssd(context, "*144#", slot, allowFallback = false) { response ->
            val parsedBalance = UssdHelper.extractBalanceFromResponse(response)
            if (parsedBalance != null) {
                viewModelScope.launch {
                    preferenceManager.setAirtimeBalance(slot, parsedBalance)
                    if (slot == 0) _airtimeSim1.value = parsedBalance
                    else if (slot == 1) _airtimeSim2.value = parsedBalance

                    android.widget.Toast.makeText(
                        context,
                        "SIM ${slot + 1} Balance Updated: KSh $parsedBalance",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    com.example.autodial.util.FirebaseDeviceManager.getExistingInstance()?.triggerSync()
                    onComplete?.invoke()
                }
            } else {
                viewModelScope.launch {
                    if (response != null && response.isNotBlank()) {
                        android.widget.Toast.makeText(
                            context,
                            "USSD SIM ${slot + 1}: $response",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Unable to refresh SIM ${slot + 1} balance automatically",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    onComplete?.invoke()
                }
            }
        }
    }

    fun setAutoRefreshConfig(context: Context, enabled: Boolean, simSlot: Int, intervalMins: Int) {
        preferenceManager.setAutoRefreshAirtimeEnabled(enabled)
        preferenceManager.setAutoRefreshSimSlot(simSlot)
        preferenceManager.setAutoRefreshIntervalMins(intervalMins)
        
        if (enabled) {
            com.example.autodial.util.AirtimeRefreshWorker.schedule(context, intervalMins)
            // Immediately trigger airtime update for selected SIM
            refreshAirtime(simSlot, context)
        } else {
            com.example.autodial.util.AirtimeRefreshWorker.cancel(context)
        }
    }
    fun markTransactionComplete(id: Int) { updateTransactionsStatus(listOf(id), Transaction.STATUS_SUCCESS) }
    fun saveTopUpPlan(plan: TopUpPlan) { viewModelScope.launch { if (plan.id == 0) repository.topUpPlanDao.insert(plan) else repository.topUpPlanDao.update(plan) } }
    fun deleteTopUpPlan(plan: TopUpPlan) { viewModelScope.launch { repository.topUpPlanDao.delete(plan) } }
    fun deleteMap(map: UssdMap) { viewModelScope.launch { repository.ussdMapDao.deleteMap(map) } }
    fun importDeviceContacts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val deviceContacts = ContactsHelper.readDeviceContacts(context)
                var newClients = 0
                var alreadyDone = 0
                val database = AppDatabase.getDatabase(context)
                val clientDao = database.clientDao()

                deviceContacts.forEach { (name, phone) ->
                    var cleanPhone = phone.replace(Regex("[^0-9]"), "")
                    if (cleanPhone.startsWith("254")) {
                        cleanPhone = "0" + cleanPhone.drop(3)
                    } else if (!cleanPhone.startsWith("0") && (cleanPhone.startsWith("7") || cleanPhone.startsWith("1"))) {
                        cleanPhone = "0$cleanPhone"
                    }
                    if (cleanPhone.length >= 10) {
                        val existing = clientDao.getClientByPhone(cleanPhone)
                        if (existing == null) {
                            clientDao.insertClient(Client(name = name, phoneNumber = cleanPhone, dateAdded = System.currentTimeMillis()))
                            newClients++
                        } else {
                            alreadyDone++
                            if (existing.name.isBlank() || existing.name == "UNKNOWN") {
                                clientDao.updateClient(existing.copy(name = name))
                            }
                        }
                    }
                }

                _scanResult.value = "CONTACTS_SYNCED|Total:${deviceContacts.size}|New:$newClients|Existing:$alreadyDone"
            } catch (e: Exception) {
                Log.e("MainViewModel", "Import device contacts failed", e)
                _scanResult.value = "ERROR|${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanMpesaInbox(context: Context, hours: Int, importContactsOnly: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val cutoff = System.currentTimeMillis() - (hours * 3600000L)
                val uri = android.net.Uri.parse("content://sms/inbox")
                val projection = arrayOf("address", "body", "date")
                val selection = "date > ? AND (address LIKE '%MPESA%' OR address LIKE '%M-PESA%')"
                val selectionArgs = arrayOf(cutoff.toString())
                
                var total = 0
                var matched = 0
                var unmatched = 0
                var alreadyDone = 0
                var processedAmt = 0.0
                var unmatchedAmt = 0.0
                var newClients = 0

                context.contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")?.use { cursor ->
                    val addrIdx = cursor.getColumnIndex("address")
                    val bodyIdx = cursor.getColumnIndex("body")
                    val dateIdx = cursor.getColumnIndex("date")

                    while (cursor.moveToNext()) {
                        total++
                        val address = cursor.getString(addrIdx)
                        val body = cursor.getString(bodyIdx)
                        val date = cursor.getLong(dateIdx)

                        val details = MpesaParser.parse(body)
                        if (details != null) {
                            val existing = repository.getTransactionByCode(details.transactionCode)
                            if (existing != null) {
                                alreadyDone++
                                continue
                            }

                            val txn = Transaction(
                                date = date,
                                amount = details.amount,
                                transactionCode = details.transactionCode,
                                customerNumber = details.customerNumber,
                                senderName = details.sender,
                                status = Transaction.STATUS_IMPORTED,
                                rawMessage = body
                            )
                            
                            val clientCreated = ClientManager.upsertClientFromTransaction(context, txn)
                            if (clientCreated) newClients++

                            if (!importContactsOnly) {
                                val offer = repository.findMatchingOffer(details.amount)
                                if (offer != null) {
                                    repository.insertTransaction(txn.copy(
                                        status = Transaction.STATUS_WAITING,
                                        offerName = offer.name,
                                        offerId = offer.id
                                    ))
                                    matched++
                                    processedAmt += details.amount
                                } else {
                                    repository.insertTransaction(txn.copy(status = Transaction.STATUS_UNMATCHED))
                                    unmatched++
                                    unmatchedAmt += details.amount
                                }
                            }
                        }
                    }
                }
                
                _scanResult.value = if (importContactsOnly) {
                    "CONTACTS_SYNCED|Total:$total|New:$newClients|Already:$alreadyDone"
                } else {
                    "COMPLETED|Total:$total|Matched:$matched|Unmatched:$unmatched|Already:$alreadyDone|ProcessedAmt:$processedAmt|UnmatchedAmt:$unmatchedAmt|NewClients:$newClients"
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Scan failed", e)
                _scanResult.value = "FAILED|${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }
    fun clearScanResult() { _scanResult.value = null }

    val receivedRelays: LiveData<List<Transaction>> = allTransactions.map { list -> 
        list.filter { it.senderName == "RELAY FROM MAIN" }.take(20) 
    }
    private fun getTotalStorageSize(): Long {
        val dbSize = AppDatabase.getDatabase(getApplication()).getDatabaseSize(getApplication())
        return dbSize + getDirSize(getApplication<Application>().cacheDir)
    }
    private fun getDirSize(dir: java.io.File): Long {
        var size: Long = 0
        if (dir.exists() && dir.isDirectory) dir.listFiles()?.forEach { file -> size += if (file.isFile) file.length() else getDirSize(file) }
        return size
    }
    fun refreshDatabaseSize() { viewModelScope.launch(Dispatchers.IO) { _databaseSize.value = AppDatabase.getDatabase(getApplication()).getDatabaseSize(getApplication()) } }
    fun fixStuckTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getProcessingTransactions().forEach { txn ->
                if (System.currentTimeMillis() - txn.processingStartedAt > 600000) repository.updateTransaction(txn.copy(status = Transaction.STATUS_FAILED))
            }
            com.example.autodial.util.TransactionQueueManager.resetProcessingLock()
        }
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: MainRepository,
    private val mpesaRepository: MpesaRepository,
    private val preferenceManager: PreferenceManager,
    private val firebaseDeviceManager: FirebaseDeviceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository, mpesaRepository, preferenceManager, firebaseDeviceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
