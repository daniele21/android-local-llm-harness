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
 * caller-provided pipe, avoiding a large text Binder transaction. A small Messenger completion
 * signal makes success/failure bounded for the caller instead of relying on pipe EOF alone.
 * OMB-2 will own the production parser protocol, bounds and typed failure contract.
 */
class OmbraPdfIsolatedParserSpikeService : Service() {
    private val messenger by lazy {
        Messenger(IncomingHandler(Looper.getMainLooper(), applicationContext))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private class IncomingHandler(looper: Looper, private val applicationContext: android.content.Context) : Handler(looper) {
        override fun handleMessage(message: Message) {
            if (message.what != MESSAGE_PARSE) {
                super.handleMessage(message)
                return
            }

            val input = message.data.parcelFileDescriptor(KEY_INPUT)
            val output = message.data.parcelFileDescriptor(KEY_OUTPUT)
            val replyTo = message.replyTo
            if (input == null || output == null || replyTo == null) {
                input?.close()
                output?.close()
                sendCompletion(replyTo, RESULT_ERROR, "InvalidRequest")
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
                                writer.write(extracted)
                            }
                        }
                    }.onSuccess {
                        sendCompletion(replyTo, RESULT_OK, null)
                    }.onFailure { throwable ->
                        runCatching { input.close() }
                        runCatching { output.close() }
                        sendCompletion(replyTo, RESULT_ERROR, throwable.javaClass.simpleName)
                    }
                },
                "ombra-pdf-parser-spike",
            ).start()
        }

        private fun sendCompletion(replyTo: Messenger?, result: Int, errorType: String?) {
            if (replyTo == null) return
            runCatching {
                replyTo.send(
                    Message.obtain(null, MESSAGE_COMPLETE).apply {
                        data =
                            Bundle().apply {
                                putInt(KEY_RESULT, result)
                                putInt(KEY_PARSER_UID, Process.myUid())
                                if (errorType != null) putString(KEY_ERROR_TYPE, errorType)
                            }
                    },
                )
            }
        }
    }

    companion object {
        const val MESSAGE_PARSE = 1
        const val MESSAGE_COMPLETE = 2
        const val RESULT_OK = 0
        const val RESULT_ERROR = 1
        const val KEY_INPUT = "input"
        const val KEY_OUTPUT = "output"
        const val KEY_RESULT = "result"
        const val KEY_PARSER_UID = "parserUid"
        const val KEY_ERROR_TYPE = "errorType"
    }
}

@Suppress("DEPRECATION")
private fun Bundle.parcelFileDescriptor(key: String): ParcelFileDescriptor? = getParcelable(key)
