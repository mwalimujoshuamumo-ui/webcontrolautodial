package com.example.autodial.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

object UssdHelper {

    private val handleCache = mutableMapOf<Int, PhoneAccountHandle>()

    @SuppressLint("MissingPermission")
    fun sendBackgroundUssd(context: Context, ussdCode: String, simIndex: Int, allowFallback: Boolean = true, onResult: (String?) -> Unit) {
        WakeManager.wakeAndUnlock(context)
        if (Build.VERSION.SDK_INT < 26) {
            // Fallback for older devices
            if (allowFallback) dialUssd(context, ussdCode, simIndex)
            onResult(null)
            return
        }

        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            val targetSubInfo = activeSubscriptions?.find { it.simSlotIndex == simIndex }
            
            val managerForSim = if (targetSubInfo != null) {
                telephonyManager.createForSubscriptionId(targetSubInfo.subscriptionId)
            } else {
                telephonyManager
            }

            managerForSim.sendUssdRequest(ussdCode, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager?,
                    request: String?,
                    response: CharSequence?
                ) {
                    super.onReceiveUssdResponse(telephonyManager, request, response)
                    Log.d("UssdHelper", "Background USSD Success: $response")
                    onResult(response?.toString())
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager?,
                    request: String?,
                    failureCode: Int
                ) {
                    super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode)
                    Log.e("UssdHelper", "Background USSD Failed. Code: $failureCode")
                    onResult(null)
                    // Fallback to regular dial if background fails and allowed
                    if (allowFallback) dialUssd(context, ussdCode, simIndex)
                }
            }, Handler(Looper.getMainLooper()))
            
        } catch (e: Exception) {
            Log.e("UssdHelper", "Error in background USSD", e)
            if (allowFallback) dialUssd(context, ussdCode, simIndex)
            onResult(null)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun dialUssd(context: Context, ussdCode: String, simIndex: Int) {
        WakeManager.wakeAndUnlock(context)
        val encodedUssd = Uri.encode(ussdCode)
        val dialIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$encodedUssd")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            
            // Add flags to show on lock screen if supported by the dialer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", false)
            }
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            val targetSubInfo = activeSubscriptions?.find { it.simSlotIndex == simIndex }
            val subId = targetSubInfo?.subscriptionId?.toLong() ?: simIndex.toLong()

            // OPTIMIZATION: Use cached handle if available to skip expensive system calls
            val cachedHandle = handleCache[simIndex]
            if (cachedHandle != null) {
                dialIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, cachedHandle)
            } else {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val phoneAccountHandles = telecomManager.getCallCapablePhoneAccounts()

                // Try to find the specific handle for the requested SIM slot
                val handle = findHandleForSim(context, phoneAccountHandles, simIndex)
                if (handle != null) {
                    dialIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
            }
            
            // Exhaustive Intent Extras for all manufacturers (2024-2025 compatible)
            dialIntent.putExtra("com.android.phone.force.slot", true)
            dialIntent.putExtra("simSlot", simIndex)
            dialIntent.putExtra("simId", simIndex)
            dialIntent.putExtra("slotId", simIndex)
            dialIntent.putExtra("phone", simIndex)
            dialIntent.putExtra("phone_id", simIndex)
            dialIntent.putExtra("slot", simIndex)
            dialIntent.putExtra("extra_asu_id", simIndex)
            dialIntent.putExtra("com.android.phone.extra.slot", simIndex)
            dialIntent.putExtra("slot_id", simIndex)
            dialIntent.putExtra("sim_slot", simIndex)
            dialIntent.putExtra("subscription", subId)
            dialIntent.putExtra("subscription_id", subId)

            context.startActivity(dialIntent)
            Log.d("UssdHelper", "Launched dialer for USSD: $ussdCode on SIM $simIndex (SubId: $subId)")
        } catch (e: SecurityException) {
            Log.e("UssdHelper", "Permission denied for CALL_PHONE", e)
        } catch (e: Exception) {
            Log.e("UssdHelper", "Error launching USSD", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun findHandleForSim(context: Context, handles: List<PhoneAccountHandle>, simIndex: Int): PhoneAccountHandle? {
        // Return cached handle if available
        handleCache[simIndex]?.let { return it }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        
        try {
            // Get subscription for the requested slot
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            
            // LOG ALL HANDLES FOR DEBUGGING
            handles.forEachIndexed { index, h ->
                Log.d("UssdHelper", "Available Handle [$index]: pkg=${h.componentName.packageName} id=${h.id}")
            }

            // Log active subs for debugging
            activeSubscriptions?.forEach { 
                Log.d("UssdHelper", "System Sub: slot=${it.simSlotIndex} id=${it.subscriptionId} iccIdLength=${it.iccId?.length ?: 0}")
            }

            val targetSubInfo = activeSubscriptions?.find { it.simSlotIndex == simIndex }
            
            if (targetSubInfo != null) {
                val targetIccId = targetSubInfo.iccId ?: ""
                val targetSubId = targetSubInfo.subscriptionId.toString()

                // 1. Exact SubId Match
                var handle = handles.find { it.id == targetSubId }
                
                // 2. ICCID Match (only if ICCID is valid/not empty)
                if (handle == null && targetIccId.isNotEmpty()) {
                    handle = handles.find { it.id.contains(targetIccId) }
                }
                
                // 3. SubId Contains Match (some handles are "pstw:1" or similar)
                if (handle == null) {
                    handle = handles.find { 
                        it.id.contains(":$targetSubId") || 
                        it.id.startsWith(targetSubId + ":") ||
                        it.id.contains("subscription=$targetSubId") 
                    }
                }
                
                if (handle != null) {
                    Log.i("UssdHelper", "Matched Handle for SIM $simIndex: ${handle.id}")
                    handleCache[simIndex] = handle
                    return handle
                }
            }
        } catch (e: Exception) {
            Log.e("UssdHelper", "Error matching SIM handle", e)
        }
        
        // Final desperate fallback: if simIndex 0, pick first handle; if 1, pick second
        // Check if handles actually correspond to slot count
        val fallbackHandle = if (handles.size > simIndex) {
            Log.w("UssdHelper", "Using index fallback for SIM $simIndex")
            handles[simIndex] 
        } else if (handles.isNotEmpty()) {
            Log.w("UssdHelper", "SIM index $simIndex out of bounds, using first handle")
            handles[0]
        } else null
        if (fallbackHandle != null) handleCache[simIndex] = fallbackHandle
        return fallbackHandle
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = "${context.packageName}/com.example.autodial.service.UssdAccessibilityService"
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun extractBalanceFromResponse(response: String?): String? {
        if (response.isNullOrBlank()) return null
        
        // Pattern 1: Match bal or balance e.g., 'bal is Ksh 50', 'airtime bal: 50.00', 'balance: Ksh 120'
        val balPattern = java.util.regex.Pattern.compile("""bal(?:ance)?\s?(?:is)?\s?(?:ksh|ksh\.)?\s?[:(]?\s?([0-9,.]+)""", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher1 = balPattern.matcher(response)
        if (matcher1.find()) {
            val raw = matcher1.group(1)?.replace(",", "")?.trim()
            if (!raw.isNullOrEmpty() && raw.matches(Regex("""^[0-9]+(\.[0-9]+)?$"""))) {
                return raw
            }
        }

        // Pattern 2: Match currency keyword e.g. 'KSh 150.00', 'KES 200', 'Credit 50'
        val currPattern = java.util.regex.Pattern.compile("""(?:ksh|ksh\.|kes|cr|credit|account|main)\s?[:.]?\s?([0-9,.]+)""", java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher2 = currPattern.matcher(response)
        if (matcher2.find()) {
            val raw = matcher2.group(1)?.replace(",", "")?.trim()
            if (!raw.isNullOrEmpty() && raw.matches(Regex("""^[0-9]+(\.[0-9]+)?$"""))) {
                return raw
            }
        }

        // Pattern 3: Fallback decimal figure e.g. '150.00'
        val numPattern = java.util.regex.Pattern.compile("""([0-9]+\.[0-9]{1,2})""")
        val matcher3 = numPattern.matcher(response)
        if (matcher3.find()) {
            val raw = matcher3.group(1)?.replace(",", "")?.trim()
            if (!raw.isNullOrEmpty()) {
                return raw
            }
        }

        return null
    }
}
