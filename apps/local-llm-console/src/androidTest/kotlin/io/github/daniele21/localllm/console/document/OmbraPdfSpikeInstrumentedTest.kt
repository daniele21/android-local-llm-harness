package io.github.daniele21.localllm.console.document

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OmbraPdfSpikeInstrumentedTest {
    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val createdFiles = mutableListOf<File>()

    @After
    fun removeFixtureFiles() {
        createdFiles.forEach { file -> file.delete() }
        createdFiles.clear()
    }

    @Test
    fun sandboxedParserExtractsPageOrderedTextAndHonorsBounds() = runBlocking {
        val source = fixtureFile("ordered-text.pdf")
        writeTextPdf(
            file = source,
            pages =
            listOf(
                listOf(
                    // Intentionally draw the left column before the right column. The parser
                    // contract under test is visual reading order, not PDF object creation order.
                    DrawnText("LEFT TOP", 36f, 90f),
                    DrawnText("LEFT BOTTOM", 36f, 120f),
                    DrawnText("RIGHT TOP", 320f, 90f),
                    DrawnText("RIGHT BOTTOM", 320f, 120f),
                ),
                listOf(DrawnText("SECOND PAGE", 36f, 90f)),
            ),
        )

        val parser = OmbraPdfParserSpike(targetContext)
        val full = parser.extractText(Uri.fromFile(source))
        assertEquals(2, full.pageCount)
        assertEquals(2, full.pages.size)
        assertFalse(full.truncated)

        val firstPage = full.pages.first().text
        assertTextOrder(firstPage, "LEFT TOP", "RIGHT TOP", "LEFT BOTTOM", "RIGHT BOTTOM")
        assertTrue(
            "Expected second-page marker in extracted text: '${diagnosticText(full.pages[1].text)}'",
            full.pages[1].text.contains("SECOND PAGE"),
        )

        val pageBounded = parser.extractText(Uri.fromFile(source), maxPages = 1)
        assertEquals(1, pageBounded.pages.size)
        assertTrue(pageBounded.truncated)

        val characterBounded = parser.extractText(Uri.fromFile(source), maxCharacters = 6)
        assertTrue(characterBounded.pages.sumOf { page -> page.text.length } <= 6)
        assertTrue(characterBounded.truncated)
    }

    @Test
    fun sandboxedParserDistinguishesImageOnlyMalformedAndEncryptedInputs() = runBlocking {
        val imageOnly = fixtureFile("image-only.pdf")
        writeImageOnlyPdf(imageOnly)

        val parser = OmbraPdfParserSpike(targetContext)
        val imageResult = parser.extractText(Uri.fromFile(imageOnly))
        assertEquals(1, imageResult.pageCount)
        assertTrue(imageResult.pages.all { page -> page.text.isBlank() })

        val malformed = fixtureFile("malformed.pdf")
        malformed.writeText("This is not a PDF")
        val malformedFailure =
            runCatching { parser.extractText(Uri.fromFile(malformed)) }.exceptionOrNull()
        assertNotNull("Malformed PDF must fail closed", malformedFailure)

        val encrypted = fixtureFile("encrypted.pdf")
        encrypted.writeBytes(readEncryptedFixture())
        val encryptedFailure =
            runCatching { parser.extractText(Uri.fromFile(encrypted)) }.exceptionOrNull()
        assertNotNull("Password-protected PDF must not be treated as plaintext", encryptedFailure)
    }

    @Test
    fun cancelledExtractionLeavesSandboxedParserReusable() = runBlocking {
        val source = fixtureFile("cancellation.pdf")
        writeTextPdf(
            file = source,
            pages =
            List(120) { pageIndex ->
                List(20) { lineIndex ->
                    DrawnText(
                        text = "PAGE_${pageIndex}_LINE_$lineIndex local parser cancellation evidence",
                        x = 36f,
                        y = 40f + (lineIndex * 18f),
                    )
                }
            },
        )

        val parser = OmbraPdfParserSpike(targetContext)
        val extraction =
            async(Dispatchers.Default) {
                parser.extractText(
                    uri = Uri.fromFile(source),
                    maxPages = 120,
                    maxCharacters = 500_000,
                )
            }
        delay(10)
        extraction.cancelAndJoin()
        assertTrue("Cancelled extraction must reach a terminal coroutine state", extraction.isCompleted)

        val retry = parser.extractText(Uri.fromFile(source), maxPages = 1)
        assertEquals(120, retry.pageCount)
        assertEquals(1, retry.pages.size)
        assertTrue(retry.pages.first().text.contains("PAGE_0_LINE_0"))
    }

    @Test
    fun normalizedWriterRoundTripsPlaceholdersWithoutHiddenSourceValues() = runBlocking {
        val output = fixtureFile("redacted-output.pdf")
        FileOutputStream(output).use { stream ->
            OmbraPdfWriterSpike().write(
                pages =
                listOf(
                    "Cliente: [FULL_NAME_1]\nEmail: [EMAIL_1]\nTelefono: [TELEPHONE_1]",
                ),
                output = stream,
            )
        }

        val extracted =
            OmbraPdfParserSpike(targetContext)
                .extractText(Uri.fromFile(output))
                .pages
                .joinToString(separator = "\n") { page -> page.text }

        assertTrue(extracted.contains("[FULL_NAME_1]"))
        assertTrue(extracted.contains("[EMAIL_1]"))
        assertTrue(extracted.contains("[TELEPHONE_1]"))
        assertFalse(extracted.contains("Mario Rossi"))
        assertFalse(extracted.contains("mario.rossi@example.it"))
        assertFalse(extracted.contains("+39 333 1234567"))
    }

    @Test
    fun normalizedWriterRoundTripsRepresentativeEuropeanGlyphsAndRejectsUnknownGlyphs() = runBlocking {
        val representativeText =
            "Città: Città Sant'Angelo — già più perché; José Müller; Straße; Łódź; nº 12; € 1.234,56"
        val output = fixtureFile("unicode-output.pdf")
        FileOutputStream(output).use { stream ->
            OmbraPdfWriterSpike().write(
                pages = listOf(representativeText),
                output = stream,
            )
        }

        val extracted =
            OmbraPdfParserSpike(targetContext)
                .extractText(Uri.fromFile(output))
                .pages
                .joinToString(separator = "\n") { page -> page.text }
        assertTrue("Representative European text must survive normalized export", extracted.contains(representativeText))

        val unsupported = String(Character.toChars(0x10FFFF))
        val rejected =
            runCatching {
                FileOutputStream(fixtureFile("unsupported-glyph.pdf")).use { stream ->
                    OmbraPdfWriterSpike().write(pages = listOf("unsupported=$unsupported"), output = stream)
                }
            }.exceptionOrNull()
        assertNotNull("Unknown glyphs must fail closed rather than substitute silently", rejected)
    }

    private fun fixtureFile(name: String): File = File(targetContext.cacheDir, "ombra-$name").also { file ->
        file.delete()
        createdFiles += file
    }

    private fun readEncryptedFixture(): ByteArray {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val encoded =
            instrumentationContext.assets.open("encrypted-ombra-fixture.pdf.b64").bufferedReader().use { reader ->
                reader.readText()
            }
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    private fun writeTextPdf(file: File, pages: List<List<DrawnText>>) {
        val document = PdfDocument()
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = 12f
            }
        try {
            FileOutputStream(file).use { output ->
                pages.forEachIndexed { index, textItems ->
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                    val page = document.startPage(pageInfo)
                    try {
                        textItems.forEach { item ->
                            page.canvas.drawText(item.text, item.x, item.y, paint)
                        }
                    } finally {
                        document.finishPage(page)
                    }
                }
                document.writeTo(output)
            }
        } finally {
            document.close()
        }
    }

    private fun writeImageOnlyPdf(file: File) {
        val document = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK }
        try {
            FileOutputStream(file).use { output ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = document.startPage(pageInfo)
                try {
                    page.canvas.drawRect(50f, 50f, 200f, 200f, paint)
                } finally {
                    document.finishPage(page)
                }
                document.writeTo(output)
            }
        } finally {
            document.close()
        }
    }

    private fun assertTextOrder(text: String, vararg expected: String) {
        var previous = -1
        expected.forEach { value ->
            val index = text.indexOf(value)
            assertTrue(
                "Expected '$value' in extracted page text: '${diagnosticText(text)}'",
                index >= 0,
            )
            assertTrue(
                "Expected visual reading order for '$value' in: '${diagnosticText(text)}'",
                index > previous,
            )
            previous = index
        }
    }

    private fun diagnosticText(text: String): String = text.replace("\n", "\\n").replace("\r", "\\r").take(500)

    private data class DrawnText(val text: String, val x: Float, val y: Float)
}
