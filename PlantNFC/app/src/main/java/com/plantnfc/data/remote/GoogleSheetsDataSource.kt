package com.plantnfc.data.remote

import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.Plant
import com.plantnfc.util.AppPreferences
import com.plantnfc.util.NfcTextCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsDataSource @Inject constructor(
    private val prefs: AppPreferences,
) {

    companion object {
        // Only load plants that are active in NFC (column CR = Active_in_NFC)
        private const val QUERY = "select A,B,C,D,E where CR = 'Y'"
    }

    // ── Plant list ────────────────────────────────────────────────────────────

    suspend fun loadActivePlants(): List<Plant> = withContext(Dispatchers.IO) {
        val sheetId   = prefs.plantSheetId.first()
        val sheetName = prefs.plantSheetName.first()
        val encoded   = URLEncoder.encode(QUERY, "UTF-8")
        val url = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:json&sheet=$sheetName&tq=$encoded"
        val raw = URL(url).readText()
        parseGvizResponse(raw)
    }

    // ── NFC ID ────────────────────────────────────────────────────────────────

    /** GET the Apps Script Web App and return the last used NFC ID, or null on failure. */
    suspend fun fetchLastNfcId(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val writerUrl = prefs.nfcWriterUrl.first()
            val body = URL(writerUrl).readText()
            val json = JSONObject(body)
            val lastId = json.opt("lastId")
            if (lastId != null && lastId.toString().isNotBlank()) lastId.toString().toIntOrNull()
            else null
        }.getOrNull()
    }

    // ── Write record ──────────────────────────────────────────────────────────

    /**
     * POST an NFC record to the Apps Script Web App.
     * Uses the same JSON fields as the HTML page's Save button.
     */
    suspend fun postNfcRecord(record: NfcRecord): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val writerUrl    = prefs.nfcWriterUrl.first()
            val writerSecret = prefs.nfcWriterSecret.first()
            val includeGps   = !record.gpsPacket.isNullOrBlank()
            val includeOther = !record.other.isNullOrBlank()
            val body = JSONObject().apply {
                put("key", writerSecret)
                put("nfcId", record.nfcId)
                put("plantId", record.plantId)
                put("nfcTyp", record.nfcType.code)
                put("datum", record.datum)
                put("nfcCreated", record.createdAt ?: "")
                put("nfcPos", record.gpsPacket ?: "")
                put("nfcData", NfcTextCodec.encode(record, includeGps, includeOther))
                put("link", record.link ?: "")
                put("other", record.other ?: "")
                put("serialNum", record.serialNumber ?: "")
            }.toString()

            val response = postWithRedirects(writerUrl, body)
            val result = JSONObject(response)
            if (result.optString("status") != "success") {
                throw Exception(result.optString("error", "Unknown error"))
            }
        }
    }

    /**
     * POST body to a URL, following up to 5 redirects manually.
     * Java's HttpURLConnection converts POST→GET on 302, so we handle redirects ourselves.
     */
    private fun postWithRedirects(startUrl: String, body: String, maxRedirects: Int = 5): String {
        var currentUrl = startUrl
        repeat(maxRedirects) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.doOutput = true
            conn.instanceFollowRedirects = false
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w -> w.write(body) }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("Redirect with no Location header")
                conn.disconnect()
                currentUrl = location
                return@repeat
            }
            // Use inputStream for 2xx, errorStream for 4xx/5xx
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().readText()
            conn.disconnect()
            return text
        }
        throw IOException("Too many redirects")
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseGvizResponse(raw: String): List<Plant> {
        val jsonStr = raw
            .substringAfter("google.visualization.Query.setResponse(")
            .trimEnd(';', ')')
            .trim()
        val table = JSONObject(jsonStr).getJSONObject("table")
        val cols = table.getJSONArray("cols")
        val rows = table.getJSONArray("rows")

        val headers = (0 until cols.length()).map { i ->
            cols.getJSONObject(i).optString("label").ifEmpty {
                cols.getJSONObject(i).optString("id")
            }
        }

        return (0 until rows.length()).mapNotNull { ri ->
            val cells = rows.optJSONObject(ri)?.optJSONArray("c") ?: return@mapNotNull null
            val entry = headers.mapIndexed { ci, h ->
                h to (cells.optJSONObject(ci)?.opt("v")?.toString() ?: "")
            }.toMap()
            if (entry.values.all { it.isBlank() }) return@mapNotNull null
            Plant(
                plantId     = entry["Plant_ID"] ?: "",
                latinName   = entry["LatinName"] ?: "",
                nameVariety = entry["Name_Variety"] ?: "",
                nameHu      = entry["Name_HU"] ?: "",
                nameEn      = entry["Name_EN"] ?: "",
                activeInNfc = true,
            )
        }
    }
}
