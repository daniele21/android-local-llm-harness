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
import io.github.daniele21.localllm.runtime.ActivationIdFactory
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.ActivationResidencyInferenceBackend
import io.github.daniele21.localllm.runtime.ConsumerCapabilityPolicyService
import io.github.daniele21.localllm.runtime.ConsumerLocalLlmFacade
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy
import io.github.daniele21.localllm.runtime.InMemoryConsumerUseCasePolicyRegistry
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeMemoryPressure
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.runtime.UseCaseActivationLeaseRegistry
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.transport.InProcessLocalLlmClient
import java.io.File
import java.util.UUID

private const val DEFAULT_RUN_LIMIT = 50
private const val DEFAULT_LOG_LIMIT = 200

/** Process-scoped owner of the embedded Harness runtime and observability sources. */
internal class HarnessRuntimeGraph private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val registry = HarnessPhoneBindingRegistry()
    private val activationLeases = UseCaseActivationLeaseRegistry(
        idFactory = ActivationIdFactory { UseCaseActivationId(UUID.randomUUID().toString()) },
    )

    val activationResidency = ActivationResidencyCoordinator(activationLeases)
    val modelStore = FileSystemModelStore(File(appContext.noBackupFilesDir, MODEL_STORE_DIRECTORY))

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
                ensureRuntime()
                runtimeClient
            }
        },
    )

    val sharedRuntimeClient: LocalLlmClient
        get() = sharedRuntimeClientFacade

    val consumerClientFactory: (ApplicationId) -> ConsumerLocalLlmClient = { applicationId ->
        val policies =
            when (applicationId) {
                HarnessSharedRuntimeBindings.consoleApplicationId -> {
                    val legacyConsolePolicy =
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
                    listOf(legacyConsolePolicy, HarnessOmbraConsumerPolicy.create(applicationId))
                }

                HarnessSharedRuntimeBindings.redactGuardApplicationId ->
                    listOf(HarnessOmbraConsumerPolicy.create(applicationId))

                else -> throw IllegalArgumentException(
                    "Consumer API is not configured for applicationId ${applicationId.value}",
                )
            }
        val capabilityPolicy =
            ConsumerCapabilityPolicyService(
                profileRegistry = registry,
                modelStore = modelStore,
                policyRegistry = InMemoryConsumerUseCasePolicyRegistry(policies),
            )
        ConsumerLocalLlmFacade(applicationId, capabilityPolicy, sharedRuntimeClientFacade)
    }

    fun installActivationBinding(
        activationId: UseCaseActivationId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        resolved: ResolvedUseCase,
    ) {
        registry.installActivationBinding(activationId, applicationId, useCaseId, resolved)
    }

    fun removeActivationBinding(activationId: UseCaseActivationId) {
        registry.removeActivationBinding(activationId)
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

    fun releaseModel(digest: ModelDigest) {
        synchronized(lock) {
            if (activationResidency.protects(digest)) return
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
            registry.clearActivationBindings()
            registry.selectedModel = null
        }
    }

    private fun ensureRuntime() {
        if (runtime != null) return

        val nativeLibraryDirectory = File(appContext.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDirectory.isDirectory) { "Native library directory is unavailable" }
        val backend = ActivationResidencyInferenceBackend(
            delegate = LlamaCppInferenceBackend(nativeLibraryDirectory),
            activationResidency = activationResidency,
        )
        val orchestrator = RuntimeOrchestrator(
            registry = registry,
            modelStore = modelStore,
            backend = backend,
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
        internal val APPLICATION_ID = ApplicationId("play-internal-phone-test")

        @Volatile
        private var instance: HarnessRuntimeGraph? = null

        fun from(context: Context): HarnessRuntimeGraph = instance ?: synchronized(this) {
            instance ?: HarnessRuntimeGraph(context).also { instance = it }
        }
    }
}

internal fun HarnessRuntimeGraph.recentRuns(limit: Int = DEFAULT_RUN_LIMIT): List<GenerationRunRecord> =
    telemetryRepository.recentRuns(limit)

internal fun HarnessRuntimeGraph.recentLogs(limit: Int = DEFAULT_LOG_LIMIT): List<StructuredLog> = telemetryRepository.recentLogs(limit)

internal enum class HarnessRuntimePurpose(val useCaseId: UseCaseId) {
    PLAYGROUND(UseCaseId("manual-inference-playground")),
    PHYSICAL_VALIDATION(UseCaseId("physical-device-validation")),
}

internal class HarnessPhoneBindingRegistry : ModelProfileRegistry {
    private data class ActivationBinding(
        val activationId: UseCaseActivationId,
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val resolved: ResolvedUseCase,
    )

    private val lock = Any()
    private var model: ImportedPhoneModel? = null
    private val activationBindings = LinkedHashMap<UseCaseActivationId, ActivationBinding>()

    var selectedModel: ImportedPhoneModel?
        get() = synchronized(lock) { model }
        set(value) {
            value?.let(Qwen35PhoneModelPolicy::requireCurated)
            synchronized(lock) { model = value }
        }

    fun installActivationBinding(
        activationId: UseCaseActivationId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        resolved: ResolvedUseCase,
    ) {
        require(resolved.binding.applicationId == applicationId) { "Activation binding application mismatch" }
        require(resolved.binding.useCaseId == useCaseId) { "Activation binding use-case mismatch" }
        synchronized(lock) {
            activationBindings[activationId] = ActivationBinding(activationId, applicationId, useCaseId, resolved)
        }
    }

    fun removeActivationBinding(activationId: UseCaseActivationId) {
        synchronized(lock) { activationBindings.remove(activationId) }
    }

    fun clearActivationBindings() {
        synchronized(lock) { activationBindings.clear() }
    }

    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        val activationResolved = synchronized(lock) {
            activationBindings.values.lastOrNull {
                it.applicationId == applicationId && it.useCaseId == useCaseId
            }?.resolved
        }
        if (activationResolved != null) return activationResolved

        check(applicationId == HarnessRuntimeGraph.APPLICATION_ID) {
            "External consumer requires an active Harness control-plane activation"
        }
        val selected = synchronized(lock) { requireNotNull(model) { "No model selected" } }
        return resolveInternal(selected, useCaseId)
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
