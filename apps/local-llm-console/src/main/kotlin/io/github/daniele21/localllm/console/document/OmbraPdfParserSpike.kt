package io.github.daniele21.localllm.console.document

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OMB-0 bounded parser spike backed by PdfBox-Android inside an Android isolated process.
 *
 * The app process opens the user-selected source and transfers only a read-only file descriptor.
 * Parsed text returns through a pipe, keeping document-sized payloads out of Binder transactions.
 * OMB-2 will replace this spike with the reviewed production extractor boundary.
 */
internal class OmbraPdfParserSpike(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun extractText(
        uri: Uri,
        maxPages: Int = DEFAULT_MAX_PAGES,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
    ): OmbraPdfParserSpikeResult {
        require(maxPages > 0) { "maxPages must be positive" }
        require(maxCharacters > 0) { "maxCharacters must be positive" }

        val session = bindParserService()
        var source: ParcelFileDescriptor? = null
        var outputRead: ParcelFileDescriptor? = null
        var outputWrite: ParcelFileDescriptor? = null
        val completion = CompletableDeferred<ParserCompletion>()
        val replyMessenger =
            Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    if (message.what != OmbraPdfIsolatedParserSpikeService.MESSAGE_COMPLETE) return@Handler false
                    completion.complete(
                        ParserCompletion(
                            result = message.data.getInt(OmbraPdfIsolatedParserSpikeService.KEY_RESULT),
                            errorType = message.data.getString(OmbraPdfIsolatedParserSpikeService.KEY_ERROR_TYPE),
                        ),
                    )
                    true
                },
            )

        try {
            source = openReadOnly(uri)
            val pipe = ParcelFileDescriptor.createPipe()
            outputRead = pipe[0]
            outputWrite = pipe[1]

            session.messenger.send(
                Message.obtain(null, OmbraPdfIsolatedParserSpikeService.MESSAGE_PARSE).apply {
                    replyTo = replyMessenger
                    data =
                        Bundle().apply {
                            putParcelable(OmbraPdfIsolatedParserSpikeService.KEY_INPUT, source)
                            putParcelable(OmbraPdfIsolatedParserSpikeService.KEY_OUTPUT, outputWrite)
                            putInt(OmbraPdfIsolatedParserSpikeService.KEY_MAX_PAGES, maxPages)
                            putInt(OmbraPdfIsolatedParserSpikeService.KEY_MAX_CHARACTERS, maxCharacters)
                        }
                },
            )

            source.close()
            source = null
            outputWrite.close()
            outputWrite = null

            val terminal = withTimeout(PARSE_TIMEOUT_MS) { completion.await() }
            if (terminal.result != OmbraPdfIsolatedParserSpikeService.RESULT_OK) {
                throw IOException("Isolated PDF parser failed: ${terminal.errorType ?: "UnknownError"}")
            }

            val readDescriptor = requireNotNull(outputRead)
            val result =
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.AutoCloseInputStream(readDescriptor).use { inputStream ->
                        DataInputStream(inputStream.buffered()).use { data ->
                            readFrame(data, maxPages, maxCharacters)
                        }
                    }
                }
            outputRead = null
            return result
        } finally {
            source?.close()
            outputRead?.close()
            outputWrite?.close()
            session.unbind()
        }
    }

    private suspend fun bindParserService(): ParserSession = suspendCancellableCoroutine { continuation ->
        var bound = false
        lateinit var connection: ServiceConnection
        connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (!continuation.isActive) {
                        if (bound) safeUnbind(connection)
                        return
                    }
                    if (service == null) {
                        continuation.resumeWithException(IOException("Isolated PDF parser connected without Binder"))
                        return
                    }
                    continuation.resume(ParserSession(Messenger(service), connection))
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("Isolated PDF parser disconnected before binding completed"))
                    }
                }

                override fun onBindingDied(name: ComponentName?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("Isolated PDF parser binding died"))
                    }
                }
            }

        bound =
            applicationContext.bindService(
                Intent(applicationContext, OmbraPdfIsolatedParserSpikeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        if (!bound) {
            continuation.resumeWithException(IOException("Unable to bind isolated PDF parser"))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            if (bound) safeUnbind(connection)
        }
    }

    private fun openReadOnly(uri: Uri): ParcelFileDescriptor {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IOException("File URI has no path")
            return ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return applicationContext.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Unable to open PDF source")
    }

    private fun readFrame(input: DataInputStream, maxPages: Int, maxCharacters: Int): OmbraPdfParserSpikeResult {
        if (input.readInt() != OmbraPdfIsolatedParserSpikeService.FRAME_MAGIC) {
            throw IOException("Invalid isolated PDF parser frame")
        }
        val pageCount = input.readInt()
        val truncated = input.readBoolean()
        val returnedPages = input.readInt()
        if (pageCount < 0 || returnedPages !in 0..maxPages) {
            throw IOException("Invalid isolated PDF parser frame bounds")
        }

        val pages = ArrayList<OmbraPdfParserSpikePage>(returnedPages)
        var returnedCharacters = 0
        repeat(returnedPages) {
            val pageIndex = input.readInt()
            val byteCount = input.readInt()
            if (pageIndex < 0 || pageIndex >= pageCount || byteCount < 0 || byteCount > MAX_PAGE_UTF8_BYTES) {
                throw IOException("Invalid isolated PDF parser page frame")
            }
            val bytes = ByteArray(byteCount)
            input.readFully(bytes)
            val text = bytes.toString(Charsets.UTF_8)
            returnedCharacters += text.length
            if (returnedCharacters > maxCharacters) {
                throw IOException("Isolated PDF parser exceeded requested character bound")
            }
            pages += OmbraPdfParserSpikePage(pageIndex, text)
        }

        return OmbraPdfParserSpikeResult(pageCount = pageCount, pages = pages, truncated = truncated)
    }

    private fun safeUnbind(connection: ServiceConnection) {
        runCatching { applicationContext.unbindService(connection) }
    }

    private inner class ParserSession(val messenger: Messenger, private val connection: ServiceConnection) {
        fun unbind() = safeUnbind(connection)
    }

    private data class ParserCompletion(val result: Int, val errorType: String?)

    private companion object {
        const val DEFAULT_MAX_PAGES = 200
        const val DEFAULT_MAX_CHARACTERS = 1_000_000
        const val PARSE_TIMEOUT_MS = 30_000L
        const val MAX_PAGE_UTF8_BYTES = 4_000_000
    }
}

internal data class OmbraPdfParserSpikeResult(val pageCount: Int, val pages: List<OmbraPdfParserSpikePage>, val truncated: Boolean)

internal data class OmbraPdfParserSpikePage(val pageIndex: Int, val text: String)
