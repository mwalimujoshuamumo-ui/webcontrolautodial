package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY amount ASC")
    fun getAllOffers(): Flow<List<Offer>>

    @Query("SELECT * FROM offers WHERE status = 1 ORDER BY amount ASC")
    fun getActiveOffers(): Flow<List<Offer>>

    @Query("SELECT * FROM offers WHERE amount = :amount AND status = 1 AND isEnabled = 1 LIMIT 1")
    suspend fun findMatchingOffer(amount: Double): Offer?

    @Query("SELECT * FROM offers WHERE amount = :amount LIMIT 1")
    suspend fun findMatchingOfferIgnoringStatus(amount: Double): Offer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffer(offer: Offer): Long

    @Update
    suspend fun updateOffer(offer: Offer): Int

    @Delete
    suspend fun deleteOffer(offer: Offer): Int
}
