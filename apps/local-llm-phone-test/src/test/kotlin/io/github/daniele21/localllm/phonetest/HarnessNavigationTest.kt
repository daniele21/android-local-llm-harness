package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessNavigationTest {
    @Test
    fun `primary destinations expose Apps and Activity and keep Diagnostics out of main navigation`() {
        assertEquals(
            listOf(
                HarnessDestination.OVERVIEW,
                HarnessDestination.PLAYGROUND,
                HarnessDestination.APPS,
                HarnessDestination.ACTIVITY,
                HarnessDestination.PERFORMANCE,
                HarnessDestination.MODELS,
            ),
            HarnessDestination.main,
        )
        assertFalse(HarnessDestination.main.contains(HarnessDestination.DIAGNOSTICS))
    }

    @Test
    fun `primary top level routes retain their shell destination`() {
        HarnessDestination.main.forEach { destination ->
            val state = HarnessRoutes.shellState(destination.route)

            assertEquals(destination, state.destination)
            assertFalse(state.isDetail)
            assertTrue(state.showBottomNavigation)
        }
    }

    @Test
    fun `diagnostics is an expert detail destination outside primary navigation`() {
        val state = HarnessRoutes.shellState(HarnessDestination.DIAGNOSTICS.route)

        assertEquals(HarnessDestination.DIAGNOSTICS, state.destination)
        assertEquals("Diagnostics", state.detailTitle)
        assertTrue(state.isDetail)
        assertFalse(state.showBottomNavigation)
    }

    @Test
    fun `application detail routes stay in Apps shell and hide bottom navigation`() {
        listOf(
            HarnessApplicationRoutes.APPLICATION_PATTERN,
            HarnessApplicationRoutes.ASSIGNMENT_PATTERN,
            HarnessApplicationRoutes.PRESET_PATTERN,
            HarnessApplicationRoutes.TECHNICAL_DETAILS_PATTERN,
            HarnessApplicationRoutes.NEW_PRESET_PATTERN,
        ).forEach { route ->
            val state = HarnessRoutes.shellState(route)

            assertEquals(HarnessDestination.APPS, state.destination)
            assertTrue(state.isDetail)
            assertFalse(state.showBottomNavigation)
        }
        assertEquals(
            "Assigned Harnex use cases",
            HarnessRoutes.shellState(HarnessApplicationRoutes.APPLICATION_PATTERN).detailSubtitle,
        )
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
    fun `inference activity detail round trips opaque request ids and stays in Activity shell`() {
        val requestId = "activity/request with spaces+unicode-è%42"
        val route = HarnessInferenceActivityRoutes.detail(requestId)
        val encoded = route.substringAfter("activity/")

        assertFalse(encoded.contains('/'))
        assertEquals(requestId, HarnessInferenceActivityRoutes.decodeRequestId(encoded))
        val shell = HarnessRoutes.shellState(route)
        assertEquals(HarnessDestination.ACTIVITY, shell.destination)
        assertTrue(shell.isDetail)
        assertFalse(shell.showBottomNavigation)
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
        assertTrue(runCatching { HarnessInferenceActivityRoutes.detail("   ") }.isFailure)
        assertNull(HarnessRoutes.decodeRequestId(null))
        assertNull(HarnessRoutes.decodeRequestId("%%%"))
        assertNull(HarnessRoutes.decodeModelIdentity(null))
        assertNull(HarnessRoutes.decodeModelIdentity("%%%"))
        assertNull(HarnessInferenceActivityRoutes.decodeRequestId(null))
        assertNull(HarnessInferenceActivityRoutes.decodeRequestId("%%%"))
    }

    @Test
    fun `unknown routes fall back to overview shell`() {
        val state = HarnessRoutes.shellState("unknown")

        assertEquals(HarnessDestination.OVERVIEW, state.destination)
        assertFalse(state.isDetail)
        assertTrue(state.showBottomNavigation)
    }
}
