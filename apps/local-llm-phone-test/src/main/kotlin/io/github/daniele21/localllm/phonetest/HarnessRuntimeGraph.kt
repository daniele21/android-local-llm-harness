package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.transport.InProcessLocalLlmClient
import java.io.File

/**
 * Process-scoped owner of the embedded Harness runtime and observability sources.
 *
 * Constructing the graph never loads a GGUF model. A runtime is created only when [harnessFor]
 * is called by an operation that needs inference. Telemetry is process-scoped and in-memory for
 * the first connected iteration; prompts and generated output are never stored.
 */
internal class HarnessRuntimeGraph private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val registry = HarnessPhoneBindingRegistry()

    val modelStore = FileSystemModelStore(
        File(appContext.noBackupFilesDir, MODEL_STORE_DIRECTORY),
    )

    val telemetryRepository: TelemetryRepository = InMemoryTelemetryRepository(
        TelemetryRetentionPolicy(
            maxRuns = MAX_RETAINED_RUNS,
            maxLogs = MAX_RETAINED_LOGS,
            maxResourceSnapshots = MAX_RETAINED_RESOURCE_SNAPSHOTS,
        ),
    )

    val loadedModelDigest: ModelDigest?
        get() = synchronized(lock) { runtime?.runtimeSnapshot()?.loadedModel }

    private var runtime: RuntimeOrchestrator? = null
    private var runtimeClient: LocalLlmClient? = null
    private var runtimeModelDigest: ModelDigest? = null
    private val sharedRuntimeClientFacade = HarnessSharedRuntimeClient {
        synchronized(lock) { runtimeClient }
    }

    /**
     * Stable service-facing facade over the same in-process client used by the host graph.
     * Merely obtaining or querying it does not create a runtime or select/load a model.
     */
    val sharedRuntimeClient: LocalLlmClient
        get() = sharedRuntimeClientFacade

    fun harnessFor(model: ImportedPhoneModel, purpose: HarnessRuntimePurpose): PhoneHarness = synchronized(lock) {
        Qwen35PhoneModelPolicy.requireCurated(model)
        ensureRuntimeFor(model)
        registry.select(model)
        val resolved = registry.resolve(APPLICATION_ID, purpose.useCaseId)
        PhoneHarness(
            runtime = requireNotNull(runtime),
            applicationId = resolved.binding.applicationId,
            useCaseId = resolved.binding.useCaseId,
        )
    }

    fun recentRuns(limit: Int = DEFAULT_RUN_LIMIT): List<GenerationRunRecord> = telemetryRepository.recentRuns(limit)

    fun recentLogs(limit: Int = DEFAULT_LOG_LIMIT): List<StructuredLog> = telemetryRepository.recentLogs(limit)

    fun dashboardSnapshot(): DeveloperDashboardSnapshot? = synchronized(lock) {
        runtime?.runtimeSnapshot()?.let(telemetryRepository::dashboard)
    }

    fun releaseModel(digest: ModelDigest) {
        synchronized(lock) {
            if (runtimeModelDigest != digest) return
            closeRuntimeLocked()
        }
    }

    fun unloadIdleModel(): Boolean = synchronized(lock) {
        runtime?.unloadIdleModel() ?: true
    }

    fun runtimeSnapshot() = synchronized(lock) {
        runtime?.runtimeSnapshot()
    }

    override fun close() {
        synchronized(lock) { closeRuntimeLocked() }
    }

    private fun ensureRuntimeFor(model: ImportedPhoneModel) {
        if (runtime != null && runtimeModelDigest == model.digest) return

        closeRuntimeLocked()
        val nativeLibraryDirectory = File(appContext.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDirectory.isDirectory) {
            "Native library directory is unavailable"
        }
        val orchestrator = RuntimeOrchestrator(
            registry = registry,
            modelStore = modelStore,
            backend = LlamaCppInferenceBackend(nativeLibraryDirectory),
            telemetryRepository = telemetryRepository,
        )
        runtime = orchestrator
        runtimeClient = InProcessLocalLlmClient(orchestrator)
        runtimeModelDigest = model.digest
    }

    private fun closeRuntimeLocked() {
        runtime?.close()
        runtime = null
        runtimeClient = null
        runtimeModelDigest = null
        registry.clear()
    }

    companion object {
        private const val MODEL_STORE_DIRECTORY = "local-llm-phone-test"
        private const val MAX_RETAINED_RUNS = 200
        private const val MAX_RETAINED_LOGS = 1_000
        private const val MAX_RETAINED_RESOURCE_SNAPSHOTS = 200
        private const val DEFAULT_RUN_LIMIT = 50
        private const val DEFAULT_LOG_LIMIT = 200
        internal val APPLICATION_ID = ApplicationId("play-internal-phone-test")

        @Volatile
        private var instance: HarnessRuntimeGraph? = null

        fun from(context: Context): HarnessRuntimeGraph = instance ?: synchronized(this) {
            instance ?: HarnessRuntimeGraph(context).also { instance = it }
        }
    }
}

internal enum class HarnessRuntimePurpose(val useCaseId: UseCaseId) {
    PLAYGROUND(UseCaseId("manual-inference-playground")),
    PHYSICAL_VALIDATION(UseCaseId("physical-device-validation")),
}

internal class HarnessPhoneBindingRegistry : ModelProfileRegistry {
    private val lock = Any()
    private var selectedModel: ImportedPhoneModel? = null

    fun select(model: ImportedPhoneModel) {
        Qwen35PhoneModelPolicy.requireCurated(model)
        synchronized(lock) { selectedModel = model }
    }

    fun clear() {
        synchronized(lock) { selectedModel = null }
    }

    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        require(applicationId == HarnessRuntimeGraph.APPLICATION_ID) {
            "Unknown applicationId ${applicationId.value}"
        }
        val model = synchronized(lock) {
            requireNotNull(selectedModel) { "No model selected" }
        }
        return when (useCaseId) {
            HarnessRuntimePurpose.PLAYGROUND.useCaseId -> resolvedPhonePlaygroundUseCase(model)

            HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId -> resolvedPhoneUseCase(
                model = model,
                maxOutputTokens = VALIDATION_MAX_OUTPUT_TOKENS,
            )

            else -> error("Unknown useCaseId ${useCaseId.value}")
        }
    }

    private companion object {
        const val VALIDATION_MAX_OUTPUT_TOKENS = 512
    }
}
