package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseDefinitionState

internal enum class HarnessApplicationStatus {
    AUTHORIZED,
    PENDING,
    DISABLED,
    IDENTITY_CHANGED,
    UNAVAILABLE,
}

internal enum class HarnessAssignmentStatus {
    ACTIVE,
    DISABLED,
    SETUP_REQUIRED,
    UNAVAILABLE,
}

internal data class HarnessApplicationsSnapshot(
    val applications: List<HarnessApplicationSummary>,
    val connectionOptions: List<HarnessConnectionUseCaseOption> = emptyList(),
)

internal data class HarnessConnectionUseCaseOption(
    val useCaseId: String,
    val useCaseRevision: Int,
    val displayName: String,
    val description: String,
    val presets: List<HarnessConnectionPresetOption>,
)

internal data class HarnessConnectionPresetOption(
    val presetId: String,
    val revision: Int,
    val displayName: String,
    val description: String,
)

internal data class HarnessApplicationSummary(
    val applicationId: String,
    val displayName: String,
    val packageName: String,
    val signerSha256: String,
    val status: HarnessApplicationStatus,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    val assignments: List<HarnessAssignmentSummary>,
)

internal data class HarnessAssignmentSummary(
    val bindingId: String,
    val bindingRevision: Int,
    val bindingEnabled: Boolean,
    val useCaseId: String,
    val useCaseRevision: Int,
    val displayName: String,
    val description: String,
    val status: HarnessAssignmentStatus,
    val defaultPreset: HarnessPresetSummary?,
    val availablePresets: List<HarnessPresetSummary>,
    val runtime: HarnessAssignmentRuntimeSummary = HarnessAssignmentRuntimeSummary(activationActive = false),
)

internal data class HarnessPresetSummary(
    val presetId: String,
    val revision: Int,
    val displayName: String,
    val description: String,
    val source: PresetCreationSource,
    val lifecycleState: PresetLifecycleState,
    val modelProfileId: String?,
    val contextTokens: Int?,
    val isDefault: Boolean,
    val inferencePresetId: String? = null,
    val inferencePresetRevision: Int? = null,
    val retainModelWarmMs: Long? = null,
    val reuseStatelessContext: Boolean? = null,
    val enablePrefixSnapshot: Boolean? = null,
    val enableDeterministicResultCache: Boolean? = null,
    val generationOverrides: PresetGenerationOverrides? = null,
)

internal data class HarnessSetDefaultPresetCommand(
    val applicationId: String,
    val useCaseId: String,
    val expectedBindingRevision: Int,
    val presetId: String,
    val presetRevision: Int,
)

internal data class HarnessSetApplicationConnectionEnabledCommand(
    val applicationId: String,
    val enabled: Boolean,
)

internal data class HarnessCreateApplicationConnectionCommand(
    val applicationId: String,
    val displayName: String,
    val packageName: String,
    val signerSha256: String,
    val useCaseId: String,
    val presetId: String,
    val presetRevision: Int,
)

internal sealed interface HarnessControlPlaneMutationResult {
    data class Success(val snapshot: HarnessApplicationsSnapshot) : HarnessControlPlaneMutationResult

    data class StaleRevision(
        val expectedRevision: Int,
        val actualRevision: Int,
    ) : HarnessControlPlaneMutationResult

    data class Rejected(val message: String) : HarnessControlPlaneMutationResult
}

internal interface HarnessApplicationsGateway {
    fun snapshot(): HarnessApplicationsSnapshot

    fun setDefaultPreset(command: HarnessSetDefaultPresetCommand): HarnessControlPlaneMutationResult

    fun setApplicationConnectionEnabled(
        command: HarnessSetApplicationConnectionEnabledCommand,
    ): HarnessControlPlaneMutationResult

    fun createApplicationConnection(
        command: HarnessCreateApplicationConnectionCommand,
    ): HarnessControlPlaneMutationResult
}

