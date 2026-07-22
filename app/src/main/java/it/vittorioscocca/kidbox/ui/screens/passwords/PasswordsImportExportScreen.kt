package it.vittorioscocca.kidbox.ui.screens.passwords

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
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
        ContextCompat.startActivity(ctx, Intent.createChooser(send, ctx.getString(R.string.passwords_share_export_chooser_title)), null)
        snackbarHostState?.showSnackbar(ctx.getString(R.string.passwords_export_completed_snackbar))
        viewModel.clearExportUri()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState?.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.passwords_import_export_title)) }) }) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.passwords_export_label))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.passwords_encrypt_passphrase_label))
                        Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                    }
                    if (encrypt) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.passwords_passphrase_label)) },
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
                    }) { Text(stringResource(R.string.passwords_export_password_button)) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.passwords_import_label))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.passwords_passphrase_optional_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Button(onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                        Text(stringResource(R.string.passwords_select_file_button))
                    }
                }
            }

            if (state.loading) CircularProgressIndicator()
        }
    }

    if (showPlainAlert) {
        AlertDialog(
            onDismissRequest = { showPlainAlert = false },
            title = { Text(stringResource(R.string.passwords_warning_dialog_title)) },
            text = { Text(stringResource(R.string.passwords_plain_export_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPlainAlert = false
                    biometricThenExport(ctx as FragmentActivity) {
                        viewModel.export(familyId, familyName, null)
                    }
                }) { Text(stringResource(R.string.location_continue_button)) }
            },
            dismissButton = { TextButton(onClick = { showPlainAlert = false }) { Text(stringResource(R.string.location_cancel_button)) } },
        )
    }

    state.preview?.let { preview ->
        ModalBottomSheet(onDismissRequest = { viewModel.clearPreview() }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.passwords_import_total_label, preview.total))
                Text(stringResource(R.string.passwords_import_conflicts_label, preview.conflicts.size))
                Text(stringResource(R.string.passwords_import_new_groups_label, preview.newGroups.size))
                Text(stringResource(R.string.passwords_import_errors_label, preview.errors.size))
                if (preview.legacyAmbiguousRecordIndices.isNotEmpty()) {
                    val refs = preview.legacyAmbiguousRecordIndices.joinToString(", ") { "N$it" }
                    Text(stringResource(R.string.passwords_import_ambiguous_text_warning, preview.legacyAmbiguousRecordIndices.size, refs))
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
                    Text(stringResource(R.string.passwords_import_count_button, preview.total))
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
            .setTitle(activity.getString(R.string.passwords_biometric_title))
            .setDescription(activity.getString(R.string.passwords_biometric_description))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build(),
    )
}
