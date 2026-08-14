package io.github.daniele21.localllm.console.document

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.SandboxedPdfLoader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalPdfApi::class)
@RunWith(AndroidJUnit4::class)
class OmbraPdfRawDiagnosticInstrumentedTest {
    @Test
    fun reportsRawTextContentsAndBounds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "ombra-raw-text-diagnostic.pdf")
        writeFixture(source)

        val loader = SandboxedPdfLoader(context.applicationContext)
        val diagnostic =
            loader.openDocument(Uri.fromFile(source)).use { document ->
                val contents = document.getPageContent(0)?.textContents.orEmpty()
                contents.joinToString(separator = "\n") { content ->
                    val escapedText =
                        content.text
                            .replace("\\", "\\\\")
                            .replace("\r", "\\r")
                            .replace("\n", "\\n")
                    val bounds =
                        content.bounds.joinToString(prefix = "[", postfix = "]") { bound ->
                            "(${bound.left},${bound.top},${bound.right},${bound.bottom})"
                        }
                    "text='$escapedText' bounds=$bounds"
                }
            }

        source.delete()
        fail("OMBRA_RAW_TEXT_CONTENTS\n$diagnostic")
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
