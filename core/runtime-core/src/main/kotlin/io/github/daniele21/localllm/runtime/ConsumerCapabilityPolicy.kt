package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityErrorCode
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPresetOption
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerReasoningPreference
import io.github.daniele21.localllm.contracts.ConsumerSelectionRequest
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness
import io.github.daniele21.localllm.models.InferencePreset
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ReasoningStreamProtocol
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.store.ModelStore
import java.security.MessageDigest

data class ConsumerUseCasePolicy(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val revision: String,
    val exposedPresets: Set<InferencePresetRef> = emptySet(),
    val defaultPreset: InferencePresetRef? = null,
    val reasoning: ConsumerReasoningCapability = ConsumerReasoningCapability.NOT_SUPPORTED,
    val outputConstraints: Set<ConsumerOutputConstraintKind>,
    val defaultOutputConstraint: ConsumerOutputConstraintKind,
    val sessionKinds: Set<SessionKind>,
    val defaultSessionKind: SessionKind,
    val limits: ConsumerLimits,
) {
    init {
        require(revision.isNotBlank()) { "Consumer policy revision must not be blank" }
        require(outputConstraints.isNotEmpty()) { "Consumer policy must expose an output constraint" }
        require(sessionKinds.isNotEmpty()) { "Consumer policy must expose a session kind" }
        require(defaultOutputConstraint in outputConstraints) { "Consumer default output constraint must be exposed" }
        require(defaultSessionKind in sessionKinds) { "Consumer default session kind must be exposed" }
        require(defaultPreset == null || defaultPreset in exposedPresets) {
            "Consumer default preset must be exposed"
        }
    }
}

interface ConsumerUseCasePolicyRegistry {
    fun find(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerUseCasePolicy?
}

class InMemoryConsumerUseCasePolicyRegistry(policies: Collection<ConsumerUseCasePolicy>) : ConsumerUseCasePolicyRegistry {
    private val policiesByKey: Map<PolicyKey, ConsumerUseCasePolicy>

    init {
        val grouped = policies.groupBy { PolicyKey(it.applicationId, it.useCaseId) }
        require(grouped.values.none { it.size > 1 }) { "Duplicate consumer use-case policy" }
        policiesByKey = grouped.mapValues { (_, entries) -> entries.single() }
    }

    override fun find(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerUseCasePolicy? =
        policiesByKey[PolicyKey(applicationId, useCaseId)]

    private data class PolicyKey(val applicationId: ApplicationId, val useCaseId: UseCaseId)
}

sealed interface ConsumerPolicyDecision {
    data class Accepted(
        val resolvedUseCase: ResolvedUseCase,
        val preset: InferencePreset?,
        val reasoningMode: EffectiveConsumerReasoningMode,
        val outputConstraint: ConsumerOutputConstraintKind,
        val sessionKind: SessionKind,
        val capabilityRevision: String,
    ) : ConsumerPolicyDecision

