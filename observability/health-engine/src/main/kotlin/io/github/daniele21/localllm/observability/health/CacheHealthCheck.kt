package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.CacheHealthProbe
import io.github.daniele21.localllm.observability.HealthStatus

class CacheHealthCheck(private val probe: CacheHealthProbe) : HealthCheck {
    override val id: String = "cache-health:${probe.id}"

    override fun evaluate(): HealthAssessment {
        val snapshot = probe.snapshot()
        return if (snapshot.healthy) {
            HealthAssessment(
                status = HealthStatus.PASS,
                detail = "Cache is consistent with ${snapshot.entryCount} tracked entry(s)",
            )
        } else {
            HealthAssessment(
                status = HealthStatus.FAIL,
                detail = "Cache has ${snapshot.staleEntryCount} stale and ${snapshot.orphanedEntryCount} orphaned entry(s)",
            )
        }
    }
}
