package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId

enum class HarnessUnavailableResource {
    BINDING,
    USE_CASE,
    PRESET,
}

enum class HarnessBoundModelProblem {
    MISSING,
    INCOMPATIBLE,
}

enum class HarnessMemoryPressureOutcome {
    MODEL_EVICTED,
    ACTIVATION_REVOKED,
}

sealed interface HarnessDecisionCondition {
    data class PendingConsumer(val applicationId: ApplicationId) : HarnessDecisionCondition

    data class MissingApplicationConfiguration(val applicationId: ApplicationId) : HarnessDecisionCondition

    data class ConfigurationUnavailable(
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val resource: HarnessUnavailableResource,
        val presetId: InferencePresetId? = null,
        val presetRevision: Int? = null,
        val bindingRevision: Int? = null,
    ) : HarnessDecisionCondition {
        init {
            require(presetRevision == null || presetRevision > 0) { "Preset revision must be positive" }
            require(bindingRevision == null || bindingRevision > 0) { "Binding revision must be positive" }
            require(presetRevision == null || presetId != null) { "Preset revision requires a preset ID" }
            require(resource == HarnessUnavailableResource.PRESET || presetId == null) {
                "Preset identity is only valid for preset-unavailable decisions"
            }
        }
    }

    data class BoundModelUnavailable(
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val problem: HarnessBoundModelProblem,
        val modelDigest: ModelDigest? = null,
        val presetId: InferencePresetId? = null,
        val presetRevision: Int? = null,
        val bindingRevision: Int? = null,
    ) : HarnessDecisionCondition {
        init {
            require(presetRevision == null || presetRevision > 0) { "Preset revision must be positive" }
            require(bindingRevision == null || bindingRevision > 0) { "Binding revision must be positive" }
            require(presetRevision == null || presetId != null) { "Preset revision requires a preset ID" }
        }
    }

    data class BrokenPreset(
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val presetId: InferencePresetId,
        val presetRevision: Int,
        val bindingRevision: Int? = null,
    ) : HarnessDecisionCondition {
        init {
            require(presetRevision > 0) { "Preset revision must be positive" }
            require(bindingRevision == null || bindingRevision > 0) { "Binding revision must be positive" }
        }
    }

    data class ProtectedResidentModelConflict(
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val requestedModelDigest: ModelDigest,
        val residentModelDigest: ModelDigest,
    ) : HarnessDecisionCondition

    data class CriticalMemoryPressure(
        val applicationId: ApplicationId?,
        val useCaseId: UseCaseId?,
        val modelDigest: ModelDigest?,
        val outcome: HarnessMemoryPressureOutcome,
    ) : HarnessDecisionCondition {
        init {
            require(applicationId != null || useCaseId == null) {
                "Use-case identity requires an application identity for memory-pressure decisions"
            }
        }
    }

    data class SignerReauthorizationRequired(val applicationId: ApplicationId) : HarnessDecisionCondition
}

fun interface HarnessDecisionIdFactory {
    fun newId(): HarnessDecisionId
}

class HarnessDecisionRuleEngine(
    private val repository: HarnessDecisionRepository,
    private val idFactory: HarnessDecisionIdFactory,
) {
    @Synchronized
    fun reconcile(conditions: Collection<HarnessDecisionCondition>, nowEpochMs: Long) {
        require(nowEpochMs >= 0) { "Decision reconciliation timestamp must not be negative" }

        val activeSpecs = conditions
            .map(::specFor)
            .associateBy(DecisionSpec::dedupeKey)
        val unresolved = repository.unresolved(MAX_RECONCILED_DECISIONS)
        val unresolvedByKey = unresolved
            .filter { it.code in MANAGED_CODES }
            .associateBy(HarnessDecisionEvent::dedupeKey)

        activeSpecs.forEach { (dedupeKey, spec) ->
            val existing = unresolvedByKey[dedupeKey]
            if (existing == null) {
                repository.upsert(spec.toEvent(idFactory.newId(), nowEpochMs))
            } else {
                val refreshed = existing.refreshedFrom(spec)
                if (refreshed != existing) repository.upsert(refreshed)
            }
        }

        unresolvedByKey
            .filterKeys { it !in activeSpecs }
            .values
            .forEach { repository.upsert(it.copy(resolvedAtEpochMs = nowEpochMs)) }
    }
}

private data class DecisionSpec(
    val category: HarnessDecisionCategory,
    val code: String,
    val title: String,
    val summary: String,
    val context: HarnessDecisionContext,
    val dedupeKey: String,
    val action: HarnessDecisionAction,
    val evidence: Map<String, String> = emptyMap(),
) {
    fun toEvent(decisionId: HarnessDecisionId, createdAtEpochMs: Long): HarnessDecisionEvent =
        HarnessDecisionEvent(
            decisionId = decisionId,
            category = category,
            code = code,
            title = title,
            summary = summary,
            context = context,
            createdAtEpochMs = createdAtEpochMs,
            dedupeKey = dedupeKey,
            action = action,
            evidence = evidence,
        )
}

