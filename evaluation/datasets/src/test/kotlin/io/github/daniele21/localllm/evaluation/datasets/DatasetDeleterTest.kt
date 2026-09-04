package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DatasetDeleterTest {
    @Test
    fun `installed pack is deleted and disappears from registry`() = runBlocking {
        withInstalledPack { root, registry, manifest ->
            val deleter = EvaluationDatasetDeleter(
                rootDirectory = root,
                registry = registry,
                activeUseProbe = ActiveEvaluationDatasetUseProbe { false },
            )

            val result = deleter.delete(manifest.datasetId, manifest.version)

            assertEquals(DatasetDeleteStatus.DELETED, result)
            assertNull(registry.find(manifest.datasetId, manifest.version))
        }
    }

    @Test
    fun `active run blocks deletion and keeps published pack visible`() = runBlocking {
        withInstalledPack { root, registry, manifest ->
            val deleter = EvaluationDatasetDeleter(
                rootDirectory = root,
                registry = registry,
                activeUseProbe = ActiveEvaluationDatasetUseProbe { identity ->
                    identity.id == manifest.datasetId && identity.version == manifest.version
                },
            )

            val result = deleter.delete(manifest.datasetId, manifest.version)

            assertEquals(DatasetDeleteStatus.ACTIVE_RUN, result)
            assertNotNull(registry.find(manifest.datasetId, manifest.version))
        }
    }

    @Test
    fun `missing pack returns not found without consulting active run state`() = runBlocking {
        val root = Files.createTempDirectory("evaluation-delete-empty").toFile()
        var probeCalled = false
        try {
            val deleter = EvaluationDatasetDeleter(
                rootDirectory = root,
                registry = EvaluationDatasetRegistry(root),
                activeUseProbe = ActiveEvaluationDatasetUseProbe {
                    probeCalled = true
                    false
                },
            )

            val result = deleter.delete(DATASET_ID, DATASET_VERSION)

            assertEquals(DatasetDeleteStatus.NOT_FOUND, result)
            assertEquals(false, probeCalled)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withInstalledPack(block: suspend (java.io.File, EvaluationDatasetRegistry, EvaluationDatasetManifestV1) -> Unit) =
        runBlocking {
            val root = Files.createTempDirectory("evaluation-delete-test").toFile()
            try {
                val case = case()
                val manifest = manifest(case)
                val parser = EvaluationDatasetJsonlParser()
                val evaluatorRegistry = EvaluatorRegistry(listOf(ExactMatchEvaluator.REGISTRATION))
                val installer = EvaluationDatasetInstaller(
                    rootDirectory = root,
                    parser = parser,
                    validator = EvaluationDatasetPackValidator(evaluatorRegistry),
                )
                val source = (EvaluationDatasetCanonicalJson.encodeCase(case) + "\n").toByteArray(StandardCharsets.UTF_8)
                val installed = installer.install(manifest, ByteArrayInputStream(source))
                check(installed is DatasetInstallResult.Installed)
                block(root, EvaluationDatasetRegistry(root), manifest)
            } finally {
                root.deleteRecursively()
            }
        }

    private fun case() = EvaluationDatasetCaseV1(
        id = EvaluationCaseId("case-1"),
        categoryId = CATEGORY_ID,
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Question")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "answer"),
        evaluator = EvaluatorSpec(
            type = EvaluatorType.EXACT_MATCH,
            version = ExactMatchEvaluator.VERSION,
            parameters = mapOf(
                ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_EXACT,
            ),
        ),
    )

    private fun manifest(case: EvaluationDatasetCaseV1) = EvaluationDatasetManifestV1(
        datasetId = DATASET_ID,
        version = DATASET_VERSION,
        displayName = "Deletion fixture",
        origin = EvaluationDatasetOrigin.USER_IMPORTED,
        caseCount = 1,
        contentDigest = EvaluationDatasetContentDigester.digest(listOf(case)),
        categories = listOf(EvaluationDatasetCategoryDefinition(CATEGORY_ID, "General")),
    )

    private companion object {
        val DATASET_ID = EvaluationDatasetId("delete-fixture")
        val DATASET_VERSION = EvaluationDatasetVersion("1")
        val CATEGORY_ID = EvaluationCategoryId("general")
    }
}
