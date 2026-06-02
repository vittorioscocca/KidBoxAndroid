@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.passwords

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import it.vittorioscocca.kidbox.data.passwords.otp.OtpConfig
import it.vittorioscocca.kidbox.data.passwords.otp.OtpUriParser
import it.vittorioscocca.kidbox.data.passwords.otp.TotpGenerator
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private val PasswordsAccentPurple = Color(0xFF9973D9)

@Composable
fun PasswordDetailScreen(
    familyId: String,
    passwordId: String,
    onBack: () -> Unit,
    onChangePassword: (() -> Unit)? = null,
    onOpenSecurityReport: ((String) -> Unit)? = null,
    viewModel: PasswordDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showActionsDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var showOtpDeleteDialog by remember { mutableStateOf(false) }
    var showOtpScanner by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val kb = MaterialTheme.kidBoxColors

    fun showCreatorOnlyMessage() {
        Toast.makeText(
            context,
            "Solo il creatore puo modificare o eliminare questa password.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    LaunchedEffect(passwordId) {
        viewModel.load(passwordId)
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(kb.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Caricamento...", color = kb.subtitle)
        }
        return
    }

    if (state.notFound) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(kb.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Password non trovata", color = kb.subtitle)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = kb.card, shadowElevation = 1.dp) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                }
            }
            Text(
                text = state.title.ifBlank { "Password" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = kb.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = CircleShape, color = kb.card, shadowElevation = 1.dp) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Preferita",
                        tint = if (state.isFavorite) Color(0xFFFFB300) else kb.subtitle,
                    )
                }
            }
            Surface(shape = CircleShape, color = kb.card, shadowElevation = 1.dp) {
                IconButton(
                    onClick = {
                        if (state.canManage && onChangePassword != null) onChangePassword.invoke()
                        else showCreatorOnlyMessage()
                    },
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Modifica", tint = kb.title)
                }
            }
            Surface(shape = CircleShape, color = kb.card, shadowElevation = 1.dp) {
                IconButton(
                    onClick = { showActionsDialog = true },
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "Altro", tint = kb.title)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HeaderCard(iconUrl = state.iconUrl, title = state.title)
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionPill(
                label = "Copia utente",
                icon = Icons.Filled.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(state.username)) },
                modifier = Modifier.weight(1f),
            )
            ActionPill(
                label = "Copia password",
                icon = Icons.Filled.Key,
                isPrimary = true,
                onClick = { clipboard.setText(AnnotatedString(state.password)) },
                modifier = Modifier.weight(1f),
            )
            ActionPill(
                label = "Cambia password",
                icon = Icons.Filled.Edit,
                onClick = {
                    if (state.canManage && onChangePassword != null) onChangePassword.invoke()
                    else showCreatorOnlyMessage()
                },
                enabled = onChangePassword != null,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        InfoCard {
            InfoRow(label = "Titolo", value = state.title.ifBlank { "—" })
            InfoRow(label = "Nome utente", value = state.username.ifBlank { "—" })
            PasswordRow(
                label = "Password",
                passwordValue = state.password,
                showPassword = showPassword,
                onTogglePassword = { showPassword = !showPassword },
            )
            InfoRow(label = "Sito web", value = state.website.ifBlank { "—" })
            if (state.notes.isNotBlank()) {
                InfoRow(label = "Note", value = state.notes)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        InfoCard {
            Text("Sicurezza password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = kb.title)
            Spacer(modifier = Modifier.height(8.dp))
            SecurityLine(
                title = "Controllo fughe (HIBP)",
                body = if ((state.pwnedCount ?: 0) > 0) {
                    "Trovata ${state.pwnedCount} volte in data breach pubblici."
                } else {
                    "Nessuna compromissione nota."
                },
                footer = if (state.pwnedCheckedAtLabel.isBlank()) "" else "Ultimo controllo: ${state.pwnedCheckedAtLabel}",
                warning = (state.pwnedCount ?: 0) > 0,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = kb.divider)
            SecurityLine(
                title = "Password duplicata",
                body = if (state.hasDuplicate) {
                    "Vulnerabile: questa password e usata anche in altri account."
                } else {
                    "Nessun altra voce nella famiglia usa la stessa password."
                },
                icon = Icons.Filled.ContentCopy,
                iconTint = if (state.hasDuplicate) Color(0xFFFFC107) else Color(0xFF34C759),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Forza stimata", style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
            LinearProgressIndicator(
                progress = { (state.estimatedBits / 80f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp),
                color = PasswordsAccentPurple,
                trackColor = kb.divider,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.strengthLabel, color = Color(0xFFFF8A00), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("~${state.estimatedBits} bit", color = kb.subtitle)
            }
            Text(state.strengthAdvice, color = Color(0xFFFF8A00), modifier = Modifier.padding(top = 4.dp))
            if (state.isVulnerable) {
                Text(
                    "Password vulnerabile: risulta esposta o riutilizzata.",
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { onOpenSecurityReport?.invoke(state.familyId.ifBlank { familyId }) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDFF), contentColor = PasswordsAccentPurple),
            ) {
                Text("Apri report Sicurezza password")
            }
        }

        state.otpConfig?.let { config ->
            Spacer(modifier = Modifier.height(12.dp))
            OtpSection(
                config = config,
                onCopy = { otp ->
                    clipboard.setText(AnnotatedString(otp.replace(" ", "")))
                    Toast.makeText(context, "OTP copiato", Toast.LENGTH_SHORT).show()
                },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Ultimo aggiornamento: ${state.updatedAtLabel}", color = kb.subtitle)
        Text("Password modificata: ${state.passwordUpdatedAtLabel}", color = kb.subtitle)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina password") },
            text = { Text("Questa azione non e reversibile.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteCurrent(onDone = onBack)
                    },
                ) { Text("Elimina") }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) { Text("Annulla") }
            },
        )
    }

    if (showActionsDialog) {
        AlertDialog(
            onDismissRequest = { showActionsDialog = false },
            title = { Text("Azioni password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showActionsDialog = false
                            if (state.canManage) {
                                otpInput = state.otpConfig?.secret.orEmpty()
                                showOtpDialog = true
                            } else {
                                showCreatorOnlyMessage()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Configura OTP")
                    }
                    if (state.otpConfig != null) {
                        Button(
                            onClick = {
                                showActionsDialog = false
                                if (state.canManage) showOtpDeleteDialog = true else showCreatorOnlyMessage()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Elimina OTP")
                        }
                    }
                    Button(
                        onClick = {
                            showActionsDialog = false
                            if (state.canManage) showDeleteDialog = true else showCreatorOnlyMessage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEFEF), contentColor = Color(0xFFC62828)),
                    ) {
                        Text("Elimina password")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { Button(onClick = { showActionsDialog = false }) { Text("Chiudi") } },
        )
    }

    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text("Configura OTP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Chiave Base32 o otpauth://") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                    )
                    Button(
                        onClick = {
                            showOtpDialog = false
                            showOtpScanner = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Scan QR Code")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val raw = otpInput.trim().uppercase()
                        val config = if (raw.startsWith("otpauth://", ignoreCase = true)) {
                            OtpUriParser.parse(raw)
                        } else {
                            OtpConfig(secret = raw, period = 30, digits = 6, algorithm = "SHA1")
                        }
                        if (config == null || config.secret.isBlank()) {
                            Toast.makeText(context, "Config OTP non valida", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.saveOtpConfig(config)
                            showOtpDialog = false
                        }
                    },
                ) { Text("Salva") }
            },
            dismissButton = { Button(onClick = { showOtpDialog = false }) { Text("Annulla") } },
        )
    }

    if (showOtpDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDeleteDialog = false },
            title = { Text("Elimina OTP") },
            text = { Text("Vuoi eliminare la configurazione OTP di questa password?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteOtpConfig()
                    showOtpDeleteDialog = false
                }) { Text("Elimina") }
            },
            dismissButton = { Button(onClick = { showOtpDeleteDialog = false }) { Text("Annulla") } },
        )
    }

    if (showOtpScanner) {
        AlertDialog(
            onDismissRequest = { showOtpScanner = false },
            title = { Text("Scanner OTP") },
            text = {
                OtpQrScanner(
                    onConfigDetected = { config ->
                        viewModel.saveOtpConfig(config)
                        showOtpScanner = false
                    },
                )
            },
            confirmButton = { Button(onClick = { showOtpScanner = false }) { Text("Chiudi") } },
        )
    }

}

