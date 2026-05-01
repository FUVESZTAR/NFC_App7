package com.plantnfc.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.plantnfc.presentation.common.NfcBridge
import com.plantnfc.util.GpsPacketCodec
import com.plantnfc.util.NfcTextCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class ReaderState(
    val nfcText: String = "",
    val nfcLink: String = "",
    val serial: String = "",
    val fields: Map<String, String> = emptyMap(),
    val gpsLat: String = "–",
    val gpsLon: String = "–",
    val gpsAlt: String = "–",
    val gpsAcc: String = "–",
    val lastSerial: String = "",
)

@HiltViewModel
class ReaderViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    fun onTagScanned(serial: String, texts: List<String>, urls: List<String>) {
        if (serial == _state.value.lastSerial) return
        val text = texts.firstOrNull() ?: ""
        val link = urls.firstOrNull() ?: ""
        val decoded = NfcTextCodec.decodeToMap(text)
        val gps = decoded["pos"]?.takeIf { it.isNotBlank() }?.let { GpsPacketCodec.unpack(it) }
        _state.update { it.copy(
            nfcText = text, nfcLink = link, serial = serial, lastSerial = serial,
            fields = decoded,
            gpsLat = gps?.latitude?.toString() ?: "–",
            gpsLon = gps?.longitude?.toString() ?: "–",
            gpsAlt = gps?.altitudeM?.let { a -> "${a}m" } ?: "–",
            gpsAcc = gps?.accuracyM?.let { a -> "±${a}m" } ?: "–",
        )}
    }

    fun clear() = _state.update { ReaderState() }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onGoToGenerator: () -> Unit,
    onGoToList: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Register NFC read callback
    DisposableEffect(Unit) {
        NfcBridge.setReadCallback { serial, texts, urls -> vm.onTagScanned(serial, texts, urls) }
        onDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("📖 NFC Reader") },
                actions = {
                    IconButton(onClick = onGoToGenerator) { Icon(Icons.Default.Edit, "Generator") }
                    IconButton(onClick = onGoToList)      { Icon(Icons.Default.List, "List") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Instructions
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scan", style = MaterialTheme.typography.titleSmall)
                    Text("Hold an NFC plant tag near your phone to read it.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.clear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                        OutlinedButton(
                            onClick = {
                                copyText(android.app.Application(), state.nfcText)
                                scope.launch { snackbarHost.showSnackbar("Copied!") }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.nfcText.isNotBlank(),
                        ) { Text("Copy NFC") }
                    }
                }
            }

            // Data preview
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NFC Data", style = MaterialTheme.typography.titleSmall)
                    MonoBlock(state.nfcText.ifEmpty { "No tag scanned yet…" })
                    Text("Link", style = MaterialTheme.typography.titleSmall)
                    MonoBlock(state.nfcLink.ifEmpty { "–" })
                    if (state.serial.isNotBlank()) {
                        Text("Serial: ${state.serial}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Decoded fields
            if (state.fields.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Decoded Fields", style = MaterialTheme.typography.titleSmall)
                        listOf(
                            "NFC ID"    to state.fields["ncfId"],
                            "Plant ID"  to state.fields["plantId"],
                            "Name"      to state.fields["name"],
                            "Variety"   to state.fields["variety"],
                            "Latin"     to state.fields["latinName"],
                            "Type"      to state.fields["nfcType"],
                            "Date"      to state.fields["datum"],
                            "Notes"     to state.fields["other"],
                        ).forEach { (label, value) ->
                            if (!value.isNullOrBlank()) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text("$label:", Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // GPS
                if (state.gpsLat != "–") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("GPS", style = MaterialTheme.typography.titleSmall)
                            listOf("Lat" to state.gpsLat, "Lon" to state.gpsLon, "Alt" to state.gpsAlt, "Acc" to state.gpsAcc).forEach { (l, v) ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text("$l:", Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(v, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
    )
}

private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
    cm?.setPrimaryClip(android.content.ClipData.newPlainText("PlantNFC", text))
}
