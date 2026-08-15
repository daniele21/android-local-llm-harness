package io.github.daniele21.localllm.console.analysis

internal data class OmbraRawFinding(val typeId: String, val surface: String, val segmentId: String) {
    override fun toString(): String = "OmbraRawFinding(typeId=<redacted>, surface=<redacted>, segmentId=$segmentId)"
}

internal data class OmbraParsedAnalysisResult(val findings: List<OmbraRawFinding>) {
    init {
        require(findings.size <= OmbraAnalysisProtocol.MAX_FINDINGS) { "Too many parsed findings" }
    }
}

internal enum class OmbraAnalysisParseFailureCode {
    INVALID_JSON,
    INVALID_SHAPE,
    UNSUPPORTED_SCHEMA,
    TOO_MANY_FINDINGS,
    FIELD_TOO_LONG,
}

internal sealed interface OmbraAnalysisParseResult {
    data class Parsed(val result: OmbraParsedAnalysisResult) : OmbraAnalysisParseResult

    data class Rejected(val code: OmbraAnalysisParseFailureCode) : OmbraAnalysisParseResult
}

/** Converts untrusted model JSON into bounded raw candidates without granting source validity. */
internal class OmbraAnalysisResultParser(private val jsonReader: OmbraStrictJsonReader = OmbraStrictJsonReader()) {
    fun parse(input: String): OmbraAnalysisParseResult =
        try {
            OmbraAnalysisParseResult.Parsed(parseRoot(jsonReader.parse(input)))
        } catch (_: OmbraJsonException) {
            OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_JSON)
        } catch (rejected: OmbraAnalysisResultRejected) {
            OmbraAnalysisParseResult.Rejected(rejected.code)
        }

    private fun parseRoot(root: OmbraJsonValue): OmbraParsedAnalysisResult {
        val rootObject = requireObject(root)
        requireExactFields(rootObject, ROOT_FIELDS)
        val schemaVersion = requireInteger(rootObject, "schemaVersion")
        if (schemaVersion != OmbraAnalysisProtocol.OUTPUT_SCHEMA_VERSION.toLong()) {
            rejectAnalysisResult(OmbraAnalysisParseFailureCode.UNSUPPORTED_SCHEMA)
        }
        val findingValues = requireArray(rootObject, "findings")
        if (findingValues.size > OmbraAnalysisProtocol.MAX_FINDINGS) {
            rejectAnalysisResult(OmbraAnalysisParseFailureCode.TOO_MANY_FINDINGS)
        }
        return OmbraParsedAnalysisResult(findingValues.map(::parseFinding))
    }

    private fun parseFinding(value: OmbraJsonValue): OmbraRawFinding {
        val objectValue = requireObject(value)
        requireExactFields(objectValue, FINDING_FIELDS)
        val finding =
            OmbraRawFinding(
                typeId = requireNonEmptyString(objectValue, "typeId"),
                surface = requireNonEmptyString(objectValue, "surface"),
                segmentId = requireNonEmptyString(objectValue, "segmentId"),
            )
        if (!isFieldLengthValid(finding)) rejectAnalysisResult(OmbraAnalysisParseFailureCode.FIELD_TOO_LONG)
        return finding
    }

    private fun requireObject(value: OmbraJsonValue): OmbraJsonValue.ObjectValue =
        value as? OmbraJsonValue.ObjectValue ?: rejectAnalysisResult(OmbraAnalysisParseFailureCode.INVALID_SHAPE)

    private fun requireExactFields(value: OmbraJsonValue.ObjectValue, expected: Set<String>) {
        if (value.fields.keys != expected) rejectAnalysisResult(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
    }

    private fun requireInteger(value: OmbraJsonValue.ObjectValue, field: String): Long =
        (value.fields[field] as? OmbraJsonValue.IntegerValue)?.value
            ?: rejectAnalysisResult(OmbraAnalysisParseFailureCode.INVALID_SHAPE)

    private fun requireArray(value: OmbraJsonValue.ObjectValue, field: String): List<OmbraJsonValue> =
        (value.fields[field] as? OmbraJsonValue.ArrayValue)?.values
            ?: rejectAnalysisResult(OmbraAnalysisParseFailureCode.INVALID_SHAPE)

    private fun requireNonEmptyString(value: OmbraJsonValue.ObjectValue, field: String): String {
        val text =
            (value.fields[field] as? OmbraJsonValue.StringValue)?.value
                ?: rejectAnalysisResult(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
        if (text.isEmpty()) rejectAnalysisResult(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
        return text
    }

    private fun isFieldLengthValid(finding: OmbraRawFinding): Boolean = finding.typeId.length <= MAX_TYPE_ID_CHARACTERS &&
        finding.surface.codePointCount(0, finding.surface.length) <= ValidatedFinding.MAX_SURFACE_CODE_POINTS &&
        finding.segmentId.length <= MAX_SEGMENT_ID_CHARACTERS

    private companion object {
        val ROOT_FIELDS = setOf("schemaVersion", "findings")
        val FINDING_FIELDS = setOf("typeId", "surface", "segmentId")
        const val MAX_TYPE_ID_CHARACTERS = 64
        const val MAX_SEGMENT_ID_CHARACTERS = 32
    }
}

private class OmbraAnalysisResultRejected(val code: OmbraAnalysisParseFailureCode) : RuntimeException()

private fun rejectAnalysisResult(code: OmbraAnalysisParseFailureCode): Nothing = throw OmbraAnalysisResultRejected(code)
