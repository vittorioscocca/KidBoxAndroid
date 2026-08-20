package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import android.Manifest
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.vittorioscocca.kidbox.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

/**
 * Scanner camera per carte fedeltà: estende lo scanner QR già usato in
 * `JoinFamilyScreen` ai formati barcode retail più comuni (EAN-13/8,
 * Code128/39, PDF417, UPC-E, ITF, Aztec, QR), riusando CameraX + ML Kit già
 * in dipendenza (nessuna nuova libreria). Include un link di fallback per
 * l'inserimento manuale.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun LoyaltyCardBarcodeScanner(
    onDetect: (text: String, format: String) -> Unit,
    onManualFallback: () -> Unit,
) {
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraPermissionGranted = granted }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            cameraPermissionGranted = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionGranted) {
            LoyaltyCardBarcodeCameraView(onDetect = onDetect)
        }

        // Stessa struttura della controparte iOS: uno Spacer sopra e uno sotto
        // centrano il gruppo mirino+istruzioni, poi un altro Spacer separa quel
        // gruppo dal link di fallback in fondo — non più tre elementi
        // posizionati/allineati indipendentemente.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 95.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Mirino
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        style = Stroke(width = 4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Istruzioni: proprio sotto il mirino, come su iOS.
            Text(
                stringResource(R.string.wallet_loyalty_scan_instructions),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Link sottolineato verso la compilazione manuale, in fondo,
            // separato dal gruppo mirino+istruzioni — come su iOS.
            Text(
                stringResource(R.string.wallet_loyalty_scan_manual_fallback),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onManualFallback),
            )
        }
    }
}

private val supportedFormats = intArrayOf(
    Barcode.FORMAT_EAN_13,
    Barcode.FORMAT_EAN_8,
    Barcode.FORMAT_CODE_128,
    Barcode.FORMAT_CODE_39,
    Barcode.FORMAT_PDF417,
    Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_ITF,
    Barcode.FORMAT_AZTEC,
    Barcode.FORMAT_QR_CODE,
)

private fun normalizedFormat(mlKitFormat: Int): String = when (mlKitFormat) {
    Barcode.FORMAT_EAN_13 -> "ean13"
    Barcode.FORMAT_EAN_8 -> "ean8"
    Barcode.FORMAT_CODE_128 -> "code128"
    Barcode.FORMAT_CODE_39 -> "code39"
    Barcode.FORMAT_PDF417 -> "pdf417"
    Barcode.FORMAT_UPC_E -> "upce"
    Barcode.FORMAT_ITF -> "itf14"
    Barcode.FORMAT_AZTEC -> "aztec"
    Barcode.FORMAT_QR_CODE -> "qr"
    else -> "unknown"
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun LoyaltyCardBarcodeCameraView(onDetect: (String, String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detected by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(supportedFormats.first(), *supportedFormats.drop(1).toIntArray())
            .build()
    }
    val barcodeScanner = remember { BarcodeScanning.getClient(options) }
    val coroutineScope = rememberCoroutineScope()

    androidx.compose.ui.viewinterop.AndroidView(
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
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    if (!detected) {
                                        barcodes.firstOrNull()?.let { code ->
                                            val value = code.rawValue
                                            if (!value.isNullOrBlank()) {
                                                detected = true
                                                onDetect(value, normalizedFormat(code.format))
                                            }
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