@Composable
private fun OtpSection(
    config: OtpConfig,
    onCopy: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val tick by produceState(initialValue = 0L, key1 = config) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val period = config.period.coerceAtLeast(1)
    val counter = tick / 1000L / period
    val code = remember(counter, config) { runCatching { TotpGenerator.generateTOTP(config, tick) }.getOrNull() }
    val remainingSeconds = (period - ((tick / 1000L) % period).toInt()).coerceAtLeast(0)
    val progress = remainingSeconds.toFloat() / period.toFloat()
    val formatted = remember(code) { code?.chunked(3)?.joinToString(" ") ?: "------" }

    InfoCard {
        Text("OTP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = kb.title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(formatted, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = kb.title)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = PasswordsAccentPurple,
            trackColor = kb.divider,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${remainingSeconds}s", color = kb.subtitle)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { if (code != null) onCopy(formatted) }, enabled = code != null) {
                Text("Copia")
            }
        }
    }
}

@Composable
private fun OtpQrScanner(
    onConfigDetected: (OtpConfig) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Text("Permesso fotocamera non concesso")
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val executor = Executors.newSingleThreadExecutor()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val image = imageProxy.image
                    if (image == null) {
                        imageProxy.close()
                    } else {
                        val input = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(input)
                            .addOnSuccessListener { codes ->
                                val cfg = OtpUriParser.parse(codes.firstOrNull()?.rawValue.orEmpty())
                                if (cfg != null) onConfigDetected(cfg)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@Composable
private fun SecurityLine(
    title: String,
    body: String,
    footer: String = "",
    warning: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon ?: if (warning) Icons.Filled.Warning else Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = iconTint ?: if (warning) Color(0xFFE53935) else Color(0xFF34C759),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = kb.subtitle)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = kb.title)
            if (footer.isNotBlank()) Text(footer, color = kb.subtitle)
        }
    }
}

@Composable
private fun HeaderCard(iconUrl: String?, title: String) {
    val kb = MaterialTheme.kidBoxColors
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Surface(color = kb.subtitle.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            title.take(1).uppercase().ifBlank { "?" },
                            color = kb.subtitle,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title.ifBlank { "Password" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = kb.title,
            )
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
) {
    val kb = MaterialTheme.kidBoxColors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) PasswordsAccentPurple else kb.divider,
            contentColor = if (isPrimary) Color.White else kb.title,
            disabledContainerColor = kb.divider,
            disabledContentColor = kb.subtitle,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 12.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = kb.card,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(label, style = MaterialTheme.typography.labelMedium, color = kb.subtitle)
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = kb.title,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun PasswordRow(
    label: String,
    passwordValue: String,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = kb.subtitle, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.clickable(onClick = onTogglePassword),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
                tint = PasswordsAccentPurple,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (showPassword) "Nascondi" else "Mostra", color = PasswordsAccentPurple)
        }
    }
    Text(
        text = if (showPassword) passwordValue else "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
        style = MaterialTheme.typography.bodyLarge,
        color = kb.title,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    )
}
