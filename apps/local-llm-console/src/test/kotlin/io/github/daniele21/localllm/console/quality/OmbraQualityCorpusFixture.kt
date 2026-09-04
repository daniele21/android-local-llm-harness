package io.github.daniele21.localllm.console.quality

import java.security.MessageDigest

internal data class QualityCorpusIdentity(val schemaVersion: Int, val corpusVersion: String, val sha256: String) {
    init {
        require(schemaVersion > 0) { "Corpus schema version must be positive" }
        require(corpusVersion.isNotBlank()) { "Corpus version must not be blank" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Corpus SHA-256 must be lowercase hexadecimal" }
    }
}

internal enum class QualityCaseTag {
    POSITIVE,
    NEGATIVE,
    BUILT_IN,
    CUSTOM,
    NO_PII,
    REPEATED,
    OVERLAP,
    NEAR_MISS,
    INJECTION,
    ITALIAN_TEXT,
}

internal data class QualitySegment(val id: String, val text: String) {
    init {
        require(id.isNotBlank()) { "Quality segment ID must not be blank" }
        require(text.startsWith(SYNTHETIC_MARKER)) { "Quality text must be explicitly marked as synthetic" }
    }
}

internal data class QualityOccurrence(
    val typeId: String,
    val segmentId: String,
    val startOffset: Int,
    val endOffset: Int,
    val surface: String,
) {
    init {
        require(typeId.isNotBlank()) { "Occurrence type ID must not be blank" }
        require(segmentId.isNotBlank()) { "Occurrence segment ID must not be blank" }
        require(startOffset >= 0 && endOffset > startOffset) { "Occurrence offsets must form a non-empty range" }
        require(surface.isNotEmpty()) { "Occurrence surface must not be empty" }
    }
}

internal data class QualityCase(
    val id: String,
    val tags: Set<QualityCaseTag>,
    val selectedTypeIds: Set<String>,
    val segments: List<QualitySegment>,
    val expectedOccurrences: List<QualityOccurrence>,
) {
    init {
        require(id.isNotBlank()) { "Quality case ID must not be blank" }
        require(tags.isNotEmpty()) { "Quality case tags must not be empty" }
        require(selectedTypeIds.isNotEmpty()) { "Selected quality types must not be empty" }
        require(segments.isNotEmpty()) { "Quality case must contain a segment" }
        require(segments.map(QualitySegment::id).toSet().size == segments.size) { "Segment IDs must be unique within a case" }
        require(expectedOccurrences.all { it.typeId in selectedTypeIds }) { "Gold occurrence type must be selected" }
        require((QualityCaseTag.POSITIVE in tags) xor (QualityCaseTag.NEGATIVE in tags)) {
            "Quality case must be exactly one of positive or negative"
        }
        if (QualityCaseTag.POSITIVE in tags) {
            require(expectedOccurrences.isNotEmpty()) { "Positive quality cases must contain gold occurrences" }
        }
        if (QualityCaseTag.NEGATIVE in tags) {
            require(expectedOccurrences.isEmpty()) { "Negative quality cases cannot contain gold occurrences" }
        }
        val segmentById = segments.associateBy(QualitySegment::id)
        expectedOccurrences.forEach { occurrence ->
            val segment = requireNotNull(segmentById[occurrence.segmentId]) { "Gold occurrence references an unknown segment" }
            require(occurrence.endOffset <= segment.text.length) { "Gold occurrence exceeds its segment" }
            require(segment.text.substring(occurrence.startOffset, occurrence.endOffset) == occurrence.surface) {
                "Gold occurrence surface must match its exact source range"
            }
        }
        if (QualityCaseTag.NO_PII in tags) {
            require(expectedOccurrences.isEmpty()) { "NO_PII cases cannot contain gold occurrences" }
        }
    }
}

internal data class QualityCorpus(
    val identity: QualityCorpusIdentity,
    val builtInDefinitionSetVersion: Int,
    val customTypeIds: Set<String>,
    val cases: List<QualityCase>,
) {
    init {
        require(builtInDefinitionSetVersion > 0) { "Built-in definition version must be positive" }
        require(cases.isNotEmpty()) { "Quality corpus must not be empty" }
        require(cases.map(QualityCase::id).toSet().size == cases.size) { "Quality case IDs must be unique" }
        require(customTypeIds.all { it.startsWith("custom-") }) { "Custom type IDs must be content-free ordinals" }
    }
}

internal object OmbraSyntheticQualityCorpus {
    const val RESOURCE_PATH = "ombra-quality/v2/corpus.tsv"
    const val EXPECTED_SHA256 = "a04f79dec42ee4208e4db27512664cc20f66cc863fd80ae4fcdc1019a2f37a5f"

