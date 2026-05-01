package com.plantnfc.presentation.generator

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.plantnfc.domain.model.NfcType
import com.plantnfc.presentation.common.NfcBridge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onGoToReader: () -> Unit,
    onGoToList: () -> Unit,
    vm: GeneratorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHost.showSnackbar(it); vm.dismissSnack() }
    }

    // Register NFC write result callbacks
    var nfcWaiting by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("🌱 NFC Generator") },
                actions = {
                    IconButton(onClick = onGoToReader) { Icon(Icons.Default.DocumentScanner, "Reader") }
                    IconButton(onClick = onGoToList)   { Icon(Icons.Default.List, "List") }
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
                    Text("Plant Selection", style = MaterialTheme.typography.titleSmall)

                    var plantExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = plantExpanded, onExpandedChange = { plantExpanded = it }) {
                        OutlinedTextField(
                            value = state.selectedPlant?.nameEn ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Plant") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(plantExpanded) },
                        )
                        ExposedDropdownMenu(expanded = plantExpanded, onDismissRequest = { plantExpanded = false }) {
                            if (state.isLoading) {
                                DropdownMenuItem(text = { Text("Loading…") }, onClick = {})
                            }
                            state.plants.distinctBy { it.nameEn }.take(100).forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.nameEn) },
                                    onClick = { vm.selectPlant(p); plantExpanded = false },
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
                                label = { Text("Variety") },
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
                    Text("NFC Data", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = state.nfcId.toString(),
                        onValueChange = { vm.setNfcId(it.toIntOrNull() ?: 0) },
                        label = { Text("NFC ID") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.latinName,
                        onValueChange = vm::setLatinName,
                        label = { Text("Latin Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.datum,
                        onValueChange = vm::setDatum,
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                        OutlinedTextField(
                            value = state.nfcType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("NFC Type") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        )
                        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            NfcType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.setNfcType(t); typeExpanded = false })
                            }
                        }
                    }
                }
            }

            // ── Preview ──────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Preview", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("${state.textBytes} B", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = state.nfcText.ifEmpty { "Data will appear here…" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp),
                    )
                    Text(
                        text = state.nfcLink.ifEmpty { "Link will appear here…" },
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
                    Text("Actions", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Write NFC
                        Button(
                            onClick = {
                                if (state.nfcText.isBlank()) { scope.launch { snackbarHost.showSnackbar("Generate NFC data first") }; return@Button }
                                nfcWaiting = true
                                NfcBridge.enqueueWrite(
                                    text = state.nfcText,
                                    link = state.nfcLink,
                                    onSerial = { vm.setSerial(it) },
                                    onSuccess = { vm.onNfcWriteSuccess(); nfcWaiting = false },
                                    onError = { vm.onNfcWriteError(it); nfcWaiting = false },
                                    onDone = { nfcWaiting = false },
                                )
                                scope.launch { snackbarHost.showSnackbar("Tap NFC tag to phone…", duration = SnackbarDuration.Short) }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !nfcWaiting && state.nfcText.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Nfc, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (nfcWaiting) "Waiting…" else "Write NFC")
                        }

                        // Save
                        Button(
                            onClick = { vm.saveRecord() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSaving,
                        ) {
                            Text(if (state.isSaving) "Saving…" else "Save")
                        }
                    }

                    // Copy
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                copyText(context, state.nfcText)
                                scope.launch { snackbarHost.showSnackbar("Copied!") }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.nfcText.isNotBlank(),
                        ) { Text("Copy NFC") }

                        OutlinedButton(
                            onClick = {
                                copyText(context, state.nfcLink)
                                scope.launch { snackbarHost.showSnackbar("Copied!") }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.nfcLink.isNotBlank(),
                        ) { Text("Copy Link") }
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
