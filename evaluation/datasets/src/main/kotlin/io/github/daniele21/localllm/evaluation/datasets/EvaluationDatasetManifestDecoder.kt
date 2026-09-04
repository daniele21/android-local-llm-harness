package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetPresetDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion

internal object EvaluationDatasetManifestDecoder {
    fun decode(source: String): EvaluationDatasetManifestV1 {
        val root = StrictJsonParser.parseObject(source)
        requireFields(
            root,
            required = setOf(
                "schemaVersion",
                "caseSchemaVersion",
                "datasetId",
                "version",
                "displayName",
                "origin",
                "caseCount",
                "contentDigest",
                "categories",
            ),
            optional = setOf("description", "presets"),
            lineNumber = MANIFEST_LINE,
        )
        return EvaluationDatasetManifestV1(
            schemaVersion = root.requireInt("schemaVersion", MANIFEST_LINE),
            caseSchemaVersion = root.requireInt("caseSchemaVersion", MANIFEST_LINE),
            datasetId = EvaluationDatasetId(root.requireString("datasetId", MANIFEST_LINE)),
            version = EvaluationDatasetVersion(root.requireString("version", MANIFEST_LINE)),
            displayName = root.requireString("displayName", MANIFEST_LINE),
            description = root.optionalString("description"),
            origin = decodeOrigin(root.requireString("origin", MANIFEST_LINE)),
            caseCount = root.requireInt("caseCount", MANIFEST_LINE),
            contentDigest = EvaluationDatasetDigest(root.requireString("contentDigest", MANIFEST_LINE)),
            categories = decodeCategories(root.requireArray("categories", MANIFEST_LINE)),
            presets = root.optionalArray("presets", MANIFEST_LINE)?.let(::decodePresets).orEmpty(),
        )
    }

    private fun decodeCategories(array: JsonValue.ArrayValue): List<EvaluationDatasetCategoryDefinition> = array.values.map { value ->
        val category = value as? JsonValue.ObjectValue
            ?: datasetParseFailure(MANIFEST_LINE, DatasetParseErrorCode.INVALID_FIELD)
        requireFields(
            category,
            required = setOf("id", "displayName"),
            optional = setOf("weight"),
            lineNumber = MANIFEST_LINE,
        )
        EvaluationDatasetCategoryDefinition(
            id = EvaluationCategoryId(category.requireString("id", MANIFEST_LINE)),
            displayName = category.requireString("displayName", MANIFEST_LINE),
            weight = category.optionalDouble("weight"),
        )
    }

    private fun decodePresets(array: JsonValue.ArrayValue): List<EvaluationDatasetPresetDefinition> = array.values.map { value ->
        val preset = value as? JsonValue.ObjectValue
            ?: datasetParseFailure(MANIFEST_LINE, DatasetParseErrorCode.INVALID_FIELD)
        requireFields(
            preset,
            required = setOf("id", "orderedCaseIds"),
            optional = emptySet(),
            lineNumber = MANIFEST_LINE,
        )
        val caseIds = preset.requireArray("orderedCaseIds", MANIFEST_LINE).values.map { caseId ->
            EvaluationCaseId(
                (caseId as? JsonValue.StringValue)?.value
                    ?: datasetParseFailure(MANIFEST_LINE, DatasetParseErrorCode.INVALID_FIELD),
            )
        }
        EvaluationDatasetPresetDefinition(
            id = preset.requireString("id", MANIFEST_LINE),
            orderedCaseIds = caseIds,
        )
    }

    private fun decodeOrigin(value: String): EvaluationDatasetOrigin = runCatching {
        EvaluationDatasetOrigin.valueOf(value)
    }.getOrElse {
        datasetParseFailure(MANIFEST_LINE, DatasetParseErrorCode.INVALID_FIELD)
    }

    private fun JsonValue.ObjectValue.optionalString(name: String): String? {
        val value = fields[name] ?: return null
        return (value as? JsonValue.StringValue)?.value
            ?: datasetParseFailure(MANIFEST_LINE, DatasetParseErrorCode.INVALID_FIELD)
    }

    private fun JsonValue.ObjectValue.optionalDouble(name: String): Double? {
        val value = fields[name] ?: return null
        return (value as? JsonValue.NumberValue)?.value?.toDouble()
            ?: datasetParseFailure(MANIFEST_LINE, DatasetParseErrorCode.INVALID_FIELD)
    }

    private const val MANIFEST_LINE = 1
}
