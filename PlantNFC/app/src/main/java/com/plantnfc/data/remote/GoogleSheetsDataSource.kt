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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsDataSource @Inject constructor(
    private val prefs: AppPreferences,
) {

    companion object {
        // Only load plants that are active in NFC (column CR = Active_in_NFC)
        private const val QUERY = "select A,B,C,D,E where CR = 'Y'"
        private const val CONNECT_TIMEOUT_MS: Int = 15_000
        private const val READ_TIMEOUT_MS: Int = 20_000
        private const val MAX_REDIRECT_ATTEMPTS: Int = 5
    }

    // ── Plant list ────────────────────────────────────────────────────────────

    suspend fun loadActivePlants(): List<Plant> = withContext(Dispatchers.IO) {
        val sheetId   = prefs.plantSheetId.first()
        val sheetName = prefs.plantSheetName.first()
        val encoded   = URLEncoder.encode(QUERY, "UTF-8")
        val encodedSheet = URLEncoder.encode(sheetName, "UTF-8")
        val url = "https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:json&sheet=$encodedSheet&tq=$encoded"
        val raw = URL(url).readText()
        parseGvizResponse(raw)
    }

    // ── NFC ID ────────────────────────────────────────────────────────────────

    /** GET the Apps Script Web App and return the last used NFC ID, or null on failure. */
    suspend fun fetchLastNfcId(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val writerUrl = prefs.nfcWriterUrl.first().trim()
            if (writerUrl.isBlank()) throw IOException("Writer URL is empty")
            val body = requestWithRedirects(writerUrl, method = "GET")
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
            val writerUrl = prefs.nfcWriterUrl.first().trim()
            val writerSecret = prefs.nfcWriterSecret.first().trim()
            if (writerUrl.isBlank()) throw IOException("Writer URL is empty")
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

            val response = requestWithRedirects(writerUrl, method = "POST", body = body)
            val result = JSONObject(response)
            if (result.optString("status") != "success") {
                throw Exception(result.optString("error", "Unknown error"))
            }
        }
    }

    /**
     * Performs an HTTP request while following redirects manually.
     * For POST requests, redirected follow-up requests are sent as GET, matching browser fetch behavior.
     * This mirrors Nfcall.html fetch(..., { redirect: 'follow' }) semantics used by the web app.
     */
    private fun requestWithRedirects(
        startUrl: String,
        method: String,
        body: String? = null,
        maxRedirects: Int = MAX_REDIRECT_ATTEMPTS,
    ): String {
        var currentUrl = URL(startUrl)
        var currentMethod = method.uppercase(Locale.ROOT)
        var currentBody = body
        require(currentMethod == "GET" || currentMethod == "POST") {
            "Unsupported HTTP method: $currentMethod"
        }

        repeat(maxRedirects) {
            val conn = currentUrl.openConnection() as HttpURLConnection
            conn.requestMethod = currentMethod
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            if (currentMethod == "POST" && currentBody != null) {
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w -> w.write(currentBody) }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("Redirect with no Location header")
                conn.disconnect()
                currentUrl = if (location.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$"))) {
                    URL(location)
                } else {
                    URL(currentUrl, location)
                }
                currentMethod = "GET"
                currentBody = null
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
        val match = Regex("""google\.visualization\.Query\.setResponse\(([\s\S]*?)\)\s*;?\s*$""")
            .find(raw)
            ?: throw IOException(
                "Failed to parse Google Sheets response: expected " +
                    "'google.visualization.Query.setResponse(...)' wrapper",
            )
        val table = JSONObject(match.groupValues[1]).getJSONObject("table")
        val cols = table.getJSONArray("cols")
        val rows = table.getJSONArray("rows")

        val indexToHeader = (0 until cols.length()).associateWith { columnIndex ->
            cols.getJSONObject(columnIndex).optString("label").trim().ifEmpty {
                cols.getJSONObject(columnIndex).optString("id").trim()
            }
        }

        return (0 until rows.length()).mapNotNull { ri ->
            val cells = rows.optJSONObject(ri)?.optJSONArray("c") ?: return@mapNotNull null

            fun cellText(index: Int): String =
                cells.optJSONObject(index)?.opt("v")?.toString()?.trim().orEmpty()

            val headerToValue = indexToHeader.entries.associate { (columnIndex, header) ->
                header to cellText(columnIndex)
            }
            if (headerToValue.values.all { it.isBlank() }) return@mapNotNull null

            val lowerCaseHeaders = headerToValue.mapKeys { (header, _) -> header.lowercase(Locale.US) }

            fun byHeaderOrIndex(header: String, fallbackIndex: Int): String {
                val normalizedHeader = header.lowercase(Locale.US)
                val value = lowerCaseHeaders[normalizedHeader]
                return if (!value.isNullOrBlank()) value else cellText(fallbackIndex)
            }

            val plant = Plant(
                plantId     = byHeaderOrIndex("Plant_ID", 0),
                latinName   = byHeaderOrIndex("LatinName", 1),
                nameVariety = byHeaderOrIndex("Name_Variety", 2),
                nameHu      = byHeaderOrIndex("Name_HU", 3),
                nameEn      = byHeaderOrIndex("Name_EN", 4),
                activeInNfc = true,
            )

            if (plant.plantId.isBlank() && plant.latinName.isBlank() && plant.nameEn.isBlank() && plant.nameHu.isBlank()) {
                null
            } else {
                plant
            }
        }
    }
}
