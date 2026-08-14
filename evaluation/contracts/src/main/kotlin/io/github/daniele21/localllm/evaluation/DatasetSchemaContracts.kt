package io.github.daniele21.localllm.evaluation

const val EVALUATION_DATASET_MANIFEST_SCHEMA_VERSION = 1
const val EVALUATION_DATASET_CASE_SCHEMA_VERSION = 1

enum class EvaluationDatasetOrigin {
    BUILT_IN,
    USER_IMPORTED,
}

enum class EvaluationMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

enum class EvaluationExpectedAnswerKind {
    TEXT,
    NUMBER,
    LABEL,
    JSON,
}

enum class EvaluationResponseFormat {
    TEXT,
    JSON,
}

data class EvaluationDatasetCategoryDefinition(val id: EvaluationCategoryId, val displayName: String, val weight: Double? = null) {
    init {
        validateStableText(displayName, "Dataset category display name", MAX_DATASET_DISPLAY_TEXT)
        require(weight == null || (weight.isFinite() && weight > 0.0)) {
            "Dataset category weight must be finite and positive"
        }
    }
}

data class EvaluationDatasetPresetDefinition(val id: String, val orderedCaseIds: List<EvaluationCaseId>) {
    init {
        validateStableText(id, "Dataset preset ID", MAX_DATASET_ID_TEXT)
        require(orderedCaseIds.isNotEmpty()) { "Dataset preset must contain at least one case" }
        require(orderedCaseIds.distinct().size == orderedCaseIds.size) {
            "Dataset preset case IDs must be unique"
        }
    }
}

data class EvaluationDatasetManifestV1(
    val schemaVersion: Int = EVALUATION_DATASET_MANIFEST_SCHEMA_VERSION,
    val caseSchemaVersion: Int = EVALUATION_DATASET_CASE_SCHEMA_VERSION,
    val datasetId: EvaluationDatasetId,
    val version: EvaluationDatasetVersion,
    val displayName: String,
    val description: String? = null,
    val origin: EvaluationDatasetOrigin,
    val caseCount: Int,
    val contentDigest: EvaluationDatasetDigest,
    val categories: List<EvaluationDatasetCategoryDefinition>,
    val presets: List<EvaluationDatasetPresetDefinition> = emptyList(),
) {
    init {
        require(schemaVersion == EVALUATION_DATASET_MANIFEST_SCHEMA_VERSION) {
            "Unsupported dataset manifest schema version: $schemaVersion"
        }
        require(caseSchemaVersion == EVALUATION_DATASET_CASE_SCHEMA_VERSION) {
            "Unsupported dataset case schema version: $caseSchemaVersion"
        }
        validateStableText(displayName, "Dataset display name", MAX_DATASET_DISPLAY_TEXT)
        validateOptionalStableText(description, "Dataset description", MAX_DATASET_DESCRIPTION_TEXT)
        require(caseCount > 0) { "Dataset case count must be positive" }
        require(categories.isNotEmpty()) { "Dataset must declare at least one category" }
        require(categories.map { it.id }.distinct().size == categories.size) {
            "Dataset category IDs must be unique"
        }
        require(presets.map { it.id }.distinct().size == presets.size) {
            "Dataset preset IDs must be unique"
        }
    }

    val identity: EvaluationDatasetIdentity
        get() = EvaluationDatasetIdentity(
            id = datasetId,
            version = version,
            digest = contentDigest,
        )
}

data class EvaluationCaseMessage(val role: EvaluationMessageRole, val content: String) {
    init {
        require(content.isNotEmpty()) { "Evaluation case message content must not be empty" }
        require('\u0000' !in content) { "Evaluation case message content must not contain NUL" }
        require(content.length <= MAX_CASE_CONTENT_TEXT) {
            "Evaluation case message content must not exceed $MAX_CASE_CONTENT_TEXT characters"
        }
    }
}

data class EvaluationExpectedAnswer(val kind: EvaluationExpectedAnswerKind, val value: String) {
    init {
        require(value.isNotEmpty()) { "Evaluation expected answer must not be empty" }
        require('\u0000' !in value) { "Evaluation expected answer must not contain NUL" }
        require(value.length <= MAX_CASE_EXPECTED_TEXT) {
            "Evaluation expected answer must not exceed $MAX_CASE_EXPECTED_TEXT characters"
        }
    }
}

data class EvaluationCaseOutputContract(
    val responseFormat: EvaluationResponseFormat = EvaluationResponseFormat.TEXT,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
) {
    init {
        require(maxOutputTokens == null || maxOutputTokens > 0) {
            "Case max output tokens must be positive when declared"
        }
        require(stopSequences.size <= MAX_STOP_SEQUENCES) {
            "Case stop sequences must not exceed $MAX_STOP_SEQUENCES entries"
        }
        stopSequences.forEach { value ->
            require(value.isNotEmpty()) { "Case stop sequence must not be empty" }
            require('\u0000' !in value) { "Case stop sequence must not contain NUL" }
            require(value.length <= MAX_STOP_SEQUENCE_TEXT) {
                "Case stop sequence must not exceed $MAX_STOP_SEQUENCE_TEXT characters"
            }
        }
        require(stopSequences.distinct().size == stopSequences.size) {
            "Case stop sequences must be unique"
        }
    }
}

data class EvaluationDatasetCaseV1(
    val schemaVersion: Int = EVALUATION_DATASET_CASE_SCHEMA_VERSION,
    val id: EvaluationCaseId,
    val categoryId: EvaluationCategoryId,
    val messages: List<EvaluationCaseMessage>,
    val expected: EvaluationExpectedAnswer,
    val evaluator: EvaluatorSpec,
    val output: EvaluationCaseOutputContract = EvaluationCaseOutputContract(),
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(schemaVersion == EVALUATION_DATASET_CASE_SCHEMA_VERSION) {
            "Unsupported evaluation case schema version: $schemaVersion"
        }
        require(messages.isNotEmpty()) { "Evaluation case must contain at least one message" }
        require(messages.any { it.role == EvaluationMessageRole.USER }) {
            "Evaluation case must contain at least one user message"
        }
        require(metadata.size <= MAX_CASE_METADATA_ENTRIES) {
            "Evaluation case metadata must not exceed $MAX_CASE_METADATA_ENTRIES entries"
        }
        metadata.forEach { (key, value) ->
            validateStableText(key, "Evaluation case metadata key", MAX_CASE_METADATA_KEY_TEXT)
            require('\u0000' !in value) { "Evaluation case metadata value must not contain NUL" }
            require(value.length <= MAX_CASE_METADATA_VALUE_TEXT) {
                "Evaluation case metadata value must not exceed $MAX_CASE_METADATA_VALUE_TEXT characters"
            }
        }
    }
}

private const val MAX_DATASET_ID_TEXT = 96
private const val MAX_DATASET_DISPLAY_TEXT = 160
private const val MAX_DATASET_DESCRIPTION_TEXT = 2_048
private const val MAX_CASE_CONTENT_TEXT = 131_072
private const val MAX_CASE_EXPECTED_TEXT = 65_536
private const val MAX_STOP_SEQUENCES = 16
private const val MAX_STOP_SEQUENCE_TEXT = 256
private const val MAX_CASE_METADATA_ENTRIES = 32
private const val MAX_CASE_METADATA_KEY_TEXT = 64
private const val MAX_CASE_METADATA_VALUE_TEXT = 1_024
