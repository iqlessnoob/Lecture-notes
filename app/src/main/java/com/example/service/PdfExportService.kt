package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.LectureNote
import com.example.data.model.SubjectSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExportService(private val context: Context) {

    data class ExportResult(
        val file: File,
        val fileName: String,
        val shareIntent: Intent
    )

    suspend fun exportLectureToPdf(
        slot: SubjectSlot,
        lecture: LectureNote
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            val sanitizedSlotName = slot.name.replace(Regex("[^a-zA-Z0-9_-]"), "-")
            val sanitizedTitle = lecture.title.replace(Regex("[^a-zA-Z0-9_-]"), "-")
            val fileName = "${sanitizedSlotName}_${sanitizedTitle}.pdf"

            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!docsDir.exists()) docsDir.mkdirs()
            val pdfFile = File(docsDir, fileName)

            val document = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points
            val margin = 40f
            val contentWidth = pageWidth - (2 * margin)

            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yCursor = margin

            val titlePaint = Paint().apply {
                color = Color.parseColor("#1A237E")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#37474F")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val sectionHeaderPaint = Paint().apply {
                color = Color.parseColor("#0D47A1")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.parseColor("#263238")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val boldBodyPaint = Paint().apply {
                color = Color.parseColor("#1A237E")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val formulaBoxPaint = Paint().apply {
                color = Color.parseColor("#F4F6F9")
                style = Paint.Style.FILL
            }

            val formulaBorderPaint = Paint().apply {
                color = Color.parseColor("#CFD8DC")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val latexFontPaint = Paint().apply {
                color = Color.parseColor("#880E4F")
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
            }

            fun checkNewPage(neededHeight: Float) {
                if (yCursor + neededHeight > pageHeight - margin - 30) {
                    // Draw footer on current page
                    val footerPaint = Paint().apply {
                        color = Color.GRAY
                        textSize = 8f
                        isAntiAlias = true
                    }
                    canvas.drawText(
                        "LectureScribe AI • ${slot.name} • Page $currentPageNumber",
                        margin,
                        pageHeight - 20f,
                        footerPaint
                    )

                    document.finishPage(page)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yCursor = margin
                }
            }

            fun drawWrappedText(text: String, paint: Paint, maxWidth: Float, lineSpacing: Float = 14f) {
                val words = text.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val testWidth = paint.measureText(testLine)
                    if (testWidth > maxWidth && currentLine.isNotEmpty()) {
                        checkNewPage(lineSpacing)
                        canvas.drawText(currentLine, margin, yCursor, paint)
                        yCursor += lineSpacing
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    checkNewPage(lineSpacing)
                    canvas.drawText(currentLine, margin, yCursor, paint)
                    yCursor += lineSpacing
                }
            }

            // Header banner
            val headerBg = Paint().apply {
                color = Color.parseColor("#E8EAF6")
                style = Paint.Style.FILL
            }
            canvas.drawRect(margin, yCursor, margin + contentWidth, yCursor + 56, headerBg)

            val badgePaint = Paint().apply {
                color = Color.parseColor("#1A237E")
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("LECTURESCRIBE AI • COMPREHENSIVE STUDY NOTES", margin + 12, yCursor + 18, badgePaint)

            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(lecture.createdAt))
            canvas.drawText("Course: ${slot.name} | Processed: $dateStr | Duration: ${lecture.videoDurationFormatted}", margin + 12, yCursor + 34, subtitlePaint)
            canvas.drawText("Source: ${lecture.sourceType} (${lecture.sourceUri.take(50)})", margin + 12, yCursor + 48, subtitlePaint)

            yCursor += 70

            // Title
            val titleLines = lecture.title
            canvas.drawText(titleLines, margin, yCursor, titlePaint)
            yCursor += 24

            // Divider
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#1A237E")
                strokeWidth = 2f
            }
            canvas.drawLine(margin, yCursor, margin + contentWidth, yCursor, dividerPaint)
            yCursor += 20

            // SECTION 1: Summary
            checkNewPage(40f)
            canvas.drawText("1. Executive Lecture Summary", margin, yCursor, sectionHeaderPaint)
            yCursor += 16

            val summaryCardPaint = Paint().apply {
                color = Color.parseColor("#F1F8E9")
                style = Paint.Style.FILL
            }
            // Estimate summary box
            drawWrappedText(lecture.summary, bodyPaint, contentWidth)
            yCursor += 16

            // SECTION 2: Key Concepts
            val concepts = lecture.getKeyConcepts()
            if (concepts.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("2. Core Concepts & Takeaways", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                val bulletPaint = Paint().apply {
                    color = Color.parseColor("#2E7D32")
                    style = Paint.Style.FILL
                }

                for (concept in concepts) {
                    checkNewPage(24f)
                    canvas.drawCircle(margin + 6, yCursor - 4, 3f, bulletPaint)

                    val words = concept.split(" ")
                    var currentLine = ""
                    var isFirstLine = true
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val testWidth = bodyPaint.measureText(testLine)
                        val lineMax = if (isFirstLine) contentWidth - 18 else contentWidth - 18
                        if (testWidth > lineMax && currentLine.isNotEmpty()) {
                            checkNewPage(14f)
                            canvas.drawText(currentLine, if (isFirstLine) margin + 18 else margin + 18, yCursor, bodyPaint)
                            yCursor += 14f
                            currentLine = word
                            isFirstLine = false
                        } else {
                            currentLine = testLine
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        checkNewPage(14f)
                        canvas.drawText(currentLine, if (isFirstLine) margin + 18 else margin + 18, yCursor, bodyPaint)
                        yCursor += 16f
                    }
                }
                yCursor += 8
            }

            // SECTION 3: Formulas (with LaTeX notation)
            val formulas = lecture.getFormulas()
            if (formulas.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("3. Formulas & Quantitative Models", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                for (formula in formulas) {
                    checkNewPage(65f)
                    val boxTop = yCursor - 4
                    canvas.drawRect(margin, boxTop, margin + contentWidth, boxTop + 54, formulaBoxPaint)
                    canvas.drawRect(margin, boxTop, margin + contentWidth, boxTop + 54, formulaBorderPaint)

                    canvas.drawText("Formula: ${formula.name}", margin + 10, yCursor + 12, boldBodyPaint)
                    canvas.drawText("LaTeX: ${formula.latex}", margin + 10, yCursor + 28, latexFontPaint)
                    canvas.drawText(formula.explanation, margin + 10, yCursor + 44, bodyPaint)
                    yCursor += 66
                }
                yCursor += 8
            }

            // SECTION 4: Recreated Diagrams
            val diagrams = lecture.getDiagrams()
            if (diagrams.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("4. Recreated Digital Diagrams & Models", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                for (diagram in diagrams) {
                    checkNewPage(70f)
                    val boxTop = yCursor - 4
                    val boxHeight = if (diagram.mermaidCode.isNotBlank()) 90f else 60f
                    canvas.drawRect(margin, boxTop, margin + contentWidth, boxTop + boxHeight, formulaBoxPaint)
                    canvas.drawRect(margin, boxTop, margin + contentWidth, boxTop + boxHeight, formulaBorderPaint)

                    canvas.drawText(
                        "${diagram.label} [${diagram.diagramType.uppercase()}] • Timestamp: ${diagram.timestamp}",
                        margin + 10,
                        yCursor + 12,
                        boldBodyPaint
                    )

                    // Draw description
                    val descWords = diagram.description.split(" ")
                    var descLine = ""
                    var descY = yCursor + 26
                    for (w in descWords) {
                        val test = if (descLine.isEmpty()) w else "$descLine $w"
                        if (bodyPaint.measureText(test) > contentWidth - 20) {
                            canvas.drawText(descLine, margin + 10, descY, bodyPaint)
                            descY += 12f
                            descLine = w
                        } else {
                            descLine = test
                        }
                    }
                    if (descLine.isNotEmpty()) {
                        canvas.drawText(descLine, margin + 10, descY, bodyPaint)
                        descY += 14f
                    }

                    if (diagram.mermaidCode.isNotBlank()) {
                        val firstMermaidLine = diagram.mermaidCode.lines().firstOrNull { it.isNotBlank() } ?: "Mermaid.js Flowchart"
                        canvas.drawText("Mermaid Spec: $firstMermaidLine", margin + 10, descY + 2, latexFontPaint)
                    }

                    yCursor += boxHeight + 14
                }
                yCursor += 8
            }

            // SECTION 5: Topic Timestamps
            val timestamps = lecture.getTimestamps()
            if (timestamps.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("5. Topic Changes & Video Timeline", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                for (ts in timestamps) {
                    checkNewPage(18f)
                    val timePaint = Paint().apply {
                        color = Color.parseColor("#00695C")
                        textSize = 9.5f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    canvas.drawText(ts.time.padEnd(8), margin + 4, yCursor, timePaint)
                    canvas.drawText(ts.topic, margin + 60, yCursor, bodyPaint)
                    yCursor += 16
                }
            }

            // Draw final page footer
            val footerPaint = Paint().apply {
                color = Color.GRAY
                textSize = 8f
                isAntiAlias = true
            }
            canvas.drawText(
                "LectureScribe AI • ${slot.name} • Page $currentPageNumber",
                margin,
                pageHeight - 20f,
                footerPaint
            )

            document.finishPage(page)

            // Write document to file
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            // Prepare share intent
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Lecture Notes: ${lecture.title}")
                putExtra(Intent.EXTRA_TEXT, "Here are the study notes for '${lecture.title}' exported from LectureScribe AI.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            Result.success(ExportResult(pdfFile, fileName, shareIntent))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
