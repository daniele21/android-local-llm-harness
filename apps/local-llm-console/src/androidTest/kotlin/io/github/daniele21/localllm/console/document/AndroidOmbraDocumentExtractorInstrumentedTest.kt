package io.github.daniele21.localllm.console.document

import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractionException
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractionFailureCode
import io.github.daniele21.localllm.console.application.OmbraDocumentSourceRef
import io.github.daniele21.localllm.console.application.OmbraExtractedDocument
import io.github.daniele21.localllm.console.application.OmbraOperationId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(AndroidJUnit4::class)
class AndroidOmbraDocumentExtractorInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun generatedPdfProducesStablePageOrderedSegments() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "ombra-production-extractor.pdf")
            FileOutputStream(sourceFile).use { output ->
                OmbraPdfWriterSpike().write(
                    pages = listOf("Alpha first block\n\nAlpha second block", "Beta page"),
                    output = output,
                )
            }
            val registry = OmbraDocumentSourceRegistry(context)
            val sourceRef = registry.register(Uri.fromFile(sourceFile))
            val extractor = AndroidOmbraDocumentExtractor(context, registry)

            val result = awaitExtraction(extractor, OmbraOperationId(1), sourceRef)
            val document = result.getOrThrow()

            assertEquals(2, document.descriptor.pageCount)
            assertTrue(document.segments.isNotEmpty())
            assertEquals(0, document.segments.first().pageIndex)
            assertTrue(document.segments.any { segment -> "Alpha" in segment.normalizedText })
            assertTrue(document.segments.any { segment -> "Beta" in segment.normalizedText })
            extractor.close()
            registry.clear()
            sourceFile.delete()
        }
    }

    @Test
    fun blankPdfFailsAsImageOnlyInsteadOfProducingPartialTaskData() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "ombra-empty-extractor.pdf")
            FileOutputStream(sourceFile).use { output -> OmbraPdfWriterSpike().write(listOf(""), output) }
            val registry = OmbraDocumentSourceRegistry(context)
            val sourceRef = registry.register(Uri.fromFile(sourceFile))
            val extractor = AndroidOmbraDocumentExtractor(context, registry)

            val failure = awaitExtraction(extractor, OmbraOperationId(2), sourceRef).exceptionOrNull()

            assertTrue(failure is OmbraDocumentExtractionException)
            assertEquals(OmbraDocumentExtractionFailureCode.IMAGE_ONLY_PDF, (failure as OmbraDocumentExtractionException).code)
            extractor.close()
            registry.clear()
            sourceFile.delete()
        }
    }

    @Test
    fun encryptedPdfMapsThroughProductionAdapterToTypedFailure() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "ombra-encrypted-extractor.pdf")
            val encoded =
                InstrumentationRegistry.getInstrumentation().context.assets.open("encrypted-ombra-fixture.pdf.b64").bufferedReader().use {
                    it.readText()
                }
            sourceFile.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
            val registry = OmbraDocumentSourceRegistry(context)
            val sourceRef = registry.register(Uri.fromFile(sourceFile))
            val extractor = AndroidOmbraDocumentExtractor(context, registry)

            val failure = awaitExtraction(extractor, OmbraOperationId(4), sourceRef).exceptionOrNull()

            assertTrue(failure is OmbraDocumentExtractionException)
            assertEquals(OmbraDocumentExtractionFailureCode.ENCRYPTED_PDF, (failure as OmbraDocumentExtractionException).code)
            extractor.close()
            registry.clear()
            sourceFile.delete()
        }
    }

    @Test
    fun malformedPdfMapsThroughProductionAdapterToTypedFailure() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "ombra-malformed-extractor.pdf")
            sourceFile.writeBytes("%PDF-1.7\nthis is not a complete PDF".toByteArray())
            val registry = OmbraDocumentSourceRegistry(context)
            val sourceRef = registry.register(Uri.fromFile(sourceFile))
            val extractor = AndroidOmbraDocumentExtractor(context, registry)

            val failure = awaitExtraction(extractor, OmbraOperationId(5), sourceRef).exceptionOrNull()

            assertTrue(failure is OmbraDocumentExtractionException)
            assertEquals(OmbraDocumentExtractionFailureCode.MALFORMED_PDF, (failure as OmbraDocumentExtractionException).code)
            extractor.close()
            registry.clear()
            sourceFile.delete()
        }
    }

    @Test
    fun truncatedReaderResultMapsToLimitExceededAtProductionAdapter() {
        runBlocking {
            val sourceRef = OmbraDocumentSourceRef(6)
            val resolver = OmbraDocumentSourceResolver { OmbraDocumentSource(Uri.parse("file:///limit.pdf"), "limit.pdf") }
            val reader =
                OmbraPdfTextReader {
                    OmbraPdfReadResult(
                        pageCount = 2,
                        pages = listOf(OmbraPdfPageText(pageIndex = 0, text = "bounded text")),
                        truncated = true,
                    )
                }
            val extractor = AndroidOmbraDocumentExtractor(resolver, reader)

            val failure = awaitExtraction(extractor, OmbraOperationId(6), sourceRef).exceptionOrNull()

            assertTrue(failure is OmbraDocumentExtractionException)
            assertEquals(OmbraDocumentExtractionFailureCode.LIMIT_EXCEEDED, (failure as OmbraDocumentExtractionException).code)
            extractor.close()
        }
    }

    @Test
    fun cancellationWaitsForReaderTerminationBeforeAcknowledgement() {
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val terminated = CompletableDeferred<Unit>()
            val reader =
                OmbraPdfTextReader {
                    started.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                        error("unreachable")
                    } finally {
                        terminated.complete(Unit)
                    }
                }
            val sourceRef = OmbraDocumentSourceRef(3)
            val resolver = OmbraDocumentSourceResolver { OmbraDocumentSource(Uri.parse("file:///cancel.pdf"), "cancel.pdf") }
            val extractor = AndroidOmbraDocumentExtractor(resolver, reader)
            extractor.extract(OmbraOperationId(3), sourceRef) { error("Cancelled extraction must not complete with a result") }
            started.await()

            val cancelled = CompletableDeferred<Unit>()
            extractor.cancel(OmbraOperationId(3)) { cancelled.complete(Unit) }

            terminated.await()
            cancelled.await()
            extractor.close()
        }
    }

    private suspend fun awaitExtraction(
        extractor: AndroidOmbraDocumentExtractor,
        operationId: OmbraOperationId,
        sourceRef: OmbraDocumentSourceRef,
    ): Result<OmbraExtractedDocument> = suspendCoroutine { continuation ->
        extractor.extract(operationId, sourceRef) { result -> continuation.resume(result) }
    }
}
