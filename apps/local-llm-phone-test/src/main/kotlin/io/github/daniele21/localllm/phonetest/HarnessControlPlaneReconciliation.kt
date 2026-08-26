package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements

private const val BUILT_IN_BINDING_REVISION = 1
private const val OMBRA_MINIMUM_CONTEXT_TOKENS = 4_096
private const val OMBRA_MAX_INPUT_CHARACTERS = 12_000
private const val OMBRA_MAX_SCHEMA_CHARACTERS = 4_096
private const val OMBRA_WARM_RETENTION_MS = 60_000L

/** Pure application identity requirement used by startup reconciliation. */
internal data class HarnessBuiltInApplicationRequirement(
    val applicationId: ApplicationId,
    val acceptedPackageNames: Set<String>,
    val acceptedSignerSha256: Set<String>,
    val displayName: String,
) {
    init {
        require(acceptedPackageNames.isNotEmpty()) { "Built-in application must accept at least one package" }
        require(acceptedPackageNames.none(String::isBlank)) { "Built-in application packages must not be blank" }
        require(acceptedSignerSha256.isNotEmpty()) { "Built-in application must accept at least one signer" }
        require(acceptedSignerSha256.all { SIGNER_SHA256.matches(it) }) {
            "Built-in application signer identity must be SHA-256"
        }
        require(displayName.isNotBlank()) { "Built-in application display name must not be blank" }
    }

    fun newRegistration(observedAtEpochMs: Long): RegisteredApplication = RegisteredApplication(
        applicationId = applicationId,
        packageName = acceptedPackageNames.minOrNull().orEmpty(),
        signerSha256 = acceptedSignerSha256.minOrNull().orEmpty(),
        displayName = displayName,
        state = ApplicationRegistrationState.AUTHORIZED,
        firstSeenAtEpochMs = observedAtEpochMs,
        lastSeenAtEpochMs = observedAtEpochMs,
    )

    fun accepts(application: RegisteredApplication): Boolean = application.applicationId == applicationId &&
        application.packageName in acceptedPackageNames &&
        application.signerSha256.lowercase() in acceptedSignerSha256.map(String::lowercase).toSet()
}

/** Canonical app-owned built-in graph. It contains no Room, Android, Binder or transport types. */
internal data class HarnessBuiltInControlPlaneSpec(
    val applications: List<HarnessBuiltInApplicationRequirement>,
    val useCase: UseCaseDefinition,
    val preset: UseCasePresetDefinition,
) {
    init {
        require(applications.isNotEmpty()) { "At least one built-in consumer application is required" }
        require(applications.distinctBy { it.applicationId }.size == applications.size) {
            "Built-in applications must be unique by application ID"
        }
        require(preset.useCaseId == useCase.useCaseId) { "Built-in preset must belong to the built-in use case" }
    }

    fun bindingFor(applicationId: ApplicationId): ApplicationUseCaseBinding = ApplicationUseCaseBinding(
        bindingId = builtInBindingId(applicationId, useCase.useCaseId.value),
        applicationId = applicationId,
        useCaseId = useCase.useCaseId,
        revision = BUILT_IN_BINDING_REVISION,
        enabled = true,
        isDefault = true,
    )

    companion object {
        fun ombra(applications: List<HarnessBuiltInApplicationRequirement>): HarnessBuiltInControlPlaneSpec =
            HarnessBuiltInControlPlaneSpec(
                applications = applications.sortedBy { it.applicationId.value },
                useCase = builtInOmbraUseCase(),
                preset = builtInOmbraPreset(),
            )
    }
}

internal enum class HarnessControlPlaneConflictCode {
    APPLICATION_IDENTITY,
    USE_CASE_DEFINITION,
    USE_CASE_REVISION_AHEAD,
    PRESET_DEFINITION,
    PRESET_REVISION_AHEAD,
    BINDING_IDENTITY,
    BINDING_BASELINE,
}

internal sealed interface HarnessControlPlaneReconciliationResult {
    data class Success(val state: HostControlPlaneState, val changed: Boolean) : HarnessControlPlaneReconciliationResult

