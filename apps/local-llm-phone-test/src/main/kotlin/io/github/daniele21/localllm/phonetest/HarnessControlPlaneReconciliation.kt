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
    val initialState: ApplicationRegistrationState = ApplicationRegistrationState.AUTHORIZED,
    val allowObservedSignerChange: Boolean = false,
    val acceptedSignerSha256ByPackage: Map<String, Set<String>> = emptyMap(),
) {
    init {
        require(acceptedPackageNames.isNotEmpty()) { "Built-in application must accept at least one package" }
        require(acceptedPackageNames.none(String::isBlank)) { "Built-in application packages must not be blank" }
        require(acceptedSignerSha256.isNotEmpty()) { "Built-in application must accept at least one signer" }
        require(acceptedSignerSha256.all { SIGNER_SHA256.matches(it) }) {
            "Built-in application signer identity must be SHA-256"
        }
        require(displayName.isNotBlank()) { "Built-in application display name must not be blank" }
        if (acceptedSignerSha256ByPackage.isNotEmpty()) {
            require(acceptedSignerSha256ByPackage.keys == acceptedPackageNames) {
                "Exact signer policy must cover every accepted package"
            }
            require(
                acceptedSignerSha256ByPackage.values.all { signers ->
                    signers.isNotEmpty() && signers.all { SIGNER_SHA256.matches(it) }
                },
            ) {
                "Exact package signer identity must contain valid SHA-256 signers"
            }
            require(acceptedSignerSha256ByPackage.values.flatten().toSet() == acceptedSignerSha256) {
                "Aggregate and package-specific signer identities must match"
            }
        }
        require(
            !allowObservedSignerChange ||
                if (acceptedSignerSha256ByPackage.isEmpty()) {
                    acceptedSignerSha256.size == 1
                } else {
                    acceptedSignerSha256ByPackage.values.all { it.size == 1 }
                },
        ) {
            "Observed signer reconciliation requires exactly one current signer per package"
        }
    }

    fun newRegistration(observedAtEpochMs: Long): RegisteredApplication {
        val packageName = acceptedPackageNames.minOrNull().orEmpty()
        val signerSha256 =
            (acceptedSignerSha256ByPackage[packageName] ?: acceptedSignerSha256).minOrNull().orEmpty()
        return RegisteredApplication(
            applicationId = applicationId,
            packageName = packageName,
            signerSha256 = signerSha256,
            displayName = displayName,
            state = initialState,
            firstSeenAtEpochMs = observedAtEpochMs,
            lastSeenAtEpochMs = observedAtEpochMs,
        )
    }

    fun accepts(application: RegisteredApplication): Boolean {
        if (application.applicationId != applicationId || application.packageName !in acceptedPackageNames) return false
        val acceptedSigners = acceptedSignerSha256ByPackage[application.packageName] ?: acceptedSignerSha256
        return application.signerSha256.lowercase() in acceptedSigners.map(String::lowercase).toSet()
    }

    fun identityChange(application: RegisteredApplication, observedAtEpochMs: Long): RegisteredApplication? {
        if (!allowObservedSignerChange || application.packageName !in acceptedPackageNames) return null
        val signers = acceptedSignerSha256ByPackage[application.packageName] ?: acceptedSignerSha256
        val signer = signers.single().lowercase()
        return application.copy(
            signerSha256 = signer,
            state = ApplicationRegistrationState.SIGNATURE_CHANGED,
            lastSeenAtEpochMs = maxOf(application.lastSeenAtEpochMs, observedAtEpochMs),
        )
    }
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
 * Conservatively reconciles mandatory Host state into an already-valid HostControlPlaneState.
 * Same-signer built-ins remain immutable. Source-observed external signers fail closed into SIGNATURE_CHANGED and
 * require an explicit user re-authorization before they re-enter the live Binder policy.
 */
internal class HarnessControlPlaneReconciler(private val spec: HarnessBuiltInControlPlaneSpec) {
    fun reconcile(current: HostControlPlaneState, observedAtEpochMs: Long): HarnessControlPlaneReconciliationResult {
        val applications = current.applications.toMutableList()
        val applicationConflict = reconcileApplications(applications, observedAtEpochMs)
        if (applicationConflict != null) return applicationConflict

        val useCases = current.useCases.toMutableList()
        val useCaseConflict = reconcileUseCase(useCases)
        if (useCaseConflict != null) return useCaseConflict

        val presets = current.presets.toMutableList()
        val presetConflict = reconcilePreset(presets)
        if (presetConflict != null) return presetConflict

        val bindings = current.bindings.toMutableList()
        val bindingConflict = reconcileBindings(bindings)
        if (bindingConflict != null) return bindingConflict

        val reconciled = current.copy(
            applications = applications,
            useCases = useCases,
            presets = presets,
            bindings = bindings,
        )
        return HarnessControlPlaneReconciliationResult.Success(reconciled, reconciled != current)
    }

    private fun reconcileApplications(
        applications: MutableList<RegisteredApplication>,
        observedAtEpochMs: Long,
    ): HarnessControlPlaneReconciliationResult.Conflict? {
        for (requirement in spec.applications) {
            val index = applications.indexOfFirst { it.applicationId == requirement.applicationId }
            val existing = applications.getOrNull(index)
            if (existing == null) {
                applications += requirement.newRegistration(observedAtEpochMs)
            } else if (!requirement.accepts(existing)) {
                val changed = requirement.identityChange(existing, observedAtEpochMs)
                    ?: return conflict(HarnessControlPlaneConflictCode.APPLICATION_IDENTITY, requirement.applicationId.value)
                applications[index] = changed
            }
        }
        return null
    }

    private fun reconcileUseCase(useCases: MutableList<UseCaseDefinition>): HarnessControlPlaneReconciliationResult.Conflict? {
        val existing = useCases.filter { it.useCaseId == spec.useCase.useCaseId }
        if (existing.isEmpty()) {
            useCases += spec.useCase
            return null
        }
        val latest = existing.maxBy { it.revision }
        if (latest.revision > spec.useCase.revision) {
            return conflict(HarnessControlPlaneConflictCode.USE_CASE_REVISION_AHEAD, spec.useCase.useCaseId.value)
        }
        if (latest.revision == spec.useCase.revision && latest != spec.useCase) {
            return conflict(HarnessControlPlaneConflictCode.USE_CASE_DEFINITION, spec.useCase.useCaseId.value)
        }
        if (latest.revision < spec.useCase.revision) {
            useCases += spec.useCase
        }
        return null
    }

    private fun reconcilePreset(presets: MutableList<UseCasePresetDefinition>): HarnessControlPlaneReconciliationResult.Conflict? {
        val existing = presets.filter { it.presetId == spec.preset.presetId }
        if (existing.isEmpty()) {
            presets += spec.preset
            return null
        }
        val latest = existing.maxBy { it.revision }
        if (latest.revision > spec.preset.revision) {
            return conflict(HarnessControlPlaneConflictCode.PRESET_REVISION_AHEAD, spec.preset.presetId.value)
        }
        if (latest.revision == spec.preset.revision && latest != spec.preset) {
            return conflict(HarnessControlPlaneConflictCode.PRESET_DEFINITION, spec.preset.presetId.value)
        }
        if (latest.revision < spec.preset.revision) {
            presets += spec.preset
        }
        return null
    }

    private fun reconcileBindings(bindings: MutableList<ApplicationUseCaseBinding>): HarnessControlPlaneReconciliationResult.Conflict? {
        for (requirement in spec.applications) {
            val expected = spec.bindingFor(requirement.applicationId)
            val revisions = bindings.filter { it.bindingId == expected.bindingId }
            if (revisions.isEmpty()) {
                bindings += expected
                continue
            }
            val latest = revisions.maxBy { it.revision }
            if (latest.applicationId != expected.applicationId || latest.useCaseId != expected.useCaseId) {
                return conflict(HarnessControlPlaneConflictCode.BINDING_IDENTITY, expected.bindingId)
            }
            if (latest.revision < expected.revision) {
                bindings += expected
            } else if (latest.revision == expected.revision && latest != expected) {
                return conflict(HarnessControlPlaneConflictCode.BINDING_BASELINE, expected.bindingId)
            }
        }
        return null
    }

    private fun conflict(code: HarnessControlPlaneConflictCode, identity: String) =
        HarnessControlPlaneReconciliationResult.Conflict(code, identity)
}

internal fun builtInBindingId(applicationId: ApplicationId, useCaseId: String): String = "builtin:${applicationId.value}:$useCaseId"

internal fun builtInOmbraUseCase(): UseCaseDefinition = UseCaseDefinition(
    useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
    displayName = "Document PII detection",
    description = "Structured local document analysis for RedactGuard.",
    requirements = UseCaseRequirements(
        outputMode = OutputMode.STRUCTURED_JSON,
        sessionKind = SessionKind.STATELESS,
        requiresStreaming = false,
        minimumContextTokens = OMBRA_MINIMUM_CONTEXT_TOKENS,
    ),
    state = UseCaseDefinitionState.ACTIVE,
    revision = HarnessSharedRuntimeBindings.OMBRA_USE_CASE_REVISION,
)

internal fun builtInOmbraPreset(): UseCasePresetDefinition = UseCasePresetDefinition(
    presetId = HarnessSharedRuntimeBindings.ombraDefaultPresetId,
    useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
    displayName = "Balanced",
    description = "Default balanced preset for RedactGuard document PII detection.",
    modelProfileId = HarnessSharedRuntimeBindings.ombraModelProfileId,
    modelProfileRevision = HarnessSharedRuntimeBindings.OMBRA_MODEL_PROFILE_REVISION,
    executionPolicy = PresetExecutionPolicy(
        maxTokens = HarnessSharedRuntimeBindings.OMBRA_MAX_TOKENS,
        temperature = HarnessSharedRuntimeBindings.OMBRA_TEMPERATURE,
        topP = HarnessSharedRuntimeBindings.OMBRA_TOP_P,
        topK = HarnessSharedRuntimeBindings.OMBRA_TOP_K,
        seed = HarnessSharedRuntimeBindings.OMBRA_SEED,
        cachePolicy = UseCaseCachePolicy(
            reusePolicy = HarnessSharedRuntimeBindings.OMBRA_REUSE_POLICY,
            warmRetentionMs = OMBRA_WARM_RETENTION_MS,
        ),
    ),
    consumerMetadata = PresetConsumerMetadata(
        maxInputCharacters = OMBRA_MAX_INPUT_CHARACTERS,
        maxSchemaCharacters = OMBRA_MAX_SCHEMA_CHARACTERS,
        recommendedChunkCharacters = HarnessSharedRuntimeBindings.OMBRA_RECOMMENDED_CHUNK_CHARACTERS,
        executionHints = emptyList(),
    ),
    exposure = StoredPresetExposure.PUBLIC,
    creationSource = PresetCreationSource.BUILT_IN,
    lifecycleState = PresetLifecycleState.ACTIVE,
    revision = HarnessSharedRuntimeBindings.OMBRA_PRESET_REVISION,
)

private val SIGNER_SHA256 = Regex("^[0-9a-fA-F]{64}$")
