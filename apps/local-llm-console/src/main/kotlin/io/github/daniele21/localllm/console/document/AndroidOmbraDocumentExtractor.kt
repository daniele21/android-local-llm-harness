package io.github.daniele21.localllm.console.document

import android.content.Context
import android.net.Uri
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractionException
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractionFailureCode
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraDocumentSourceRef
import io.github.daniele21.localllm.console.application.OmbraExtractedDocument
import io.github.daniele21.localllm.console.application.OmbraOperationId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal fun interface OmbraPdfTextReader {
    suspend fun read(uri: Uri): OmbraPdfReadResult
}

internal class IsolatedOmbraPdfTextReader(context: Context) : OmbraPdfTextReader {
    private val parser = OmbraPdfParserSpike(context)

    override suspend fun read(uri: Uri): OmbraPdfReadResult {
        val parsed = parser.extractText(uri)
        return OmbraPdfReadResult(
            pageCount = parsed.pageCount,
            pages = parsed.pages.map { page -> OmbraPdfPageText(page.pageIndex, page.text) },
            truncated = parsed.truncated,
        )
    }
}

/** Production OMB-2 application-port adapter over the reviewed isolated PDF parser boundary. */
internal class AndroidOmbraDocumentExtractor(
    private val sourceResolver: OmbraDocumentSourceResolver,
    private val reader: OmbraPdfTextReader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : OmbraDocumentExtractor, AutoCloseable {
    private val operations = ConcurrentHashMap<OmbraOperationId, Job>()

    constructor(context: Context, sourceResolver: OmbraDocumentSourceResolver) : this(
        sourceResolver = sourceResolver,
        reader = IsolatedOmbraPdfTextReader(context.applicationContext),
    )

    override fun extract(
        operationId: OmbraOperationId,
        sourceRef: OmbraDocumentSourceRef,
        onResult: (Result<OmbraExtractedDocument>) -> Unit,
    ) {
        val source = sourceResolver.resolve(sourceRef)
        if (source == null) {
            onResult(Result.failure(OmbraDocumentExtractionException(OmbraDocumentExtractionFailureCode.SOURCE_NOT_FOUND)))
            return
        }

        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val result = runCatching { extract(source) }
                if (operations.remove(operationId) != null) onResult(result)
            }
        check(operations.putIfAbsent(operationId, job) == null) { "Duplicate OMBRA extraction operation ID" }
        job.start()
    }

    override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
        val job = operations.remove(operationId)
        if (job == null) {
            onCancelled()
            return
        }
        scope.launch {
            job.cancelAndJoin()
            onCancelled()
        }
    }

    override fun close() {
        operations.clear()
        scope.cancel()
    }

    private suspend fun extract(source: OmbraDocumentSource): OmbraExtractedDocument {
        val parsed =
            try {
                reader.read(source.uri)
            } catch (exception: IOException) {
                throw mapReaderFailure(exception)
            } catch (_: SecurityException) {
                throw OmbraDocumentExtractionException(OmbraDocumentExtractionFailureCode.SOURCE_UNREADABLE)
            }

        if (parsed.truncated) {
            throw OmbraDocumentExtractionException(OmbraDocumentExtractionFailureCode.LIMIT_EXCEEDED)
        }
        if (parsed.pageCount <= 0) {
            throw OmbraDocumentExtractionException(OmbraDocumentExtractionFailureCode.EMPTY_PDF)
        }
        val segments = OmbraPdfSegmenter.segment(parsed.pages)
        if (segments.isEmpty()) {
            throw OmbraDocumentExtractionException(OmbraDocumentExtractionFailureCode.IMAGE_ONLY_PDF)
        }
        return OmbraExtractedDocument(
            descriptor = DocumentDescriptor(displayName = source.displayName, pageCount = parsed.pageCount),
            segments = segments,
        )
    }

    private fun mapReaderFailure(exception: IOException): OmbraDocumentExtractionException {
        val message = exception.message.orEmpty()
        val code =
            when {
                "InvalidPasswordException" in message -> OmbraDocumentExtractionFailureCode.ENCRYPTED_PDF
                "Unable to open PDF source" in message -> OmbraDocumentExtractionFailureCode.SOURCE_UNREADABLE
                else -> OmbraDocumentExtractionFailureCode.PARSER_FAILED
            }
        return OmbraDocumentExtractionException(code)
    }
}
