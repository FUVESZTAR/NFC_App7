package com.plantnfc.data.remote

import com.plantnfc.domain.model.Plant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsDataSource @Inject constructor() {

    companion object {
        // Replace with your own Sheet ID
        const val SHEET_ID = "1QHJzWztssucMlnozk2tV9ym6gLedgDj4Zh3DzCTFWCY"
        const val SHEET_NAME = "plant_list"
        private const val QUERY = "select A,B,C,D,E"
    }

    suspend fun loadActivePlants(): List<Plant> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(QUERY, "UTF-8")
        val url = "https://docs.google.com/spreadsheets/d/$SHEET_ID/gviz/tq?tqx=out:json&sheet=$SHEET_NAME&tq=$encoded"
        val raw = URL(url).readText()
        parseGvizResponse(raw)
    }

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
