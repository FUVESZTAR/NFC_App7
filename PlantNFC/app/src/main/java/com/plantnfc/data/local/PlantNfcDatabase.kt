package com.plantnfc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.plantnfc.data.local.dao.NfcRecordDao
import com.plantnfc.data.local.dao.PlantDao
import com.plantnfc.data.local.entities.NfcRecordEntity
import com.plantnfc.data.local.entities.PlantEntity

@Database(
    entities = [PlantEntity::class, NfcRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PlantNfcDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun nfcRecordDao(): NfcRecordDao
}
