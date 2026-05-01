package com.plantnfc.data.local.dao

import androidx.room.*
import com.plantnfc.data.local.entities.NfcRecordEntity
import com.plantnfc.data.local.entities.PlantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE activeInNfc = 1 ORDER BY nameEn ASC")
    fun observeActivePlants(): Flow<List<PlantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plants: List<PlantEntity>)

    @Query("DELETE FROM plants")
    suspend fun deleteAll()
}

@Dao
interface NfcRecordDao {
    @Query("SELECT * FROM nfc_records ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NfcRecordEntity>>

    @Query("SELECT * FROM nfc_records WHERE syncStatus = 'PENDING'")
    suspend fun getPending(): List<NfcRecordEntity>

    @Query("SELECT MAX(nfcId) FROM nfc_records")
    suspend fun maxNfcId(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: NfcRecordEntity): Long

    @Query("DELETE FROM nfc_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE nfc_records SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)
}
