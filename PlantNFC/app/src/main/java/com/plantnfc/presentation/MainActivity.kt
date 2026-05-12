package com.plantnfc.presentation

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plantnfc.presentation.common.NfcBridge
import com.plantnfc.presentation.generator.GeneratorScreen
import com.plantnfc.presentation.nfclist.NfcListScreen
import com.plantnfc.presentation.reader.ReaderScreen
import com.plantnfc.presentation.settings.SettingsScreen
import com.plantnfc.presentation.theme.PlantNfcTheme
import com.plantnfc.util.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            // Observe the language setting and pick the matching string set
            val language by appPreferences.language
                .map { if (it == "hu") hungarianStrings else englishStrings }
                .collectAsState(initial = englishStrings)

            PlantNfcTheme {
                CompositionLocalProvider(LocalAppStrings provides language) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val nav = rememberNavController()
                        NavHost(nav, startDestination = "generator") {
                            composable("generator") {
                                GeneratorScreen(
                                    onGoToReader   = { nav.navigate("reader") },
                                    onGoToList     = { nav.navigate("list") },
                                    onGoToSettings = { nav.navigate("settings") },
                                )
                            }
                            composable("reader") {
                                ReaderScreen(
                                    onGoToGenerator = { nav.navigate("generator") },
                                    onGoToList      = { nav.navigate("list") },
                                    onGoToSettings  = { nav.navigate("settings") },
                                )
                            }
                            composable("list") {
                                NfcListScreen(onBack = { nav.popBackStack() })
                            }
                            composable("settings") {
                                SettingsScreen(onBack = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            { tag: Tag -> runOnUiThread { NfcBridge.handleTag(tag) } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let { NfcBridge.handleTag(it) }
    }
}
