package it.vittorioscocca.kidbox.data.health.clinical

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object ClinicalRecordPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f
    private const val BODY_LINE_SPACING = 6.6f

    private const val SECTION_COLOR = 0xFF1C3A5E.toInt()
    private const val BODY_COLOR = 0xFF2D2D2D.toInt()
    private const val DIVIDER_COLOR = 0xFFE0E0E0.toInt()
    private const val SUMMARY_FILL = 0xFFF0F7FF.toInt()

    fun writePdf(lines: List<String>, output: File) {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN
        var drewFirstSection = false

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            isFakeBoldText = true
            color = SECTION_COLOR
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            isFakeBoldText = true
            color = SECTION_COLOR
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = BODY_COLOR
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIVIDER_COLOR
            strokeWidth = 0.5f
        }

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        val maxWidth = PAGE_WIDTH - MARGIN * 2
        var paragraphBuffer = mutableListOf<String>()
        var currentSummary = false

        fun ensureSpace(h: Float) {
            if (y + h > PAGE_HEIGHT - MARGIN) newPage()
        }

        fun drawDivider() {
            ensureSpace(14f)
            y += 8f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            y += 12f
        }

        fun drawParagraph(text: String, inSummaryBox: Boolean) {
            if (text.isBlank()) return
            val pad = 12f
            val textW = if (inSummaryBox) maxWidth - pad * 2 else maxWidth
            val wrapped = wrapText(text, bodyPaint, textW)
            val lineH = bodyPaint.fontSpacing + BODY_LINE_SPACING
            val blockH = wrapped.size * lineH + if (inSummaryBox) pad * 2 else 0f
            ensureSpace(blockH + 8f)
            if (inSummaryBox) {
                val box = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + blockH)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUMMARY_FILL }
                canvas.drawRoundRect(box, 8f, 8f, fill)
                canvas.drawRoundRect(box, 8f, 8f, dividerPaint.apply { style = Paint.Style.STROKE })
                y += pad
            }
            val startX = if (inSummaryBox) MARGIN + pad else MARGIN
            for (segment in wrapped) {
                canvas.drawText(segment, startX, y + bodyPaint.textSize, bodyPaint)
                y += lineH
            }
            if (inSummaryBox) y += pad
            y += 4f
        }

        fun flushParagraph() {
            if (paragraphBuffer.isEmpty()) return
            val text = paragraphBuffer.joinToString(" ").trim()
            paragraphBuffer = mutableListOf()
            drawParagraph(text, currentSummary)
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line == "---" -> {
                    flushParagraph()
                    if (drewFirstSection) drawDivider()
                }
                isSectionHeader(line) -> {
                    flushParagraph()
                    if (drewFirstSection) drawDivider()
                    drewFirstSection = true
                    ensureSpace(28f)
                    y += 20f
                    canvas.drawText(line, MARGIN, y + sectionPaint.textSize, sectionPaint)
                    y += sectionPaint.fontSpacing + 6f
                    currentSummary = isSummarySection(line)
                }
                line == "CARTELLA CLINICA" || line.startsWith("CARTELLA CLINICA —") -> {
                    flushParagraph()
                    canvas.drawText(line, MARGIN, y + titlePaint.textSize, titlePaint)
                    y += titlePaint.fontSpacing + 8f
                }
                else -> paragraphBuffer.add(line)
            }
        }
        flushParagraph()

        document.finishPage(page)
        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
    }

    private fun isSectionHeader(line: String): Boolean {
        if (line.startsWith("CARTELLA CLINICA")) return false
        if (line.startsWith("•") || line.startsWith("-")) return false
        if (line.firstOrNull()?.isDigit() == true) return false
        if (line.contains(":") && line != line.uppercase()) return false
        return line == line.uppercase() && line.length > 6
    }

    private fun isSummarySection(title: String): Boolean {
        val t = title.lowercase()
        return t.contains("riepilogo") || t.contains("valutazione generale")
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf(" ")
        val words = text.split(' ')
        val out = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) out += current
                current = word
            }
        }
        if (current.isNotEmpty()) out += current
        return out.ifEmpty { listOf(text) }
    }
}
