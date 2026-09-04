package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.RequestId
import org.junit.Assert.assertTrue
import org.junit.Test

class HostRuntimeResourcesTest {
    @Test
    fun `close drains existing service-owned resources`() {
        val resources = HostRuntimeResources()
        val handle = FakeHandle()
        val link = FakeDeathLink()
        val dispatcher = FakeDispatcher()
        resources.attachHandle(handle.requestId, handle)
        resources.attachDeathLink(HostClientToken("client"), link)
        resources.attachCallbackDispatcher(HostClientToken("client"), dispatcher)

        resources.closeAll()

        assertTrue(handle.cancelled)
        assertTrue(link.unlinked)
        assertTrue(dispatcher.closed)
    }

    @Test
    fun `resources attached after close are rejected and released immediately`() {
        val resources = HostRuntimeResources()
        resources.closeAll()
        val handle = FakeHandle()
        val link = FakeDeathLink()
        val dispatcher = FakeDispatcher()

        resources.attachHandle(handle.requestId, handle)
        resources.attachDeathLink(HostClientToken("client"), link)
        resources.attachCallbackDispatcher(HostClientToken("client"), dispatcher)

        assertTrue(handle.cancelled)
        assertTrue(link.unlinked)
        assertTrue(dispatcher.closed)
    }

    private class FakeHandle : GenerationHandle {
        override val requestId = RequestId("request")
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeDeathLink : ClientDeathLink {
        var unlinked = false

        override fun unlink() {
            unlinked = true
        }
    }

    private class FakeDispatcher : HostCallbackDispatcher {
        var closed = false

        override fun dispatch(task: () -> Unit): Boolean = false

        override fun close() {
            closed = true
        }
    }
}
