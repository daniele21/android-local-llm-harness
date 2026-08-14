package io.github.daniele21.localllm.console.document

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OmbraPdfIsolationInstrumentedTest {
    @Test
    fun parsesThroughPermissionlessIsolatedProcessWithCapabilityFileDescriptor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, OmbraPdfIsolatedParserSpikeService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertTrue(
            "OMBRA parser service must be declared isolated",
            serviceInfo.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0,
        )

        val source = File(context.cacheDir, "ombra-isolated-parser.pdf")
        writeFixture(source)
        val connected = CountDownLatch(1)
        var remote: Messenger? = null
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    remote = Messenger(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    remote = null
                }
            }

        val bound =
            context.bindService(
                Intent(context, OmbraPdfIsolatedParserSpikeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        assertTrue("Expected isolated parser service binding to succeed", bound)

        try {
            assertTrue("Timed out binding isolated parser service", connected.await(10, TimeUnit.SECONDS))
            val messenger = remote ?: fail("Isolated parser service connected without Messenger")
            val sourceDescriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            val outputPipe = ParcelFileDescriptor.createPipe()
            val message =
                Message.obtain(null, OmbraPdfIsolatedParserSpikeService.MESSAGE_PARSE).apply {
                    data =
                        Bundle().apply {
                            putParcelable(OmbraPdfIsolatedParserSpikeService.KEY_INPUT, sourceDescriptor)
                            putParcelable(OmbraPdfIsolatedParserSpikeService.KEY_OUTPUT, outputPipe[1])
                        }
                }

            messenger.send(message)
            sourceDescriptor.close()
            outputPipe[1].close()

            val response =
                ParcelFileDescriptor.AutoCloseInputStream(outputPipe[0])
                    .bufferedReader(Charsets.UTF_8)
                    .use { reader -> reader.readText() }
            assertTrue("Expected successful isolated parser response, got '$response'", response.startsWith("OK:"))

            val headerEnd = response.indexOf('\n')
            assertTrue("Expected isolated parser UID header, got '$response'", headerEnd > 3)
            val parserUid = response.substring(3, headerEnd).toInt()
            assertNotEquals("Parser must not run under the OMBRA app UID", Process.myUid(), parserUid)

            val normalized =
                response
                    .substring(headerEnd + 1)
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
        } finally {
            context.unbindService(connection)
            source.delete()
        }
    }

    private fun assertTextOrder(text: String, markers: List<String>) {
        var previousIndex = -1
        markers.forEach { marker ->
            val markerIndex = text.indexOf(marker)
            assertTrue("Expected '$marker' in isolated parser text: '$text'", markerIndex >= 0)
            assertTrue("Expected '$marker' after the previous marker in isolated parser text: '$text'", markerIndex > previousIndex)
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
