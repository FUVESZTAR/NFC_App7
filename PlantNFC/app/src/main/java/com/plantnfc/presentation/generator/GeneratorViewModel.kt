package com.plantnfc.presentation.generator

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantnfc.domain.model.GpsData
import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.NfcType
import com.plantnfc.domain.model.Plant
import com.plantnfc.domain.repository.NfcRecordRepository
import com.plantnfc.domain.repository.PlantRepository
import com.plantnfc.util.GpsPacketCodec
import com.plantnfc.util.NfcTextCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    // GPS
    val gpsEnabled: Boolean = false,
    val gpsTracking: Boolean = false,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsAlt: Int? = null,
    val gpsAcc: Int? = null,
    val gpsPacketLocked: String? = null,
    val gpsStatus: String = "Ready.",
)

private fun todayStr() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val plantRepo: PlantRepository,
    private val recordRepo: NfcRecordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    private var locationListener: LocationListener? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { plantRepo.refreshPlants() }
            val localNext = recordRepo.nextNfcId()
            val remoteLastId = runCatching { recordRepo.loadRemoteLastId() }.getOrNull()
            val nextId = if (remoteLastId != null) maxOf(localNext, remoteLastId + 1) else localNext
            _state.update { it.copy(nfcId = nextId, isLoading = false) }
        }
        viewModelScope.launch {
            plantRepo.getActivePlants().collect { plants ->
                _state.update { it.copy(plants = plants) }
            }
        }
    }

    // ── Plant / variety selection ─────────────────────────────────────────────

    fun selectPlant(plant: Plant?) {
        if (plant == null) {
            _state.update { s -> GeneratorUiState(nfcId = s.nfcId, plants = s.plants) }
            return
        }
        val vars = _state.value.plants.filter { it.nameEn == plant.nameEn }.map { it.nameVariety }.distinct()
        _state.update { it.copy(selectedPlant = plant, latinName = plant.latinName, varieties = vars, variety = vars.firstOrNull() ?: "") }
        updatePreview()
    }

    fun selectVariety(v: String) {
        val match = _state.value.plants.firstOrNull { it.nameEn == _state.value.selectedPlant?.nameEn && it.nameVariety == v }
        _state.update { it.copy(variety = v, latinName = match?.latinName ?: it.latinName) }
        updatePreview()
    }

    // ── Data fields ───────────────────────────────────────────────────────────

    fun setNfcId(v: Int) { _state.update { it.copy(nfcId = v) }; updatePreview() }
    fun setLatinName(v: String) { _state.update { it.copy(latinName = v) }; updatePreview() }
    fun setDatum(v: String) { _state.update { it.copy(datum = v) }; updatePreview() }
    fun setNfcType(v: NfcType) { _state.update { it.copy(nfcType = v) }; updatePreview() }
    fun setSerial(v: String) { _state.update { it.copy(serialNumber = v) } }
    fun onNfcWriteSuccess() = snack("Written to NFC tag ✅")
    fun onNfcWriteError(msg: String) = snack("Write error: $msg")
    fun dismissSnack() = _state.update { it.copy(snackbar = null) }

    // ── GPS ───────────────────────────────────────────────────────────────────

    fun setGpsEnabled(enabled: Boolean) {
        if (!enabled) stopGps()
        _state.update { it.copy(gpsEnabled = enabled, gpsPacketLocked = if (enabled) it.gpsPacketLocked else null) }
        updatePreview()
    }

    fun startGps() {
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return
        // Remove any previous listener
        locationListener?.let { lm.removeUpdates(it) }

        _state.update { it.copy(gpsTracking = true, gpsStatus = "Fetching GPS satellites…") }

        val listener = LocationListener { loc ->
            _state.update { it.copy(
                gpsLat = loc.latitude,
                gpsLon = loc.longitude,
                gpsAlt = loc.altitude.toInt(),
                gpsAcc = loc.accuracy.toInt(),
                gpsStatus = "Updating… (±${loc.accuracy.toInt()}m)",
            )}
        }
        locationListener = listener

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,   // min 1 s between updates
                0.5f,    // min 0.5 m movement
                listener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            _state.update { it.copy(gpsStatus = "GPS permission denied", gpsTracking = false) }
            locationListener = null
        }
    }

    fun stopGps() {
        val lm = appContext.getSystemService(LocationManager::class.java)
        locationListener?.let { lm?.removeUpdates(it) }
        locationListener = null

        val s = _state.value
        val packet = if (s.gpsLat != null && s.gpsLon != null) {
            GpsPacketCodec.pack(GpsData(
                latitude  = s.gpsLat,
                longitude = s.gpsLon,
                altitudeM = s.gpsAlt ?: 0,
                accuracyM = s.gpsAcc ?: 0,
            ))
        } else null

        _state.update { it.copy(gpsTracking = false, gpsPacketLocked = packet, gpsStatus = if (packet != null) "Data Locked." else "No fix yet.") }
        updatePreview()
    }

    override fun onCleared() {
        super.onCleared()
        val lm = appContext.getSystemService(LocationManager::class.java)
        locationListener?.let { lm?.removeUpdates(it) }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveRecord() {
        val s = _state.value
        if (s.selectedPlant == null) { snack("Select a plant first"); return }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                recordRepo.insert(NfcRecord(
                    nfcId        = s.nfcId,
                    plantId      = s.selectedPlant.plantId,
                    plantName    = s.selectedPlant.nameEn,
                    variety      = s.variety,
                    latinName    = s.latinName,
                    nfcType      = s.nfcType,
                    datum        = s.datum,
                    serialNumber = s.serialNumber.takeIf { it.isNotBlank() },
                    link         = s.nfcLink,
                    gpsPacket    = if (s.gpsEnabled) s.gpsPacketLocked else null,
                ))
                // Immediately push to Google Sheets; failure is non-fatal (stays PENDING locally)
                recordRepo.syncToRemote()
                val nextId = recordRepo.nextNfcId()
                _state.update { it.copy(nfcId = nextId) }
                snack("Saved!")
            }.onFailure { snack("Save failed: ${it.message}") }
            _state.update { it.copy(isSaving = false) }
        }
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    private fun updatePreview() {
        val s = _state.value
        val includeGps = s.gpsEnabled && !s.gpsPacketLocked.isNullOrBlank()
        val record = NfcRecord(
            nfcId     = s.nfcId,
            plantId   = s.selectedPlant?.plantId ?: "",
            plantName = s.selectedPlant?.nameEn ?: "",
            variety   = s.variety,
            latinName = s.latinName,
            nfcType   = s.nfcType,
            datum     = s.datum,
            gpsPacket = s.gpsPacketLocked,
        )
        val text = NfcTextCodec.encode(record, includeGps, false)
        val link = if (s.selectedPlant != null) "https://your-domain.com/W/P.html?id=${s.selectedPlant.plantId}" else ""
        _state.update { it.copy(nfcText = text, nfcLink = link, textBytes = NfcTextCodec.sizeBytes(text)) }
    }

    private fun snack(msg: String) = _state.update { it.copy(snackbar = msg) }
}
