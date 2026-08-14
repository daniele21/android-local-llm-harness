package io.github.daniele21.localllm.console.pii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraPiiDefinitionDomainTest {
    @Test
    fun builtInCatalogIsVersionedStableAndValid() {
        assertEquals(1, OmbraBuiltInPiiDefinitions.VERSION)
        assertEquals(
            listOf(
                "full-name",
                "email",
                "telephone",
                "postal-address",
                "italian-tax-code",
                "iban",
            ),
            OmbraBuiltInPiiDefinitions.all.map { it.id.value },
        )
        assertTrue(OmbraBuiltInPiiDefinitions.all.all { it.source == PiiDefinitionSource.BUILT_IN })
        assertTrue(PiiDefinitionSet.create(OmbraBuiltInPiiDefinitions.all).isSuccess)
    }

    @Test
    fun customDefinitionsUseNormalizedCollisionSafeIds() {
        val first =
            createCustom(
                label = "Matricola dipendente",
                definition = "Codice interno assegnato a un dipendente.",
                existing = OmbraBuiltInPiiDefinitions.all,
            )
        assertEquals("custom-matricola-dipendente", first.id.value)

        val second =
            createCustom(
                label = "Matricola dipendente",
                definition = "Seconda definizione volutamente omonima.",
                existing = OmbraBuiltInPiiDefinitions.all + first,
            )
        assertEquals("custom-matricola-dipendente-2", second.id.value)

        val accented =
            createCustom(
                label = "Numero tesséra",
                definition = "Numero della tessera personale.",
                existing = OmbraBuiltInPiiDefinitions.all + first + second,
            )
        assertEquals("custom-numero-tessera", accented.id.value)
    }

    @Test
    fun customDraftValidationIsTypedAndRejectsUnsafeInput() {
        val validation =
            PiiDefinitionFactory.validateCustomDraft(
                draft =
                    PiiDefinitionDraft(
                        label = " ",
                        definition = "unsafe\u0000definition",
                        example = "x".repeat(PiiDefinitionLimits.MAX_EXAMPLE_CODE_POINTS + 1),
                    ),
                existingDefinitions = OmbraBuiltInPiiDefinitions.all,
            )

        assertFalse(validation.isValid)
        assertTrue(PiiDefinitionIssue.BLANK_LABEL in validation.issues)
        assertTrue(PiiDefinitionIssue.UNSUPPORTED_CONTROL_CHARACTER in validation.issues)
        assertTrue(PiiDefinitionIssue.EXAMPLE_TOO_LONG in validation.issues)
    }

    @Test
    fun customDefinitionLimitIsEnforcedBeforeConstruction() {
        var active = OmbraBuiltInPiiDefinitions.all
        repeat(PiiDefinitionLimits.MAX_CUSTOM_DEFINITIONS) { index ->
            active +=
                createCustom(
                    label = "Custom $index",
                    definition = "Definizione personalizzata $index",
                    existing = active,
                )
        }

        val result =
            PiiDefinitionFactory.createCustom(
                draft =
                    PiiDefinitionDraft(
                        label = "One too many",
                        definition = "Questa definizione supera il limite.",
                    ),
                existingDefinitions = active,
            )
        assertTrue(result is PiiDefinitionCreationResult.Invalid)
        val invalid = result as PiiDefinitionCreationResult.Invalid
        assertTrue(PiiDefinitionIssue.CUSTOM_DEFINITION_LIMIT_REACHED in invalid.validation.issues)
    }

    @Test
    fun activeDefinitionSetRejectsDuplicateIds() {
        val duplicate = OmbraBuiltInPiiDefinitions.all.first()
        val result = PiiDefinitionSet.create(OmbraBuiltInPiiDefinitions.all + duplicate)
        assertTrue(result.isFailure)
    }

    @Test
    fun generatedCustomIdRemainsInsideSchemaBound() {
        val created =
            createCustom(
                label = "Identificatore personale " + "molto-lungo-".repeat(12),
                definition = "Valore identificativo personale definito dall'utente.",
                existing = OmbraBuiltInPiiDefinitions.all,
            )
        assertTrue(created.id.value.length <= PiiDefinitionLimits.MAX_TYPE_ID_CHARS)
        assertTrue(created.id.value.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")))
    }

    private fun createCustom(
        label: String,
        definition: String,
        existing: Collection<PiiDefinition>,
    ): PiiDefinition {
        val result =
            PiiDefinitionFactory.createCustom(
                draft = PiiDefinitionDraft(label = label, definition = definition),
                existingDefinitions = existing,
            )
        assertTrue("Expected valid custom definition but got $result", result is PiiDefinitionCreationResult.Created)
        return (result as PiiDefinitionCreationResult.Created).definition
    }
}
