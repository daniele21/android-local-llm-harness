package io.github.daniele21.localllm.evaluation.datasetadapter

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import io.github.daniele21.localllm.evaluation.datasets.DatasetInstallResult
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetCanonicalJson
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetContentDigester
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetInstaller
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetJsonlParser
import io.github.daniele21.localllm.evaluation.datasets.EvaluationDatasetPackValidator
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorKey
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistration
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class InstalledEvaluationDatasetAdapterTest {
    @Test
    fun `preflight case source and categories consume exact published pack`() = withFixture { fixture ->
        val installed = fixture.install()
        val adapter = InstalledEvaluationDatasetAdapter(fixture.root)
        val config = config(installed.identity, installed.caseIds)

        assertNull(adapter.validate(config.dataset, config.sampling))
        assertEquals("case-a", adapter.load(config, EvaluationCaseId("case-a"))?.id?.value)
        assertEquals(listOf("reasoning", "structured"), adapter.categories(config.dataset)?.map { it.id.value })
    }

    @Test
    fun `preflight rejects digest mismatch and sample outside published pack`() = withFixture { fixture ->
        val installed = fixture.install()
        val adapter = InstalledEvaluationDatasetAdapter(fixture.root)
        val wrongIdentity = installed.identity.copy(
            digest = io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest("f".repeat(64)),
        )
        val wrongDigestConfig = config(wrongIdentity, installed.caseIds)

        assertEquals(
            EvaluationFailureCode.DATASET_DIGEST_MISMATCH,
            adapter.validate(wrongDigestConfig.dataset, wrongDigestConfig.sampling)?.code,
        )

        val invalidSample = config(installed.identity, listOf(EvaluationCaseId("missing")))
        assertEquals(
            EvaluationFailureCode.SAMPLE_SET_INVALID,
            adapter.validate(invalidSample.dataset, invalidSample.sampling)?.code,
        )
        assertNull(adapter.load(invalidSample, EvaluationCaseId("missing")))
    }

    @Test
    fun `missing published identity fails closed`() = withFixture { fixture ->
        val adapter = InstalledEvaluationDatasetAdapter(fixture.root)
        val identity = EvaluationDatasetIdentity(
            EvaluationDatasetId("missing"),
            EvaluationDatasetVersion("1"),
            io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest("0".repeat(64)),
        )
        val config = config(identity, listOf(EvaluationCaseId("case-a")))

        assertEquals(EvaluationFailureCode.DATASET_NOT_FOUND, adapter.validate(identity, config.sampling)?.code)
        assertNull(adapter.load(config, EvaluationCaseId("case-a")))
        assertNull(adapter.categories(identity))
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("evaluation-dataset-adapter").toFile()
        try {
            block(Fixture(root))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun config(identity: EvaluationDatasetIdentity, caseIds: List<EvaluationCaseId>): EvaluationRunConfig = EvaluationRunConfig(
        runId = EvaluationRunId("run-1"),
        model = EvaluationModelIdentity(ModelDigest("a".repeat(64)), "supported-model"),
        dataset = identity,
        sampling = SamplingSelection.create(
            dataset = identity,
            policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
            seed = 0,
            orderedCaseIds = caseIds,
        ),
        executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("profile"), 1),
        loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
        warmupPolicy = EvaluationWarmupPolicy.NONE,
        caseTimeoutMs = 30_000,
    )

    private class Fixture(val root: java.io.File) {
        private val evaluatorRegistry = EvaluatorRegistry(
            listOf(EvaluatorRegistration(EvaluatorKey(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)))),
        )
        private val installer = EvaluationDatasetInstaller(
            rootDirectory = root,
            parser = EvaluationDatasetJsonlParser(),
            validator = EvaluationDatasetPackValidator(evaluatorRegistry),
        )

        fun install(): InstalledFixture {
            val categories = listOf(
                EvaluationDatasetCategoryDefinition(EvaluationCategoryId("reasoning"), "Reasoning", 0.6),
                EvaluationDatasetCategoryDefinition(EvaluationCategoryId("structured"), "Structured", 0.4),
            )
            val cases = listOf(
                case("case-a", "reasoning"),
                case("case-b", "structured"),
            )
            val manifest = EvaluationDatasetManifestV1(
                datasetId = EvaluationDatasetId("fixture"),
                version = EvaluationDatasetVersion("1"),
                displayName = "Fixture",
                origin = EvaluationDatasetOrigin.USER_IMPORTED,
                caseCount = cases.size,
                contentDigest = EvaluationDatasetContentDigester.digest(cases),
                categories = categories,
            )
            val bytes = cases.joinToString("") { EvaluationDatasetCanonicalJson.encodeCase(it) + "\n" }
                .toByteArray(StandardCharsets.UTF_8)
            val result = installer.install(manifest, ByteArrayInputStream(bytes))
            require(result is DatasetInstallResult.Installed)
            return InstalledFixture(manifest.identity, cases.map { it.id })
        }

        private fun case(id: String, category: String) = EvaluationDatasetCaseV1(
            id = EvaluationCaseId(id),
            categoryId = EvaluationCategoryId(category),
            messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Return alpha")),
            expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "alpha"),
            evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        )
    }

    private data class InstalledFixture(val identity: EvaluationDatasetIdentity, val caseIds: List<EvaluationCaseId>)
}