    fun load(): QualityCorpus {
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream(RESOURCE_PATH)) {
            "Missing OMBRA synthetic quality corpus"
        }.use { it.readBytes() }
        val actualSha256 = sha256(bytes)
        require(actualSha256 == EXPECTED_SHA256) { "OMBRA synthetic quality corpus hash changed without review" }

        val lines = bytes.toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).toList()
        val metadata = lines.filter { it.startsWith('#') && !it.startsWith("#caseId") }.associate { line ->
            val separator = line.indexOf('=')
            require(separator > 1) { "Malformed quality corpus metadata" }
            line.substring(1, separator) to line.substring(separator + 1)
        }
        require(metadata.getValue("synthetic") == "true") { "Quality corpus must declare synthetic-only content" }
        require(metadata.getValue("syntheticPolicy").isNotBlank()) { "Quality corpus must document its synthetic-value policy" }

        val cases = lines.filterNot { it.startsWith('#') }.map(::parseCase)
        val customMetadata =
            metadata.keys
                .mapNotNull { key ->
                    CUSTOM_METADATA_PATTERN.matchEntire(key)?.let { match ->
                        match.groupValues[1] to match.groupValues[2]
                    }
                }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        require(customMetadata.values.all { it.toSet() == CUSTOM_METADATA_FIELDS }) {
            "Every custom quality type must have a label and definition"
        }
        return QualityCorpus(
            identity =
            QualityCorpusIdentity(
                schemaVersion = metadata.getValue("schemaVersion").toInt(),
                corpusVersion = metadata.getValue("corpusVersion"),
                sha256 = actualSha256,
            ),
            builtInDefinitionSetVersion = metadata.getValue("builtInDefinitionSetVersion").toInt(),
            customTypeIds = customMetadata.keys,
            cases = cases,
        )
    }

    private fun parseCase(line: String): QualityCase {
        val columns = line.split('\t')
        require(columns.size == COLUMN_COUNT) { "Malformed quality corpus row" }
        val segment = QualitySegment(id = columns[3], text = columns[4])
        val occurrences =
            if (columns[5] == "-") {
                emptyList()
            } else {
                columns[5].split(';').map { encoded -> parseOccurrence(encoded, segment) }
            }
        return QualityCase(
            id = columns[0],
            tags = columns[1].split(',').mapTo(linkedSetOf()) { QualityCaseTag.valueOf(it) },
            selectedTypeIds = columns[2].split(',').toCollection(linkedSetOf()),
            segments = listOf(segment),
            expectedOccurrences = occurrences,
        )
    }

    private fun parseOccurrence(encoded: String, segment: QualitySegment): QualityOccurrence {
        val fields = encoded.split(':')
        require(fields.size == 3) { "Malformed gold occurrence" }
        val startOffset = fields[1].toInt()
        val endOffset = fields[2].toInt()
        require(startOffset >= 0 && endOffset <= segment.text.length && endOffset > startOffset) {
            "Gold occurrence range is invalid"
        }
        return QualityOccurrence(
            typeId = fields[0],
            segmentId = segment.id,
            startOffset = startOffset,
            endOffset = endOffset,
            surface = segment.text.substring(startOffset, endOffset),
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private val CUSTOM_METADATA_PATTERN = Regex("custom\\.([a-z0-9-]+)\\.(label|definition)")
    private val CUSTOM_METADATA_FIELDS = setOf("label", "definition")
    private const val COLUMN_COUNT = 6
}

internal const val SYNTHETIC_MARKER = "[DATI SINTETICI]"
