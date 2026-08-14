package io.github.daniele21.localllm.console.document

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OmbraPdfBoxFallbackInstrumentedTest {
    @Test
    fun extractsVisualReadingOrderAndWordSpacesFromFragmentedFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context.applicationContext)
        val source = File(context.cacheDir, "ombra-pdfbox-fidelity.pdf")
        writeFixture(source)

        val extracted =
            PDDocument.load(source).use { document ->
                PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = 1
                    endPage = 1
                }.getText(document)
            }
        source.delete()

        val normalized =
            extracted
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lineSequence()
                .map { line -> line.trim().replace(Regex("\\s+"), " ") }
                .filter { line -> line.isNotEmpty() }
                .joinToString("\n")

        assertTextOrder(
            normalized,
            listOf("LEFT TOP", "RIGHT TOP", "LEFT BOTTOM", "RIGHT BOTTOM"),
        )
    }

    private fun assertTextOrder(text: String, markers: List<String>) {
        var previousIndex = -1
        markers.forEach { marker ->
            val markerIndex = text.indexOf(marker)
            assertTrue("Expected '$marker' in PdfBox text: '$text'", markerIndex >= 0)
            assertTrue("Expected '$marker' after the previous marker in PdfBox text: '$text'", markerIndex > previousIndex)
            previousIndex = markerIndex
        }
    }

    private fun writeFixture(file: File) {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
            val page = document.startPage(pageInfo)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f }
            page.canvas.drawText("LEFT TOP", 36f, 90f, paint)
            page.canvas.drawText("LEFT BOTTOM", 36f, 120f, paint)
            page.canvas.drawText("RIGHT TOP", 320f, 90f, paint)
            page.canvas.drawText("RIGHT BOTTOM", 320f, 120f, paint)
            document.finishPage(page)
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