internal class StoreHarnessApplicationsGateway(
    private val store: HostControlPlaneStore,
    private val runtimeSource: HarnessApplicationsRuntimeSource = NoHarnessApplicationsRuntimeSource,
    private val epochClock: () -> Long = System::currentTimeMillis,
) : HarnessApplicationsGateway {
    override fun snapshot(): HarnessApplicationsSnapshot = store.snapshot().toApplicationsSnapshot(runtimeSource)

    override fun setDefaultPreset(command: HarnessSetDefaultPresetCommand): HarnessControlPlaneMutationResult =
        when (val identity = command.identity()) {
            is ParsedCommandIdentity.Rejected -> HarnessControlPlaneMutationResult.Rejected(identity.message)
            is ParsedCommandIdentity.Valid -> updateDefaultPreset(command, identity)
        }

    override fun setApplicationConnectionEnabled(
        command: HarnessSetApplicationConnectionEnabledCommand,
    ): HarnessControlPlaneMutationResult = mutate {
        val applicationId = parseApplicationId(command.applicationId)
        val existing = applications.singleOrNull { it.applicationId == applicationId }
            ?: throw ControlPlaneMutationRejected("Application connection is no longer available")
        val target =
            if (command.enabled) {
                ApplicationRegistrationState.AUTHORIZED
            } else {
                ApplicationRegistrationState.DISABLED
            }
        if (existing.state !in setOf(ApplicationRegistrationState.AUTHORIZED, ApplicationRegistrationState.DISABLED)) {
            throw ControlPlaneMutationRejected("This application identity must be resolved before access can be changed")
        }
        copy(
            applications = applications.map { application ->
                if (application.applicationId == applicationId) {
                    application.copy(state = target)
                } else {
                    application
                }
            },
        )
    }

    override fun createApplicationConnection(
        command: HarnessCreateApplicationConnectionCommand,
    ): HarnessControlPlaneMutationResult = mutate {
        val applicationId = parseApplicationId(command.applicationId)
        val useCaseId = parseUseCaseId(command.useCaseId)
        val displayName = command.displayName.trim()
        val packageName = command.packageName.trim()
        val signer = command.signerSha256.trim().lowercase()
        if (displayName.isBlank()) throw ControlPlaneMutationRejected("Application name is required")
        if (packageName.isBlank()) throw ControlPlaneMutationRejected("Package name is required")
        if (command.presetRevision <= 0) throw ControlPlaneMutationRejected("Preset revision is invalid")
        if (applications.any { it.applicationId == applicationId }) {
            throw ControlPlaneMutationRejected("Application ID is already connected")
        }
        if (applications.any { it.packageName == packageName }) {
            throw ControlPlaneMutationRejected("Package name is already connected")
        }
        val useCase = latestUseCase(useCaseId)
            ?.takeIf { it.state == UseCaseDefinitionState.ACTIVE }
            ?: throw ControlPlaneMutationRejected("Selected use case is no longer available")
        val preset = preset(useCaseId, command.presetId, command.presetRevision)
            ?.takeIf { it.isConsumerVisible }
            ?: throw ControlPlaneMutationRejected("Selected preset is no longer available")
        val now = epochClock()
        val application = runCatching {
            RegisteredApplication(
                applicationId = applicationId,
                packageName = packageName,
                signerSha256 = signer,
                displayName = displayName,
                state = ApplicationRegistrationState.AUTHORIZED,
                firstSeenAtEpochMs = now,
                lastSeenAtEpochMs = now,
            )
        }.getOrElse {
            throw ControlPlaneMutationRejected("Signing certificate must be a SHA-256 fingerprint")
        }
        val binding = ApplicationUseCaseBinding(
            bindingId = "connection:${applicationId.value}:${useCase.useCaseId.value}",
            applicationId = applicationId,
            useCaseId = useCase.useCaseId,
            revision = 1,
            enabled = true,
            isDefault = true,
        )
        val exposure = StoredPresetExposure(
            bindingId = binding.bindingId,
            bindingRevision = binding.revision,
            presetId = preset.metadata.presetId,
            presetRevision = preset.metadata.revision,
            isDefault = true,
        )
        copy(
            applications = applications + application,
            bindings = bindings + binding,
            exposures = exposures + exposure,
        )
    }

    private fun mutate(
        transform: HostControlPlaneState.() -> HostControlPlaneState,
    ): HarnessControlPlaneMutationResult = try {
        val updated = store.transact(transform)
        HarnessControlPlaneMutationResult.Success(updated.toApplicationsSnapshot(runtimeSource))
    } catch (rejected: ControlPlaneMutationRejected) {
        HarnessControlPlaneMutationResult.Rejected(rejected.message.orEmpty())
    } catch (_: IllegalArgumentException) {
        HarnessControlPlaneMutationResult.Rejected("Configuration could not be updated")
    }

    private fun updateDefaultPreset(
        command: HarnessSetDefaultPresetCommand,
        identity: ParsedCommandIdentity.Valid,
    ): HarnessControlPlaneMutationResult = try {
        val updated = store.transact { current -> current.withDefaultPreset(command, identity) }
        HarnessControlPlaneMutationResult.Success(updated.toApplicationsSnapshot(runtimeSource))
    } catch (stale: StaleBindingRevision) {
        HarnessControlPlaneMutationResult.StaleRevision(stale.expectedRevision, stale.actualRevision)
    } catch (rejected: ControlPlaneMutationRejected) {
        HarnessControlPlaneMutationResult.Rejected(rejected.message.orEmpty())
    } catch (_: IllegalArgumentException) {
        HarnessControlPlaneMutationResult.Rejected("Configuration could not be updated")
    }
}

