package it.vittorioscocca.kidbox.ui.screens.passwords

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.feature.passwords.io.MergeStrategy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsImportExportScreen(
    familyId: String,
    familyName: String?,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    viewModel: PasswordsImportExportViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var encrypt by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }
    var mergeStrategy by remember { mutableStateOf(MergeStrategy.SKIP_DUPLICATES) }
    var showPlainAlert by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.parse(familyId, uri, passphrase.ifBlank { null })
    }

    LaunchedEffect(state.exportUri) {
        val uri = state.exportUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ContextCompat.startActivity(ctx, Intent.createChooser(send, "Condividi export password"), null)
        snackbarHostState?.showSnackbar("Export completato")
        viewModel.clearExportUri()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState?.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Importa/Esporta password") }) }) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Esporta")
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Cifra con passphrase")
                        Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                    }
                    if (encrypt) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    Button(onClick = {
                        if (!encrypt) {
                            showPlainAlert = true
                        } else {
                            biometricThenExport(ctx as FragmentActivity) {
                                viewModel.export(familyId, familyName, passphrase.ifBlank { null })
                            }
                        }
                    }) { Text("Esporta password") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Importa")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passphrase (se cifrato)") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                        Text("Seleziona file")
                    }
                }
            }

            if (state.loading) CircularProgressIndicator()
        }
    }

    if (showPlainAlert) {
        AlertDialog(
            onDismissRequest = { showPlainAlert = false },
            title = { Text("Attenzione") },
            text = { Text("⚠️ Il file conterrà tutte le tue password in chiaro. Conservalo solo in luoghi affidabili.") },
            confirmButton = {
                TextButton(onClick = {
                    showPlainAlert = false
                    biometricThenExport(ctx as FragmentActivity) {
                        viewModel.export(familyId, familyName, null)
                    }
                }) { Text("Continua") }
            },
            dismissButton = { TextButton(onClick = { showPlainAlert = false }) { Text("Annulla") } },
        )
    }

    state.preview?.let { preview ->
        ModalBottomSheet(onDismissRequest = { viewModel.clearPreview() }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Import totali: ${preview.total}")
                Text("Conflitti: ${preview.conflicts.size}")
                Text("Nuovi gruppi: ${preview.newGroups.size}")
                Text("Errori: ${preview.errors.size}")
                if (preview.legacyAmbiguousRecordIndices.isNotEmpty()) {
                    val refs = preview.legacyAmbiguousRecordIndices.joinToString(", ") { "N$it" }
                    Text("Trovato testo ambiguo in ${preview.legacyAmbiguousRecordIndices.size} note — verifica i record $refs")
                }
                MergeStrategy.entries.forEach { strategy ->
                    TextButton(onClick = { mergeStrategy = strategy }) {
                        Text(if (mergeStrategy == strategy) "• $strategy" else strategy.name)
                    }
                }
                Button(
                    onClick = {
                        viewModel.commit(familyId, mergeStrategy)
                        viewModel.clearPreview()
                    },
                    enabled = preview.total > 0,
                ) {
                    Text("Importa ${preview.total} password")
                }
            }
        }
    }
}

private fun biometricThenExport(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticazione richiesta")
            .setDescription("Conferma identità per esportare le password")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build(),
    )
}
