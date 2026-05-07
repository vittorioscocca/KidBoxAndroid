package it.vittorioscocca.kidbox.data.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class HealthDocumentTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extractText(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): String = withContext(Dispatchers.Default) {
        val mt = mimeType.lowercase()
        when {
            mt.contains("pdf") || fileName.lowercase().endsWith(".pdf") -> extractPdfText(bytes)
            mt.startsWith("image/") -> extractImageText(bytes)
            else -> ""
        }
    }

    private suspend fun extractPdfText(bytes: ByteArray): String {
        val directText = runCatching {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(bytes).use { doc ->
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(12, doc.numberOfPages)
                }
                stripper.getText(doc).orEmpty().trim()
            }
        }.getOrDefault("")
        if (directText.isNotBlank()) return directText
        return runCatching { extractPdfTextWithOcr(bytes) }.getOrDefault("")
    }

    private suspend fun extractPdfTextWithOcr(bytes: ByteArray): String = withContext(Dispatchers.Default) {
        val tempFile = File.createTempFile("kb_ocr_", ".pdf", context.cacheDir)
        FileOutputStream(tempFile).use { it.write(bytes) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val buffer = StringBuilder()
        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, 6)
                    for (pageIndex in 0 until pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val width = (page.width * 2).coerceAtLeast(1200)
                            val height = (page.height * 2).coerceAtLeast(1200)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val image = InputImage.fromBitmap(bmp, 0)
                                val text = recognizer.process(image).await().text.orEmpty().trim()
                                if (text.isNotBlank()) {
                                    if (buffer.isNotEmpty()) buffer.append("\n\n")
                                    buffer.append(text)
                                }
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
            tempFile.delete()
        }
        buffer.toString().trim()
    }

    private suspend fun extractImageText(bytes: ByteArray): String {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromBitmap(bmp, 0)
            recognizer.process(image).await().text.orEmpty().trim()
        } finally {
            recognizer.close()
            bmp.recycle()
        }
    }
}