private sealed interface ParsedCommandIdentity {
    data class Valid(
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
    ) : ParsedCommandIdentity

    data class Rejected(val message: String) : ParsedCommandIdentity
}

private fun HarnessSetDefaultPresetCommand.identity(): ParsedCommandIdentity {
    val parsedApplicationId = runCatching { ApplicationId(applicationId) }.getOrElse {
        return ParsedCommandIdentity.Rejected("Application identity is invalid")
    }
    val parsedUseCaseId = runCatching { UseCaseId(useCaseId) }.getOrElse {
        return ParsedCommandIdentity.Rejected("Use-case identity is invalid")
    }
    return ParsedCommandIdentity.Valid(parsedApplicationId, parsedUseCaseId)
}

private fun parseApplicationId(value: String): ApplicationId =
    runCatching { ApplicationId(value.trim()) }
        .getOrElse { throw ControlPlaneMutationRejected("Application ID is invalid") }

private fun parseUseCaseId(value: String): UseCaseId =
    runCatching { UseCaseId(value.trim()) }
        .getOrElse { throw ControlPlaneMutationRejected("Use-case identity is invalid") }

private fun HostControlPlaneState.withDefaultPreset(
    command: HarnessSetDefaultPresetCommand,
    identity: ParsedCommandIdentity.Valid,
): HostControlPlaneState {
    val binding = requireLatestBinding(identity.applicationId, identity.useCaseId)
    requireBindingRevision(binding, command.expectedBindingRevision)
    val targetExposure = exposures.firstOrNull { exposure ->
        exposure.bindingId == binding.bindingId &&
            exposure.bindingRevision == binding.revision &&
            exposure.presetId == command.presetId &&
            exposure.presetRevision == command.presetRevision
    } ?: throw ControlPlaneMutationRejected("Preset is no longer available for this assignment")
    val targetPreset = preset(identity.useCaseId, targetExposure.presetId, targetExposure.presetRevision)
    if (targetPreset?.isConsumerVisible != true) {
        throw ControlPlaneMutationRejected("Preset is not published for consumer use")
    }
    return copy(
        exposures = exposures.map { exposure ->
            if (exposure.bindingId == binding.bindingId && exposure.bindingRevision == binding.revision) {
                exposure.copy(
                    isDefault = exposure.presetId == targetExposure.presetId &&
                        exposure.presetRevision == targetExposure.presetRevision,
                )
            } else {
                exposure
            }
        },
    )
}

private fun HostControlPlaneState.requireLatestBinding(
    applicationId: ApplicationId,
    useCaseId: UseCaseId,
): ApplicationUseCaseBinding =
    latestBinding(applicationId, useCaseId)
        ?: throw ControlPlaneMutationRejected("Assignment is no longer available")

private fun requireBindingRevision(binding: ApplicationUseCaseBinding, expectedRevision: Int) {
    if (binding.revision != expectedRevision) throw StaleBindingRevision(expectedRevision, binding.revision)
    if (!binding.enabled) throw ControlPlaneMutationRejected("Assignment is disabled")
}

private fun HostControlPlaneState.toApplicationsSnapshot(
    runtimeSource: HarnessApplicationsRuntimeSource,
): HarnessApplicationsSnapshot {
    val latestBindings = bindings
        .groupBy { it.applicationId to it.useCaseId }
        .values
        .mapNotNull { revisions -> revisions.maxByOrNull(ApplicationUseCaseBinding::revision) }
        .groupBy(ApplicationUseCaseBinding::applicationId)

    val connectionOptions = useCases
        .groupBy { it.useCaseId }
        .mapNotNull { (useCaseId, revisions) ->
            val useCase = revisions.maxByOrNull { it.revision }
                ?.takeIf { it.state == UseCaseDefinitionState.ACTIVE }
                ?: return@mapNotNull null
            val presetOptions = presets
                .filter { it.useCaseId == useCaseId && it.isConsumerVisible }
                .groupBy { it.metadata.presetId }
                .mapNotNull { (_, presetRevisions) -> presetRevisions.maxByOrNull { it.metadata.revision } }
                .sortedBy { it.metadata.displayName.lowercase() }
                .map { preset ->
                    HarnessConnectionPresetOption(
                        presetId = preset.metadata.presetId,
                        revision = preset.metadata.revision,
                        displayName = preset.metadata.displayName,
                        description = preset.metadata.description,
                    )
                }
            if (presetOptions.isEmpty()) return@mapNotNull null
            HarnessConnectionUseCaseOption(
                useCaseId = useCase.useCaseId.value,
                useCaseRevision = useCase.revision,
                displayName = useCase.displayName,
                description = useCase.description,
                presets = presetOptions,
            )
        }
        .sortedBy { it.displayName.lowercase() }

    return HarnessApplicationsSnapshot(
        applications = applications
            .sortedBy { it.displayName.lowercase() }
            .map { application ->
                HarnessApplicationSummary(
                    applicationId = application.applicationId.value,
                    displayName = application.displayName,
                    packageName = application.packageName,
                    signerSha256 = application.signerSha256,
                    status = application.state.toHarnessStatus(),
                    firstSeenAtEpochMs = application.firstSeenAtEpochMs,
                    lastSeenAtEpochMs = application.lastSeenAtEpochMs,
                    assignments = latestBindings[application.applicationId].orEmpty()
                        .sortedBy { binding -> latestUseCase(binding.useCaseId)?.displayName.orEmpty().lowercase() }
                        .mapNotNull { binding -> assignmentSummary(binding, runtimeSource) },
                )
            },
        connectionOptions = connectionOptions,
    )
}

