package it.vittorioscocca.kidbox.ui.screens.settings

import android.Manifest
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinFamilyScreen(
    onBack: () -> Unit,
    onJoined: () -> Unit,
    viewModel: JoinFamilyViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) showScanner = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text("Entra con codice", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.kidBoxColors.title)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Inserisci il codice invito ricevuto oppure scansiona il QR code.",
            color = MaterialTheme.kidBoxColors.subtitle,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Campo codice testuale
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase(Locale.ROOT) },
            label = { Text("Codice invito") },
            placeholder = { Text("Es. K7P4D2") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card con bottone QR + bottone Entra
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        ) {
            // Bottone scanner QR — ora funzionante
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.size(22.dp),
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scansiona QR code", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.kidBoxColors.divider),
            )

            // Bottone Entra con codice testuale
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (code.isNotBlank()) viewModel.joinWithCode(code, onJoined)
                    },
                    enabled = !state.isBusy && code.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Entra")
                    }
                }
            }
        }

        // Errore
        if (!state.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                state.error.orEmpty(),
                color = Color(0xFFE53E3E),
                fontSize = 13.sp,
            )
        }

        // Successo
        if (state.didJoin) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Sei entrato nella famiglia! ✓", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
        }
    }

    // Bottom sheet con camera QR scanner
    if (showScanner) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showScanner = false },
            sheetState = sheetState,
            containerColor = Color.Black,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                contentAlignment = Alignment.Center,
            ) {
                QRScannerView(
                    onQRDetected = { payload ->
                        showScanner = false
                        viewModel.onQRScanned(payload, onJoined)
                    },
                )
                // Mirino
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent),
                ) {
                    // Angoli del mirino
                    CornerBrackets()
                }
                // Bottone chiudi
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Chiudi", tint = Color.White)
                }
                Text(
                    "Inquadra il QR code",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ── QR Scanner (CameraX + ML Kit) ────────────────────────────────────────────

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
internal fun QRScannerView(onQRDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detected by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            coroutineScope.launch {
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(ctx).await()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !detected) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    if (!detected) {
                                        barcodes.firstOrNull()?.rawValue?.let { value ->
                                            detected = true
                                            onQRDetected(value)
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    } catch (_: Exception) {}
                } catch (_: Exception) {}
            }
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun CornerBrackets() {
    // Disegna i 4 angoli del mirino con Canvas
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        val color = androidx.compose.ui.graphics.Color.White
        val cornerLen = 40f
        val r = 16f
        val w = size.width
        val h = size.height

        // Top-left
        drawLine(color, androidx.compose.ui.geometry.Offset(r, 0f), androidx.compose.ui.geometry.Offset(cornerLen, 0f), strokeWidth = 4f)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, r), androidx.compose.ui.geometry.Offset(0f, cornerLen), strokeWidth = 4f)
        // Top-right
        drawLine(color, androidx.compose.ui.geometry.Offset(w - cornerLen, 0f), androidx.compose.ui.geometry.Offset(w - r, 0f), strokeWidth = 4f)
        drawLine(color, androidx.compose.ui.geometry.Offset(w, r), androidx.compose.ui.geometry.Offset(w, cornerLen), strokeWidth = 4f)
        // Bottom-left
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, h - cornerLen), androidx.compose.ui.geometry.Offset(0f, h - r), strokeWidth = 4f)
        drawLine(color, androidx.compose.ui.geometry.Offset(r, h), androidx.compose.ui.geometry.Offset(cornerLen, h), strokeWidth = 4f)
        // Bottom-right
        drawLine(color, androidx.compose.ui.geometry.Offset(w, h - cornerLen), androidx.compose.ui.geometry.Offset(w, h - r), strokeWidth = 4f)
        drawLine(color, androidx.compose.ui.geometry.Offset(w - cornerLen, h), androidx.compose.ui.geometry.Offset(w - r, h), strokeWidth = 4f)
    }
}