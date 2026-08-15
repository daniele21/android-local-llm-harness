package io.github.daniele21.localllm.console.document

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.console.application.OmbraDocumentExportException
import io.github.daniele21.localllm.console.application.OmbraExportRequest
import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.console.pii.OmbraBuiltInPiiDefinitions
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(AndroidJUnit4::class)
class AndroidOmbraDocumentExporterInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val createdFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        createdFiles.forEach(File::delete)
        createdFiles.clear()
    }

    @Test
    fun exportedPdfRemovesAcceptedSurfacePreservesIgnoredSurfaceAndContainsPlaceholder() = runBlocking {
        val sourceText = "Cliente Mario Rossi email mario.rossi@example.it"
        val segment = segment(sourceText)
        val name = definition("full-name")
        val email = definition("email")
        val acceptedName = review(segment, name, sourceText, "Mario Rossi", ReviewDecisionState.ACCEPTED)
        val ignoredEmail = review(segment, email, sourceText, "mario.rossi@example.it", ReviewDecisionState.IGNORED)
        val request =
            OmbraExportRequest(
                descriptor = DocumentDescriptor(displayName = "synthetic.pdf", pageCount = 1),
                segments = listOf(segment),
                definitions = listOf(name, email),
                reviewOccurrences = listOf(acceptedName, ignoredEmail),
            )
        val output = fixture("verified-redacted.pdf")
        val registry = OmbraExportDestinationRegistry(context)
        val destinationRef = registry.register(Uri.fromFile(output))
        val exporter = AndroidOmbraDocumentExporter(context, registry)

        val result = awaitExport(exporter, OmbraOperationId(1), destinationRef, request)

        val receipt = result.getOrThrow()
        assertTrue(receipt.byteCount > 0)
        val extracted =
            OmbraPdfParserSpike(context)
                .extractText(Uri.fromFile(output))
                .pages
                .joinToString(separator = "\n") { page -> page.text }
        assertTrue(extracted.contains("[NOME_COMPLETO_1]"))
        assertFalse(extracted.contains("Mario Rossi"))
        assertTrue(extracted.contains("mario.rossi@example.it"))
        exporter.close()
    }

    @Test
    fun partialWriterFailureDeletesRecoverableOutput() = runBlocking {
        val sourceText = "Cliente Mario Rossi"
        val segment = segment(sourceText)
        val name = definition("full-name")
        val acceptedName = review(segment, name, sourceText, "Mario Rossi", ReviewDecisionState.ACCEPTED)
        val request =
            OmbraExportRequest(
                descriptor = DocumentDescriptor(displayName = "synthetic.pdf", pageCount = 1),
                segments = listOf(segment),
                definitions = listOf(name),
                reviewOccurrences = listOf(acceptedName),
            )
        val output = fixture("partial-failure.pdf")
        val registry = OmbraExportDestinationRegistry(context)
        val destinationRef = registry.register(Uri.fromFile(output))
        val failingWriter =
            OmbraFlattenedPdfWriter { _, stream ->
                stream.write("partial".toByteArray())
                stream.flush()
                throw IOException("synthetic writer failure")
            }
        val exporter = AndroidOmbraDocumentExporter(context, registry, writer = failingWriter)

        val result = awaitExport(exporter, OmbraOperationId(2), destinationRef, request)

        val failure = result.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure is OmbraDocumentExportException)
        assertFalse("Partial output must be deleted after writer failure", output.exists())
        exporter.close()
    }

    private fun fixture(name: String): File = File(context.cacheDir, "ombra-$name").also { file ->
        file.delete()
        createdFiles += file
    }

    private fun definition(id: String): PiiDefinition =
        OmbraBuiltInPiiDefinitions.all.single { definition -> definition.id == PiiTypeId.parse(id) }

    private fun segment(text: String): DocumentSegment = DocumentSegment(
        id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
        pageIndex = 0,
        blockIndex = 0,
        normalizedText = text,
    )

    private fun review(
        segment: DocumentSegment,
        definition: PiiDefinition,
        sourceText: String,
        surface: String,
        decision: ReviewDecisionState,
    ): ReviewOccurrence {
        val start = sourceText.indexOf(surface)
        require(start >= 0)
        return ReviewOccurrence(
            id =
            OccurrenceId(
                definition.id,
                SourceOccurrence(segment.id, SourceRange(start, start + surface.length)),
            ),
            surface = surface,
            decision = decision,
        )
    }

    private suspend fun awaitExport(
        exporter: AndroidOmbraDocumentExporter,
        operationId: OmbraOperationId,
        destinationRef: io.github.daniele21.localllm.console.application.OmbraExportDestinationRef,
        request: OmbraExportRequest,
    ) = suspendCoroutine { continuation ->
        exporter.export(operationId, destinationRef, request) { result -> continuation.resume(result) }
    }
}
