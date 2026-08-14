package io.github.daniele21.localllm.console.document

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.OutputStreamWriter

/**
 * OMB-0 trust-boundary spike for parsing untrusted PDFs outside the OMBRA app process.
 *
 * The caller opens the source URI and transfers only a read-only file descriptor. The isolated
 * service has no application permissions of its own and writes the extracted result through a
 * caller-provided pipe, avoiding a large text Binder transaction. OMB-2 will own the production
 * parser protocol, bounds and typed failure contract.
 */
class OmbraPdfIsolatedParserSpikeService : Service() {
    private val messenger by lazy {
        Messenger(IncomingHandler(Looper.getMainLooper(), applicationContext))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private class IncomingHandler(
        looper: Looper,
        private val applicationContext: android.content.Context,
    ) : Handler(looper) {
        override fun handleMessage(message: Message) {
            if (message.what != MESSAGE_PARSE) {
                super.handleMessage(message)
                return
            }

            val input = message.data.parcelFileDescriptor(KEY_INPUT)
            val output = message.data.parcelFileDescriptor(KEY_OUTPUT)
            if (input == null || output == null) {
                input?.close()
                output?.close()
                return
            }

            Thread(
                {
                    runCatching {
                        PDFBoxResourceLoader.init(applicationContext)
                        val extracted =
                            ParcelFileDescriptor.AutoCloseInputStream(input).use { inputStream ->
                                PDDocument.load(inputStream).use { document ->
                                    PDFTextStripper().apply { sortByPosition = true }.getText(document)
                                }
                            }
                        ParcelFileDescriptor.AutoCloseOutputStream(output).use { outputStream ->
                            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                                writer.write("OK:")
                                writer.write(Process.myUid().toString())
                                writer.write('\n'.code)
                                writer.write(extracted)
                            }
                        }
                    }.onFailure { throwable ->
                        runCatching {
                            input.close()
                            ParcelFileDescriptor.AutoCloseOutputStream(output).use { outputStream ->
                                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                                    writer.write("ERROR:")
                                    writer.write(Process.myUid().toString())
                                    writer.write(':'.code)
                                    writer.write(throwable.javaClass.simpleName)
                                }
                            }
                        }
                    }
                },
                "ombra-pdf-parser-spike",
            ).start()
        }
    }

    companion object {
        const val MESSAGE_PARSE = 1
        const val KEY_INPUT = "input"
        const val KEY_OUTPUT = "output"
    }
}

@Suppress("DEPRECATION")
private fun Bundle.parcelFileDescriptor(key: String): ParcelFileDescriptor? =
    getParcelable(key)
