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
    fun parse(input: String): OmbraAnalysisParseResult {
        val root =
            try {
                jsonReader.parse(input)
            } catch (_: OmbraJsonException) {
                return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_JSON)
            }
        val rootObject =
            root as? OmbraJsonValue.ObjectValue
                ?: return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
        if (rootObject.fields.keys != ROOT_FIELDS) {
            return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
        }
        val schemaVersion =
            (rootObject.fields["schemaVersion"] as? OmbraJsonValue.IntegerValue)?.value
                ?: return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
        if (schemaVersion != OmbraAnalysisProtocol.OUTPUT_SCHEMA_VERSION.toLong()) {
            return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.UNSUPPORTED_SCHEMA)
        }
        val findingsValue =
            rootObject.fields["findings"] as? OmbraJsonValue.ArrayValue
                ?: return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
        if (findingsValue.values.size > OmbraAnalysisProtocol.MAX_FINDINGS) {
            return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.TOO_MANY_FINDINGS)
        }

        val findings = ArrayList<OmbraRawFinding>(findingsValue.values.size)
        findingsValue.values.forEach { value ->
            val finding =
                parseFinding(value)
                    ?: return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_SHAPE)
            if (!isFieldLengthValid(finding)) {
                return OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.FIELD_TOO_LONG)
            }
            findings += finding
        }
        return OmbraAnalysisParseResult.Parsed(OmbraParsedAnalysisResult(findings))
    }

    private fun parseFinding(value: OmbraJsonValue): OmbraRawFinding? {
        val objectValue = value as? OmbraJsonValue.ObjectValue ?: return null
        if (objectValue.fields.keys != FINDING_FIELDS) return null
        val typeId = (objectValue.fields["typeId"] as? OmbraJsonValue.StringValue)?.value ?: return null
        val surface = (objectValue.fields["surface"] as? OmbraJsonValue.StringValue)?.value ?: return null
        val segmentId = (objectValue.fields["segmentId"] as? OmbraJsonValue.StringValue)?.value ?: return null
        if (typeId.isEmpty() || surface.isEmpty() || segmentId.isEmpty()) return null
        return OmbraRawFinding(typeId = typeId, surface = surface, segmentId = segmentId)
    }

    private fun isFieldLengthValid(finding: OmbraRawFinding): Boolean =
        finding.typeId.length <= MAX_TYPE_ID_CHARACTERS &&
            finding.surface.codePointCount(0, finding.surface.length) <= ValidatedFinding.MAX_SURFACE_CODE_POINTS &&
            finding.segmentId.length <= MAX_SEGMENT_ID_CHARACTERS

    private companion object {
        val ROOT_FIELDS = setOf("schemaVersion", "findings")
        val FINDING_FIELDS = setOf("typeId", "surface", "segmentId")
        const val MAX_TYPE_ID_CHARACTERS = 64
        const val MAX_SEGMENT_ID_CHARACTERS = 32
    }
}
