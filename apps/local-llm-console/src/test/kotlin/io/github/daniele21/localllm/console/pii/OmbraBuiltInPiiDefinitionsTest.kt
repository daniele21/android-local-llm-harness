package io.github.daniele21.localllm.console.pii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraBuiltInPiiDefinitionsTest {
    @Test
    fun builtInIdsStayAlignedWithTheFrozenV1SchemaContract() {
        assertEquals(
            setOf(
                "full-name",
                "email",
                "telephone",
                "postal-address",
                "italian-tax-code",
                "iban",
            ),
            OmbraBuiltInPiiDefinitions.all.mapTo(linkedSetOf()) { it.id.value },
        )
        assertTrue(OmbraBuiltInPiiDefinitions.all.none { it.example != null })
    }
}
