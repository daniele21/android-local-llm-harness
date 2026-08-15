package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorKey
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistration
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDatasetDocumentImporterTest {
    @Test
    fun `canonical document import derives local manifest and uses atomic installer`() {
        withImporter { importer, root ->
            val cases = listOf(
                case(id = "case-b", category = "zeta"),
                case(id = "case-a", category = "alpha"),
            )

            val result = importer.importDataset(source(jsonl(cases)), metadata()) as DatasetDocumentImportResult.Imported

            assertEquals(EvaluationDatasetOrigin.USER_IMPORTED, result.manifest.origin)
            assertEquals(cases.size, result.manifest.caseCount)
            assertEquals(EvaluationDatasetContentDigester.digest(cases), result.manifest.contentDigest)
            assertEquals(listOf("alpha", "zeta"), result.manifest.categories.map { it.id.value })
            assertTrue(result.directory.isDirectory)
            assertTrue(result.directory.resolve("manifest.json").isFile)
            assertTrue(result.directory.resolve("cases.jsonl").isFile)
            assertTrue(result.directory.toPath().startsWith(root.toPath()))
        }
    }

    @Test
    fun `malformed canonical jsonl returns typed parse failure without publication`() {
        withImporter { importer, _ ->
            val result = importer.importDataset(
                source("{not-json}\n"),
                metadata(),
            ) as DatasetDocumentImportResult.Rejected

            assertEquals(DatasetDocumentImportRejectionCode.PARSE_FAILURE, result.code)
            assertEquals(1, result.parseLineNumber)
            assertEquals(DatasetParseErrorCode.MALFORMED_JSON, result.parseCode)
        }
    }

    @Test
    fun `duplicate case identity is rejected by canonical installer validation`() {
        withImporter { importer, _ ->
            val cases = listOf(
                case(id = "duplicate", category = "general"),
                case(id = "duplicate", category = "general"),
            )

            val result = importer.importDataset(source(jsonl(cases)), metadata()) as DatasetDocumentImportResult.Rejected

            assertEquals(DatasetDocumentImportRejectionCode.INSTALL_REJECTED, result.code)
            assertEquals(DatasetInstallRejectionCode.VALIDATION_FAILURE, result.installCode)
            assertTrue(result.validationIssues.any { it.code == DatasetValidationIssueCode.DUPLICATE_CASE_ID })
        }
    }

    @Test
    fun `unavailable document is rejected without attempting installation`() {
        withImporter { importer, _ ->
            val result = importer.importDataset(
                EvaluationDatasetDocumentSource { null },
                metadata(),
            ) as DatasetDocumentImportResult.Rejected

            assertEquals(DatasetDocumentImportRejectionCode.DOCUMENT_UNAVAILABLE, result.code)
        }
    }

    @Test
    fun `empty document and invalid display metadata fail closed`() {
        withImporter { importer, _ ->
            val empty = importer.importDataset(source(""), metadata()) as DatasetDocumentImportResult.Rejected
            assertEquals(DatasetDocumentImportRejectionCode.EMPTY_DATASET, empty.code)

            val invalidMetadata = importer.importDataset(
                source(jsonl(listOf(case("case-a", "general")))),
                metadata(displayName = ""),
            ) as DatasetDocumentImportResult.Rejected
            assertEquals(DatasetDocumentImportRejectionCode.INVALID_METADATA, invalidMetadata.code)
        }
    }

    private fun withImporter(block: (EvaluationDatasetDocumentImporter, java.io.File) -> Unit) {
        val root = Files.createTempDirectory("evaluation-import-test").toFile()
        try {
            val parser = EvaluationDatasetJsonlParser()
            val registry = EvaluatorRegistry(
                listOf(
                    EvaluatorRegistration(
                        EvaluatorKey(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
                    ),
                ),
            )
            val installer = EvaluationDatasetInstaller(
                rootDirectory = root,
                parser = parser,
                validator = EvaluationDatasetPackValidator(registry),
            )
            block(EvaluationDatasetDocumentImporter(parser, installer), root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun metadata(displayName: String = "Imported fixture") = EvaluationDatasetImportMetadata(
        datasetId = EvaluationDatasetId("imported-fixture"),
        version = EvaluationDatasetVersion("1"),
        displayName = displayName,
    )

    private fun case(id: String, category: String) = EvaluationDatasetCaseV1(
        id = EvaluationCaseId(id),
        categoryId = EvaluationCategoryId(category),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Question for $id")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "answer"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
    )

    private fun jsonl(cases: List<EvaluationDatasetCaseV1>): String = cases.joinToString(separator = "", transform = { case ->
        EvaluationDatasetCanonicalJson.encodeCase(case) + "\n"
    })

    private fun source(content: String): EvaluationDatasetDocumentSource = EvaluationDatasetDocumentSource {
        ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
    }
}
