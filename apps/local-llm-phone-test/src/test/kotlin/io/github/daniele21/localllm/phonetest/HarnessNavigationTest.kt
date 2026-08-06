package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessNavigationTest {
    @Test
    fun `top level routes retain their shell destination`() {
        HarnessDestination.entries.forEach { destination ->
            val state = HarnessRoutes.shellState(destination.route)

            assertEquals(destination, state.destination)
            assertFalse(state.isDetail)
            assertEquals(
                destination != HarnessDestination.SETTINGS,
                state.showBottomNavigation,
            )
        }
    }

    @Test
    fun `settings detail routes use settings shell and hide bottom navigation`() {
        HarnessSettingsDetail.entries.forEach { detail ->
            val state = HarnessRoutes.shellState(detail.route)

            assertEquals(HarnessDestination.SETTINGS, state.destination)
            assertEquals(detail.title, state.detailTitle)
            assertEquals(detail.subtitle, state.detailSubtitle)
            assertTrue(state.isDetail)
            assertFalse(state.showBottomNavigation)
        }
    }

    @Test
    fun `request timeline route round trips opaque request ids`() {
        val requestId = "request/with spaces+unicode-è%42"
        val route = HarnessRoutes.requestTimeline(requestId)
        val encoded = route.substringAfter("runs/")

        assertFalse(encoded.contains('/'))
        assertEquals(requestId, HarnessRoutes.decodeRequestId(encoded))
        assertEquals(HarnessDestination.DIAGNOSTICS, HarnessRoutes.shellState(route).destination)
        assertTrue(HarnessRoutes.shellState(route).isDetail)
    }

    @Test
    fun `model detail route round trips digest and stable identities`() {
        listOf(
            "digest:${"a".repeat(64)}",
            "stable:qwen/release with spaces+è",
        ).forEach { identity ->
            val route = HarnessRoutes.modelDetail(identity)
            val encoded = route.substringAfter("models/")

            assertFalse(encoded.contains('/'))
            assertEquals(identity, HarnessRoutes.decodeModelIdentity(encoded))
            val shell = HarnessRoutes.shellState(route)
            assertEquals(HarnessDestination.MODELS, shell.destination)
            assertTrue(shell.isDetail)
            assertFalse(shell.showBottomNavigation)
        }
    }

    @Test
    fun `detail routes reject blank identifiers and malformed arguments`() {
        assertTrue(runCatching { HarnessRoutes.requestTimeline("   ") }.isFailure)
        assertTrue(runCatching { HarnessRoutes.modelDetail("   ") }.isFailure)
        assertNull(HarnessRoutes.decodeRequestId(null))
        assertNull(HarnessRoutes.decodeRequestId("%%%"))
        assertNull(HarnessRoutes.decodeModelIdentity(null))
        assertNull(HarnessRoutes.decodeModelIdentity("%%%"))
    }

    @Test
    fun `unknown routes fall back to overview shell`() {
        val state = HarnessRoutes.shellState("unknown")

        assertEquals(HarnessDestination.OVERVIEW, state.destination)
        assertFalse(state.isDetail)
        assertTrue(state.showBottomNavigation)
    }
}
