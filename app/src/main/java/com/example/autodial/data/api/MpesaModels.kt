package com.example.autodial.data.api

import com.google.gson.annotations.SerializedName

/**
 * Native Safaricom Daraja Models
 */

data class MpesaAuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: String
)

data class StkPushRequest(
    @SerializedName("BusinessShortCode") val businessShortCode: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("TransactionType") val transactionType: String,
    @SerializedName("Amount") val amount: Int,
    @SerializedName("PartyA") val partyA: String,
    @SerializedName("PartyB") val partyB: String,
    @SerializedName("PhoneNumber") val phoneNumber: String,
    @SerializedName("CallBackURL") val callbackUrl: String,
    @SerializedName("AccountReference") val accountReference: String,
    @SerializedName("TransactionDesc") val transactionDesc: String
)

data class StkPushResponse(
    @SerializedName("MerchantRequestID") val merchantRequestId: String,
    @SerializedName("CheckoutRequestID") val checkoutRequestId: String,
    @SerializedName("ResponseCode") val responseCode: String,
    @SerializedName("ResponseDescription") val responseDescription: String,
    @SerializedName("CustomerMessage") val customerMessage: String
)

data class StkQueryRequest(
    @SerializedName("BusinessShortCode") val businessShortCode: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("CheckoutRequestID") val checkoutRequestId: String
)

data class StkQueryResponse(
    @SerializedName("ResponseCode") val responseCode: String,
    @SerializedName("ResponseDescription") val responseDescription: String,
    @SerializedName("MerchantRequestID") val merchantRequestId: String,
    @SerializedName("CheckoutRequestID") val checkoutRequestId: String,
    @SerializedName("ResultCode") val resultCode: String?,
    @SerializedName("ResultDesc") val resultDesc: String?
)

/**
 * Legacy Lovable Models (Marked for removal)
 */
@Deprecated("Use native Daraja models")
data class PaymentStkRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("packageId") val packageId: String,
    @SerializedName("agentId") val agentId: String
)

@Deprecated("Use native Daraja models")
data class PaymentStkResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("orderId") val orderId: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null
)

@Deprecated("Use native Daraja models")
data class PaymentStatusResponse(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null
)
