package com.plantnfc.util

import android.util.Base64
import com.plantnfc.domain.model.GpsData
import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.NfcType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object GpsPacketCodec {
    fun pack(gps: GpsData): String {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(((gps.latitude + 90.0) * 1_000_000).roundToInt())
        buf.putInt(((gps.longitude + 180.0) * 1_000_000).roundToInt())
        buf.putShort((gps.altitudeM + 1000).toShort())
        buf.putShort((gps.accuracyM + 1000).toShort())
        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "L|$b64|L"
    }

    fun unpack(packet: String): GpsData? = runCatching {
        val b64 = packet.removePrefix("L|").removeSuffix("|L").trim()
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val latInt = buf.int.toLong() and 0xFFFFFFFFL
        val lonInt = buf.int.toLong() and 0xFFFFFFFFL
        val altInt = buf.short.toInt() and 0xFFFF
        val accInt = buf.short.toInt() and 0xFFFF
        GpsData(
            latitude  = latInt / 1_000_000.0 - 90.0,
            longitude = lonInt / 1_000_000.0 - 180.0,
            altitudeM = altInt - 1000,
            accuracyM = accInt - 1000,
        )
    }.getOrNull()
}

object NfcTextCodec {
    private val KEYS = listOf("ncfId","plantId","name","variety","latinName","nfcType","datum","pos","other")

    fun encode(record: NfcRecord, includeGps: Boolean, includeOther: Boolean): String {
        val sb = StringBuilder()
        sb.append(record.nfcId).append('/')
        sb.append(record.plantId).append('/')
        sb.append(record.plantName).append('/')
        sb.append(record.variety).append('/')
        sb.append(record.latinName).append('/')
        sb.append(record.nfcType.code).append('/')
        sb.append(record.datum)
        if (includeGps && !record.gpsPacket.isNullOrBlank()) sb.append('/').append(record.gpsPacket)
        if (includeOther && !record.other.isNullOrBlank()) sb.append('/').append(record.other)
        sb.append('/')
        return sb.toString()
    }

    fun decodeToMap(text: String): Map<String, String> {
        val parts = text.split('/')
        return KEYS.mapIndexed { i, key -> key to (parts.getOrNull(i)?.trim() ?: "") }.toMap()
    }

    fun sizeBytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    fun formatSize(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        else -> "${"%.1f".format(bytes / 1024.0)} KB"
    }
}
