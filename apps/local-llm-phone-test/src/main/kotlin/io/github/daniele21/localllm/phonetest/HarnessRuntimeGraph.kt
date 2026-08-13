package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.runtime.ConsumerCapabilityPolicyService
import io.github.daniele21.localllm.runtime.ConsumerLocalLlmFacade
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy
import io.github.daniele21.localllm.runtime.InMemoryConsumerUseCasePolicyRegistry
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeMemoryPressure
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.transport.InProcessLocalLlmClient
import java.io.File

/**
 * Process-scoped owner of the embedded Harness runtime and observability sources.
 *
 * Constructing the graph never loads a GGUF model. The host-selected model lives in the single
 * binding registry; a runtime is created only by an explicit prepare/inference action. Telemetry
 * is process-scoped and in-memory; prompts and generated output are never stored.
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

    var selectedModel: ImportedPhoneModel?
        get() = registry.selectedModel
        set(value) {
            registry.selectedModel = value
        }

    private var runtime: RuntimeOrchestrator? = null
    private var runtimeClient: LocalLlmClient? = null
    private val sharedRuntimeClientFacade = HarnessSharedRuntimeClient(
        activeClient = { synchronized(lock) { runtimeClient } },
        prepareClient = {
            synchronized(lock) {
                if (registry.selectedModel == null) {
                    null
                } else {
                    ensureRuntime()
                    runtimeClient
                }
            }
        },
    )

    /**
     * Stable service-facing facade over the same in-process client used by the host graph.
     * Obtaining or observing it does not create a runtime or select/load a model.
     */
    val sharedRuntimeClient: LocalLlmClient
        get() = sharedRuntimeClientFacade

    val consumerClientFactory: (ApplicationId) -> ConsumerLocalLlmClient = { applicationId ->
        require(applicationId == HarnessSharedRuntimeBindings.consoleApplicationId) {
            "Consumer API is not configured for applicationId ${applicationId.value}"
        }
        val policy =
            ConsumerUseCasePolicy(
                applicationId = applicationId,
                useCaseId = HarnessSharedRuntimeBindings.consoleUseCaseId,
                revision = "shared-console-consumer-v1",
                exposedPresets = emptySet(),
                defaultPreset = null,
                reasoning = ConsumerReasoningCapability.NOT_SUPPORTED,
                outputConstraints = setOf(ConsumerOutputConstraintKind.TEXT),
                defaultOutputConstraint = ConsumerOutputConstraintKind.TEXT,
                sessionKinds = setOf(SessionKind.STATELESS),
                defaultSessionKind = SessionKind.STATELESS,
                limits =
                    ConsumerLimits(
                        maxInputCharacters = 32_768,
                        maxConversationMessages = 128,
                        maxJsonSchemaCharacters = 32_768,
                    ),
            )
        val capabilityPolicy =
            ConsumerCapabilityPolicyService(
                profileRegistry = registry,
                modelStore = modelStore,
                policyRegistry = InMemoryConsumerUseCasePolicyRegistry(listOf(policy)),
            )
        return ConsumerLocalLlmFacade(applicationId, capabilityPolicy, sharedRuntimeClientFacade)
    }

    fun harnessFor(model: ImportedPhoneModel, purpose: HarnessRuntimePurpose): PhoneHarness = synchronized(lock) {
        Qwen35PhoneModelPolicy.requireCurated(model)
        registry.selectedModel = model
        ensureRuntime()
        val resolved = registry.resolve(APPLICATION_ID, purpose.useCaseId)
        PhoneHarness(
            runtime = requireNotNull(runtime),
            applicationId = resolved.binding.applicationId,
            useCaseId = resolved.binding.useCaseId,
        )
    }

    fun recentRuns(limit: Int = DEFAULT_RUN_LIMIT): List<GenerationRunRecord> = telemetryRepository.recentRuns(limit)

    fun recentLogs(limit: Int = DEFAULT_LOG_LIMIT): List<StructuredLog> = telemetryRepository.recentLogs(limit)

    fun releaseModel(digest: ModelDigest) {
        synchronized(lock) {
            if (runtime?.runtimeSnapshot()?.loadedModel != digest) return
            closeRuntimeLocked()
        }
    }

    fun unloadIdleModel(): Boolean = synchronized(lock) {
        runtime?.unloadIdleModel() ?: false
    }

    fun handleMemoryPressure(pressure: RuntimeMemoryPressure) = synchronized(lock) {
        runtime?.handleMemoryPressure(pressure)
    }

    fun runtimeSnapshot() = synchronized(lock) {
        runtime?.runtimeSnapshot()
    }

    override fun close() {
        synchronized(lock) {
            closeRuntimeLocked()
            registry.selectedModel = null
        }
    }

    private fun ensureRuntime() {
        if (runtime != null) return

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
    }

    private fun closeRuntimeLocked() {
        runtime?.close()
        runtime = null
        runtimeClient = null
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
    private var model: ImportedPhoneModel? = null

    var selectedModel: ImportedPhoneModel?
        get() = synchronized(lock) { model }
        set(value) {
            value?.let(Qwen35PhoneModelPolicy::requireCurated)
            synchronized(lock) { model = value }
        }

    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        val selected = synchronized(lock) {
            requireNotNull(model) { "No model selected" }
        }
        return when (applicationId) {
            HarnessRuntimeGraph.APPLICATION_ID -> resolveInternal(selected, useCaseId)

            HarnessSharedRuntimeBindings.consoleApplicationId -> {
                require(useCaseId == HarnessSharedRuntimeBindings.consoleUseCaseId) {
                    "Unknown useCaseId ${useCaseId.value}"
                }
                HarnessSharedRuntimeBindings.resolveConsole(selected)
            }

            else -> throw IllegalArgumentException("Unknown applicationId ${applicationId.value}")
        }
    }

    private fun resolveInternal(model: ImportedPhoneModel, useCaseId: UseCaseId): ResolvedUseCase = when (useCaseId) {
        HarnessRuntimePurpose.PLAYGROUND.useCaseId -> resolvedPhonePlaygroundUseCase(model)

        HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId -> resolvedPhoneUseCase(
            model = model,
            maxOutputTokens = VALIDATION_MAX_OUTPUT_TOKENS,
        )

        else -> error("Unknown useCaseId ${useCaseId.value}")
    }

    private companion object {
        const val VALIDATION_MAX_OUTPUT_TOKENS = 512
    }
}
