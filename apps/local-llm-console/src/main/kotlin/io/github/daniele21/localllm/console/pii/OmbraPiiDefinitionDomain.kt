package io.github.daniele21.localllm.console.pii

import java.text.Normalizer
import java.util.Locale

/** Stable application-owned PII type identifier; never a model or runtime identifier. */
@JvmInline
internal value class PiiTypeId private constructor(val value: String) {
    companion object {
        private val VALUE_PATTERN = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

        fun parse(value: String): PiiTypeId {
            require(value.length <= PiiDefinitionLimits.MAX_TYPE_ID_CHARS) { "PII type ID is too long" }
            require(VALUE_PATTERN.matches(value)) { "Invalid PII type ID" }
            return PiiTypeId(value)
        }
    }
}

internal enum class PiiDefinitionSource {
    BUILT_IN,
    CUSTOM,
}

/** Validated definition supplied to OMBRA analysis composition. */
internal data class PiiDefinition(
    val id: PiiTypeId,
    val label: String,
    val definition: String,
    val example: String? = null,
    val source: PiiDefinitionSource,
) {
    init {
        require(label.isNotBlank()) { "PII label must not be blank" }
        require(definition.isNotBlank()) { "PII definition must not be blank" }
        require(codePointCount(label) <= PiiDefinitionLimits.MAX_LABEL_CODE_POINTS) {
            "PII label is too long"
        }
        require(codePointCount(definition) <= PiiDefinitionLimits.MAX_DEFINITION_CODE_POINTS) {
            "PII definition is too long"
        }
        require(example == null || codePointCount(example) <= PiiDefinitionLimits.MAX_EXAMPLE_CODE_POINTS) {
            "PII example is too long"
        }
        require(!containsUnsupportedControl(label)) { "PII label contains unsupported control characters" }
        require(!containsUnsupportedControl(definition)) {
            "PII definition contains unsupported control characters"
        }
        require(example == null || !containsUnsupportedControl(example)) {
            "PII example contains unsupported control characters"
        }
    }
}

/** Raw custom-definition input owned by the active task. */
internal data class PiiDefinitionDraft(
    val label: String,
    val definition: String,
    val example: String? = null,
)

internal enum class PiiDefinitionIssue {
    BLANK_LABEL,
    BLANK_DEFINITION,
    LABEL_TOO_LONG,
    DEFINITION_TOO_LONG,
    EXAMPLE_TOO_LONG,
    UNSUPPORTED_CONTROL_CHARACTER,
    CUSTOM_DEFINITION_LIMIT_REACHED,
}

internal data class PiiDefinitionValidation(
    val issues: Set<PiiDefinitionIssue>,
) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

internal sealed interface PiiDefinitionCreationResult {
    data class Created(val definition: PiiDefinition) : PiiDefinitionCreationResult

    data class Invalid(val validation: PiiDefinitionValidation) : PiiDefinitionCreationResult
}

/** Conservative v1 limits; OMB-3 may tighten them after measured context-budget evidence. */
internal object PiiDefinitionLimits {
    const val MAX_TYPE_ID_CHARS = 64
    const val MAX_LABEL_CODE_POINTS = 64
    const val MAX_DEFINITION_CODE_POINTS = 320
    const val MAX_EXAMPLE_CODE_POINTS = 160
    const val MAX_ACTIVE_DEFINITIONS = 12
    const val MAX_CUSTOM_DEFINITIONS = 6
}

