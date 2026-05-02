package com.plantnfc.data.repository

import com.plantnfc.data.local.dao.NfcRecordDao
import com.plantnfc.data.local.dao.PlantDao
import com.plantnfc.data.local.entities.NfcRecordEntity
import com.plantnfc.data.local.entities.PlantEntity
import com.plantnfc.data.remote.GoogleSheetsDataSource
import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.NfcType
import com.plantnfc.domain.model.Plant
import com.plantnfc.domain.model.SyncStatus
import com.plantnfc.domain.repository.NfcRecordRepository
import com.plantnfc.domain.repository.PlantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepositoryImpl @Inject constructor(
    private val plantDao: PlantDao,
    private val sheetsDataSource: GoogleSheetsDataSource,
) : PlantRepository {

    override fun getActivePlants(): Flow<List<Plant>> =
        plantDao.observeActivePlants().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshPlants(): Result<Unit> = runCatching {
        val remote = sheetsDataSource.loadActivePlants()
        plantDao.deleteAll()
        plantDao.insertAll(remote.map { it.toEntity() })
    }
}

@Singleton
class NfcRecordRepositoryImpl @Inject constructor(
    private val dao: NfcRecordDao,
    private val sheetsDataSource: GoogleSheetsDataSource,
) : NfcRecordRepository {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun getAllRecords(): Flow<List<NfcRecord>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insert(record: NfcRecord): Long =
        dao.insert(record.copy(createdAt = dateFmt.format(Date()), syncStatus = SyncStatus.PENDING).toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun loadRemoteLastId(): Int? = sheetsDataSource.fetchLastNfcId()

    /** POST every PENDING record to the Google Sheet and mark it SYNCED on success. */
    override suspend fun syncToRemote(): Result<Unit> = runCatching {
        val pending = dao.getPending()
        pending.forEach { entity ->
            sheetsDataSource.postNfcRecord(entity.toDomain())
                .onSuccess { dao.updateSyncStatus(entity.id, SyncStatus.SYNCED.name) }
        }
    }

    override suspend fun syncFromRemote(): Result<Unit> = Result.success(Unit)

    override suspend fun nextNfcId(): Int = (dao.maxNfcId() ?: 0) + 1
}

// Mappers
fun PlantEntity.toDomain() = Plant(plantId, latinName, nameVariety, nameHu, nameEn, activeInNfc)
fun Plant.toEntity() = PlantEntity(plantId, latinName, nameVariety, nameHu, nameEn, activeInNfc)

fun NfcRecordEntity.toDomain() = NfcRecord(
    id, nfcId, plantId, plantName, variety, latinName,
    NfcType.fromCode(nfcTypeCode), datum, gpsPacket, other,
    serialNumber, link, createdAt,
    runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.PENDING),
)

fun NfcRecord.toEntity() = NfcRecordEntity(
    id, nfcId, plantId, plantName, variety, latinName,
    nfcType.code, datum, gpsPacket, other,
    serialNumber, link, createdAt, syncStatus.name,
)
