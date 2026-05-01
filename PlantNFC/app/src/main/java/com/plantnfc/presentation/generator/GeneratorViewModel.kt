package com.plantnfc.presentation.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.NfcType
import com.plantnfc.domain.model.Plant
import com.plantnfc.domain.repository.NfcRecordRepository
import com.plantnfc.domain.repository.PlantRepository
import com.plantnfc.util.NfcTextCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class GeneratorUiState(
    val plants: List<Plant> = emptyList(),
    val isLoading: Boolean = false,
    val selectedPlant: Plant? = null,
    val variety: String = "",
    val varieties: List<String> = emptyList(),
    val nfcId: Int = 0,
    val latinName: String = "",
    val datum: String = todayStr(),
    val nfcType: NfcType = NfcType.PLANT,
    val serialNumber: String = "",
    val nfcText: String = "",
    val nfcLink: String = "",
    val textBytes: Int = 0,
    val snackbar: String? = null,
    val isSaving: Boolean = false,
)

private fun todayStr() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val plantRepo: PlantRepository,
    private val recordRepo: NfcRecordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { plantRepo.refreshPlants() }
            val nextId = recordRepo.nextNfcId()
            _state.update { it.copy(nfcId = nextId, isLoading = false) }
        }
        viewModelScope.launch {
            plantRepo.getActivePlants().collect { plants ->
                _state.update { it.copy(plants = plants) }
            }
        }
    }

    fun selectPlant(plant: Plant?) {
        if (plant == null) { _state.update { GeneratorUiState(nfcId = it.nfcId, plants = it.plants) }; return }
        val vars = _state.value.plants.filter { it.nameEn == plant.nameEn }.map { it.nameVariety }.distinct()
        _state.update { it.copy(selectedPlant = plant, latinName = plant.latinName, varieties = vars, variety = vars.firstOrNull() ?: "") }
        updatePreview()
    }

    fun selectVariety(v: String) {
        val match = _state.value.plants.firstOrNull { it.nameEn == _state.value.selectedPlant?.nameEn && it.nameVariety == v }
        _state.update { it.copy(variety = v, latinName = match?.latinName ?: it.latinName) }
        updatePreview()
    }

    fun setNfcId(v: Int) { _state.update { it.copy(nfcId = v) }; updatePreview() }
    fun setLatinName(v: String) { _state.update { it.copy(latinName = v) }; updatePreview() }
    fun setDatum(v: String) { _state.update { it.copy(datum = v) }; updatePreview() }
    fun setNfcType(v: NfcType) { _state.update { it.copy(nfcType = v) }; updatePreview() }
    fun setSerial(v: String) { _state.update { it.copy(serialNumber = v) } }
    fun onNfcWriteSuccess() = snack("Written to NFC tag ✅")
    fun onNfcWriteError(msg: String) = snack("Write error: $msg")
    fun dismissSnack() = _state.update { it.copy(snackbar = null) }

    fun saveRecord() {
        val s = _state.value
        if (s.selectedPlant == null) { snack("Select a plant first"); return }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                recordRepo.insert(NfcRecord(
                    nfcId = s.nfcId, plantId = s.selectedPlant.plantId,
                    plantName = s.selectedPlant.nameEn, variety = s.variety,
                    latinName = s.latinName, nfcType = s.nfcType, datum = s.datum,
                    serialNumber = s.serialNumber.takeIf { it.isNotBlank() },
                    link = s.nfcLink,
                ))
                val nextId = recordRepo.nextNfcId()
                _state.update { it.copy(nfcId = nextId) }
                snack("Saved!")
            }.onFailure { snack("Save failed: ${it.message}") }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun updatePreview() {
        val s = _state.value
        val record = NfcRecord(
            nfcId = s.nfcId, plantId = s.selectedPlant?.plantId ?: "",
            plantName = s.selectedPlant?.nameEn ?: "", variety = s.variety,
            latinName = s.latinName, nfcType = s.nfcType, datum = s.datum,
        )
        val text = NfcTextCodec.encode(record, false, false)
        val link = if (s.selectedPlant != null) "https://your-domain.com/W/P.html?id=${s.selectedPlant.plantId}" else ""
        _state.update { it.copy(nfcText = text, nfcLink = link, textBytes = NfcTextCodec.sizeBytes(text)) }
    }

    private fun snack(msg: String) = _state.update { it.copy(snackbar = msg) }
}
