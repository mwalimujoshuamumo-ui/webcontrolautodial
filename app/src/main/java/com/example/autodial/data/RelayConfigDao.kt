package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayConfigDao {
    @Query("SELECT * FROM relay_configs")
    fun getAllConfigs(): Flow<List<RelayConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: RelayConfig): Long

    @Update
    suspend fun updateConfig(config: RelayConfig): Int

    @Delete
    suspend fun deleteConfig(config: RelayConfig): Int
}
