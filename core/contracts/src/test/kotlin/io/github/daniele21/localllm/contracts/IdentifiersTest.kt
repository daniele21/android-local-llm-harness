package io.github.daniele21.localllm.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdentifiersTest {
    @Test
    fun identifierEqualityIsValueBased() {
        assertEquals(ApplicationId("app.example"), ApplicationId("app.example"))
        assertNotEquals(ApplicationId("app.example"), ApplicationId("app.other"))
    }

    @Test
    fun differentIdentifierTypesKeepTheirOwnValues() {
        assertEquals("classification", UseCaseId("classification").value)
        assertEquals("session-1", SessionId("session-1").value)
        assertEquals("request-1", RequestId("request-1").value)
        assertEquals("abc123", ModelDigest("abc123").sha256)
    }
}
