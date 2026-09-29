package com.example.autodial.data

import android.util.Base64
import android.util.Log
import com.example.autodial.data.api.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

/**
 * MpesaRepository - Native Safaricom Daraja Integration
 * Communicates directly with Safaricom APIs.
 */
class MpesaRepository {
    
    companion object {
        private const val BASE_URL = "https://api.safaricom.co.ke/"
        
        // DARAJA CREDENTIALS - 4005721
        private const val BUSINESS_SHORT_CODE = "4005721"
        private const val PASSKEY = "ZxFeJ9OutMoPbbc9VlBx76cxVOspZDAk8Cik7wLYEmUbKT3SiaPo06ADp0tp4thq"
        
        private const val CONSUMER_KEY = "oodb7Tr5KwQ1Ck8VG8KE4XZ6GcfpbIPJ"
        private const val CONSUMER_SECRET = "1uQzFmGMEuy02tdu"
    }

    private val api: MpesaApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpesaApiService::class.java)
    }

    /**
     * Fetches OAuth Access Token from Safaricom.
     */
    suspend fun getAccessToken(): String? {
        return try {
            val auth = "$CONSUMER_KEY:$CONSUMER_SECRET"
            val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
            val response = api.getAccessToken("Basic $encodedAuth")
            
            if (response.isSuccessful) {
                response.body()?.accessToken
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("MpesaRepository", "Auth Error: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e("MpesaRepository", "Auth Exception", e)
            null
        }
    }

    /**
     * Initiates STK Push directly to Safaricom.
     */
    suspend fun initiateStkPush(phone: String, amount: Int, accountRef: String): Result<StkPushResponse> {
        return try {
            val token = getAccessToken() ?: return Result.failure(Exception("Failed to get Access Token"))
            
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
            val password = Base64.encodeToString(
                (BUSINESS_SHORT_CODE + PASSKEY + timestamp).toByteArray(),
                Base64.NO_WRAP
            )

            // AccountReference must be <= 12 chars alphanumeric for Safaricom Daraja API
            val safeAccountRef = accountRef.replace(Regex("[^a-zA-Z0-9]"), "").take(12).ifEmpty { "AUTODIAL" }

            val request = StkPushRequest(
                businessShortCode = BUSINESS_SHORT_CODE,
                password = password,
                timestamp = timestamp,
                transactionType = "CustomerPayBillOnline",
                amount = amount,
                partyA = formatTo254(phone),
                partyB = BUSINESS_SHORT_CODE,
                phoneNumber = formatTo254(phone),
                callbackUrl = "https://example.com/callback", // Standalone apps use polling
                accountReference = safeAccountRef,
                transactionDesc = "Unit Purchase"
            )

            val response = api.sendStkPush("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown Error"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries the status of an STK Push from Safaricom.
     */
    suspend fun queryStkStatus(checkoutRequestId: String): Result<StkQueryResponse> {
        return try {
            val token = getAccessToken() ?: return Result.failure(Exception("Auth Failed"))
            
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
            val password = Base64.encodeToString(
                (BUSINESS_SHORT_CODE + PASSKEY + timestamp).toByteArray(),
                Base64.NO_WRAP
            )

            val request = StkQueryRequest(
                businessShortCode = BUSINESS_SHORT_CODE,
                password = password,
                timestamp = timestamp,
                checkoutRequestId = checkoutRequestId
            )

            val response = api.queryStkStatus("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown Error"
                // Parse error for pending status ("being processed")
                if (error.contains("processed", ignoreCase = true) || error.contains("processing", ignoreCase = true)) {
                    Result.success(StkQueryResponse("1", "The transaction is being processed", "", checkoutRequestId, null, "The transaction is being processed"))
                } else {
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatTo254(phone: String): String {
        var cleaned = phone.replace(Regex("[^0-9]"), "")
        if (cleaned.startsWith("0")) {
            cleaned = "254" + cleaned.substring(1)
        } else if ((cleaned.startsWith("7") || cleaned.startsWith("1")) && cleaned.length == 9) {
            cleaned = "254" + cleaned
        }
        return cleaned
    }
}
