package com.plantnfc.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantnfc.domain.repository.PlantRepository
import com.plantnfc.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val language: String = AppPreferences.DEFAULT_LANGUAGE,
    val plantSheetId: String = AppPreferences.DEFAULT_PLANT_SHEET_ID,
    val plantSheetName: String = AppPreferences.DEFAULT_PLANT_SHEET_NAME,
    val nfcWriterUrl: String = AppPreferences.DEFAULT_NFC_WRITER_URL,
    val nfcWriterSecret: String = AppPreferences.DEFAULT_NFC_WRITER_SECRET,
    val isSaving: Boolean = false,
    val savedMsg: Boolean = false,
    val isReloading: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val plantRepository: PlantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = SettingsUiState(
                language        = prefs.language.first(),
                plantSheetId    = prefs.plantSheetId.first(),
                plantSheetName  = prefs.plantSheetName.first(),
                nfcWriterUrl    = prefs.nfcWriterUrl.first(),
                nfcWriterSecret = prefs.nfcWriterSecret.first(),
            )
        }
    }

    fun setLanguage(lang: String) = _state.update { it.copy(language = lang) }
    fun setPlantSheetId(v: String) = _state.update { it.copy(plantSheetId = v) }
    fun setPlantSheetName(v: String) = _state.update { it.copy(plantSheetName = v) }
    fun setNfcWriterUrl(v: String) = _state.update { it.copy(nfcWriterUrl = v) }
    fun setNfcWriterSecret(v: String) = _state.update { it.copy(nfcWriterSecret = v) }

    fun save() = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        val s = _state.value
        prefs.setLanguage(s.language)
        prefs.setPlantSheetId(s.plantSheetId)
        prefs.setPlantSheetName(s.plantSheetName)
        prefs.setNfcWriterUrl(s.nfcWriterUrl)
        prefs.setNfcWriterSecret(s.nfcWriterSecret)
        _state.update { it.copy(isSaving = false, savedMsg = true) }
    }

    fun dismissSavedMsg() = _state.update { it.copy(savedMsg = false) }

    fun reloadPlants() = viewModelScope.launch {
        _state.update { it.copy(isReloading = true) }
        plantRepository.refreshPlants()
        _state.update { it.copy(isReloading = false) }
    }

    fun resetDefaults() {
        _state.update {
            it.copy(
                plantSheetId    = AppPreferences.DEFAULT_PLANT_SHEET_ID,
                plantSheetName  = AppPreferences.DEFAULT_PLANT_SHEET_NAME,
                nfcWriterUrl    = AppPreferences.DEFAULT_NFC_WRITER_URL,
                nfcWriterSecret = AppPreferences.DEFAULT_NFC_WRITER_SECRET,
            )
        }
    }
}
