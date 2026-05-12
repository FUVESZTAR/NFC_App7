package com.plantnfc.presentation.generator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.plantnfc.domain.model.NfcType
import com.plantnfc.presentation.LocalAppStrings
import com.plantnfc.presentation.common.NfcBridge
import com.plantnfc.presentation.localize
import com.plantnfc.util.NfcTextCodec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onGoToReader: () -> Unit,
    onGoToList: () -> Unit,
    onGoToSettings: () -> Unit,
    vm: GeneratorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(strings.localize(it)); vm.dismissSnack() }
    }

    var nfcWaiting by remember { mutableStateOf(false) }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) vm.startGps()
        else scope.launch { snackbarHost.showSnackbar(strings.msgLocationPermRequired) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(strings.generatorTitle) },
                actions = {
                    IconButton(onClick = onGoToReader)   { Icon(Icons.Default.DocumentScanner, strings.readerTitle) }
                    IconButton(onClick = onGoToList)     { Icon(Icons.Default.List, strings.listTitle) }
                    IconButton(onClick = onGoToSettings) { Icon(Icons.Default.Settings, strings.settingsTitle) }
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
            // ── Plant picker ─────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.plantSelection, style = MaterialTheme.typography.titleSmall)

                    var plantExpanded by remember { mutableStateOf(false) }
                    val plantQuery = state.plantSearchQuery
                    // Filter by latin name; show all when query is blank
                    val filteredPlants = remember(state.plants, plantQuery) {
                        val distinct = state.plants.distinctBy { it.latinName }
                        if (plantQuery.isBlank()) distinct.take(100)
                        else distinct.filter { it.latinName.contains(plantQuery, ignoreCase = true) }.take(100)
                    }
                    val showFreeTextOption = plantQuery.isNotBlank() && state.selectedPlant == null

                    ExposedDropdownMenuBox(expanded = plantExpanded, onExpandedChange = { plantExpanded = it }) {
                        OutlinedTextField(
                            value = plantQuery,
                            onValueChange = { vm.setPlantSearchQuery(it); plantExpanded = true },
                            label = { Text(strings.plant) },
                            placeholder = { Text(strings.plantSearchHint) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(plantExpanded) },
                            singleLine = true,
                        )
                        ExposedDropdownMenu(expanded = plantExpanded, onDismissRequest = { plantExpanded = false }) {
                            if (state.isLoading) {
                                DropdownMenuItem(text = { Text(strings.loadingDots) }, onClick = {})
                            }
                            filteredPlants.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.latinName) },
                                    onClick = { vm.selectPlant(p); plantExpanded = false },
                                )
                            }
                            if (showFreeTextOption) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("${strings.freeTextOption} \"$plantQuery\"") },
                                    onClick = { plantExpanded = false },
                                )
                            }
                        }
                    }

                    if (state.varieties.isNotEmpty()) {
                        var varExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = varExpanded, onExpandedChange = { varExpanded = it }) {
                            OutlinedTextField(
                                value = state.variety,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(strings.variety) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(varExpanded) },
                            )
                            ExposedDropdownMenu(expanded = varExpanded, onDismissRequest = { varExpanded = false }) {
                                state.varieties.forEach { v ->
                                    DropdownMenuItem(text = { Text(v) }, onClick = { vm.selectVariety(v); varExpanded = false })
                                }
                            }
                        }
                    }
                }
            }

            // ── Data fields ──────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.nfcData, style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = state.nfcId.toString(),
                        onValueChange = { vm.setNfcId(it.toIntOrNull() ?: 0) },
                        label = { Text(strings.nfcId) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.latinName,
                        onValueChange = vm::setLatinName,
                        label = { Text(strings.latinName) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.datum,
                        onValueChange = vm::setDatum,
                        label = { Text(strings.dateHint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                        val typeLabel = when (state.nfcType) {
                            NfcType.PLANT -> strings.nfcTypePlant
                            NfcType.GRAFT -> strings.nfcTypeGraft
                            NfcType.SEED  -> strings.nfcTypeSeed
                        }
                        OutlinedTextField(
                            value = typeLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.nfcType) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        )
                        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            NfcType.entries.forEach { t ->
                                val tLabel = when (t) {
                                    NfcType.PLANT -> strings.nfcTypePlant
                                    NfcType.GRAFT -> strings.nfcTypeGraft
                                    NfcType.SEED  -> strings.nfcTypeSeed
                                }
                                DropdownMenuItem(text = { Text(tLabel) }, onClick = { vm.setNfcType(t); typeExpanded = false })
                            }
                        }
                    }
                }
            }

            // ── GPS ──────────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header row with On/Off switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.gpsData, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            text = if (state.gpsEnabled) strings.on else strings.off,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = state.gpsEnabled,
                            onCheckedChange = { vm.setGpsEnabled(it) },
                        )
                    }

                    if (state.gpsEnabled) {
                        // Start / Stop button
                        if (!state.gpsTracking) {
                            Button(
                                onClick = {
                                    val hasFine = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine || hasCoarse) {
                                        vm.startGps()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.startLiveTracking)
                            }
                        } else {
                            Button(
                                onClick = { vm.stopGps() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.stopLockData)
                            }
                        }

                        // Live data rows
                        listOf(
                            strings.latitude  to (state.gpsLat?.let { "%.6f".format(it) } ?: "–"),
                            strings.longitude to (state.gpsLon?.let { "%.6f".format(it) } ?: "–"),
                            strings.altitude  to (state.gpsAlt?.let { "${it}m" }           ?: "–"),
                            strings.accuracy  to (state.gpsAcc?.let { "±${it}m" }          ?: "–"),
                        ).forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    label,
                                    Modifier.width(80.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(value, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }

                        // Status
                        Text(
                            text = strings.localize(state.gpsStatus),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        // Locked GPS packet
                        val gpsPacketLocked = state.gpsPacketLocked
                        if (!gpsPacketLocked.isNullOrBlank()) {
                            Text(
                                strings.compressedPacket,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = gpsPacketLocked,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                        .padding(8.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        "${NfcTextCodec.sizeBytes(gpsPacketLocked)} B",
                                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Preview ──────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.preview, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("${state.textBytes} B", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = state.nfcText.ifEmpty { strings.dataWillAppear },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp),
                    )
                    Text(
                        text = state.nfcLink.ifEmpty { strings.linkWillAppear },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp),
                    )
                }
            }

            // ── Actions ──────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.actions, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Write NFC
                        Button(
                            onClick = {
                                if (state.nfcText.isBlank()) { scope.launch { snackbarHost.showSnackbar(strings.msgGenerateFirst) }; return@Button }
                                nfcWaiting = true
                                NfcBridge.enqueueWrite(
                                    text = state.nfcText,
                                    link = state.nfcLink,
                                    onSerial = { vm.setSerial(it) },
                                    onSuccess = { vm.onNfcWriteSuccess(); nfcWaiting = false },
                                    onError = { vm.onNfcWriteError(it); nfcWaiting = false },
                                    onDone = { nfcWaiting = false },
                                )
                                scope.launch { snackbarHost.showSnackbar(strings.msgTapNfcTag, duration = SnackbarDuration.Short) }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !nfcWaiting && state.nfcText.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Nfc, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (nfcWaiting) strings.waiting else strings.writeNfc)
                        }

                        // Save
                        Button(
                            onClick = { vm.saveRecord() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSaving,
                        ) {
                            Text(if (state.isSaving) strings.saving else strings.save)
                        }
                    }

                    // Copy
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                copyText(context, state.nfcText)
                                scope.launch { snackbarHost.showSnackbar(strings.msgCopied) }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.nfcText.isNotBlank(),
                        ) { Text(strings.copyNfc) }

                        OutlinedButton(
                            onClick = {
                                copyText(context, state.nfcLink)
                                scope.launch { snackbarHost.showSnackbar(strings.msgCopied) }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.nfcLink.isNotBlank(),
                        ) { Text(strings.copyLink) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun copyText(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    cm.setPrimaryClip(android.content.ClipData.newPlainText("PlantNFC", text))
}
