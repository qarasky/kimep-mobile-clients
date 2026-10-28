package dev.qarasky.unofficialkimep.data.calendar

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** Reads a calendar PDF's words, with positions, on the device (PDFBox for Android). */
object PdfTextExtractor {

    fun words(context: Context, pdf: ByteArray): List<PdfWord> {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(pdf).use(::wordsOf)
    }

    internal fun wordsOf(document: PDDocument): List<PdfWord> {
        val stripper = WordStripper()
        stripper.getText(document)
        if (stripper.words.isEmpty()) throw NoTextLayerException()
        return mergeTouching(stripper.words)
    }

    /**
     * Some cells are drawn one glyph at a time ("21-25" arrives as five runs). Real word
     * gaps are about 1.4pt; glyphs of one word touch (gap around 0), so anything under
     * [TOUCHING_GAP] on the same line is one word.
     */
    internal fun mergeTouching(words: List<PdfWord>): List<PdfWord> {
        val merged = mutableListOf<PdfWord>()
        for (w in words.sortedWith(compareBy({ Math.round(it.y * 2) }, { it.x0 }))) {
            val prev = merged.lastOrNull()
            val gap = if (prev == null) Double.MAX_VALUE else w.x0 - prev.x1
            if (prev != null && Math.abs(w.y - prev.y) < 0.5 && gap > -2.0 && gap < TOUCHING_GAP) {
                merged[merged.lastIndex] = prev.copy(x1 = maxOf(prev.x1, w.x1), text = prev.text + w.text)
            } else {
                merged.add(w)
            }
        }
        return merged
    }

    private const val TOUCHING_GAP = 0.6

    /**
     * PDFBox calls [writeString] once per text run, which can hold several words (and the
     * long runs of spaces used as column gaps), so words are split on whitespace glyphs
     * here and each word's box comes from its own glyphs.
     */
    private class WordStripper : PDFTextStripper() {
        val words = mutableListOf<PdfWord>()

        init {
            sortByPosition = true
            startPage = 1
            endPage = 1 // the calendar is a single sheet; a second page would overlap its rows
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            var glyphs = mutableListOf<TextPosition>()
            fun flush() {
                if (glyphs.isEmpty()) return
                val first = glyphs.first()
                val last = glyphs.last()
                words.add(
                    PdfWord(
                        x0 = first.xDirAdj.toDouble(),
                        y = glyphs.minOf { (it.yDirAdj - it.heightDir).toDouble() },
                        x1 = (last.xDirAdj + last.widthDirAdj).toDouble(),
                        text = glyphs.joinToString("") { it.unicode.orEmpty() },
                    ),
                )
                glyphs = mutableListOf()
            }
            for (glyph in textPositions) {
                if (glyph.unicode.isNullOrBlank()) flush() else glyphs.add(glyph)
            }
            flush()
        }
    }
}