internal object PiiDefinitionFactory {
    fun validateCustomDraft(
        draft: PiiDefinitionDraft,
        existingDefinitions: Collection<PiiDefinition>,
    ): PiiDefinitionValidation {
        val issues = linkedSetOf<PiiDefinitionIssue>()
        if (draft.label.isBlank()) issues += PiiDefinitionIssue.BLANK_LABEL
        if (draft.definition.isBlank()) issues += PiiDefinitionIssue.BLANK_DEFINITION
        if (codePointCount(draft.label) > PiiDefinitionLimits.MAX_LABEL_CODE_POINTS) {
            issues += PiiDefinitionIssue.LABEL_TOO_LONG
        }
        if (codePointCount(draft.definition) > PiiDefinitionLimits.MAX_DEFINITION_CODE_POINTS) {
            issues += PiiDefinitionIssue.DEFINITION_TOO_LONG
        }
        if (
            draft.example != null &&
                codePointCount(draft.example) > PiiDefinitionLimits.MAX_EXAMPLE_CODE_POINTS
        ) {
            issues += PiiDefinitionIssue.EXAMPLE_TOO_LONG
        }
        if (
            containsUnsupportedControl(draft.label) ||
                containsUnsupportedControl(draft.definition) ||
                (draft.example?.let(::containsUnsupportedControl) == true)
        ) {
            issues += PiiDefinitionIssue.UNSUPPORTED_CONTROL_CHARACTER
        }
        if (
            existingDefinitions.count { it.source == PiiDefinitionSource.CUSTOM } >=
                PiiDefinitionLimits.MAX_CUSTOM_DEFINITIONS ||
                existingDefinitions.size >= PiiDefinitionLimits.MAX_ACTIVE_DEFINITIONS
        ) {
            issues += PiiDefinitionIssue.CUSTOM_DEFINITION_LIMIT_REACHED
        }
        return PiiDefinitionValidation(issues)
    }

    fun createCustom(
        draft: PiiDefinitionDraft,
        existingDefinitions: Collection<PiiDefinition>,
    ): PiiDefinitionCreationResult {
        val validation = validateCustomDraft(draft, existingDefinitions)
        if (!validation.isValid) return PiiDefinitionCreationResult.Invalid(validation)

        val id = nextCustomId(draft.label, existingDefinitions.map { it.id }.toSet())
        return PiiDefinitionCreationResult.Created(
            PiiDefinition(
                id = id,
                label = draft.label.trim(),
                definition = draft.definition.trim(),
                example = draft.example?.trim()?.takeIf(String::isNotEmpty),
                source = PiiDefinitionSource.CUSTOM,
            ),
        )
    }

    private fun nextCustomId(label: String, existingIds: Set<PiiTypeId>): PiiTypeId {
        val normalized =
            Normalizer.normalize(label, Normalizer.Form.NFKD)
                .lowercase(Locale.ROOT)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "definition" }
        val prefix = "custom-"
        val maxBaseLength = PiiDefinitionLimits.MAX_TYPE_ID_CHARS - prefix.length
        val base = normalized.take(maxBaseLength).trimEnd('-').ifBlank { "definition" }
        var candidate = PiiTypeId.parse(prefix + base)
        var suffix = 2
        while (candidate in existingIds) {
            val suffixText = "-$suffix"
            val shortened = base.take(maxBaseLength - suffixText.length).trimEnd('-')
            candidate = PiiTypeId.parse(prefix + shortened + suffixText)
            suffix += 1
        }
        return candidate
    }
}

/** Immutable active definition set used by one OMBRA task. */
internal class PiiDefinitionSet private constructor(definitions: List<PiiDefinition>) {
    val definitions: List<PiiDefinition> = definitions.toList()

    val ids: Set<PiiTypeId> = this.definitions.mapTo(linkedSetOf()) { it.id }

    companion object {
        fun create(definitions: Collection<PiiDefinition>): Result<PiiDefinitionSet> = runCatching {
            require(definitions.isNotEmpty()) { "At least one PII definition is required" }
            require(definitions.size <= PiiDefinitionLimits.MAX_ACTIVE_DEFINITIONS) {
                "Too many active PII definitions"
            }
            require(
                definitions.count { it.source == PiiDefinitionSource.CUSTOM } <=
                    PiiDefinitionLimits.MAX_CUSTOM_DEFINITIONS,
            ) { "Too many custom PII definitions" }
            val duplicateIds = definitions.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
            require(duplicateIds.isEmpty()) { "Duplicate PII type IDs" }
            PiiDefinitionSet(definitions.toList())
        }
    }
}

private fun codePointCount(value: String): Int = value.codePointCount(0, value.length)

private fun containsUnsupportedControl(value: String): Boolean =
    value.any { character -> Character.isISOControl(character) }
