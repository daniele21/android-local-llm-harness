package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.health.HealthAssessment
import io.github.daniele21.localllm.observability.health.HealthCheck
import io.github.daniele21.localllm.observability.health.HealthEngine
import io.github.daniele21.localllm.observability.health.HealthSuiteReport
import io.github.daniele21.localllm.store.ModelStore

internal class HarnessHealthSource(
    private val modelStore: ModelStore,
    private val telemetryRepository: TelemetryRepository,
    private val selectedModel: () -> ImportedPhoneModel?,
    private val runtimeState: () -> RuntimeState?,
) {
    private val engine = HealthEngine(
        checks = listOf(
            SelectedModelHealthCheck(selectedModel),
            ModelIntegrityHealthCheck(modelStore, selectedModel),
            RuntimeStateHealthCheck(runtimeState),
            TelemetryRepositoryHealthCheck(telemetryRepository),
        ),
        telemetryRepository = telemetryRepository,
    )

    fun availableChecks(): Set<String> = engine.availableChecks()

    fun runAll(): HealthSuiteReport = engine.runAll()
}

private class SelectedModelHealthCheck(private val selectedModel: () -> ImportedPhoneModel?) : HealthCheck {
    override val id: String = "model.selected"

    override fun evaluate(): HealthAssessment = if (selectedModel() == null) {
        HealthAssessment(HealthStatus.WARN, "No GGUF model is currently selected")
    } else {
        HealthAssessment(HealthStatus.PASS, "A GGUF model is selected")
    }
}

private class ModelIntegrityHealthCheck(private val modelStore: ModelStore, private val selectedModel: () -> ImportedPhoneModel?) :
    HealthCheck {
    override val id: String = "model.integrity"

    override fun evaluate(): HealthAssessment {
        val model = selectedModel()
            ?: return HealthAssessment(HealthStatus.NOT_RUN, "Model integrity requires a selected model")
        val verification = modelStore.verify(model.digest)
        return if (verification.valid) {
            HealthAssessment(HealthStatus.PASS, "Selected GGUF digest and stored artifact match")
        } else {
            HealthAssessment(HealthStatus.FAIL, "Selected GGUF integrity verification failed")
        }
    }
}

private class RuntimeStateHealthCheck(private val runtimeState: () -> RuntimeState?) : HealthCheck {
    override val id: String = "runtime.state"

    override fun evaluate(): HealthAssessment {
        val state = runtimeState()
            ?: return HealthAssessment(HealthStatus.NOT_RUN, "Embedded runtime has not been created yet")
        val failed = state.name.contains("FAIL", ignoreCase = true) ||
            state.name.contains("ERROR", ignoreCase = true)
        return if (failed) {
            HealthAssessment(HealthStatus.FAIL, "Embedded runtime is in a failed state")
        } else {
            HealthAssessment(HealthStatus.PASS, "Embedded runtime state is operational")
        }
    }
}

private class TelemetryRepositoryHealthCheck(private val telemetryRepository: TelemetryRepository) : HealthCheck {
    override val id: String = "telemetry.repository"

    override fun evaluate(): HealthAssessment {
        telemetryRepository.recentRuns(1)
        telemetryRepository.recentLogs(1)
        telemetryRepository.healthResults()
        return HealthAssessment(HealthStatus.PASS, "Telemetry repository is readable")
    }
}
