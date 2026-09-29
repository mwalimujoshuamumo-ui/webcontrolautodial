package com.example.autodial.data

import androidx.room.*

@Entity(tableName = "ussd_maps")
data class UssdMap(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startingCode: String,
    val ussdTemplate: String, // Final resolved string e.g. *188*8*1*BF*1#
    val version: Int = 1,
    val status: String = STATUS_DRAFT, // DRAFT, TRAINING, APPROVED, ACTIVE, ARCHIVED
    val offerId: Int,
    val dateCreated: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_TRAINING = "TRAINING"
        const val STATUS_TRAINING_COMPLETE = "TRAINING COMPLETE"
        const val STATUS_PENDING_APPROVAL = "PENDING APPROVAL"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_ARCHIVED = "ARCHIVED"
    }
}

@Entity(
    tableName = "ussd_map_steps",
    foreignKeys = [ForeignKey(
        entity = UssdMap::class,
        parentColumns = ["id"],
        childColumns = ["mapId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class UssdMapStep(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mapId: Int,
    val stepNumber: Int,
    val ussdRequest: String,
    val fullResponse: String,
    val selectedOption: String, // Number or BF
    val selectedText: String,
    val selectedBy: String, // MAP, AGENT
    val selectionType: String, // AUTOMATIC, AGENT_SELECTION, CUSTOMER_NUMBER, FIXED_VALUE, SUCCESS, FAILURE
    val inputType: String, // MENU_OPTION, TEXT_INPUT, CONFIRMATION
    val variableName: String = "", // BF
    val trainingValue: String = "", // Dummy number used
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ussd_map_options",
    foreignKeys = [ForeignKey(
        entity = UssdMapStep::class,
        parentColumns = ["id"],
        childColumns = ["stepId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class UssdMapOption(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stepId: Int,
    val optionNumber: String,
    val optionText: String,
    val isSelected: Boolean = false
)

data class UssdMapWithSteps(
    @Embedded val map: UssdMap,
    @Relation(
        entity = UssdMapStep::class,
        parentColumn = "id",
        entityColumn = "mapId"
    )
    val steps: List<UssdStepWithOptions>
)

data class UssdStepWithOptions(
    @Embedded val step: UssdMapStep,
    @Relation(
        entity = UssdMapOption::class,
        parentColumn = "id",
        entityColumn = "stepId"
    )
    val options: List<UssdMapOption>
)

@Dao
interface UssdMapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMap(map: UssdMap): Long

    @Update
    suspend fun updateMap(map: UssdMap)

    @Delete
    suspend fun deleteMap(map: UssdMap)

    @Query("SELECT * FROM ussd_maps WHERE id = :id")
    suspend fun getMapById(id: Int): UssdMap?

    @Query("SELECT * FROM ussd_maps WHERE status = 'ACTIVE'")
    fun getAllActiveMapsFlow(): kotlinx.coroutines.flow.Flow<List<UssdMap>>

    @androidx.room.Transaction
    @Query("SELECT * FROM ussd_maps WHERE offerId = :offerId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveMapWithStepsForOffer(offerId: Int): UssdMapWithSteps?

    @androidx.room.Transaction
    @Query("SELECT * FROM ussd_maps WHERE status = 'ACTIVE'")
    suspend fun getAllActiveMapsWithSteps(): List<UssdMapWithSteps>

    @Query("SELECT * FROM ussd_maps WHERE offerId = :offerId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveMapForOffer(offerId: Int): UssdMap?

    @androidx.room.Transaction
    @Query("SELECT * FROM ussd_maps WHERE id = :mapId")
    fun getMapWithStepsFlow(mapId: Int): kotlinx.coroutines.flow.Flow<UssdMapWithSteps?>

    // Steps
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: UssdMapStep): Long

    @Update
    suspend fun updateStep(step: UssdMapStep)

    @Query("SELECT * FROM ussd_map_steps WHERE mapId = :mapId AND stepNumber = :stepNumber LIMIT 1")
    suspend fun getStepByNumber(mapId: Int, stepNumber: Int): UssdMapStep?

    @Query("SELECT * FROM ussd_map_steps WHERE mapId = :mapId ORDER BY stepNumber ASC")
    suspend fun getStepsForMap(mapId: Int): List<UssdMapStep>

    @Query("DELETE FROM ussd_map_steps WHERE mapId = :mapId")
    suspend fun deleteStepsForMap(mapId: Int)

    // Options
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOption(option: UssdMapOption)

    @Query("DELETE FROM ussd_map_options WHERE stepId = :stepId")
    suspend fun deleteOptionsForStep(stepId: Int)
}

@Entity(tableName = "relay_devices")
data class RelayDevice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceName: String,
    val role: String, // MAIN, LINKED
    val connectionType: String, // HOTSPOT, BLUETOOTH
    val ipAddress: String = "",
    val bluetoothAddress: String = "",
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    
    // Pro Features
    val model: String = "",
    val androidVersion: String = "",
    val autodialId: String = "",
    val relayAmounts: String = "", // Comma-separated
    val automationEnabled: Boolean = true,
    val status: String = "ACTIVE", // ACTIVE, RECONNECTING, INACTIVE, DISCONNECTED
    
    // Stats
    val totalReceived: Int = 0,
    val totalCompleted: Int = 0,
    val totalFailed: Int = 0
)

@Dao
interface RelayDeviceDao {
    @Query("SELECT * FROM relay_devices")
    fun getAllDevicesFlow(): kotlinx.coroutines.flow.Flow<List<RelayDevice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: RelayDevice): Long

    @Update
    suspend fun updateDevice(device: RelayDevice)

    @Delete
    suspend fun deleteDevice(device: RelayDevice)

    @Query("UPDATE relay_devices SET isConnected = :connected, status = :status WHERE id = :id")
    suspend fun updateConnectionStatus(id: Int, connected: Boolean, status: String)
    
    @Query("UPDATE relay_devices SET relayAmounts = :amounts, automationEnabled = :enabled WHERE id = :id")
    suspend fun updateDeviceConfig(id: Int, amounts: String, enabled: Boolean)

    @Query("SELECT * FROM relay_devices WHERE autodialId = :autodialId LIMIT 1")
    suspend fun getDeviceById(autodialId: String): RelayDevice?
}
