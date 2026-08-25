package io.github.daniele21.localllm.contracts

/**
 * Bounded structured task context supplied by an authorized consumer.
 *
 * This is deliberately not a free-form system prompt. The runtime owns prompt composition and must
 * treat these values as untrusted data rather than instructions.
 */
data class TaskDefinition(
    val id: String,
    val description: String,
    val example: String? = null,
) {
    init {
        require(id.matches(ID_PATTERN)) { "Task definition ID is invalid" }
        require(id.length <= TaskDefinitionLimits.MAX_ID_CHARACTERS) { "Task definition ID is too long" }
        require(description.isNotBlank()) { "Task definition description must not be blank" }
        require(description.length <= TaskDefinitionLimits.MAX_DESCRIPTION_CHARACTERS) {
            "Task definition description is too long"
        }
        require(example == null || example.length <= TaskDefinitionLimits.MAX_EXAMPLE_CHARACTERS) {
            "Task definition example is too long"
        }
        require(!containsUnsupportedControl(description)) { "Task definition description contains unsupported control characters" }
        require(example == null || !containsUnsupportedControl(example)) {
            "Task definition example contains unsupported control characters"
        }
    }

    override fun toString(): String = "TaskDefinition(id=$id, description=<redacted>, example=<redacted>)"

    private companion object {
        val ID_PATTERN = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
    }
}

object TaskDefinitionLimits {
    const val MAX_DEFINITIONS = 24
    const val MAX_ID_CHARACTERS = 64
    const val MAX_DESCRIPTION_CHARACTERS = 320
    const val MAX_EXAMPLE_CHARACTERS = 160
    const val MAX_AGGREGATE_CHARACTERS = 12_288

    fun validate(definitions: List<TaskDefinition>) {
        require(definitions.size <= MAX_DEFINITIONS) { "Too many task definitions" }
        require(definitions.map(TaskDefinition::id).distinct().size == definitions.size) {
            "Task definition IDs must be unique"
        }
        require(
            definitions.sumOf { definition ->
                definition.id.length + definition.description.length + (definition.example?.length ?: 0)
            } <= MAX_AGGREGATE_CHARACTERS,
        ) { "Task definitions exceed the aggregate size limit" }
    }
}

private fun containsUnsupportedControl(value: String): Boolean = value.any(Character::isISOControl)