private fun HarnessDecisionEvent.refreshedFrom(spec: DecisionSpec): HarnessDecisionEvent =
    copy(
        category = spec.category,
        code = spec.code,
        title = spec.title,
        summary = spec.summary,
        context = spec.context,
        action = spec.action,
        evidence = spec.evidence,
    )

private fun specFor(condition: HarnessDecisionCondition): DecisionSpec =
    when (condition) {
        is HarnessDecisionCondition.PendingConsumer -> {
            spec(
                category = HarnessDecisionCategory.ACTION_REQUIRED,
                code = CODE_NEW_CONSUMER_PENDING,
                title = "Application authorization required",
                summary = "A consumer application is waiting for Harness authorization and configuration.",
                context = HarnessDecisionContext(applicationId = condition.applicationId),
                action = HarnessDecisionAction.CONFIGURE_APPLICATION,
            )
        }

        is HarnessDecisionCondition.MissingApplicationConfiguration -> {
            spec(
                category = HarnessDecisionCategory.ACTION_REQUIRED,
                code = CODE_APPLICATION_CONFIGURATION_REQUIRED,
                title = "Application configuration required",
                summary = "The consumer application has no complete Harness configuration.",
                context = HarnessDecisionContext(applicationId = condition.applicationId),
                action = HarnessDecisionAction.CONFIGURE_APPLICATION,
            )
        }

        is HarnessDecisionCondition.ConfigurationUnavailable -> configurationUnavailableSpec(condition)
        is HarnessDecisionCondition.BoundModelUnavailable -> boundModelUnavailableSpec(condition)
        is HarnessDecisionCondition.BrokenPreset -> {
            spec(
                category = HarnessDecisionCategory.ACTION_REQUIRED,
                code = CODE_PRESET_BROKEN,
                title = "Preset requires repair",
                summary = "A published preset can no longer resolve to a valid execution configuration.",
                context = HarnessDecisionContext(
                    applicationId = condition.applicationId,
                    useCaseId = condition.useCaseId,
                    presetId = condition.presetId,
                    presetRevision = condition.presetRevision,
                    bindingRevision = condition.bindingRevision,
                ),
                action = HarnessDecisionAction.REPAIR_PRESET,
            )
        }

        is HarnessDecisionCondition.ProtectedResidentModelConflict -> {
            spec(
                category = HarnessDecisionCategory.ACTION_REQUIRED,
                code = CODE_PROTECTED_MODEL_CONFLICT,
                title = "Resident model conflict",
                summary = "The requested model conflicts with a model protected by an active use-case lease.",
                context = HarnessDecisionContext(
                    applicationId = condition.applicationId,
                    useCaseId = condition.useCaseId,
                ),
                action = HarnessDecisionAction.INSPECT_MODEL_CONFLICT,
                evidence = mapOf(
                    "requestedModelDigest" to condition.requestedModelDigest.value,
                    "residentModelDigest" to condition.residentModelDigest.value,
                ),
            )
        }

        is HarnessDecisionCondition.CriticalMemoryPressure -> {
            spec(
                category = HarnessDecisionCategory.WARNING,
                code = CODE_CRITICAL_MEMORY_PRESSURE,
                title = "Critical memory-pressure intervention",
                summary = "Harness evicted protected runtime state or revoked an activation to protect device stability.",
                context = HarnessDecisionContext(
                    applicationId = condition.applicationId,
                    useCaseId = condition.useCaseId,
                ),
                action = HarnessDecisionAction.REVIEW_MEMORY_PRESSURE,
                evidence = buildMap {
                    put("outcome", condition.outcome.name)
                    condition.modelDigest?.let { put("modelDigest", it.value) }
                },
            )
        }

        is HarnessDecisionCondition.SignerReauthorizationRequired -> {
            spec(
                category = HarnessDecisionCategory.ACTION_REQUIRED,
                code = CODE_SIGNER_REAUTHORIZATION_REQUIRED,
                title = "Application identity changed",
                summary = "The registered application signer changed and requires security re-authorization.",
                context = HarnessDecisionContext(applicationId = condition.applicationId),
                action = HarnessDecisionAction.REVIEW_SECURITY,
            )
        }
    }

