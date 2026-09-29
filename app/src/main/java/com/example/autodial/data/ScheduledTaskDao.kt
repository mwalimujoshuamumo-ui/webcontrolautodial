package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY scheduledTime ASC")
    fun getAllScheduledTasks(): Flow<List<ScheduledTask>>

    @Query("SELECT * FROM scheduled_tasks WHERE status = :status ORDER BY scheduledTime ASC")
    fun getTasksByStatus(status: String): Flow<List<ScheduledTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ScheduledTask): Long

    @Update
    suspend fun updateTask(task: ScheduledTask): Int

    @Delete
    suspend fun deleteTask(task: ScheduledTask): Int

    @Query("DELETE FROM scheduled_tasks WHERE scheduledTime < :timestamp")
    suspend fun deleteOldTasks(timestamp: Long): Int
}
