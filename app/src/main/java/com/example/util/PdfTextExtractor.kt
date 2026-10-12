package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PdfPageInfo(
    val pageIndex: Int,
    val imagePath: String,
    val width: Int,
    val height: Int
)

data class PdfExtractionResult(
    val title: String,
    val pageCount: Int,
    val pages: List<PdfPageInfo>,
    val pdfFilePath: String
)

object PdfTextExtractor {

    suspend fun extractFromUri(context: Context, uri: Uri): Result<PdfExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val slidesDir = File(context.filesDir, "slides").apply { mkdirs() }
            val tempPdfFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.pdf")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open PDF file stream"))

            val titleFromUri = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".pdf") ?: "Medical Lecture"
            val sanitizedTitle = titleFromUri.replace('_', ' ').replace('-', ' ')

            val pfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            val pagesList = mutableListOf<PdfPageInfo>()
            val timestamp = System.currentTimeMillis()

            for (i in 0 until pageCount) {
                try {
                    val page = renderer.openPage(i)
                    // Render page at max width 1600px preserving aspect ratio
                    val scale = if (page.width > 0) {
                        (1600f / page.width).coerceAtMost(2.0f)
                    } else 1.0f
                    val targetWidth = (page.width * scale).toInt().coerceIn(1, 1600)
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageFile = File(slidesDir, "slide_${timestamp}_p${i + 1}.jpg")
                    FileOutputStream(pageFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                    }
                    bitmap.recycle()

                    pagesList.add(
                        PdfPageInfo(
                            pageIndex = i + 1,
                            imagePath = pageFile.absolutePath,
                            width = targetWidth,
                            height = targetHeight
                        )
                    )
                } catch (e: Exception) {
                    Log.e("PdfTextExtractor", "Error rendering PDF page $i", e)
                }
            }

            renderer.close()
            pfd.close()

            Result.success(
                PdfExtractionResult(
                    title = sanitizedTitle,
                    pageCount = pageCount,
                    pages = pagesList,
                    pdfFilePath = tempPdfFile.absolutePath
                )
            )
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Failed extracting PDF pages", e)
            Result.failure(e)
        }
    }

    /**
     * Phase 5: Crops a validated visual region using normalized [ymin, xmin, ymax, xmax] (0-1000 scale).
     * Validates min size (5%), drops degenerate boxes, pads by ~3%, and returns cropped filename or null (falling back to full page).
     */
    fun cropBoundingBoxFromPage(
        sourceImagePath: String,
        outputDir: File,
        baseName: String,
        box: List<Int>?
    ): String? {
        if (box == null || box.size < 4) return null
        var ymin = box[0].coerceIn(0, 1000)
        var xmin = box[1].coerceIn(0, 1000)
        var ymax = box[2].coerceIn(0, 1000)
        var xmax = box[3].coerceIn(0, 1000)

        // Drop degenerate or too small boxes (<5% of dimension)
        if (ymax <= ymin || xmax <= xmin) return null
        if (ymax - ymin < 50 || xmax - xmin < 50) return null

        // Pad by ~3% (30 units on 0-1000 scale)
        ymin = (ymin - 30).coerceAtLeast(0)
        xmin = (xmin - 30).coerceAtLeast(0)
        ymax = (ymax + 30).coerceAtMost(1000)
        xmax = (xmax + 30).coerceAtMost(1000)

        val topPercent = ymin / 1000f
        val leftPercent = xmin / 1000f
        val bottomPercent = ymax / 1000f
        val rightPercent = xmax / 1000f

        return cropDiagramFromPage(
            sourceImagePath = sourceImagePath,
            outputDir = outputDir,
            baseName = baseName,
            topPercent = topPercent,
            bottomPercent = bottomPercent,
            leftPercent = leftPercent,
            rightPercent = rightPercent
        )
    }

    /**
     * Crops a detected visual element (e.g. diagram, histology image, table)
     * using normalized bounding coordinates (0.0 to 1.0) and saves it to the media folder.
     */
    fun cropDiagramFromPage(
        sourceImagePath: String,
        outputDir: File,
        baseName: String,
        topPercent: Float = 0.05f,
        bottomPercent: Float = 0.95f,
        leftPercent: Float = 0.05f,
        rightPercent: Float = 0.95f
    ): String? {
        return try {
            val sourceFile = File(sourceImagePath)
            if (!sourceFile.exists()) return null

            val originalBitmap = BitmapFactory.decodeFile(sourceImagePath) ?: return null
            val width = originalBitmap.width
            val height = originalBitmap.height

            val left = (width * leftPercent.coerceIn(0f, 1f)).toInt()
            val top = (height * topPercent.coerceIn(0f, 1f)).toInt()
            val right = (width * rightPercent.coerceIn(0f, 1f)).toInt()
            val bottom = (height * bottomPercent.coerceIn(0f, 1f)).toInt()

            val cropWidth = (right - left).coerceAtLeast(100).coerceAtMost(width - left)
            val cropHeight = (bottom - top).coerceAtLeast(100).coerceAtMost(height - top)

            val croppedBitmap = Bitmap.createBitmap(originalBitmap, left, top, cropWidth, cropHeight)
            outputDir.mkdirs()
            val destFile = File(outputDir, "${baseName}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { fos ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }

            if (croppedBitmap != originalBitmap) {
                croppedBitmap.recycle()
            }
            originalBitmap.recycle()

            destFile.name
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Failed to crop diagram", e)
            null
        }
    }

    fun fileToBase64(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Failed to convert file to Base64", e)
            null
        }
    }
}
