package io.github.daniele21.localllm.console.document

import android.content.Context
import io.github.daniele21.localllm.console.application.OmbraDocumentExportException
import io.github.daniele21.localllm.console.application.OmbraDocumentExportFailureCode
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraExportDestinationRef
import io.github.daniele21.localllm.console.application.OmbraExportReceipt
import io.github.daniele21.localllm.console.application.OmbraExportRequest
import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.console.redaction.OmbraRedactionPlanFailureCode
import io.github.daniele21.localllm.console.redaction.OmbraRedactionPlanResult
import io.github.daniele21.localllm.console.redaction.OmbraRedactionPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

internal fun interface OmbraFlattenedPdfWriter {
    fun write(pages: List<String>, output: OutputStream)
}

internal class ReviewedOmbraFlattenedPdfWriter : OmbraFlattenedPdfWriter {
    private val writer = OmbraPdfWriterSpike()

    override fun write(pages: List<String>, output: OutputStream) = writer.write(pages, output)
}

/** Production OMB-5B exporter: redaction plan -> normalized text pages -> newly generated PDF. */
internal class AndroidOmbraDocumentExporter(
    context: Context,
    private val destinationRegistry: OmbraExportDestinationRegistry,
    private val writer: OmbraFlattenedPdfWriter = ReviewedOmbraFlattenedPdfWriter(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : OmbraDocumentExporter,
    AutoCloseable {
    private val applicationContext = context.applicationContext
    private val operations = ConcurrentHashMap<OmbraOperationId, ExportOperation>()

    override fun export(
        operationId: OmbraOperationId,
        destinationRef: OmbraExportDestinationRef,
        request: OmbraExportRequest,
        onResult: (Result<OmbraExportReceipt>) -> Unit,
    ) {
        val destination = destinationRegistry.resolve(destinationRef)
        if (destination == null) {
            onResult(Result.failure(OmbraDocumentExportException(OmbraDocumentExportFailureCode.DESTINATION_NOT_FOUND)))
            return
        }
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val result = exportSafely(destinationRef, destination, request)
                if (operations.remove(operationId) != null) onResult(result)
            }
        val operation = ExportOperation(destinationRef, job)
        check(operations.putIfAbsent(operationId, operation) == null) { "Duplicate OMBRA export operation ID" }
        job.start()
    }

    override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
        val operation = operations.remove(operationId)
        if (operation == null) {
            onCancelled()
            return
        }
        scope.launch {
            operation.job.cancelAndJoin()
            cleanupDestination(operation.destinationRef)
            onCancelled()
        }
    }

    override fun close() {
        operations.values.forEach { operation -> operation.job.cancel() }
        operations.clear()
        scope.cancel()
    }

    private suspend fun exportSafely(
        destinationRef: OmbraExportDestinationRef,
        destination: OmbraExportDestination,
        request: OmbraExportRequest,
    ): Result<OmbraExportReceipt> = try {
        Result.success(exportNow(destination, request))
    } catch (cancelled: CancellationException) {
        cleanupDestination(destinationRef)
        throw cancelled
    } catch (failure: Throwable) {
        cleanupDestination(destinationRef)
        Result.failure(mapFailure(failure))
    } finally {
        destinationRegistry.release(destinationRef)
    }

    private fun exportNow(destination: OmbraExportDestination, request: OmbraExportRequest): OmbraExportReceipt {
        val redactionPlan = when (val plan = OmbraRedactionPlanner.build(request.segments, request.definitions, request.reviewOccurrences)) {
            is OmbraRedactionPlanResult.Ready -> plan.plan
            is OmbraRedactionPlanResult.Blocked -> throw blockedPlanFailure(plan.code)
        }
        val pages = composePages(request, redactionPlan.renderedSegments.associate { it.segmentId to it.text })
        val countingOutput = CountingOutputStream(openDestination(destination))
        countingOutput.use { output -> writer.write(pages, output) }
        if (countingOutput.byteCount <= 0L) {
            throw OmbraDocumentExportException(OmbraDocumentExportFailureCode.WRITER_FAILED)
        }
        return OmbraExportReceipt(pageCount = pages.size, byteCount = countingOutput.byteCount)
    }

    private fun composePages(request: OmbraExportRequest, renderedText: Map<SegmentId, String>): List<String> {
        val byPage = request.segments.groupBy(DocumentSegment::pageIndex)
        return (0 until request.descriptor.pageCount).map { pageIndex ->
            byPage[pageIndex]
                .orEmpty()
                .sortedBy(DocumentSegment::blockIndex)
                .joinToString(separator = "\n\n") { segment -> requireNotNull(renderedText[segment.id]) }
        }
    }

    private fun openDestination(destination: OmbraExportDestination): OutputStream = when (destination.uri.scheme) {
        "content" -> applicationContext.contentResolver.openOutputStream(destination.uri, "wt")
            ?: throw OmbraDocumentExportException(OmbraDocumentExportFailureCode.DESTINATION_UNWRITABLE)

        "file" -> {
            val path = destination.uri.path
                ?: throw OmbraDocumentExportException(OmbraDocumentExportFailureCode.DESTINATION_UNWRITABLE)
            FileOutputStream(File(path), false)
        }

        else -> throw OmbraDocumentExportException(OmbraDocumentExportFailureCode.DESTINATION_UNWRITABLE)
    }

    private fun blockedPlanFailure(code: OmbraRedactionPlanFailureCode): OmbraDocumentExportException {
        val exportCode = when (code) {
            OmbraRedactionPlanFailureCode.PENDING_DECISION -> OmbraDocumentExportFailureCode.REVIEW_INCOMPLETE
            OmbraRedactionPlanFailureCode.OVERLAP_CONFLICT -> OmbraDocumentExportFailureCode.REDACTION_CONFLICT
            OmbraRedactionPlanFailureCode.SOURCE_MISMATCH,
            OmbraRedactionPlanFailureCode.UNKNOWN_SEGMENT,
            OmbraRedactionPlanFailureCode.MISSING_DEFINITION,
            OmbraRedactionPlanFailureCode.DUPLICATE_OCCURRENCE,
            -> OmbraDocumentExportFailureCode.SOURCE_MISMATCH
        }
        return OmbraDocumentExportException(exportCode)
    }

    private fun mapFailure(failure: Throwable): Throwable = when (failure) {
        is OmbraDocumentExportException -> failure
        is SecurityException -> OmbraDocumentExportException(OmbraDocumentExportFailureCode.DESTINATION_UNWRITABLE)
        is IllegalArgumentException -> OmbraDocumentExportException(OmbraDocumentExportFailureCode.OUTPUT_LIMIT_EXCEEDED)
        is IOException -> OmbraDocumentExportException(OmbraDocumentExportFailureCode.DESTINATION_UNWRITABLE)
        else -> OmbraDocumentExportException(OmbraDocumentExportFailureCode.WRITER_FAILED)
    }

    private fun cleanupDestination(destinationRef: OmbraExportDestinationRef) {
        destinationRegistry.deleteBestEffort(destinationRef)
        destinationRegistry.release(destinationRef)
    }

    private data class ExportOperation(val destinationRef: OmbraExportDestinationRef, val job: Job)

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var byteCount: Long = 0
            private set

        override fun write(value: Int) {
            out.write(value)
            byteCount += 1
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            byteCount += length
        }
    }
}
