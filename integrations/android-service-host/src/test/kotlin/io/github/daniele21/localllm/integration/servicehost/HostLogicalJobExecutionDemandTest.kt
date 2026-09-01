package io.github.daniele21.localllm.integration.servicehost

import org.junit.Assert.assertEquals
import org.junit.Test

class HostLogicalJobExecutionDemandTest {
    @Test
    fun `first job acquires demand and last job releases it`() {
        val changes = mutableListOf<Boolean>()
        val demand = HostLogicalJobExecutionDemand().also { it.setListener(changes::add) }
        val first = HostLogicalJobId("job-1")
        val second = HostLogicalJobId("job-2")

        demand.acquire(first)
        demand.acquire(second)
        demand.release(first)
        demand.release(second)

        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `duplicate acquire and release do not publish duplicate demand`() {
        val changes = mutableListOf<Boolean>()
        val demand = HostLogicalJobExecutionDemand().also { it.setListener(changes::add) }
        val job = HostLogicalJobId("job-1")

        demand.acquire(job)
        demand.acquire(job)
        demand.release(job)
        demand.release(job)

        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `close releases outstanding demand exactly once`() {
        val changes = mutableListOf<Boolean>()
        val demand = HostLogicalJobExecutionDemand().also { it.setListener(changes::add) }

        demand.acquire(HostLogicalJobId("job-1"))
        demand.acquire(HostLogicalJobId("job-2"))
        demand.close()
        demand.close()

        assertEquals(listOf(true, false), changes)
    }
}
