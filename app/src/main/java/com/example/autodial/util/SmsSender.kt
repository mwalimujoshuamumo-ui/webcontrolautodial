package com.example.autodial.util

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.example.autodial.data.NotificationTemplate
import com.example.autodial.data.Transaction
import java.text.SimpleDateFormat
import java.util.*

object SmsSender {

    fun sendAutoNotification(context: Context, transaction: Transaction, template: NotificationTemplate, simSlot: Int = -2) {
        val message = replacePlaceholders(template.body, transaction)
        if (message.isBlank()) return

        // Global Preference takes priority if set (0 or 1)
        val notifSim = PreferenceManager(context).getNotificationSimSlot()
        val finalSim = if (notifSim >= 0) {
            notifSim
        } else {
            // Fallback to specific override or transaction SIM if Default (-1) is selected
            when {
                simSlot >= 0 -> simSlot
                transaction.simUsed >= 0 -> transaction.simUsed
                else -> -1
            }
        }

        sendSms(context, transaction.customerNumber, message, finalSim)
    }

    fun forwardMpesa(context: Context, rawMessage: String, targetNumber: String, simSlot: Int) {
        if (targetNumber.isBlank()) return
        sendSms(context, targetNumber, rawMessage, simSlot)
    }

    /** Engage Bot SMS — always uses the Notification SIM setting. */
    fun sendEngageMessage(context: Context, number: String, body: String) {
        if (number.isBlank() || body.isBlank()) return
        val simSlot = PreferenceManager(context).getNotificationSimSlot()
        sendSms(context, number, body, simSlot)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun sendSms(context: Context, number: String, body: String, simSlot: Int) {
        try {
            val smsManager: SmsManager = if (simSlot != -1) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                val targetSub = activeSubscriptions?.find { it.simSlotIndex == simSlot }
                if (targetSub != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        context.getSystemService(SmsManager::class.java).createForSubscriptionId(targetSub.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(targetSub.subscriptionId)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            Log.d("SmsSender", "Sent SMS to $number: $body")
        } catch (e: Exception) {
            Log.e("SmsSender", "Failed to send SMS to $number", e)
        }
    }

    private fun replacePlaceholders(body: String, trans: Transaction): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val names = trans.senderName.split(" ")
        val fname = names.getOrNull(0) ?: ""
        val lname = names.getOrNull(1) ?: ""

        return body
            .replace("{txnid}", trans.transactionCode)
            .replace("{fname}", fname)
            .replace("{Lname}", lname)
            .replace("{Phonenumber}", trans.customerNumber)
            .replace("{time}", sdf.format(Date(trans.date)))
            .replace("{matchedoffer}", trans.offerName)
    }
}
