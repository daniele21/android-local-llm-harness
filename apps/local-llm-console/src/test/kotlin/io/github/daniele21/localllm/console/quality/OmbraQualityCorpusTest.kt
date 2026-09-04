package io.github.daniele21.localllm.console.quality

import io.github.daniele21.localllm.console.pii.OmbraBuiltInPiiDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraQualityCorpusTest {
    @Test
    fun `versioned corpus identity and built-in definition version stay frozen`() {
        val corpus = OmbraSyntheticQualityCorpus.load()

        assertEquals(1, corpus.identity.schemaVersion)
        assertEquals("ombra-pii-synthetic-v2", corpus.identity.corpusVersion)
        assertEquals(OmbraSyntheticQualityCorpus.EXPECTED_SHA256, corpus.identity.sha256)
        assertEquals(OmbraBuiltInPiiDefinitions.VERSION, corpus.builtInDefinitionSetVersion)
    }

    @Test
    fun `corpus covers every built-in custom and required adversarial shape`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val builtInIds = OmbraBuiltInPiiDefinitions.all.mapTo(linkedSetOf()) { it.id.value }
        val expectedTags =
            setOf(
                QualityCaseTag.NO_PII,
                QualityCaseTag.REPEATED,
                QualityCaseTag.OVERLAP,
                QualityCaseTag.NEAR_MISS,
                QualityCaseTag.INJECTION,
                QualityCaseTag.ITALIAN_TEXT,
            )

        assertEquals(setOf("custom-1"), corpus.customTypeIds)
        assertTrue(corpus.cases.flatMap { it.expectedOccurrences }.mapTo(linkedSetOf()) { it.typeId }.containsAll(builtInIds))
        assertTrue(corpus.cases.flatMap(QualityCase::tags).containsAll(expectedTags))
        assertTrue(
            corpus.cases.any { case ->
                QualityCaseTag.NO_PII in case.tags && case.expectedOccurrences.isEmpty() && case.selectedTypeIds.containsAll(builtInIds)
            },
        )
        assertTrue(
            corpus.cases.any { case ->
                QualityCaseTag.NEAR_MISS in case.tags && case.expectedOccurrences.isEmpty() && case.selectedTypeIds.containsAll(builtInIds)
            },
        )
    }

    @Test
    fun `every scored category has at least five positive exact occurrences`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val requiredTypeIds =
            OmbraBuiltInPiiDefinitions.all.mapTo(linkedSetOf()) { it.id.value } + corpus.customTypeIds
        val positiveCountByType = corpus.cases
            .flatMap(QualityCase::expectedOccurrences)
            .groupingBy(QualityOccurrence::typeId)
            .eachCount()

        requiredTypeIds.forEach { typeId ->
            assertTrue(
                "Expected at least five positive occurrences for $typeId",
                positiveCountByType.getOrDefault(typeId, 0) >= 5,
            )
        }
    }

    @Test
    fun `all fixture text and exact surfaces are explicitly synthetic`() {
        val corpus = OmbraSyntheticQualityCorpus.load()

        assertTrue(corpus.cases.flatMap(QualityCase::segments).all { it.text.startsWith(SYNTHETIC_MARKER) })
        assertTrue(
            corpus.cases
                .flatMap(QualityCase::expectedOccurrences)
                .filter { it.typeId == "email" }
                .all { it.surface.endsWith(".test") },
        )
        corpus.cases.forEach { case ->
            val segments = case.segments.associateBy(QualitySegment::id)
            case.expectedOccurrences.forEach { occurrence ->
                val source = segments.getValue(occurrence.segmentId).text
                assertEquals(occurrence.surface, source.substring(occurrence.startOffset, occurrence.endOffset))
            }
        }
    }
}
