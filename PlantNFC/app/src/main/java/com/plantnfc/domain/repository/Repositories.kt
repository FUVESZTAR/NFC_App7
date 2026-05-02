package com.plantnfc.domain.repository

import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.Plant
import kotlinx.coroutines.flow.Flow

interface PlantRepository {
    fun getActivePlants(): Flow<List<Plant>>
    suspend fun refreshPlants(): Result<Unit>
}

interface NfcRecordRepository {
    fun getAllRecords(): Flow<List<NfcRecord>>
    suspend fun insert(record: NfcRecord): Long
    suspend fun delete(id: Long)
    suspend fun syncToRemote(): Result<Unit>
    suspend fun syncFromRemote(): Result<Unit>
    suspend fun nextNfcId(): Int
    /** Returns the last NFC ID stored in the remote Google Sheet, or null if unavailable. */
    suspend fun loadRemoteLastId(): Int?
}
