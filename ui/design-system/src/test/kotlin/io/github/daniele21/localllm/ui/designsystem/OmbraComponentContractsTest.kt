package io.github.daniele21.localllm.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraComponentContractsTest {
    @Test
    fun `hidden finding accessibility summary contains only safe metadata`() {
        val sensitiveTestValue = "persona.test@example.invalid"
        val summary =
            ombraFindingAccessibilitySummary(
                category = "Email",
                positionLabel = "Occorrenza 1 di 3",
                value = OmbraFindingDisplayValue.Hidden("[EMAIL_1]", "Valore nascosto"),
                decisionLabel = "Da decidere",
            )

        assertEquals("Email. Occorrenza 1 di 3. Valore nascosto. Da decidere", summary)
        assertFalse(summary.contains(sensitiveTestValue))
    }

    @Test
    fun `revealed finding accessibility summary includes current value`() {
        val syntheticTestValue = "persona.test@example.invalid"
        val summary =
            ombraFindingAccessibilitySummary(
                category = "Email",
                positionLabel = "Occorrenza 1 di 3",
                value = OmbraFindingDisplayValue.Revealed(syntheticTestValue, "Contenuto sensibile rivelato"),
                decisionLabel = "Accettata",
            )

        assertTrue(summary.contains(syntheticTestValue))
    }

    @Test
    fun `revealed finding diagnostic string redacts value and state label`() {
        val finding =
            OmbraFindingDisplayValue.Revealed(
                value = "persona.test@example.invalid",
                stateLabel = "Contenuto persona.test@example.invalid rivelato",
            )

        assertEquals("Revealed(value=<redacted>, stateLabel=<redacted>)", finding.toString())
        assertFalse(finding.toString().contains("persona.test@example.invalid"))
    }

    @Test
    fun `definition editor diagnostic string redacts every user controlled string`() {
        val sensitiveTestValue = "persona.test@example.invalid"
        val state =
            OmbraDefinitionEditorState(
                name = sensitiveTestValue,
                definition = sensitiveTestValue,
                example = sensitiveTestValue,
                nameError = sensitiveTestValue,
                definitionError = sensitiveTestValue,
                exampleError = sensitiveTestValue,
                nameSupportingText = sensitiveTestValue,
                definitionSupportingText = sensitiveTestValue,
                exampleSupportingText = sensitiveTestValue,
                canAdd = true,
            )

        assertFalse(state.toString().contains(sensitiveTestValue))
        assertTrue(state.toString().contains("name=<redacted>"))
        assertTrue(state.toString().contains("nameError=true"))
    }

    @Test
    fun `hidden finding rejects content instead of a safe placeholder`() {
        assertThrows(IllegalArgumentException::class.java) {
            OmbraFindingDisplayValue.Hidden("persona.test@example.invalid", "Valore nascosto")
        }
    }

    @Test
    fun `hidden finding diagnostic string redacts caller supplied state label`() {
        val finding =
            OmbraFindingDisplayValue.Hidden(
                placeholder = "[EMAIL_1]",
                stateLabel = "Valore persona.test@example.invalid nascosto",
            )

        assertEquals("Hidden(placeholder=[EMAIL_1], stateLabel=<redacted>)", finding.toString())
        assertFalse(finding.toString().contains("persona.test@example.invalid"))
    }

    @Test
    fun `task content width remains bounded on expanded layouts`() {
        assertTrue(OmbraLayoutTokens.ReadableContentMaxWidth.value in 600f..840f)
    }

    @Test
    fun `document picker exceeds minimum interactive target`() {
        assertTrue(OmbraLayoutTokens.DocumentPickerMinHeight >= DefaultOmbraSpacing.minimumTouchTarget)
    }
}
