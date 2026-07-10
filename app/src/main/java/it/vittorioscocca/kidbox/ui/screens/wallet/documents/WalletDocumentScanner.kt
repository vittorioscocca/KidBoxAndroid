package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WalletDocumentScanner"

/** Risultato di una sessione di scansione: pagine come bitmap (per OCR/anteprima) + PDF pronto per l'upload. */
data class WalletDocumentScanResult(
    val pages: List<Bitmap>,
    val pdfBytes: ByteArray?,
)

/**
 * Scanner documenti di sistema (ML Kit Document Scanner / `GmsDocumentScanning`),
 * equivalente Android di `VNDocumentCameraViewController` (VisionKit) su iOS:
 * rilevamento bordi automatico, multi-pagina in un'unica sessione, restituisce
 * sia le pagine come JPEG (per OCR locale/AI) sia un PDF già pronto per l'upload.
 *
 * @return funzione da invocare per avviare la scansione (es. da un `onClick`).
 */
@Composable
fun rememberWalletDocumentScannerLauncher(
    pageLimit: Int = 6,
    onResult: (WalletDocumentScanResult) -> Unit,
    onCancelledOrFailed: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val data = activityResult.data
        if (activityResult.resultCode != Activity.RESULT_OK || data == null) {
            onCancelledOrFailed()
            return@rememberLauncherForActivityResult
        }
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(data)
        if (scanResult == null) {
            onCancelledOrFailed()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val pages = scanResult.pages.orEmpty().mapNotNull { page ->
                    runCatching {
                        context.contentResolver.openInputStream(page.imageUri)?.use { BitmapFactory.decodeStream(it) }
                    }.onFailure { KBLog.data.warning("decode page failed: ${it.message}", TAG) }.getOrNull()
                }
                val pdfBytes = scanResult.pdf?.uri?.let { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.onFailure { KBLog.data.warning("read pdf failed: ${it.message}", TAG) }.getOrNull()
                }
                WalletDocumentScanResult(pages = pages, pdfBytes = pdfBytes)
            }
            if (result.pages.isEmpty()) onCancelledOrFailed() else onResult(result)
        }
    }

    val scannerOptions = remember(pageLimit) {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    return {
        val activity = context as? Activity
        if (activity == null) {
            KBLog.data.warning("scanner start failed: context non è un'Activity", TAG)
            onCancelledOrFailed()
        } else {
            val scanner = GmsDocumentScanning.getClient(scannerOptions)
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    KBLog.data.warning("getStartScanIntent failed: ${it.message}", TAG)
                    onCancelledOrFailed()
                }
        }
    }
}
