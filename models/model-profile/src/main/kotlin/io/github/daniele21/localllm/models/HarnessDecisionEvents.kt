package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.UseCaseId

@JvmInline
value class HarnessDecisionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Decision ID must not be blank" }
        require(value.length <= MAX_DECISION_ID_LENGTH) { "Decision ID is too long" }
    }
}

enum class HarnessDecisionCategory {
    ACTION_REQUIRED,
    WARNING,
    INFORMATION,
    COMPLETED,
}

enum class HarnessDecisionAction {
    NONE,
    CONFIGURE_APPLICATION,
    CONFIGURE_USE_CASE,
    REPAIR_PRESET,
    INSPECT_MODEL_CONFLICT,
    REVIEW_SECURITY,
    REVIEW_MEMORY_PRESSURE,
}

data class HarnessDecisionContext(
    val applicationId: ApplicationId? = null,
    val useCaseId: UseCaseId? = null,
    val presetId: InferencePresetId? = null,
    val presetRevision: Int? = null,
    val bindingRevision: Int? = null,
) {
    init {
        require(presetRevision == null || presetRevision > 0) { "Preset revision must be positive" }
        require(bindingRevision == null || bindingRevision > 0) { "Binding revision must be positive" }
        require(presetRevision == null || presetId != null) { "Preset revision requires a preset ID" }
    }
}

data class HarnessDecisionEvent(
    val decisionId: HarnessDecisionId,
    val category: HarnessDecisionCategory,
    val code: String,
    val title: String,
    val summary: String,
    val context: HarnessDecisionContext = HarnessDecisionContext(),
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long? = null,
    val dedupeKey: String,
    val action: HarnessDecisionAction = HarnessDecisionAction.NONE,
    val evidence: Map<String, String> = emptyMap(),
) {
    init {
        require(DECISION_CODE_PATTERN.matches(code)) { "Decision code must be a stable uppercase identifier" }
        require(title.isNotBlank() && title.length <= MAX_DECISION_TITLE_LENGTH) {
            "Decision title must be non-blank and bounded"
        }
        require(summary.isNotBlank() && summary.length <= MAX_DECISION_SUMMARY_LENGTH) {
            "Decision summary must be non-blank and bounded"
        }
        require(createdAtEpochMs >= 0) { "Decision creation timestamp must not be negative" }
        require(resolvedAtEpochMs == null || resolvedAtEpochMs >= createdAtEpochMs) {
            "Decision resolution timestamp must not precede creation"
        }
        require(dedupeKey.isNotBlank() && dedupeKey.length <= MAX_DEDUPE_KEY_LENGTH) {
            "Decision dedupe key must be non-blank and bounded"
        }
        require(evidence.size <= MAX_EVIDENCE_FIELDS) { "Decision evidence has too many fields" }
        require(evidence.keys.all { EVIDENCE_KEY_PATTERN.matches(it) }) { "Decision evidence keys must be stable identifiers" }
        require(evidence.values.all { it.length <= MAX_EVIDENCE_VALUE_LENGTH && '\u0000' !in it }) {
            "Decision evidence values must be bounded and must not contain NUL"
        }
        require(category != HarnessDecisionCategory.ACTION_REQUIRED || action != HarnessDecisionAction.NONE) {
            "Action-required decision must provide a recovery action"
        }
    }

    val isResolved: Boolean
        get() = resolvedAtEpochMs != null
}

interface HarnessDecisionRepository {
    fun upsert(event: HarnessDecisionEvent)

    fun unresolved(limit: Int = 100): List<HarnessDecisionEvent>

    fun recent(limit: Int = 100): List<HarnessDecisionEvent>

    fun find(decisionId: HarnessDecisionId): HarnessDecisionEvent?
}

object NoOpHarnessDecisionRepository : HarnessDecisionRepository {
    override fun upsert(event: HarnessDecisionEvent) = Unit

    override fun unresolved(limit: Int): List<HarnessDecisionEvent> = emptyList()

    override fun recent(limit: Int): List<HarnessDecisionEvent> = emptyList()

    override fun find(decisionId: HarnessDecisionId): HarnessDecisionEvent? = null
}

private const val MAX_DECISION_ID_LENGTH = 128
private const val MAX_DECISION_TITLE_LENGTH = 120
private const val MAX_DECISION_SUMMARY_LENGTH = 512
private const val MAX_DEDUPE_KEY_LENGTH = 256
private const val MAX_EVIDENCE_FIELDS = 16
private const val MAX_EVIDENCE_VALUE_LENGTH = 256
private val DECISION_CODE_PATTERN = Regex("[A-Z][A-Z0-9_]{2,63}")
private val EVIDENCE_KEY_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
