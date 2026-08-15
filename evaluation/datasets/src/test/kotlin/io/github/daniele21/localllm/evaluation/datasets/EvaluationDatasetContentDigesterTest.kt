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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationDatasetContentDigesterTest {
    @Test
    fun parameterAndMetadataMapConstructionOrderDoNotChangeDigest() {
        val first = case(
            id = "case-1",
            parameters = linkedMapOf("b" to "2", "a" to "1"),
            metadata = linkedMapOf("z" to "last", "a" to "first"),
        )
        val second = case(
            id = "case-1",
            parameters = linkedMapOf("a" to "1", "b" to "2"),
            metadata = linkedMapOf("a" to "first", "z" to "last"),
        )

        assertEquals(
            EvaluationDatasetContentDigester.digest(listOf(first)),
            EvaluationDatasetContentDigester.digest(listOf(second)),
        )
    }

    @Test
    fun orderedCaseContentChangesDigestWhenCaseOrderChanges() {
        val first = case("case-1")
        val second = case("case-2")

        assertNotEquals(
            EvaluationDatasetContentDigester.digest(listOf(first, second)),
            EvaluationDatasetContentDigester.digest(listOf(second, first)),
        )
    }

    @Test
    fun verificationReturnsActualDigestWithoutMutatingManifestIdentity() {
        val cases = listOf(case("case-1"))
        val manifest = manifest(EvaluationDatasetDigest("0".repeat(64)))

        val result = EvaluationDatasetContentDigester.verify(manifest, cases)

        assertTrue(result is DatasetDigestVerification.Mismatch)
        assertEquals(
            EvaluationDatasetContentDigester.digest(cases),
            (result as DatasetDigestVerification.Mismatch).actualDigest,
        )
    }

    @Test
    fun canonicalWriterUsesFrozenFieldOrderAndOmitsDefaultOutput() {
        val encoded = EvaluationDatasetCanonicalJson.encodeCase(
            case(
                id = "case-1",
                parameters = mapOf("case" to "sensitive", "whitespace" to "trim"),
                metadata = mapOf("source" to "fixture"),
            ),
        )

        assertEquals(
            """{"schemaVersion":1,"id":"case-1","categoryId":"reasoning","messages":[{"role":"USER","content":"Line 1\nLine 2"}],"expected":{"kind":"TEXT","value":"alpha"},"evaluator":{"type":"EXACT_MATCH","version":1,"parameters":{"case":"sensitive","whitespace":"trim"}},"metadata":{"source":"fixture"}}""",
            encoded,
        )
    }

    private fun manifest(digest: EvaluationDatasetDigest) = EvaluationDatasetManifestV1(
        datasetId = EvaluationDatasetId("fixture"),
        version = EvaluationDatasetVersion("1.0.0"),
        displayName = "Fixture",
        origin = EvaluationDatasetOrigin.BUILT_IN,
        caseCount = 1,
        contentDigest = digest,
        categories = listOf(EvaluationDatasetCategoryDefinition(EvaluationCategoryId("reasoning"), "Reasoning")),
    )

    private fun case(
        id: String,
        parameters: Map<String, String> = mapOf("case" to "sensitive", "whitespace" to "trim"),
        metadata: Map<String, String> = emptyMap(),
    ) = EvaluationDatasetCaseV1(
        id = EvaluationCaseId(id),
        categoryId = EvaluationCategoryId("reasoning"),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Line 1\nLine 2")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "alpha"),
        evaluator = EvaluatorSpec(
            type = EvaluatorType.EXACT_MATCH,
            version = EvaluatorVersion(1),
            parameters = parameters,
        ),
        metadata = metadata,
    )
}