    data class Conflict(val code: HarnessControlPlaneConflictCode, val identity: String) : HarnessControlPlaneReconciliationResult
}

/**
 * Conservatively reconciles mandatory built-in state into an already-valid HostControlPlaneState.
 * Existing user-owned revisions/defaults/disabled state are preserved; incompatible built-in identity fails closed.
 */
internal class HarnessControlPlaneReconciler(private val spec: HarnessBuiltInControlPlaneSpec) {
    fun reconcile(current: HostControlPlaneState, observedAtEpochMs: Long): HarnessControlPlaneReconciliationResult {
        require(observedAtEpochMs >= 0) { "Reconciliation timestamp must not be negative" }
        val canonicalCurrent = current.canonical()
        val applications = canonicalCurrent.applications.toMutableList()
        val useCases = canonicalCurrent.useCases.toMutableList()
        val presets = canonicalCurrent.presets.toMutableList()
        val bindings = canonicalCurrent.bindings.toMutableList()
        val exposures = canonicalCurrent.exposures.toMutableList()

        var reconciliationConflict = reconcileApplications(applications, observedAtEpochMs)
        if (reconciliationConflict == null) reconciliationConflict = reconcileUseCase(useCases)
        if (reconciliationConflict == null) reconciliationConflict = reconcilePreset(presets)
        if (reconciliationConflict == null) reconciliationConflict = reconcileBindings(bindings, exposures)
        if (reconciliationConflict != null) return reconciliationConflict

        val next = HostControlPlaneState(
            applications = applications,
            useCases = useCases,
            presets = presets,
            bindings = bindings,
            exposures = exposures,
        ).canonical()
        return HarnessControlPlaneReconciliationResult.Success(next, changed = next != canonicalCurrent)
    }

    private fun reconcileApplications(
        applications: MutableList<RegisteredApplication>,
        observedAtEpochMs: Long,
    ): HarnessControlPlaneReconciliationResult.Conflict? {
        for (requirement in spec.applications) {
            val existing = applications.singleOrNull { it.applicationId == requirement.applicationId }
            if (existing == null) {
                applications += requirement.newRegistration(observedAtEpochMs)
            } else if (!requirement.accepts(existing)) {
                return conflict(HarnessControlPlaneConflictCode.APPLICATION_IDENTITY, requirement.applicationId.value)
            }
        }
        return null
    }

    private fun reconcileUseCase(useCases: MutableList<UseCaseDefinition>): HarnessControlPlaneReconciliationResult.Conflict? {
        val matching = useCases.filter { it.useCaseId == spec.useCase.useCaseId }
        if (matching.any { it.revision > spec.useCase.revision }) {
            return conflict(HarnessControlPlaneConflictCode.USE_CASE_REVISION_AHEAD, spec.useCase.useCaseId.value)
        }
        val sameRevision = matching.singleOrNull { it.revision == spec.useCase.revision }
        if (sameRevision == null) {
            useCases += spec.useCase
        } else if (sameRevision != spec.useCase) {
            return conflict(HarnessControlPlaneConflictCode.USE_CASE_DEFINITION, spec.useCase.useCaseId.value)
        }
        return null
    }

    private fun reconcilePreset(presets: MutableList<UseCasePresetDefinition>): HarnessControlPlaneReconciliationResult.Conflict? {
        val matching = presets.filter {
            it.useCaseId == spec.preset.useCaseId && it.metadata.presetId == spec.preset.metadata.presetId
        }
        if (matching.any { it.metadata.revision > spec.preset.metadata.revision }) {
            return conflict(HarnessControlPlaneConflictCode.PRESET_REVISION_AHEAD, spec.preset.metadata.presetId)
        }
        val sameRevision = matching.singleOrNull { it.metadata.revision == spec.preset.metadata.revision }
        if (sameRevision == null) {
            presets += spec.preset
        } else if (sameRevision != spec.preset) {
            return conflict(HarnessControlPlaneConflictCode.PRESET_DEFINITION, spec.preset.metadata.presetId)
        }
        return null
    }

