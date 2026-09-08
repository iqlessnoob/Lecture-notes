package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.LectureNote
import com.example.data.model.SubjectSlot
import com.example.util.LatexToHumanConverter
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

            val humanMathPaint = Paint().apply {
                color = Color.parseColor("#0D47A1")
                textSize = 12f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            val diagramNodeBgPaint = Paint().apply {
                color = Color.parseColor("#E8EAF6")
                style = Paint.Style.FILL
            }

            val diagramNodeBorderPaint = Paint().apply {
                color = Color.parseColor("#3F51B5")
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }

            val diagramNodeTextPaint = Paint().apply {
                color = Color.parseColor("#1A237E")
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            val arrowPaint = Paint().apply {
                color = Color.parseColor("#3F51B5")
                strokeWidth = 1.5f
                isAntiAlias = true
            }

            fun extractDiagramNodes(mermaidCode: String): List<String> {
                if (mermaidCode.isBlank()) return emptyList()
                val nodes = mutableListOf<String>()
                val regex = Regex("""\[(.*?)\]|\((.*?)\)""")
                for (line in mermaidCode.lines()) {
                    val matches = regex.findAll(line)
                    for (match in matches) {
                        val label = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim()
                        if (!label.isNullOrBlank() && !nodes.contains(label)) {
                            nodes.add(label)
                        }
                    }
                }
                return nodes
            }

            // Divider
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#1A237E")
                strokeWidth = 2f
            }
            canvas.drawLine(margin, yCursor, margin + contentWidth, yCursor, dividerPaint)
            yCursor += 20

            var sectionIndex = 1

            // SECTION 1: Summary
            checkNewPage(40f)
            canvas.drawText("${sectionIndex++}. Executive Lecture Summary", margin, yCursor, sectionHeaderPaint)
            yCursor += 16

            val cleanSummary = LatexToHumanConverter.cleanMarkdownAndMath(lecture.summary)
            drawWrappedText(cleanSummary, bodyPaint, contentWidth)
            yCursor += 16

            // SECTION 2: Key Concepts
            val concepts = lecture.getKeyConcepts()
            if (concepts.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("${sectionIndex++}. Core Concepts & Takeaways", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                val bulletPaint = Paint().apply {
                    color = Color.parseColor("#2E7D32")
                    style = Paint.Style.FILL
                }

                for (concept in concepts) {
                    val cleanConcept = LatexToHumanConverter.cleanMarkdownAndMath(concept)
                    checkNewPage(24f)
                    canvas.drawCircle(margin + 6, yCursor - 4, 3f, bulletPaint)

                    val words = cleanConcept.split(" ")
                    var currentLine = ""
                    var isFirstLine = true
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val testWidth = bodyPaint.measureText(testLine)
                        val lineMax = contentWidth - 18
                        if (testWidth > lineMax && currentLine.isNotEmpty()) {
                            checkNewPage(14f)
                            canvas.drawText(currentLine, margin + 18, yCursor, bodyPaint)
                            yCursor += 14f
                            currentLine = word
                            isFirstLine = false
                        } else {
                            currentLine = testLine
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        checkNewPage(14f)
                        canvas.drawText(currentLine, margin + 18, yCursor, bodyPaint)
                        yCursor += 16f
                    }
                }
                yCursor += 8
            }

            // SECTION 3: Formulas (Human-Readable Form)
            val formulas = lecture.getFormulas().filter { it.latex.isNotBlank() && it.name.isNotBlank() }
            if (formulas.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("${sectionIndex++}. Formulas & Quantitative Models", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                for (formula in formulas) {
                    val humanEq = LatexToHumanConverter.convert(formula.latex)
                    val cleanExplanation = LatexToHumanConverter.cleanMarkdownAndMath(formula.explanation)

                    // Wrap explanation lines
                    val expLines = mutableListOf<String>()
                    val expWords = cleanExplanation.split(" ")
                    var curLine = ""
                    for (w in expWords) {
                        val test = if (curLine.isEmpty()) w else "$curLine $w"
                        if (bodyPaint.measureText(test) > contentWidth - 24) {
                            expLines.add(curLine)
                            curLine = w
                        } else {
                            curLine = test
                        }
                    }
                    if (curLine.isNotEmpty()) expLines.add(curLine)

                    val boxHeight = 44f + (expLines.size.coerceAtLeast(1) * 14f)
                    checkNewPage(boxHeight + 12f)

                    val boxTop = yCursor - 2
                    val boxRect = RectF(margin, boxTop, margin + contentWidth, boxTop + boxHeight)
                    canvas.drawRoundRect(boxRect, 6f, 6f, formulaBoxPaint)
                    canvas.drawRoundRect(boxRect, 6f, 6f, formulaBorderPaint)

                    // Formula Name
                    canvas.drawText("Formula: ${formula.name}", margin + 12, yCursor + 13, boldBodyPaint)

                    // Human-Readable Equation (No LaTeX code!)
                    canvas.drawText("Equation:  $humanEq", margin + 12, yCursor + 30, humanMathPaint)

                    // Explanation
                    var expY = yCursor + 44
                    for (line in expLines) {
                        canvas.drawText(line, margin + 12, expY, bodyPaint)
                        expY += 14f
                    }

                    yCursor += boxHeight + 12
                }
                yCursor += 8
            }

            // SECTION 4: Diagrams & Flowcharts (with Visual Flowchart and Keyframe)
            val diagrams = lecture.getDiagrams().filter { it.label.isNotBlank() && (it.description.isNotBlank() || it.mermaidCode.isNotBlank()) }
            if (diagrams.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("${sectionIndex++}. Diagrams & Visual Models", margin, yCursor, sectionHeaderPaint)
                yCursor += 16

                for (diagram in diagrams) {
                    val nodes = extractDiagramNodes(diagram.mermaidCode)
                    val hasVisualFlow = nodes.size in 2..6

                    // Try to extract frame if upload
                    var frameBitmap: Bitmap? = null
                    if (lecture.sourceType == "UPLOAD" && lecture.sourceUri.isNotBlank()) {
                        try {
                            frameBitmap = GeminiLectureService(context).extractFrameAtTimestamp(Uri.parse(lecture.sourceUri), diagram.timestamp)
                        } catch (_: Exception) {}
                    }

                    val descClean = LatexToHumanConverter.cleanMarkdownAndMath(diagram.description)
                    val descLines = mutableListOf<String>()
                    val descWords = descClean.split(" ")
                    var dCur = ""
                    for (w in descWords) {
                        val test = if (dCur.isEmpty()) w else "$dCur $w"
                        if (bodyPaint.measureText(test) > contentWidth - 24) {
                            descLines.add(dCur)
                            dCur = w
                        } else {
                            dCur = test
                        }
                    }
                    if (dCur.isNotEmpty()) descLines.add(dCur)

                    var extraHeight = 0f
                    if (hasVisualFlow) extraHeight += 50f
                    if (frameBitmap != null) extraHeight += 120f

                    val boxHeight = 36f + (descLines.size * 14f) + extraHeight
                    checkNewPage(boxHeight + 14f)

                    val boxTop = yCursor - 2
                    val boxRect = RectF(margin, boxTop, margin + contentWidth, boxTop + boxHeight)
                    canvas.drawRoundRect(boxRect, 6f, 6f, formulaBoxPaint)
                    canvas.drawRoundRect(boxRect, 6f, 6f, formulaBorderPaint)

                    // Header
                    canvas.drawText(
                        "${diagram.label} [${diagram.diagramType.uppercase()}] • Timestamp: ${diagram.timestamp}",
                        margin + 12,
                        yCursor + 13,
                        boldBodyPaint
                    )

                    var innerY = yCursor + 28
                    for (dLine in descLines) {
                        canvas.drawText(dLine, margin + 12, innerY, bodyPaint)
                        innerY += 14f
                    }
                    innerY += 4f

                    // Draw actual visual flowchart if nodes exist
                    if (hasVisualFlow) {
                        val nodeCount = nodes.size
                        if (nodeCount in 2..4) {
                            // Horizontal flow
                            val arrowGap = 18f
                            val totalGaps = (nodeCount - 1) * arrowGap
                            val nodeW = (contentWidth - 24 - totalGaps) / nodeCount
                            val nodeH = 26f
                            var nodeX = margin + 12

                            for (i in 0 until nodeCount) {
                                val nRect = RectF(nodeX, innerY, nodeX + nodeW, innerY + nodeH)
                                canvas.drawRoundRect(nRect, 4f, 4f, diagramNodeBgPaint)
                                canvas.drawRoundRect(nRect, 4f, 4f, diagramNodeBorderPaint)

                                val labelText = nodes[i].take(18)
                                canvas.drawText(labelText, nRect.centerX(), innerY + 16, diagramNodeTextPaint)

                                if (i < nodeCount - 1) {
                                    val arrowStartX = nodeX + nodeW + 2
                                    val arrowEndX = arrowStartX + arrowGap - 4
                                    val arrowY = innerY + (nodeH / 2)
                                    canvas.drawLine(arrowStartX, arrowY, arrowEndX, arrowY, arrowPaint)
                                    canvas.drawLine(arrowEndX - 4, arrowY - 3, arrowEndX, arrowY, arrowPaint)
                                    canvas.drawLine(arrowEndX - 4, arrowY + 3, arrowEndX, arrowY, arrowPaint)
                                }
                                nodeX += nodeW + arrowGap
                            }
                        } else {
                            // Compact sequence badges
                            var badgeX = margin + 12
                            for (i in 0 until nodeCount) {
                                val badgeText = "${i + 1}. ${nodes[i]}"
                                val bWidth = diagramNodeTextPaint.measureText(badgeText) + 16
                                if (badgeX + bWidth > margin + contentWidth - 12) {
                                    badgeX = margin + 12
                                    innerY += 28f
                                }
                                val bRect = RectF(badgeX, innerY, badgeX + bWidth, innerY + 22f)
                                canvas.drawRoundRect(bRect, 4f, 4f, diagramNodeBgPaint)
                                canvas.drawRoundRect(bRect, 4f, 4f, diagramNodeBorderPaint)
                                canvas.drawText(badgeText, bRect.centerX(), innerY + 14, diagramNodeTextPaint)
                                badgeX += bWidth + 8
                            }
                        }
                        innerY += 34f
                    }

                    // Draw video keyframe if available
                    if (frameBitmap != null) {
                        try {
                            val targetW = 180
                            val targetH = 101 // 16:9 approx
                            val scaledFrame = Bitmap.createScaledBitmap(frameBitmap, targetW, targetH, true)
                            canvas.drawBitmap(scaledFrame, margin + 14, innerY, null)
                            val frameCaptionPaint = Paint().apply {
                                color = Color.parseColor("#37474F")
                                textSize = 8.5f
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                                isAntiAlias = true
                            }
                            canvas.drawText("Video frame at ${diagram.timestamp}", margin + 14, innerY + targetH + 11, frameCaptionPaint)
                        } catch (_: Exception) {}
                    }

                    yCursor += boxHeight + 12
                }
                yCursor += 8
            }

            // SECTION 5: Topic Timestamps
            val timestamps = lecture.getTimestamps()
            if (timestamps.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("${sectionIndex++}. Topic Changes & Video Timeline", margin, yCursor, sectionHeaderPaint)
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
