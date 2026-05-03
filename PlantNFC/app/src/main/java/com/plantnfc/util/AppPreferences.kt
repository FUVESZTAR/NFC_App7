package com.plantnfc.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "plantnfc_settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val KEY_LANGUAGE           = stringPreferencesKey("language")
        val KEY_PLANT_SHEET_ID     = stringPreferencesKey("plant_sheet_id")
        val KEY_PLANT_SHEET_NAME   = stringPreferencesKey("plant_sheet_name")
        val KEY_NFC_WRITER_URL     = stringPreferencesKey("nfc_writer_url")
        val KEY_NFC_WRITER_SECRET  = stringPreferencesKey("nfc_writer_secret")

        const val DEFAULT_LANGUAGE          = "en"
        const val DEFAULT_PLANT_SHEET_ID    = "1QHJzWztssucMlnozk2tV9ym6gLedgDj4Zh3DzCTFWCY"
        const val DEFAULT_PLANT_SHEET_NAME  = "plant_list"
        const val DEFAULT_NFC_WRITER_URL    =
            "https://script.google.com/macros/s/AKfycbysWB68AM6TKlobnA3MLR_18LpJjGVkHolPf3G_WNziV3r93_fztJIenTVSoll-Kmtp/exec"
        // Default secret matches the deployed Apps Script; override via Settings screen in production.
        const val DEFAULT_NFC_WRITER_SECRET = "159753g9d5rt4Ht4eg7e5z4d6szo89fsef"
    }

    val language: Flow<String> =
        context.dataStore.data.map { it[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE }

    val plantSheetId: Flow<String> =
        context.dataStore.data.map { it[KEY_PLANT_SHEET_ID] ?: DEFAULT_PLANT_SHEET_ID }

    val plantSheetName: Flow<String> =
        context.dataStore.data.map { it[KEY_PLANT_SHEET_NAME] ?: DEFAULT_PLANT_SHEET_NAME }

    val nfcWriterUrl: Flow<String> =
        context.dataStore.data.map { it[KEY_NFC_WRITER_URL] ?: DEFAULT_NFC_WRITER_URL }

    val nfcWriterSecret: Flow<String> =
        context.dataStore.data.map { it[KEY_NFC_WRITER_SECRET] ?: DEFAULT_NFC_WRITER_SECRET }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setPlantSheetId(id: String) {
        context.dataStore.edit { it[KEY_PLANT_SHEET_ID] = id }
    }

    suspend fun setPlantSheetName(name: String) {
        context.dataStore.edit { it[KEY_PLANT_SHEET_NAME] = name }
    }

    suspend fun setNfcWriterUrl(url: String) {
        context.dataStore.edit { it[KEY_NFC_WRITER_URL] = url }
    }

    suspend fun setNfcWriterSecret(secret: String) {
        context.dataStore.edit { it[KEY_NFC_WRITER_SECRET] = secret }
    }
}
