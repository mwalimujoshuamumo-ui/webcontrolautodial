package com.example.autodial.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationTemplateDao {
    @Query("SELECT * FROM notification_templates")
    fun getAllTemplates(): Flow<List<NotificationTemplate>>

    @Query("SELECT * FROM notification_templates WHERE category = :category LIMIT 1")
    suspend fun getTemplateByCategory(category: String): NotificationTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: NotificationTemplate): Long

    @Update
    suspend fun updateTemplate(template: NotificationTemplate): Int

    @Delete
    suspend fun deleteTemplate(template: NotificationTemplate): Int
}
