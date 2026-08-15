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
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorKey
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistration
import io.github.daniele21.localllm.evaluation.evaluators.EvaluatorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DatasetRegistryTest {
    @Test
    fun `discovers published packs and filters by identity origin and category`() = withFixture { fixture ->
        fixture.install("alpha", "1.0.0", EvaluationDatasetOrigin.BUILT_IN, "reasoning")
        fixture.install("alpha", "2.0.0", EvaluationDatasetOrigin.USER_IMPORTED, "knowledge")
        fixture.install("beta", "1.0.0", EvaluationDatasetOrigin.USER_IMPORTED, "reasoning")

        assertEquals(
            listOf("alpha@1.0.0", "alpha@2.0.0", "beta@1.0.0"),
            fixture.registry.discover().map(::identity),
        )
        assertEquals(
            listOf("alpha@1.0.0", "alpha@2.0.0"),
            fixture.registry.discover(
                EvaluationDatasetRegistryFilter(datasetId = EvaluationDatasetId("alpha")),
            ).map(::identity),
        )
        assertEquals(
            listOf("alpha@2.0.0", "beta@1.0.0"),
            fixture.registry.discover(
                EvaluationDatasetRegistryFilter(origin = EvaluationDatasetOrigin.USER_IMPORTED),
            ).map(::identity),
        )
        assertEquals(
            listOf("alpha@1.0.0", "beta@1.0.0"),
            fixture.registry.discover(
                EvaluationDatasetRegistryFilter(categoryId = EvaluationCategoryId("reasoning")),
            ).map(::identity),
        )
        assertEquals(
            "alpha@2.0.0",
            fixture.registry.find(EvaluationDatasetId("alpha"), EvaluationDatasetVersion("2.0.0"))?.let(::identity),
        )
    }

    @Test
    fun `staging incomplete and corrupted packs are never visible`() = withFixture { fixture ->
        val staged = File(File(fixture.root, ".staging"), "pending")
        assertTrue(staged.mkdirs())
        File(staged, "manifest.json").writeText("{}")
        File(staged, "cases.jsonl").writeText("{}\n")

        val incomplete = fixture.install("incomplete", "1.0.0", EvaluationDatasetOrigin.USER_IMPORTED, "reasoning")
        File(incomplete, "cases.jsonl").delete()

        val corrupted = fixture.install("corrupted", "1.0.0", EvaluationDatasetOrigin.USER_IMPORTED, "reasoning")
        File(corrupted, "manifest.json").writeText("{not-json")

        assertTrue(fixture.registry.discover().isEmpty())
        assertNull(
            fixture.registry.find(
                EvaluationDatasetId("incomplete"),
                EvaluationDatasetVersion("1.0.0"),
            ),
        )
    }

    private fun withFixture(block: (RegistryFixture) -> Unit) {
        val root = Files.createTempDirectory("evaluation-dataset-registry").toFile()
        try {
            val validator = EvaluationDatasetPackValidator(
                EvaluatorRegistry(
                    listOf(
                        EvaluatorRegistration(
                            EvaluatorKey(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
                        ),
                    ),
                ),
            )
            block(
                RegistryFixture(
                    root = root,
                    installer = EvaluationDatasetInstaller(
                        rootDirectory = root,
                        parser = EvaluationDatasetJsonlParser(),
                        validator = validator,
                    ),
                    registry = EvaluationDatasetRegistry(root),
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun identity(pack: InstalledEvaluationDatasetPack): String =
        "${pack.manifest.datasetId.value}@${pack.manifest.version.value}"

    private data class RegistryFixture(
        val root: File,
        val installer: EvaluationDatasetInstaller,
        val registry: EvaluationDatasetRegistry,
    ) {
        fun install(
            datasetId: String,
            version: String,
            origin: EvaluationDatasetOrigin,
            category: String,
        ): File {
            val cases = listOf(datasetCase("$datasetId-$version", category))
            val manifest = EvaluationDatasetManifestV1(
                datasetId = EvaluationDatasetId(datasetId),
                version = EvaluationDatasetVersion(version),
                displayName = "$datasetId $version",
                origin = origin,
                caseCount = cases.size,
                contentDigest = EvaluationDatasetContentDigester.digest(cases),
                categories = listOf(
                    EvaluationDatasetCategoryDefinition(
                        id = EvaluationCategoryId(category),
                        displayName = category,
                    ),
                ),
            )
            val input = ByteArrayInputStream(
                cases.joinToString(
                    separator = "",
                    transform = { case -> EvaluationDatasetCanonicalJson.encodeCase(case) + "\n" },
                ).toByteArray(StandardCharsets.UTF_8),
            )
            val result = installer.install(manifest, input) as DatasetInstallResult.Installed
            return result.directory
        }

        private fun datasetCase(id: String, category: String) = EvaluationDatasetCaseV1(
            id = EvaluationCaseId(id),
            categoryId = EvaluationCategoryId(category),
            messages = listOf(
                EvaluationCaseMessage(
                    role = EvaluationMessageRole.USER,
                    content = "Return alpha",
                ),
            ),
            expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "alpha"),
            evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        )
    }
}
