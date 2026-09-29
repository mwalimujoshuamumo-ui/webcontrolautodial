package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TopUpPlanDao {
    @Query("SELECT * FROM top_up_plans ORDER BY requiredAmount ASC")
    fun getAllPlans(): Flow<List<TopUpPlan>>

    @Query("SELECT * FROM top_up_plans WHERE isEnabled = 1 ORDER BY requiredAmount ASC")
    fun getEnabledPlans(): Flow<List<TopUpPlan>>

    @Query("SELECT * FROM top_up_plans WHERE isEnabled = 1 ORDER BY requiredAmount ASC")
    suspend fun getEnabledPlansOnce(): List<TopUpPlan>

    @Query("SELECT * FROM top_up_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): TopUpPlan?

    @Query("SELECT * FROM top_up_plans WHERE isEnabled = 1 AND requiredAmount = :amount LIMIT 1")
    suspend fun findEnabledByAmount(amount: Double): TopUpPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: TopUpPlan): Long

    @Update
    suspend fun update(plan: TopUpPlan)

    @Delete
    suspend fun delete(plan: TopUpPlan)
}
