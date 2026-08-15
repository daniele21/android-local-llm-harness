package io.github.daniele21.localllm.evaluation.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationRoomEntitiesTest {
    @Test
    fun entitiesContainNoPromptExpectedOrGeneratedAnswerColumns() {
        val fieldNames = listOf(
            EvaluationRunEntity::class.java,
            EvaluationRunConfigEntity::class.java,
            EvaluationRunIdentityEntity::class.java,
            EvaluationSemanticExecutionEntity::class.java,
            EvaluationRuntimeEnvironmentEntity::class.java,
            EvaluationProgressEntity::class.java,
            EvaluationReliabilityEntity::class.java,
            EvaluationFailureEntity::class.java,
            EvaluationSampleCaseEntity::class.java,
            EvaluationCategoryScoreEntity::class.java,
            EvaluationCaseResultEntity::class.java,
            EvaluationEvaluatorParameterEntity::class.java,
        ).flatMap { type -> type.declaredFields.map { field -> field.name.lowercase() } }

        val forbiddenContentFields = setOf(
            "prompt",
            "prompttext",
            "expectedanswer",
            "expectedtext",
            "generatedanswer",
            "generatedtext",
            "messagecontent",
            "outputtext",
            "responsetext",
        )
        forbiddenContentFields.forEach { forbidden ->
            assertFalse("Forbidden persisted content field: $forbidden", forbidden in fieldNames)
        }
    }

    @Test
    fun sampleOrderAndCaseEvaluatorParametersHaveNormalizedRows() {
        val sample = EvaluationSampleCaseEntity(runId = "run-1", ordinal = 7, caseId = "case-8")
        val parameter = EvaluationEvaluatorParameterEntity(
            runId = "run-1",
            caseId = "case-8",
            parameterKey = "whitespace",
            parameterValue = "trim",
        )

        assertEquals(7, sample.ordinal)
        assertEquals("case-8", sample.caseId)
        assertEquals("whitespace", parameter.parameterKey)
        assertEquals("trim", parameter.parameterValue)
    }

    @Test
    fun runEntityKeepsConfigAndIdentityAsSeparateEmbeddedShapes() {
        val configField = EvaluationRunEntity::class.java.declaredFields.singleOrNull { it.name == "config" }
        val identityField = EvaluationRunEntity::class.java.declaredFields.singleOrNull { it.name == "identity" }

        assertNotNull(configField)
        assertNotNull(identityField)
        assertTrue(EvaluationRunConfigEntity::class.java.declaredFields.any { it.name == "sampleSetDigest" })
        assertTrue(EvaluationRunIdentityEntity::class.java.declaredFields.any { it.name == "runFingerprint" })
    }
}
