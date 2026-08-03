package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.store.ModelStore

class ModelIntegrityHealthCheck(private val modelStore: ModelStore) : HealthCheck {
    override val id: String = "model-integrity"

    override fun evaluate(): HealthAssessment {
        val snapshot = modelStore.snapshot()
        if (snapshot.entries.isEmpty()) {
            return HealthAssessment(
                status = HealthStatus.WARN,
                detail = "No model artifacts are installed",
            )
        }

        val invalid = snapshot.entries.count { stored ->
            !modelStore.verify(stored.digest).valid
        }
        return if (invalid == 0) {
            HealthAssessment(
                status = HealthStatus.PASS,
                detail = "Verified ${snapshot.modelCount} installed model artifact(s)",
            )
        } else {
            HealthAssessment(
                status = HealthStatus.FAIL,
                detail = "$invalid of ${snapshot.modelCount} installed model artifact(s) failed integrity verification",
            )
        }
    }
}
