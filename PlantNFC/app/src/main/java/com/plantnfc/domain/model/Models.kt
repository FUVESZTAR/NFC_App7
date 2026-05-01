package com.plantnfc.domain.model

data class Plant(
    val plantId: String,
    val latinName: String,
    val nameVariety: String,
    val nameHu: String,
    val nameEn: String,
    val activeInNfc: Boolean = true,
)

enum class NfcType(val code: String, val label: String) {
    PLANT("n", "n – plant"),
    GRAFT("o", "o – graft"),
    SEED("m", "m – seed");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: PLANT
    }
}

data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Int,
    val accuracyM: Int,
)

data class NfcRecord(
    val id: Long = 0,
    val nfcId: Int,
    val plantId: String,
    val plantName: String,
    val variety: String,
    val latinName: String,
    val nfcType: NfcType,
    val datum: String,
    val gpsPacket: String? = null,
    val other: String? = null,
    val serialNumber: String? = null,
    val link: String? = null,
    val createdAt: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

enum class SyncStatus { PENDING, SYNCED, CONFLICT }
