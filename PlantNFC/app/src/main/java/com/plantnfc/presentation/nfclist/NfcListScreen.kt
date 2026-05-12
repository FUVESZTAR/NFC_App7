package com.plantnfc.presentation.nfclist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantnfc.domain.model.NfcRecord
import com.plantnfc.domain.model.SyncStatus
import com.plantnfc.domain.repository.NfcRecordRepository
import com.plantnfc.presentation.LocalAppStrings
import com.plantnfc.presentation.SnackMsg
import com.plantnfc.presentation.localize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class NfcListViewModel @Inject constructor(
    private val repo: NfcRecordRepository,
) : ViewModel() {

    private val _query   = MutableStateFlow("")
    private val _syncing = MutableStateFlow(false)
    private val _msg     = MutableStateFlow<SnackMsg?>(null)

    val state: StateFlow<NfcListState> = combine(repo.getAllRecords(), _query, _syncing, _msg) { records, q, syncing, msg ->
        val filtered = if (q.isBlank()) records
        else records.filter { r ->
            r.plantName.contains(q, true) || r.variety.contains(q, true) ||
            r.plantId.contains(q, true)   || r.nfcId.toString().contains(q)
        }
        NfcListState(filtered, q, syncing, msg)
    }.stateIn(viewModelScope, SharingStarted.Lazily, NfcListState())

    fun setQuery(q: String) = _query.update { q }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun syncUp()   = viewModelScope.launch {
        _syncing.value = true
        repo.syncToRemote()
            .onSuccess { _msg.value = SnackMsg.Synced }
            .onFailure { _msg.value = SnackMsg.SyncFailed(it.message ?: "Unknown error") }
        _syncing.value = false
    }
    fun syncDown() = viewModelScope.launch {
        _syncing.value = true
        repo.syncFromRemote()
            .onSuccess { _msg.value = SnackMsg.Imported }
            .onFailure { _msg.value = SnackMsg.SyncFailed(it.message ?: "Unknown error") }
        _syncing.value = false
    }
    fun dismissMsg() = _msg.update { null }
}

data class NfcListState(
    val records: List<NfcRecord> = emptyList(),
    val query: String = "",
    val syncing: Boolean = false,
    val message: SnackMsg? = null,
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcListScreen(
    onBack: () -> Unit,
    vm: NfcListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHost.showSnackbar(strings.localize(it)); vm.dismissMsg() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(strings.listTitle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, strings.cancel) } },
                actions = {
                    if (state.syncing) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { vm.syncDown() }) { Icon(Icons.Default.CloudDownload, "Download") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Button(
                onClick = { vm.syncUp() },
                enabled = !state.syncing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.syncingCloud)
                } else {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.saveToCloud)
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text(strings.search) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
            )
            if (state.records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.noRecordsYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.records, key = { it.id }) { record ->
                        RecordCard(record) { vm.delete(record.id) }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(record: NfcRecord, onDelete: () -> Unit) {
    val strings = LocalAppStrings.current
    var showConfirm by remember { mutableStateOf(false) }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(strings.deleteQuestion) },
            text = { Text("NFC #${record.nfcId} – ${record.plantName}") },
            confirmButton = { TextButton(onClick = { onDelete(); showConfirm = false }) { Text(strings.delete) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(strings.cancel) } },
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("#${record.nfcId} • ${record.plantName}", style = MaterialTheme.typography.titleSmall)
                if (record.variety.isNotBlank()) Text(record.variety, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(record.latinName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("${record.nfcType.label} • ${record.datum}", style = MaterialTheme.typography.bodySmall)
                val syncLabel = when (record.syncStatus) {
                    SyncStatus.SYNCED   -> strings.syncedLabel
                    SyncStatus.PENDING  -> strings.pendingLabel
                    SyncStatus.CONFLICT -> strings.conflictLabel
                }
                Text(syncLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, strings.delete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