private fun HostControlPlaneState.assignmentSummary(
    binding: ApplicationUseCaseBinding,
    runtimeSource: HarnessApplicationsRuntimeSource,
): HarnessAssignmentSummary? {
    val useCase = latestUseCase(binding.useCaseId) ?: return null
    val exposed = exposures.filter { exposure ->
        exposure.bindingId == binding.bindingId && exposure.bindingRevision == binding.revision
    }
    val summaries = exposed
        .mapNotNull { exposure ->
            preset(binding.useCaseId, exposure.presetId, exposure.presetRevision)?.let { preset ->
                HarnessPresetSummary(
                    presetId = preset.metadata.presetId,
                    revision = preset.metadata.revision,
                    displayName = preset.metadata.displayName,
                    description = preset.metadata.description,
                    source = preset.creationSource,
                    lifecycleState = preset.state,
                    modelProfileId = preset.execution.modelProfileId,
                    contextTokens = preset.execution.contextTokens,
                    isDefault = exposure.isDefault,
                    inferencePresetId = preset.execution.inferencePreset.id.value,
                    inferencePresetRevision = preset.execution.inferencePreset.version,
                    retainModelWarmMs = preset.execution.cachePolicy.retainModelWarmMs,
                    reuseStatelessContext = preset.execution.cachePolicy.reuseStatelessContext,
                    enablePrefixSnapshot = preset.execution.cachePolicy.enablePrefixSnapshot,
                    enableDeterministicResultCache = preset.execution.cachePolicy.enableDeterministicResultCache,
                    generationOverrides = preset.execution.generationOverrides,
                )
            }
        }.sortedWith(
            compareByDescending<HarnessPresetSummary> { it.isDefault }
                .thenBy { it.displayName.lowercase() },
        )

    return HarnessAssignmentSummary(
        bindingId = binding.bindingId,
        bindingRevision = binding.revision,
        bindingEnabled = binding.enabled,
        useCaseId = binding.useCaseId.value,
        useCaseRevision = useCase.revision,
        displayName = useCase.displayName,
        description = useCase.description,
        status = when {
            !binding.enabled || useCase.state == UseCaseDefinitionState.DISABLED -> HarnessAssignmentStatus.DISABLED
            useCase.state == UseCaseDefinitionState.DRAFT -> HarnessAssignmentStatus.SETUP_REQUIRED
            summaries.isEmpty() -> HarnessAssignmentStatus.SETUP_REQUIRED
            else -> HarnessAssignmentStatus.ACTIVE
        },
        defaultPreset = summaries.singleOrNull(HarnessPresetSummary::isDefault),
        availablePresets = summaries,
        runtime = runtimeSource.assignmentRuntime(binding.applicationId, binding.useCaseId),
    )
}

private fun ApplicationRegistrationState.toHarnessStatus(): HarnessApplicationStatus = when (this) {
    ApplicationRegistrationState.AUTHORIZED -> HarnessApplicationStatus.AUTHORIZED
    ApplicationRegistrationState.PENDING -> HarnessApplicationStatus.PENDING
    ApplicationRegistrationState.DISABLED -> HarnessApplicationStatus.DISABLED
    ApplicationRegistrationState.SIGNATURE_CHANGED -> HarnessApplicationStatus.IDENTITY_CHANGED
    ApplicationRegistrationState.UNAVAILABLE -> HarnessApplicationStatus.UNAVAILABLE
}

private class StaleBindingRevision(val expectedRevision: Int, val actualRevision: Int) : RuntimeException()

private class ControlPlaneMutationRejected(message: String) : RuntimeException(message)
