package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DatasetInstallerTest {
    @Test
    fun `valid pack is canonicalized then atomically published`() = withInstaller { installer ->
        val cases = listOf(case("case-b", "reasoning"), case("case-a", "reasoning"))
        val manifest = manifest(cases)

        val result = installer.install(manifest, input(cases))

        assertTrue(result is DatasetInstallResult.Installed)
        val directory = installer.installedDirectory(manifest)
        assertTrue(directory.isDirectory)
        assertEquals(
            cases.joinToString(separator = "", transform = { EvaluationDatasetCanonicalJson.encodeCase(it) + "\n" }),
            directory.resolve("cases.jsonl").readText(),
        )
        val manifestText = directory.resolve("manifest.json").readText()
        assertTrue(manifestText.contains("\"datasetId\":\"fixture-pack\""))
        assertTrue(manifestText.contains("\"contentDigest\":\"${manifest.contentDigest.sha256}\""))
        assertStagingEmpty(installer)
    }

    @Test
    fun `digest mismatch fails before publication`() = withInstaller { installer ->
        val cases = listOf(case("case-a", "reasoning"))
        val manifest = manifest(cases).copy(contentDigest = EvaluationDatasetDigest("f".repeat(64)))

        val result = installer.install(manifest, input(cases)) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.DIGEST_MISMATCH, result.code)
        assertFalse(installer.installedDirectory(manifest).exists())
        assertStagingEmpty(installer)
    }

    @Test
    fun `semantic validation failure never becomes installed`() = withInstaller { installer ->
        val cases = listOf(case("case-a", "undeclared"))
        val manifest = manifest(cases, declaredCategory = "reasoning")

        val result = installer.install(manifest, input(cases)) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.VALIDATION_FAILURE, result.code)
        assertTrue(result.validationIssues.any { it.code == DatasetValidationIssueCode.UNKNOWN_CATEGORY })
        assertFalse(installer.installedDirectory(manifest).exists())
        assertStagingEmpty(installer)
    }

    @Test
    fun `parse failure preserves line context and never publishes`() = withInstaller { installer ->
        val cases = listOf(case("case-a", "reasoning"))
        val manifest = manifest(cases)
        val malformed = ByteArrayInputStream("{}\n".toByteArray(StandardCharsets.UTF_8))

        val result = installer.install(manifest, malformed) as DatasetInstallResult.Rejected

        assertEquals(DatasetInstallRejectionCode.PARSE_FAILURE, result.code)
        assertEquals(1, result.parseLineNumber)
        assertFalse(installer.installedDirectory(manifest).exists())
        assertStagingEmpty(installer)
    }

    @Test
    fun `existing version is never overwritten`() = withInstaller { installer ->
        val cases = listOf(case("case-a", "reasoning"))
        val manifest = manifest(cases)
        val first = installer.install(manifest, input(cases))
        val casesFile = installer.installedDirectory(manifest).resolve("cases.jsonl")
        val firstBytes = casesFile.readBytes()

        val second = installer.install(manifest, input(cases)) as DatasetInstallResult.Rejected

        assertTrue(first is DatasetInstallResult.Installed)
        assertEquals(DatasetInstallRejectionCode.ALREADY_INSTALLED, second.code)
        assertTrue(firstBytes.contentEquals(casesFile.readBytes()))
        assertStagingEmpty(installer)
    }

    private fun withInstaller(block: (EvaluationDatasetInstaller) -> Unit) {
        val root = Files.createTempDirectory("evaluation-dataset-install").toFile()
        try {
            block(
                EvaluationDatasetInstaller(
                    rootDirectory = root,
                    parser = EvaluationDatasetJsonlParser(),
                    validator = EvaluationDatasetPackValidator(registry()),
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertStagingEmpty(installer: EvaluationDatasetInstaller) {
        val staging = installer.stagingRoot()
        assertTrue(!staging.exists() || staging.listFiles().orEmpty().isEmpty())
    }

    private fun input(cases: List<EvaluationDatasetCaseV1>) = ByteArrayInputStream(
        cases.joinToString(separator = "", transform = { EvaluationDatasetCanonicalJson.encodeCase(it) + "\n" })
            .toByteArray(StandardCharsets.UTF_8),
    )

    private fun manifest(
        cases: List<EvaluationDatasetCaseV1>,
        declaredCategory: String = cases.first().categoryId.value,
    ) = EvaluationDatasetManifestV1(
        datasetId = EvaluationDatasetId("fixture-pack"),
        version = EvaluationDatasetVersion("1.0.0"),
        displayName = "Fixture pack",
        origin = EvaluationDatasetOrigin.USER_IMPORTED,
        caseCount = cases.size,
        contentDigest = EvaluationDatasetContentDigester.digest(cases),
        categories = listOf(
            EvaluationDatasetCategoryDefinition(
                id = EvaluationCategoryId(declaredCategory),
                displayName = "Reasoning",
            ),
        ),
    )

    private fun case(id: String, category: String) = EvaluationDatasetCaseV1(
        id = EvaluationCaseId(id),
        categoryId = EvaluationCategoryId(category),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Return alpha")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "alpha"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
    )

    private fun registry() = EvaluatorRegistry(
        listOf(EvaluatorRegistration(EvaluatorKey(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)))),
    )
}
