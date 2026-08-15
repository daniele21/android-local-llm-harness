package io.github.daniele21.localllm.evaluation.datasets

import android.content.ContentResolver
import android.net.Uri
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class EvaluationDatasetImportMetadata(
    val datasetId: EvaluationDatasetId,
    val version: EvaluationDatasetVersion,
    val displayName: String,
    val description: String? = null,
)

enum class DatasetDocumentImportRejectionCode {
    DOCUMENT_UNAVAILABLE,
    EMPTY_DATASET,
    INVALID_METADATA,
    PARSE_FAILURE,
    INSTALL_REJECTED,
}

sealed interface DatasetDocumentImportResult {
    data class Imported(
        val manifest: EvaluationDatasetManifestV1,
        val directory: File,
    ) : DatasetDocumentImportResult

    data class Rejected(
        val code: DatasetDocumentImportRejectionCode,
        val parseLineNumber: Int? = null,
        val parseCode: DatasetParseErrorCode? = null,
        val installCode: DatasetInstallRejectionCode? = null,
        val validationIssues: List<DatasetValidationIssue> = emptyList(),
    ) : DatasetDocumentImportResult
}

fun interface EvaluationDatasetDocumentSource {
    fun open(): InputStream?
}

class AndroidEvaluationDatasetDocumentSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : EvaluationDatasetDocumentSource {
    override fun open(): InputStream? = contentResolver.openInputStream(uri)
}

private sealed interface DatasetDocumentReadResult {
    data class Success(val cases: List<EvaluationDatasetCaseV1>) : DatasetDocumentReadResult

    data class Rejected(val result: DatasetDocumentImportResult.Rejected) : DatasetDocumentReadResult
}

class EvaluationDatasetDocumentImporter(
    private val parser: EvaluationDatasetJsonlParser,
    private val installer: EvaluationDatasetInstaller,
) {
    fun importDataset(
        source: EvaluationDatasetDocumentSource,
        metadata: EvaluationDatasetImportMetadata,
    ): DatasetDocumentImportResult {
        val readResult = readCases(source)
        if (readResult is DatasetDocumentReadResult.Rejected) return readResult.result
        val cases = (readResult as DatasetDocumentReadResult.Success).cases
        if (cases.isEmpty()) {
            return DatasetDocumentImportResult.Rejected(DatasetDocumentImportRejectionCode.EMPTY_DATASET)
        }

        val manifest = createManifest(metadata, cases)
            ?: return DatasetDocumentImportResult.Rejected(DatasetDocumentImportRejectionCode.INVALID_METADATA)
        return when (val installed = installer.install(manifest, CanonicalCaseListInputStream(cases))) {
            is DatasetInstallResult.Installed -> DatasetDocumentImportResult.Imported(manifest, installed.directory)
            is DatasetInstallResult.Rejected -> DatasetDocumentImportResult.Rejected(
                code = DatasetDocumentImportRejectionCode.INSTALL_REJECTED,
                parseLineNumber = installed.parseLineNumber,
                parseCode = installed.parseCode,
                installCode = installed.code,
                validationIssues = installed.validationIssues,
            )
        }
    }

    private fun readCases(source: EvaluationDatasetDocumentSource): DatasetDocumentReadResult {
        return try {
            val input = source.open()
                ?: return DatasetDocumentReadResult.Rejected(
                    DatasetDocumentImportResult.Rejected(DatasetDocumentImportRejectionCode.DOCUMENT_UNAVAILABLE),
                )
            DatasetDocumentReadResult.Success(input.use(parser::parse))
        } catch (failure: EvaluationDatasetParseException) {
            DatasetDocumentReadResult.Rejected(
                DatasetDocumentImportResult.Rejected(
                    code = DatasetDocumentImportRejectionCode.PARSE_FAILURE,
                    parseLineNumber = failure.lineNumber,
                    parseCode = failure.code,
                ),
            )
        } catch (_: Exception) {
            DatasetDocumentReadResult.Rejected(
                DatasetDocumentImportResult.Rejected(DatasetDocumentImportRejectionCode.DOCUMENT_UNAVAILABLE),
            )
        }
    }

    private fun createManifest(
        metadata: EvaluationDatasetImportMetadata,
        cases: List<EvaluationDatasetCaseV1>,
    ): EvaluationDatasetManifestV1? = try {
        EvaluationDatasetManifestV1(
            datasetId = metadata.datasetId,
            version = metadata.version,
            displayName = metadata.displayName,
            description = metadata.description,
            origin = EvaluationDatasetOrigin.USER_IMPORTED,
            caseCount = cases.size,
            contentDigest = EvaluationDatasetContentDigester.digest(cases),
            categories = cases
                .map { it.categoryId }
                .distinct()
                .sortedBy { it.value }
                .map { categoryId ->
                    EvaluationDatasetCategoryDefinition(
                        id = categoryId,
                        displayName = categoryId.value,
                    )
                },
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}

private class CanonicalCaseListInputStream(
    cases: List<EvaluationDatasetCaseV1>,
) : InputStream() {
    private val iterator = cases.iterator()
    private var current = ByteArrayInputStream(ByteArray(0))

    override fun read(): Int {
        while (true) {
            val value = current.read()
            if (value >= 0) return value
            if (!iterator.hasNext()) return -1
            current = ByteArrayInputStream(
                (EvaluationDatasetCanonicalJson.encodeCase(iterator.next()) + "\n")
                    .toByteArray(StandardCharsets.UTF_8),
            )
        }
    }
}
