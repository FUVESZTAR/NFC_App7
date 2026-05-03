package com.plantnfc.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.plantnfc.presentation.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val strings = LocalAppStrings.current
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.savedMsg) {
        if (state.savedMsg) {
            snackbarHost.showSnackbar(strings.settingsSaved)
            vm.dismissSavedMsg()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, strings.cancel)
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Language ──────────────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.language, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.language == "en",
                            onClick  = { vm.setLanguage("en") },
                            label    = { Text(strings.english) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = state.language == "hu",
                            onClick  = { vm.setLanguage("hu") },
                            label    = { Text(strings.hungarian) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Plant Data Sheet ──────────────────────────────────────────────
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.googleSheets, style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = state.plantSheetId,
                        onValueChange = vm::setPlantSheetId,
                        label = { Text(strings.plantSheetIdLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.plantSheetName,
                        onValueChange = vm::setPlantSheetName,
                        label = { Text(strings.plantSheetNameLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.nfcWriterUrl,
                        onValueChange = vm::setNfcWriterUrl,
                        label = { Text(strings.nfcWriterUrlLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.nfcWriterSecret,
                        onValueChange = vm::setNfcWriterSecret,
                        label = { Text(strings.nfcWriterSecretLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    // Reload plant list
                    OutlinedButton(
                        onClick = { vm.reloadPlants() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isReloading,
                    ) {
                        if (state.isReloading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(strings.reloadPlants)
                    }
                }
            }

            // ── Save / Reset ──────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { vm.resetDefaults() },
                    modifier = Modifier.weight(1f),
                ) { Text("⟳ Reset") }

                Button(
                    onClick = { vm.save() },
                    modifier = Modifier.weight(2f),
                    enabled = !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(strings.saveSettings)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
