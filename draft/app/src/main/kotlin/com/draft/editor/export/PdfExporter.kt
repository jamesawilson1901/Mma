package com.draft.editor.export

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.draft.editor.format.BlockAlign
import com.draft.editor.format.BlockType
import com.draft.editor.format.DocBlock
import com.draft.editor.format.DraftDocument
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the styled document to PDF with Android's PdfDocument +
 * StaticLayout. Pageless on screen; A4 with margins on paper.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 @72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 56
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN
    private const val BLOCK_SPACING = 8f
    private const val BASE_TEXT_SIZE = 11.5f

    suspend fun render(document: DraftDocument): ByteArray =
        withContext(Dispatchers.Default) {
            renderBlocking(document)
        }

    private fun renderBlocking(document: DraftDocument): ByteArray {
        val pdf = PdfDocument()
        try {
            var page = newPage(pdf, 1)
            var y = 0f
            var pageNumber = 1
            var numberedIndex = 0

            for (block in document.blocks) {
                numberedIndex = if (block.type == BlockType.NUMBER_ITEM) numberedIndex + 1 else 0

                val layout = layoutFor(block, numberedIndex)
                var line = 0
                while (line < layout.lineCount) {
                    val remaining = CONTENT_HEIGHT - y
                    val lineBottom = layout.getLineBottom(line)
                    val lineTop = layout.getLineTop(line)
                    if (lineBottom - lineTop > remaining && y > 0f) {
                        pdf.finishPage(page)
                        pageNumber++
                        page = newPage(pdf, pageNumber)
                        y = 0f
                        continue
                    }
                    // Draw the largest run of lines that fits this page.
                    var lastLine = line
                    while (lastLine + 1 < layout.lineCount &&
                        layout.getLineBottom(lastLine + 1) - lineTop <= CONTENT_HEIGHT - y
                    ) {
                        lastLine++
                    }
                    // (If a single line is taller than a page it is drawn
                    // anyway rather than looping forever.)
                    val canvas = page.canvas
                    canvas.save()
                    canvas.translate(MARGIN.toFloat(), MARGIN + y - lineTop)
                    canvas.clipRect(
                        0,
                        lineTop,
                        CONTENT_WIDTH,
                        layout.getLineBottom(lastLine),
                    )
                    layout.draw(canvas)
                    canvas.restore()

                    y += (layout.getLineBottom(lastLine) - lineTop).toFloat()
                    line = lastLine + 1
                    if (line < layout.lineCount) {
                        pdf.finishPage(page)
                        pageNumber++
                        page = newPage(pdf, pageNumber)
                        y = 0f
                    }
                }
                y += BLOCK_SPACING
                if (y >= CONTENT_HEIGHT) {
                    pdf.finishPage(page)
                    pageNumber++
                    page = newPage(pdf, pageNumber)
                    y = 0f
                }
            }
            pdf.finishPage(page)

            val out = ByteArrayOutputStream()
            pdf.writeTo(out)
            return out.toByteArray()
        } finally {
            pdf.close()
        }
    }

    private fun newPage(pdf: PdfDocument, number: Int): PdfDocument.Page =
        pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())

    private fun layoutFor(block: DocBlock, numberedIndex: Int): StaticLayout {
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = BASE_TEXT_SIZE
            color = Color.BLACK
        }

        val text = SpannableStringBuilder()
        val prefix = when (block.type) {
            BlockType.BULLET_ITEM -> "•  "
            BlockType.NUMBER_ITEM -> "$numberedIndex.  "
            else -> ""
        }
        text.append(prefix)

        for (run in block.runs) {
            val start = text.length
            text.append(run.text.ifEmpty { "" })
            val end = text.length
            if (end == start) continue
            val style = run.style
            when {
                style.bold && style.italic ->
                    text.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                style.bold ->
                    text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                style.italic ->
                    text.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.underline || style.linkUrl != null) {
                text.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.strikethrough) {
                text.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val color = style.color ?: if (style.linkUrl != null) "#1A468F" else null
            color?.let {
                runCatching { Color.parseColor(it) }.getOrNull()?.let { parsed ->
                    text.setSpan(ForegroundColorSpan(parsed), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            style.highlight?.let {
                runCatching { Color.parseColor(it) }.getOrNull()?.let { parsed ->
                    text.setSpan(BackgroundColorSpan(parsed), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        if (text.isEmpty()) text.append(" ")

        val sizeMultiplier = when (block.type) {
            BlockType.HEADING1 -> 1.9f
            BlockType.HEADING2 -> 1.5f
            BlockType.HEADING3 -> 1.2f
            else -> 1f
        }
        if (sizeMultiplier != 1f) {
            text.setSpan(RelativeSizeSpan(sizeMultiplier), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (prefix.isNotEmpty()) {
            val indent = paint.measureText(prefix).toInt()
            text.setSpan(
                LeadingMarginSpan.Standard(0, indent),
                0,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val alignment = when (block.align) {
            BlockAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            BlockAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
            BlockAlign.START -> Layout.Alignment.ALIGN_NORMAL
        }
        text.setSpan(
            AlignmentSpan.Standard(alignment),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        return StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()
    }
}
