package io.github.c0dewars.pvcalculator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.c0dewars.pvcalculator.domain.OcrToken
import io.github.c0dewars.pvcalculator.domain.RowBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Extracts text from attachments, fully on-device:
 * - PDFs with a text layer are read directly (fast and exact);
 * - scanned PDFs, photos and screenshots go through ML Kit OCR.
 */
class DocumentReader(private val context: Context) {

    @Volatile
    private var pdfBoxReady = false

    /** Text as visual rows. OCR keeps the position of each piece of text so labels can be matched to values. */
    suspend fun readRows(file: File, mimeType: String): List<List<OcrToken>> = withContext(Dispatchers.IO) {
        if (mimeType == "application/pdf") {
            val text = runCatching { pdfText(file) }.getOrDefault("")
            if (text.count { it.isLetterOrDigit() } > 40) RowBuilder.fromText(text)
            else runCatching { ocrPdf(file) }.getOrDefault(emptyList())
        } else {
            runCatching { decodeImage(file)?.let { RowBuilder.rows(ocrBitmap(it)) } }.getOrNull().orEmpty()
        }
    }

    suspend fun readText(file: File, mimeType: String): String = RowBuilder.toText(readRows(file, mimeType))

    private fun pdfText(file: File): String {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                startPage = 1
                endPage = minOf(doc.numberOfPages, 4)
            }
            return stripper.getText(doc)
        }
    }

    private suspend fun ocrPdf(file: File): List<List<OcrToken>> {
        val pages = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, 2)) {
                    renderer.openPage(i).use { page ->
                        val scale = 2000f / maxOf(page.width, page.height)
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888,
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages += bmp
                    }
                }
            }
        }
        return pages.flatMap { RowBuilder.rows(ocrBitmap(it)) }
    }

    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private suspend fun ocrBitmap(bitmap: Bitmap): List<OcrToken> = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val tokens = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrToken(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
                }
                cont.resume(tokens)
            }
            .addOnFailureListener { cont.resume(emptyList()) }
            .addOnCompleteListener { recognizer.close() }
    }

    companion object {
        fun mimeTypeOf(context: Context, uri: Uri): String =
            context.contentResolver.getType(uri)
                ?: when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
                    "pdf" -> "application/pdf"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
    }
}
