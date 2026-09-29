package com.example.autodial.data.api

import retrofit2.Response
import retrofit2.http.*

interface MpesaApiService {

    /**
     * Native Safaricom Daraja Endpoints
     */

    @GET("oauth/v1/generate?grant_type=client_credentials")
    suspend fun getAccessToken(
        @Header("Authorization") authHeader: String
    ): Response<MpesaAuthResponse>

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun sendStkPush(
        @Header("Authorization") authHeader: String,
        @Body request: StkPushRequest
    ): Response<StkPushResponse>

    @POST("mpesa/stkpushquery/v1/query")
    suspend fun queryStkStatus(
        @Header("Authorization") authHeader: String,
        @Body request: StkQueryRequest
    ): Response<StkQueryResponse>

    /**
     * Legacy Lovable Endpoints (Deprecated)
     */
    @Deprecated("Use native endpoints")
    @POST("api/public/payments/stk-push")
    suspend fun initiateStkPush(
        @Header("Authorization") authToken: String,
        @Body request: PaymentStkRequest
    ): Response<PaymentStkResponse>

    @Deprecated("Use queryStkStatus")
    @GET("api/public/payments/status")
    suspend fun queryPaymentStatus(
        @Header("Authorization") authToken: String,
        @Query("orderId") orderId: String
    ): Response<PaymentStatusResponse>
}