private fun configurationUnavailableSpec(condition: HarnessDecisionCondition.ConfigurationUnavailable): DecisionSpec {
    val code = when (condition.resource) {
        HarnessUnavailableResource.BINDING -> CODE_BINDING_UNAVAILABLE
        HarnessUnavailableResource.USE_CASE -> CODE_USE_CASE_UNAVAILABLE
        HarnessUnavailableResource.PRESET -> CODE_PRESET_UNAVAILABLE
    }
    val action = if (condition.resource == HarnessUnavailableResource.PRESET) {
        HarnessDecisionAction.REPAIR_PRESET
    } else {
        HarnessDecisionAction.CONFIGURE_USE_CASE
    }
    return spec(
        category = HarnessDecisionCategory.ACTION_REQUIRED,
        code = code,
        title = "Use-case configuration unavailable",
        summary = "A required Harness binding, use case or published preset is unavailable.",
        context = HarnessDecisionContext(
            applicationId = condition.applicationId,
            useCaseId = condition.useCaseId,
            presetId = condition.presetId,
            presetRevision = condition.presetRevision,
            bindingRevision = condition.bindingRevision,
        ),
        action = action,
        evidence = mapOf("resource" to condition.resource.name),
    )
}

private fun boundModelUnavailableSpec(condition: HarnessDecisionCondition.BoundModelUnavailable): DecisionSpec =
    spec(
        category = HarnessDecisionCategory.ACTION_REQUIRED,
        code = when (condition.problem) {
            HarnessBoundModelProblem.MISSING -> CODE_BOUND_MODEL_MISSING
            HarnessBoundModelProblem.INCOMPATIBLE -> CODE_BOUND_MODEL_INCOMPATIBLE
        },
        title = "Bound model unavailable",
        summary = "The model assigned to this use case is missing or incompatible with its execution requirements.",
        context = HarnessDecisionContext(
            applicationId = condition.applicationId,
            useCaseId = condition.useCaseId,
            presetId = condition.presetId,
            presetRevision = condition.presetRevision,
            bindingRevision = condition.bindingRevision,
        ),
        action = HarnessDecisionAction.REPAIR_PRESET,
        evidence = buildMap {
            put("problem", condition.problem.name)
            condition.modelDigest?.let { put("modelDigest", it.value) }
        },
    )

private fun spec(
    category: HarnessDecisionCategory,
    code: String,
    title: String,
    summary: String,
    context: HarnessDecisionContext,
    action: HarnessDecisionAction,
    evidence: Map<String, String> = emptyMap(),
): DecisionSpec =
    DecisionSpec(
        category = category,
        code = code,
        title = title,
        summary = summary,
        context = context,
        dedupeKey = dedupeKey(code, context),
        action = action,
        evidence = evidence,
    )

private fun dedupeKey(code: String, context: HarnessDecisionContext): String =
    listOf(
        code,
        context.applicationId?.value ?: "-",
        context.useCaseId?.value ?: "-",
        context.presetId?.value ?: "-",
        context.presetRevision?.toString() ?: "-",
        context.bindingRevision?.toString() ?: "-",
    ).joinToString("|")

private const val MAX_RECONCILED_DECISIONS = 1_000
private const val CODE_NEW_CONSUMER_PENDING = "NEW_CONSUMER_PENDING"
private const val CODE_APPLICATION_CONFIGURATION_REQUIRED = "APPLICATION_CONFIGURATION_REQUIRED"
private const val CODE_BINDING_UNAVAILABLE = "BINDING_UNAVAILABLE"
private const val CODE_USE_CASE_UNAVAILABLE = "USE_CASE_UNAVAILABLE"
private const val CODE_PRESET_UNAVAILABLE = "PRESET_UNAVAILABLE"
private const val CODE_BOUND_MODEL_MISSING = "BOUND_MODEL_MISSING"
private const val CODE_BOUND_MODEL_INCOMPATIBLE = "BOUND_MODEL_INCOMPATIBLE"
private const val CODE_PRESET_BROKEN = "PRESET_BROKEN"
private const val CODE_PROTECTED_MODEL_CONFLICT = "PROTECTED_MODEL_CONFLICT"
private const val CODE_CRITICAL_MEMORY_PRESSURE = "CRITICAL_MEMORY_PRESSURE"
private const val CODE_SIGNER_REAUTHORIZATION_REQUIRED = "SIGNER_REAUTHORIZATION_REQUIRED"
private val MANAGED_CODES = setOf(
    CODE_NEW_CONSUMER_PENDING,
    CODE_APPLICATION_CONFIGURATION_REQUIRED,
    CODE_BINDING_UNAVAILABLE,
    CODE_USE_CASE_UNAVAILABLE,
    CODE_PRESET_UNAVAILABLE,
    CODE_BOUND_MODEL_MISSING,
    CODE_BOUND_MODEL_INCOMPATIBLE,
    CODE_PRESET_BROKEN,
    CODE_PROTECTED_MODEL_CONFLICT,
    CODE_CRITICAL_MEMORY_PRESSURE,
    CODE_SIGNER_REAUTHORIZATION_REQUIRED,
)
