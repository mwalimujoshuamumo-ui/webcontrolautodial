package com.example.autodial.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

class PreferenceManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("autodial_prefs", Context.MODE_PRIVATE)

    fun getContext(): Context = context

    fun isAutomationEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMATION_ENABLED, false) && isGlobalAutomationEnabled()
    fun setAutomationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTOMATION_ENABLED, enabled).apply()

    fun isGlobalAutomationEnabled(): Boolean = prefs.getBoolean(KEY_GLOBAL_AUTOMATION, true)
    fun setGlobalAutomationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_GLOBAL_AUTOMATION, enabled).apply()

    fun isAnnouncementActive(): Boolean = prefs.getBoolean(KEY_ANNOUNCEMENT_ACTIVE, false)
    fun setAnnouncementActive(active: Boolean) = prefs.edit().putBoolean(KEY_ANNOUNCEMENT_ACTIVE, active).apply()

    fun getAnnouncementTitle(): String = prefs.getString(KEY_ANNOUNCEMENT_TITLE, "") ?: ""
    fun setAnnouncementTitle(title: String) = prefs.edit().putString(KEY_ANNOUNCEMENT_TITLE, title).apply()

    fun getAnnouncementMessage(): String = prefs.getString(KEY_ANNOUNCEMENT_MSG, "") ?: ""
    fun setAnnouncementMessage(msg: String) = prefs.edit().putString(KEY_ANNOUNCEMENT_MSG, msg).apply()

    fun getLastAnnouncementHash(): String = prefs.getString(KEY_LAST_ANNOUNCEMENT_HASH, "") ?: ""
    fun setLastAnnouncementHash(hash: String) = prefs.edit().putString(KEY_LAST_ANNOUNCEMENT_HASH, hash).apply()

    fun getSimpleModeTimeout(): Int = prefs.getInt(KEY_SIMPLE_TIMEOUT, 10)
    fun setSimpleModeTimeout(seconds: Int) = prefs.edit().putInt(KEY_SIMPLE_TIMEOUT, seconds).apply()

    fun getAdvancedModeTimeout(): Int = prefs.getInt(KEY_ADVANCED_TIMEOUT, 30)
    fun setAdvancedModeTimeout(seconds: Int) = prefs.edit().putInt(KEY_ADVANCED_TIMEOUT, seconds).apply()


    fun getScanSpanHours(): Int = prefs.getInt(KEY_SCAN_SPAN, 1)
    fun setScanSpanHours(hours: Int) = prefs.edit().putInt(KEY_SCAN_SPAN, hours).apply()

    fun getUssdResponseDelay(): Long = prefs.getLong(KEY_USSD_DELAY, 0L)
    fun setUssdResponseDelay(delay: Long) = prefs.edit().putLong(KEY_USSD_DELAY, delay).apply()

    fun getQueueCooldownDelay(): Long = prefs.getLong(KEY_QUEUE_COOLDOWN_DELAY, 100L)
    fun setQueueCooldownDelay(delay: Long) = prefs.edit().putLong(KEY_QUEUE_COOLDOWN_DELAY, delay).apply()

    fun getAppTheme(): String = prefs.getString(KEY_APP_THEME, "Dark") ?: "Dark"
    fun setAppTheme(theme: String) = prefs.edit().putString(KEY_APP_THEME, theme).apply()

    fun isDataHidden(): Boolean = prefs.getBoolean(KEY_DATA_HIDDEN, false)
    fun setDataHidden(hidden: Boolean) = prefs.edit().putBoolean(KEY_DATA_HIDDEN, hidden).apply()

    // Device Identity & Agent Profile
    fun getUniqueDeviceId(): String = getPhysicalDeviceId()

    /**
     * Permanent hardware-linked ID. Survives clearAccountData and logouts.
     * This is the PRIMARY identity of the phone.
     */
    fun getPhysicalDeviceId(): String {
        val existing = prefs.getString(KEY_PERMANENT_DEVICE_ID, "") ?: ""
        if (existing.isNotEmpty()) return existing
        
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val permanentId = if (androidId.isNotEmpty()) {
            "AD-HW-${androidId.takeLast(12)}".uppercase()
        } else {
            "AD-P-" + java.util.UUID.randomUUID().toString().take(12).uppercase()
        }
        
        prefs.edit().putString(KEY_PERMANENT_DEVICE_ID, permanentId).apply()
        return permanentId
    }

    /**
     * The Agent ID / Token ID / Phone Number associated with this session.
     * This is NOT the device identity.
     */
    fun getAgentId(): String = prefs.getString(KEY_AGENT_ID, "") ?: ""
    fun setAgentId(id: String) = prefs.edit().putString(KEY_AGENT_ID, id).apply()
    
    fun setUniqueDeviceId(id: String) {
        // We keep this for backward compatibility in the app logic but redirect to getAgentId
        setAgentId(id)
        prefs.edit().putBoolean(KEY_ID_USER_SET, true).apply()
    }
    fun isIdUserSet(): Boolean = prefs.getBoolean(KEY_ID_USER_SET, false)

    fun getDeviceRole(): String = prefs.getString(KEY_DEVICE_ROLE, "STANDALONE") ?: "STANDALONE"
    fun setDeviceRole(role: String) = prefs.edit().putString(KEY_DEVICE_ROLE, role).apply()

    fun getRelayAmounts(): String = prefs.getString(KEY_RELAY_AMOUNTS, "") ?: ""
    fun setRelayAmounts(amounts: String) = prefs.edit().putString(KEY_RELAY_AMOUNTS, amounts).apply()

    fun getLastConnectedIp(): String = prefs.getString(KEY_LAST_IP, "") ?: ""
    fun setLastConnectedIp(ip: String) = prefs.edit().putString(KEY_LAST_IP, ip).apply()

    fun clearAccountData() {
        prefs.edit()
            .remove(KEY_TOKENS)
            .remove(KEY_UNLIMITED_EXPIRY)
            .apply()
    }

    fun getAirtimeBalance(sim: Int): String = prefs.getString("airtime_sim_$sim", "0.00") ?: "0.00"
    fun setAirtimeBalance(sim: Int, balance: String) = prefs.edit().putString("airtime_sim_$sim", balance).apply()

    fun getTokens(): Int = prefs.getInt(KEY_TOKENS, 0)
    fun setTokens(tokens: Int) = prefs.edit().putInt(KEY_TOKENS, tokens).apply()
    fun addTokens(count: Int) = setTokens(getTokens() + count)
    
    fun getUnlimitedExpiry(): Long = prefs.getLong(KEY_UNLIMITED_EXPIRY, 0L)
    fun setUnlimitedExpiry(expiry: Long) = prefs.edit().putLong(KEY_UNLIMITED_EXPIRY, expiry).apply()
    fun isUnlimitedActive(): Boolean = getUnlimitedExpiry() > System.currentTimeMillis()

    fun useToken(): Boolean {
        if (!isUnitDeductionEnabled()) return true // No deduction for testing phase
        if (isUnlimitedActive()) return true // No deduction if unlimited package is active
        
        val current = getTokens()
        return if (current > 0) {
            setTokens(current - 1)
            true
        } else false
    }

    fun isUnitDeductionEnabled(): Boolean = prefs.getBoolean(KEY_DEDUCTION_ENABLED, true)
    fun setUnitDeductionEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DEDUCTION_ENABLED, enabled).apply()

    // Relay Settings
    fun isRelayEnabled(): Boolean = prefs.getBoolean(KEY_RELAY_ENABLED, false)
    fun setRelayEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_RELAY_ENABLED, enabled).apply()

    fun getRelayTargetNumber(): String = prefs.getString(KEY_RELAY_TARGET, "") ?: ""
    fun setRelayTargetNumber(number: String) = prefs.edit().putString(KEY_RELAY_TARGET, number).apply()

    fun getRelaySimSlot(): Int = prefs.getInt(KEY_RELAY_SIM, 0)
    fun setRelaySimSlot(slot: Int) = prefs.edit().putInt(KEY_RELAY_SIM, slot).apply()

    fun getRelayOfferIds(): Set<String> = prefs.getStringSet(KEY_RELAY_OFFERS, emptySet()) ?: emptySet()
    fun setRelayOfferIds(ids: Set<String>) = prefs.edit().putStringSet(KEY_RELAY_OFFERS, ids).apply()

    fun getNotificationSimSlot(): Int = prefs.getInt(KEY_NOTIF_SIM, -1)
    fun setNotificationSimSlot(slot: Int) = prefs.edit().putInt(KEY_NOTIF_SIM, slot).apply()

    fun isEngageBotEnabled(): Boolean = prefs.getBoolean(KEY_ENGAGE_BOT, false)
    fun setEngageBotEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENGAGE_BOT, enabled).apply()

    /** When true, top-ups must match the plan amount exactly (overpays are rejected). */
    fun isEngageExactTopUpMatch(): Boolean = prefs.getBoolean(KEY_ENGAGE_EXACT_TOPUP, true)
    fun setEngageExactTopUpMatch(exact: Boolean) = prefs.edit().putBoolean(KEY_ENGAGE_EXACT_TOPUP, exact).apply()

    fun isKeyboardSuppressionEnabled(): Boolean = prefs.getBoolean(KEY_KEYBOARD_SUPPRESSION, true)
    fun setKeyboardSuppressionEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_KEYBOARD_SUPPRESSION, enabled).apply()

    // Storage Management
    fun isAutoCleanupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CLEANUP_ENABLED, true)
    fun setAutoCleanupEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_CLEANUP_ENABLED, enabled).apply()

    fun getRetentionDays(): Int = prefs.getInt(KEY_RETENTION_DAYS, 14)
    fun setRetentionDays(days: Int) = prefs.edit().putInt(KEY_RETENTION_DAYS, days).apply()

    fun isPruningEnabled(): Boolean = prefs.getBoolean(KEY_PRUNING_ENABLED, true)
    fun setPruningEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PRUNING_ENABLED, enabled).apply()

    fun isAutosaveContactsEnabled(): Boolean = prefs.getBoolean(KEY_AUTOSAVE_CONTACTS, false)
    fun setAutosaveContactsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTOSAVE_CONTACTS, enabled).apply()

    fun getAutosavePrefix(): String = prefs.getString(KEY_AUTOSAVE_PREFIX, "Auto") ?: "Auto"
    fun setAutosavePrefix(prefix: String) = prefs.edit().putString(KEY_AUTOSAVE_PREFIX, prefix).apply()

    fun getUnmatchedRetentionDays(): Int = prefs.getInt(KEY_UNMATCHED_RETENTION, 3)
    fun setUnmatchedRetentionDays(days: Int) = prefs.edit().putInt(KEY_UNMATCHED_RETENTION, days).apply()

    fun isFirstRun(): Boolean = prefs.getBoolean(KEY_FIRST_RUN, true)
    fun setFirstRun(firstRun: Boolean) = prefs.edit().putBoolean(KEY_FIRST_RUN, firstRun).apply()

    fun isUserRegistered(): Boolean = prefs.getBoolean(KEY_USER_REGISTERED, false)
    fun setUserRegistered(registered: Boolean) = prefs.edit().putBoolean(KEY_USER_REGISTERED, registered).apply()

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun setUserName(name: String) = prefs.edit().putString(KEY_USER_NAME, name).apply()

    fun getRegisteredEmail(): String = prefs.getString(KEY_REGISTERED_EMAIL, "") ?: ""
    fun setRegisteredEmail(email: String) = prefs.edit().putString(KEY_REGISTERED_EMAIL, email).apply()

    fun getAgentPhone(): String = prefs.getString(KEY_AGENT_PHONE, "") ?: ""
    fun setAgentPhone(phone: String) = prefs.edit().putString(KEY_AGENT_PHONE, phone).apply()

    fun getRegistrationDate(): String = prefs.getString(KEY_REG_DATE, "") ?: ""
    fun setRegistrationDate(date: String) = prefs.edit().putString(KEY_REG_DATE, date).apply()

    fun getRegistrationTime(): String = prefs.getString(KEY_REG_TIME, "") ?: ""
    fun setRegistrationTime(time: String) = prefs.edit().putString(KEY_REG_TIME, time).apply()

    fun getVerifiedSenders(): Set<String> = prefs.getStringSet(KEY_VERIFIED_SENDERS, setOf("MPESA")) ?: setOf("MPESA")
    fun addVerifiedSender(number: String) {
        val current = getVerifiedSenders().toMutableSet()
        current.add(number)
        prefs.edit().putStringSet(KEY_VERIFIED_SENDERS, current).apply()
    }

    fun removeVerifiedSender(number: String) {
        val current = getVerifiedSenders().toMutableSet()
        current.remove(number)
        prefs.edit().putStringSet(KEY_VERIFIED_SENDERS, current).apply()
    }

    // Pending Payment State
    fun getPendingOrderId(): String = prefs.getString(KEY_PENDING_ORDER_ID, "") ?: ""
    fun setPendingOrderId(id: String) = prefs.edit().putString(KEY_PENDING_ORDER_ID, id).apply()

    fun getPendingPackageId(): String = prefs.getString(KEY_PENDING_PACKAGE_ID, "") ?: ""
    fun setPendingPackageId(id: String) = prefs.edit().putString(KEY_PENDING_PACKAGE_ID, id).apply()

    fun getPendingPaymentUnits(): Int = prefs.getInt(KEY_PENDING_UNITS, 0)
    fun setPendingPaymentUnits(units: Int) = prefs.edit().putInt(KEY_PENDING_UNITS, units).apply()

    fun isPendingUnlimited(): Boolean = prefs.getBoolean(KEY_PENDING_IS_UNLIMITED, false)
    fun setPendingUnlimited(unlimited: Boolean) = prefs.edit().putBoolean(KEY_PENDING_IS_UNLIMITED, unlimited).apply()

    fun getPendingDurationDays(): Int = prefs.getInt(KEY_PENDING_DURATION, 0)
    fun setPendingDurationDays(days: Int) = prefs.edit().putInt(KEY_PENDING_DURATION, days).apply()

    fun clearPendingPayment() {
        prefs.edit()
            .remove(KEY_PENDING_ORDER_ID)
            .remove(KEY_PENDING_PACKAGE_ID)
            .remove(KEY_PENDING_UNITS)
            .remove(KEY_PENDING_IS_UNLIMITED)
            .remove(KEY_PENDING_DURATION)
            .apply()
    }

    // Airtime Auto-Refresh Settings
    fun isAutoRefreshAirtimeEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH_AIRTIME, false)
    fun setAutoRefreshAirtimeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_REFRESH_AIRTIME, enabled).apply()

    fun getAutoRefreshSimSlot(): Int = prefs.getInt(KEY_AUTO_REFRESH_SIM, 0) // 0 = SIM 1, 1 = SIM 2, 2 = BOTH
    fun setAutoRefreshSimSlot(slot: Int) = prefs.edit().putInt(KEY_AUTO_REFRESH_SIM, slot).apply()

    fun getAutoRefreshIntervalMins(): Int = prefs.getInt(KEY_AUTO_REFRESH_INTERVAL, 30) // 5, 10, 15, 30, 60, 120
    fun setAutoRefreshIntervalMins(mins: Int) = prefs.edit().putInt(KEY_AUTO_REFRESH_INTERVAL, mins).apply()

    fun getTrainingPhoneNumber(): String = prefs.getString(KEY_TRAINING_PHONE, "0700000000") ?: "0700000000"
    fun setTrainingPhoneNumber(number: String) = prefs.edit().putString(KEY_TRAINING_PHONE, number).apply()

    // Web Control Server Settings
    fun isWebControlEnabled(): Boolean = prefs.getBoolean(KEY_WEB_CONTROL_ENABLED, true)
    fun setWebControlEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_WEB_CONTROL_ENABLED, enabled).apply()

    fun getWebControlPort(): Int = prefs.getInt(KEY_WEB_CONTROL_PORT, 8080)
    fun setWebControlPort(port: Int) = prefs.edit().putInt(KEY_WEB_CONTROL_PORT, port).apply()

    fun getTerminalNotes(): String = prefs.getString(KEY_TERMINAL_NOTES, "[]") ?: "[]"
    fun setTerminalNotes(json: String) = prefs.edit().putString(KEY_TERMINAL_NOTES, json).apply()

    companion object {
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
        private const val KEY_GLOBAL_AUTOMATION = "global_automation_enabled"
        private const val KEY_ANNOUNCEMENT_ACTIVE = "announcement_active"
        private const val KEY_ANNOUNCEMENT_TITLE = "announcement_title"
        private const val KEY_ANNOUNCEMENT_MSG = "announcement_message"
        private const val KEY_LAST_ANNOUNCEMENT_HASH = "last_announcement_hash"
        private const val KEY_SIMPLE_TIMEOUT = "simple_timeout"
        private const val KEY_ADVANCED_TIMEOUT = "advanced_timeout"
        private const val KEY_SCAN_SPAN = "scan_span"
        private const val KEY_USSD_DELAY = "ussd_response_delay"
        private const val KEY_QUEUE_COOLDOWN_DELAY = "queue_cooldown_delay"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_VERIFIED_SENDERS = "verified_senders"
        private const val KEY_DATA_HIDDEN = "data_hidden"
        private const val KEY_TOKENS = "tokens"
        private const val KEY_UNLIMITED_EXPIRY = "unlimited_expiry"
        private const val KEY_DEDUCTION_ENABLED = "unit_deduction_enabled"
        
        private const val KEY_DEVICE_ID = "unique_device_id"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_PERMANENT_DEVICE_ID = "permanent_device_id"
        
        private const val KEY_RELAY_ENABLED = "relay_enabled"
        private const val KEY_RELAY_TARGET = "relay_target"
        private const val KEY_RELAY_SIM = "relay_sim"
        private const val KEY_RELAY_OFFERS = "relay_offers"
        private const val KEY_NOTIF_SIM = "notification_sim"
        private const val KEY_ENGAGE_BOT = "engage_bot_enabled"
        private const val KEY_ENGAGE_EXACT_TOPUP = "engage_exact_topup_match"
        private const val KEY_KEYBOARD_SUPPRESSION = "keyboard_suppression"
        
        private const val KEY_AUTO_CLEANUP_ENABLED = "auto_cleanup_enabled"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_PRUNING_ENABLED = "pruning_enabled"
        private const val KEY_AUTOSAVE_CONTACTS = "autosave_contacts"
        private const val KEY_AUTOSAVE_PREFIX = "autosave_prefix"
        private const val KEY_UNMATCHED_RETENTION = "unmatched_retention_days"

        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_ID_USER_SET = "id_user_set"
        private const val KEY_DEVICE_ROLE = "device_role"
        private const val KEY_RELAY_AMOUNTS = "relay_amounts"
        private const val KEY_LAST_IP = "last_connected_ip"
        
        private const val KEY_USER_REGISTERED = "user_registered"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_REGISTERED_EMAIL = "registered_email"
        private const val KEY_AGENT_PHONE = "agent_phone"
        private const val KEY_REG_DATE = "registration_date"
        private const val KEY_REG_TIME = "registration_time"

        private const val KEY_PENDING_ORDER_ID = "pending_order_id"
        private const val KEY_PENDING_PACKAGE_ID = "pending_package_id"
        private const val KEY_PENDING_UNITS = "pending_units"
        private const val KEY_PENDING_IS_UNLIMITED = "pending_is_unlimited"
        private const val KEY_PENDING_DURATION = "pending_duration"

        private const val KEY_AUTO_REFRESH_AIRTIME = "auto_refresh_airtime_enabled"
        private const val KEY_AUTO_REFRESH_SIM = "auto_refresh_sim_slot"
        private const val KEY_AUTO_REFRESH_INTERVAL = "auto_refresh_interval_mins"
        private const val KEY_TRAINING_PHONE = "training_phone_number"

        private const val KEY_WEB_CONTROL_ENABLED = "web_control_enabled"
        private const val KEY_WEB_CONTROL_PORT = "web_control_port"
        private const val KEY_TERMINAL_NOTES = "terminal_notes"
    }

    // Local Token Backup Storage for Offline / Fallback
    fun saveLocalBackup(code: String, amount: Int, expiry: Long) {
        prefs.edit().putString("backup_amt_$code", amount.toString()).putLong("backup_exp_$code", expiry).putBoolean("backup_used_$code", false).apply()
    }

    fun getLocalBackup(code: String): Pair<Int, Long>? {
        val amtStr = prefs.getString("backup_amt_$code", null) ?: return null
        val amount = amtStr.toIntOrNull() ?: 0
        val expiry = prefs.getLong("backup_exp_$code", 0L)
        val isUsed = prefs.getBoolean("backup_used_$code", false)
        if (isUsed) return null
        return Pair(amount, expiry)
    }

    fun markLocalBackupUsed(code: String) {
        prefs.edit().putBoolean("backup_used_$code", true).apply()
    }
}
