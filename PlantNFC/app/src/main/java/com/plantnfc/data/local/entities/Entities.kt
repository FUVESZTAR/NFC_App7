package com.plantnfc.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey val plantId: String,
    val latinName: String,
    val nameVariety: String,
    val nameHu: String,
    val nameEn: String,
    val activeInNfc: Boolean,
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "nfc_records")
data class NfcRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nfcId: Int,
    val plantId: String,
    val plantName: String,
    val variety: String,
    val latinName: String,
    val nfcTypeCode: String,
    val datum: String,
    val gpsPacket: String?,
    val other: String?,
    val serialNumber: String?,
    val link: String?,
    val createdAt: String?,
    val syncStatus: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
