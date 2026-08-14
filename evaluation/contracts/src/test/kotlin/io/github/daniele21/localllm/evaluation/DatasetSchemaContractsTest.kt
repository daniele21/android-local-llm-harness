package io.github.daniele21.localllm.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DatasetSchemaContractsTest {
    @Test
    fun manifestExposesDatasetIdentity() {
        val manifest = manifest()

        assertEquals(manifest.datasetId, manifest.identity.id)
        assertEquals(manifest.version, manifest.identity.version)
        assertEquals(manifest.contentDigest, manifest.identity.digest)
    }

    @Test
    fun manifestRejectsUnsupportedSchemaVersion() {
        expectIllegalArgument {
            manifest(schemaVersion = 2)
        }
    }

    @Test
    fun manifestRejectsDuplicateCategoryIds() {
        val category = category("reasoning")
        expectIllegalArgument {
            manifest(categories = listOf(category, category))
        }
    }

    @Test
    fun presetPreservesDeclaredCaseOrderAndRejectsDuplicates() {
        val preset = EvaluationDatasetPresetDefinition(
            id = "smoke-20",
            orderedCaseIds = listOf(caseId("case-b"), caseId("case-a")),
        )
        assertEquals(listOf("case-b", "case-a"), preset.orderedCaseIds.map { it.value })

        expectIllegalArgument {
            EvaluationDatasetPresetDefinition(
                id = "invalid",
                orderedCaseIds = listOf(caseId("case-a"), caseId("case-a")),
            )
        }
    }

    @Test
    fun caseRequiresUserInputAndKeepsExpectedAnswerSeparateFromEvaluatorSpec() {
        val case = EvaluationDatasetCaseV1(
            id = caseId("case-1"),
            categoryId = EvaluationCategoryId("reasoning"),
            messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Question")),
            expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.LABEL, "B"),
            evaluator = EvaluatorSpec(
                type = EvaluatorType.MULTIPLE_CHOICE,
                version = EvaluatorVersion(1),
                parameters = mapOf("labels" to "A,B,C,D"),
            ),
        )

        assertEquals("B", case.expected.value)
        assertEquals(EvaluatorType.MULTIPLE_CHOICE, case.evaluator.type)
        assertEquals(mapOf("labels" to "A,B,C,D"), case.evaluator.parameters)

        expectIllegalArgument {
            case.copy(messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.SYSTEM, "Rules")))
        }
    }

    @Test
    fun outputContractRejectsDuplicateStopsAndInvalidTokenLimit() {
        expectIllegalArgument {
            EvaluationCaseOutputContract(maxOutputTokens = 0)
        }
        expectIllegalArgument {
            EvaluationCaseOutputContract(stopSequences = listOf("END", "END"))
        }
    }

    private fun manifest(
        schemaVersion: Int = EVALUATION_DATASET_MANIFEST_SCHEMA_VERSION,
        categories: List<EvaluationDatasetCategoryDefinition> = listOf(category("reasoning")),
    ): EvaluationDatasetManifestV1 = EvaluationDatasetManifestV1(
        schemaVersion = schemaVersion,
        datasetId = EvaluationDatasetId("fixture-pack"),
        version = EvaluationDatasetVersion("1.0.0"),
        displayName = "Fixture Pack",
        origin = EvaluationDatasetOrigin.BUILT_IN,
        caseCount = 1,
        contentDigest = EvaluationDatasetDigest("1".repeat(64)),
        categories = categories,
    )

    private fun category(id: String) = EvaluationDatasetCategoryDefinition(
        id = EvaluationCategoryId(id),
        displayName = id,
        weight = 1.0,
    )

    private fun caseId(value: String) = EvaluationCaseId(value)

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }
}
