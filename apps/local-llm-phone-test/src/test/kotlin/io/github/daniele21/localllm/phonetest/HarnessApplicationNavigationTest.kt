package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessApplicationNavigationTest {
    @Test
    fun `application route round trips opaque identity`() {
        val applicationId = "redactguard/app with spaces+è"
        val route = HarnessApplicationRoutes.application(applicationId)
        val encoded = route.substringAfter("applications/")

        assertFalse(encoded.contains('/'))
        assertEquals(applicationId, HarnessApplicationRoutes.decodeApplicationId(encoded))
    }

    @Test
    fun `assignment and preset routes preserve independent opaque identities`() {
        val applicationId = "redactguard"
        val useCaseId = "document/pii detection"
        val presetId = "balanced local pii"
        val route = HarnessApplicationRoutes.preset(applicationId, useCaseId, presetId, 3)
        val parts = route.split('/')

        val identity = HarnessApplicationRoutes.identity(
            encodedApplicationId = parts[1],
            encodedUseCaseId = parts[3],
            encodedPresetId = parts[5],
            presetRevision = parts[6].toInt(),
        )

        assertEquals(
            HarnessApplicationRouteIdentity(applicationId, useCaseId, presetId, 3),
            identity,
        )
    }

    @Test
    fun `new preset route does not serialize domain objects`() {
        val route = HarnessApplicationRoutes.newPreset("redactguard", "document-pii-detection")

        assertTrue(route.startsWith("applications/"))
        assertTrue(route.endsWith("/presets/new"))
        assertFalse(route.contains("io.github.daniele21.redactguard"))
        assertFalse(route.contains("binding"))
    }

    @Test
    fun `invalid route arguments fail closed`() {
        assertTrue(runCatching { HarnessApplicationRoutes.application("   ") }.isFailure)
        assertTrue(runCatching { HarnessApplicationRoutes.assignment("redactguard", "") }.isFailure)
        assertTrue(runCatching { HarnessApplicationRoutes.preset("redactguard", "pii", "balanced", 0) }.isFailure)
        assertNull(HarnessApplicationRoutes.decodeApplicationId("%%%"))
        assertNull(HarnessApplicationRoutes.identity("%%%"))
        assertNull(HarnessApplicationRoutes.identity("cmVkYWN0Z3VhcmQ", "%%%"))
        assertNull(
            HarnessApplicationRoutes.identity(
                encodedApplicationId = "cmVkYWN0Z3VhcmQ",
                encodedUseCaseId = "cGlp",
                encodedPresetId = "YmFsYW5jZWQ",
                presetRevision = null,
            ),
        )
    }
}