    private fun reconcileBindings(
        bindings: MutableList<ApplicationUseCaseBinding>,
        exposures: MutableList<StoredPresetExposure>,
    ): HarnessControlPlaneReconciliationResult.Conflict? {
        for (requirement in spec.applications) {
            val canonicalBinding = spec.bindingFor(requirement.applicationId)
            val assignmentBindings = bindings.filter {
                it.applicationId == requirement.applicationId && it.useCaseId == spec.useCase.useCaseId
            }
            if (assignmentBindings.any { it.bindingId != canonicalBinding.bindingId }) {
                return conflict(HarnessControlPlaneConflictCode.BINDING_IDENTITY, requirement.applicationId.value)
            }
            val baseline = assignmentBindings.singleOrNull { it.revision == BUILT_IN_BINDING_REVISION }
            if (baseline == null) {
                bindings += canonicalBinding
            } else if (baseline != canonicalBinding) {
                return conflict(HarnessControlPlaneConflictCode.BINDING_BASELINE, canonicalBinding.bindingId)
            }

            val currentBinding = (
                bindings.filter {
                    it.applicationId == requirement.applicationId && it.useCaseId == spec.useCase.useCaseId
                }.maxByOrNull(ApplicationUseCaseBinding::revision)
                ) ?: canonicalBinding
            reconcileExposure(currentBinding, exposures)
        }
        return null
    }

    private fun reconcileExposure(binding: ApplicationUseCaseBinding, exposures: MutableList<StoredPresetExposure>) {
        val bindingExposures = exposures.filter {
            it.bindingId == binding.bindingId && it.bindingRevision == binding.revision
        }
        val required = bindingExposures.singleOrNull {
            it.presetId == spec.preset.metadata.presetId && it.presetRevision == spec.preset.metadata.revision
        }
        if (required != null) return
        exposures += StoredPresetExposure(
            bindingId = binding.bindingId,
            bindingRevision = binding.revision,
            presetId = spec.preset.metadata.presetId,
            presetRevision = spec.preset.metadata.revision,
            isDefault = binding.enabled && bindingExposures.none(StoredPresetExposure::isDefault),
        )
    }

    private fun conflict(code: HarnessControlPlaneConflictCode, identity: String) =
        HarnessControlPlaneReconciliationResult.Conflict(code = code, identity = identity)
}

private fun builtInOmbraUseCase() = UseCaseDefinition(
    useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
    displayName = "Document PII detection",
    description = "Detect configured PII locally from document text",
    requirements = UseCaseRequirements(
        outputMode = OutputMode.JSON_SCHEMA,
        sessionKind = SessionKind.STATELESS,
        reasoningSupported = false,
        minimumContextTokens = OMBRA_MINIMUM_CONTEXT_TOKENS,
        maxInputCharacters = OMBRA_MAX_INPUT_CHARACTERS,
        maxJsonSchemaCharacters = OMBRA_MAX_SCHEMA_CHARACTERS,
    ),
    state = UseCaseDefinitionState.ACTIVE,
    revision = 1,
)

private fun builtInOmbraPreset() = UseCasePresetDefinition(
    useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
    metadata = PresetConsumerMetadata(
        presetId = HarnessSharedRuntimeBindings.ombraDefaultPreset.id.value,
        revision = HarnessSharedRuntimeBindings.ombraDefaultPreset.version,
        displayName = "Balanced local PII",
        description = "Automatic local Qwen3.5 selection for structured PII detection",
    ),
    creationSource = PresetCreationSource.SUGGESTED,
    state = PresetLifecycleState.PUBLISHED,
    execution = PresetExecutionPolicy(
        modelProfileId = null,
        inferencePreset = HarnessSharedRuntimeBindings.ombraDefaultPreset,
        contextTokens = OMBRA_MINIMUM_CONTEXT_TOKENS,
        cachePolicy = UseCaseCachePolicy(
            retainModelWarmMs = OMBRA_WARM_RETENTION_MS,
            reuseStatelessContext = false,
            enablePrefixSnapshot = false,
            enableDeterministicResultCache = false,
        ),
    ),
)

private fun builtInBindingId(applicationId: ApplicationId, useCaseId: String): String = "seed-${applicationId.value}-$useCaseId"

private val SIGNER_SHA256 = Regex("[0-9a-fA-F]{64}")