    data class Rejected(val code: ConsumerCapabilityErrorCode, val detail: String) : ConsumerPolicyDecision
}

class ConsumerCapabilityPolicyService(
    private val profileRegistry: ModelProfileRegistry,
    private val modelStore: ModelStore,
    private val policyRegistry: ConsumerUseCasePolicyRegistry,
) {
    fun discover(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerCapabilityResult =
        when (val context = resolveContext(applicationId, useCaseId)) {
            is ContextResult.Rejected -> ConsumerCapabilityResult.Rejected(context.code, context.detail)
            is ContextResult.Resolved -> ConsumerCapabilityResult.Available(context.capabilities)
        }

    fun validateSelection(applicationId: ApplicationId, useCaseId: UseCaseId, request: ConsumerSelectionRequest): ConsumerPolicyDecision {
        val context = when (val result = resolveContext(applicationId, useCaseId)) {
            is ContextResult.Rejected -> return ConsumerPolicyDecision.Rejected(result.code, result.detail)
            is ContextResult.Resolved -> result
        }
        val capabilities = context.capabilities

        if (request.capabilityRevision != null && request.capabilityRevision != capabilities.capabilityRevision) {
            return rejected(
                ConsumerCapabilityErrorCode.STALE_CAPABILITY,
                "Capability revision is stale; discover capabilities again before preparation",
            )
        }
        when (capabilities.readiness) {
            UseCaseReadiness.READY,
            UseCaseReadiness.AVAILABLE_REQUIRES_PREPARATION,
            -> Unit

            UseCaseReadiness.UNAVAILABLE_MODEL -> return rejected(
                ConsumerCapabilityErrorCode.MODEL_UNAVAILABLE,
                "The model bound to this use case is unavailable",
            )

            UseCaseReadiness.UNAVAILABLE_HOST_POLICY -> return rejected(
                ConsumerCapabilityErrorCode.CAPABILITY_INCOMPATIBLE,
                "The host cannot currently evaluate model readiness",
            )

            UseCaseReadiness.INCOMPATIBLE -> return rejected(
                ConsumerCapabilityErrorCode.CAPABILITY_INCOMPATIBLE,
                "Consumer policy is incompatible with the resolved use-case profile",
            )
        }

        val presetRef = request.preset ?: context.policy.defaultPreset
        if (presetRef != null && presetRef !in context.policy.exposedPresets) {
            return rejected(
                ConsumerCapabilityErrorCode.PRESET_NOT_ALLOWED,
                "Requested preset is not exposed for this use case",
            )
        }
        val preset = presetRef?.let(context.presetsByRef::get)
        if (presetRef != null && preset == null) {
            return rejected(
                ConsumerCapabilityErrorCode.CAPABILITY_INCOMPATIBLE,
                "Requested preset is not present in the resolved use-case profile",
            )
        }

        val outputConstraint = request.outputConstraint ?: context.policy.defaultOutputConstraint
        val sessionKind = request.sessionKind ?: context.policy.defaultSessionKind

        if (sessionKind !in context.policy.sessionKinds) {
            return rejected(
                ConsumerCapabilityErrorCode.SESSION_KIND_NOT_ALLOWED,
                "Requested session kind is not allowed for this use case",
            )
        }
        if (outputConstraint !in context.policy.outputConstraints ||
            outputConstraint !in effectiveOutputConstraints(context.resolved, preset)
        ) {
            return rejected(
                ConsumerCapabilityErrorCode.OUTPUT_NOT_ALLOWED,
                "Requested output constraint is not allowed by the effective preset/use-case policy",
            )
        }

        val reasoningMode = when (
            val result = resolveReasoningMode(
                capability = context.policy.reasoning,
                preference = request.reasoning,
                preset = preset,
                resolved = context.resolved,
            )
        ) {
            is ReasoningResolution.Accepted -> result.mode
            is ReasoningResolution.Rejected -> return rejected(result.code, result.detail)
        }

        return ConsumerPolicyDecision.Accepted(
            resolvedUseCase = context.resolved,
            preset = preset,
            reasoningMode = reasoningMode,
            outputConstraint = outputConstraint,
            sessionKind = sessionKind,
            capabilityRevision = capabilities.capabilityRevision,
        )
    }

    private fun resolveContext(applicationId: ApplicationId, useCaseId: UseCaseId): ContextResult {
        val policy = policyRegistry.find(applicationId, useCaseId)
            ?: return ContextResult.Rejected(
                ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED,
                "Use case is not allowed for this application",
            )
        val resolved = runCatching { profileRegistry.resolve(applicationId, useCaseId) }.getOrNull()
            ?: return ContextResult.Rejected(
                ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED,
                "Use case cannot be resolved for this application",
            )
        if (!resolved.binding.enabled ||
            resolved.binding.applicationId != applicationId ||
            resolved.binding.useCaseId != useCaseId
        ) {
            return ContextResult.Rejected(
                ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED,
                "Resolved binding does not authorize this application and use case",
            )
        }

        val presetsByRef = resolved.useCase.presets.associateBy(InferencePreset::ref)
        val policyCompatible = policyIsCompatible(policy, resolved, presetsByRef)
        val readiness = if (!policyCompatible) {
            UseCaseReadiness.INCOMPATIBLE
        } else {
            resolveReadiness(resolved)
        }
        val visiblePresets = policy.exposedPresets
            .mapNotNull(presetsByRef::get)
            .sortedWith(compareBy({ it.ref.id.value }, { it.ref.version }))
            .map { preset ->
                ConsumerPresetOption(
                    ref = preset.ref,
                    isDefault = preset.ref == policy.defaultPreset,
                )
            }
        val capabilityRevision = capabilityRevision(policy, resolved, readiness)
        val capabilities = UseCaseCapabilities(
            useCaseId = useCaseId,
            readiness = readiness,
            presets = visiblePresets,
            defaultPreset = policy.defaultPreset?.takeIf(presetsByRef::containsKey),
            reasoning = policy.reasoning,
            outputConstraints = policy.outputConstraints,
            defaultOutputConstraint = policy.defaultOutputConstraint,
            sessionKinds = policy.sessionKinds,
            defaultSessionKind = policy.defaultSessionKind,
            limits = policy.limits,
            capabilityRevision = capabilityRevision,
        )
        return ContextResult.Resolved(policy, resolved, presetsByRef, capabilities)
    }

    private fun resolveReadiness(resolved: ResolvedUseCase): UseCaseReadiness =
        runCatching { modelStore.find(resolved.model.artifact.digest) }
            .fold(
                onSuccess = { stored ->
                    when {
                        stored == null -> UseCaseReadiness.UNAVAILABLE_MODEL
                        stored.verified -> UseCaseReadiness.READY
                        else -> UseCaseReadiness.AVAILABLE_REQUIRES_PREPARATION
                    }
                },
                onFailure = { UseCaseReadiness.UNAVAILABLE_HOST_POLICY },
            )

    private fun policyIsCompatible(
        policy: ConsumerUseCasePolicy,
        resolved: ResolvedUseCase,
        presetsByRef: Map<InferencePresetRef, InferencePreset>,
    ): Boolean {
        if (!presetsByRef.keys.containsAll(policy.exposedPresets)) return false
        if (policy.defaultPreset != null && policy.defaultPreset !in presetsByRef) return false

        val internalOutputKinds = buildSet {
            add(resolved.useCase.outputMode.toConsumerKind())
            resolved.useCase.presets.forEach { preset ->
                preset.allowedOutputModes.forEach { add(it.toConsumerKind()) }
            }
        }
        if (!internalOutputKinds.containsAll(policy.outputConstraints)) return false

        if (policy.reasoning == ConsumerReasoningCapability.SURFACED_REQUIRED_BY_POLICY) {
            val defaultPreset = policy.defaultPreset?.let(presetsByRef::get)
            if (!supportsSurfacedReasoning(defaultPreset, resolved)) return false
        }
        return true
    }

    private fun effectiveOutputConstraints(resolved: ResolvedUseCase, preset: InferencePreset?): Set<ConsumerOutputConstraintKind> =
        if (preset == null) {
            setOf(resolved.useCase.outputMode.toConsumerKind())
        } else {
            preset.allowedOutputModes.mapTo(linkedSetOf(), OutputMode::toConsumerKind)
        }

    private fun resolveReasoningMode(
        capability: ConsumerReasoningCapability,
        preference: ConsumerReasoningPreference,
        preset: InferencePreset?,
        resolved: ResolvedUseCase,
    ): ReasoningResolution {
        val surfacedSupported = supportsSurfacedReasoning(preset, resolved)
        return when (preference) {
            ConsumerReasoningPreference.DEFAULT -> {
                if (capability == ConsumerReasoningCapability.SURFACED_REQUIRED_BY_POLICY) {
                    if (surfacedSupported) {
                        ReasoningResolution.Accepted(EffectiveConsumerReasoningMode.SURFACED)
                    } else {
                        ReasoningResolution.Rejected(
                            ConsumerCapabilityErrorCode.CAPABILITY_INCOMPATIBLE,
                            "Policy requires surfaced reasoning but the effective configuration cannot provide it",
                        )
                    }
                } else {
                    ReasoningResolution.Accepted(EffectiveConsumerReasoningMode.DISABLED)
                }
            }

            ConsumerReasoningPreference.DISABLED -> {
                if (capability == ConsumerReasoningCapability.SURFACED_REQUIRED_BY_POLICY) {
                    ReasoningResolution.Rejected(
                        ConsumerCapabilityErrorCode.REASONING_REQUIRED,
                        "This use case requires surfaced reasoning",
                    )
                } else {
                    ReasoningResolution.Accepted(EffectiveConsumerReasoningMode.DISABLED)
                }
            }

            ConsumerReasoningPreference.SURFACED_IF_SUPPORTED -> {
                val exposed = capability == ConsumerReasoningCapability.SURFACED_OPTIONAL ||
                    capability == ConsumerReasoningCapability.SURFACED_REQUIRED_BY_POLICY
                if (!exposed || !surfacedSupported) {
                    ReasoningResolution.Rejected(
                        ConsumerCapabilityErrorCode.REASONING_NOT_ALLOWED,
                        "Surfaced reasoning is not available for this effective configuration",
                    )
                } else {
                    ReasoningResolution.Accepted(EffectiveConsumerReasoningMode.SURFACED)
                }
            }
        }
    }

    private fun supportsSurfacedReasoning(preset: InferencePreset?, resolved: ResolvedUseCase): Boolean {
        val generation = preset?.generation ?: resolved.useCase.generationDefaults
        return generation.thinkingMode == ThinkingMode.ENABLED &&
            generation.reasoningStreamProtocol != ReasoningStreamProtocol.NONE
    }

    private fun capabilityRevision(policy: ConsumerUseCasePolicy, resolved: ResolvedUseCase, readiness: UseCaseReadiness): String {
        val canonical = buildString {
            append(policy.revision)
            append('|').append(policy.applicationId.value)
            append('|').append(policy.useCaseId.value)
            append('|').append(resolved.useCase.id)
            append('|').append(resolved.model.id)
            append('|').append(resolved.model.artifact.digest.sha256)
            append('|').append(readiness.name)
            append('|').append(policy.reasoning.name)
            append('|').append(policy.defaultPreset?.canonicalValue().orEmpty())
            append('|').append(policy.exposedPresets.map(InferencePresetRef::canonicalValue).sorted().joinToString(","))
            append('|').append(policy.outputConstraints.map { it.name }.sorted().joinToString(","))
            append('|').append(policy.defaultOutputConstraint.name)
            append('|').append(policy.sessionKinds.map { it.name }.sorted().joinToString(","))
            append('|').append(policy.defaultSessionKind.name)
            append('|').append(policy.limits.maxInputCharacters)
            append('|').append(policy.limits.maxConversationMessages)
            append('|').append(policy.limits.maxJsonSchemaCharacters)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun rejected(code: ConsumerCapabilityErrorCode, detail: String): ConsumerPolicyDecision.Rejected =
        ConsumerPolicyDecision.Rejected(code, detail)

    private sealed interface ContextResult {
        data class Resolved(
            val policy: ConsumerUseCasePolicy,
            val resolved: ResolvedUseCase,
            val presetsByRef: Map<InferencePresetRef, InferencePreset>,
            val capabilities: UseCaseCapabilities,
        ) : ContextResult

        data class Rejected(val code: ConsumerCapabilityErrorCode, val detail: String) : ContextResult
    }

    private sealed interface ReasoningResolution {
        data class Accepted(val mode: EffectiveConsumerReasoningMode) : ReasoningResolution

        data class Rejected(val code: ConsumerCapabilityErrorCode, val detail: String) : ReasoningResolution
    }
}

private fun OutputMode.toConsumerKind(): ConsumerOutputConstraintKind = when (this) {
    OutputMode.TEXT -> ConsumerOutputConstraintKind.TEXT
    OutputMode.JSON -> ConsumerOutputConstraintKind.JSON
    OutputMode.JSON_SCHEMA -> ConsumerOutputConstraintKind.JSON_SCHEMA
}

private fun InferencePresetRef.canonicalValue(): String = "${id.value}@$version"
