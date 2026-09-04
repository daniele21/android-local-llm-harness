package io.github.daniele21.localllm.console.quality

internal data class QualityModelArtifactIdentity(val modelId: String, val artifactSha256: String) {
    init {
        require(modelId.matches(Regex("[A-Za-z0-9._:-]+"))) { "Model evidence ID must be opaque and token-safe" }
        require(artifactSha256.matches(Regex("[0-9a-f]{64}"))) { "Model artifact SHA-256 must be lowercase hexadecimal" }
    }
}

internal data class QualityEvidenceSnapshot(
    val evidenceSchemaVersion: Int,
    val model: QualityModelArtifactIdentity,
    val policyVersion: Int,
    val score: QualityScore,
    val acceptance: QualityAcceptanceReport,
) {
    init {
        require(evidenceSchemaVersion > 0) { "Quality evidence schema version must be positive" }
    }
}

internal object OmbraQualityEvidence {
    fun capture(model: QualityModelArtifactIdentity, policy: QualityAcceptancePolicy, score: QualityScore): QualityEvidenceSnapshot =
        QualityEvidenceSnapshot(
            evidenceSchemaVersion = 1,
            model = model,
            policyVersion = policy.policyVersion,
            score = score,
            acceptance = OmbraQualityAcceptanceGate.evaluate(policy, score),
        )

    fun serialize(snapshot: QualityEvidenceSnapshot): String {
        val score = snapshot.score
        return buildString {
            appendLine("#evidenceSchemaVersion=${snapshot.evidenceSchemaVersion}")
            appendLine("#modelId=${snapshot.model.modelId}")
            appendLine("#modelArtifactSha256=${snapshot.model.artifactSha256}")
            appendLine("#policyVersion=${snapshot.policyVersion}")
            appendLine("#corpusSchemaVersion=${score.corpusIdentity.schemaVersion}")
            appendLine("#corpusVersion=${score.corpusIdentity.corpusVersion}")
            appendLine("#corpusSha256=${score.corpusIdentity.sha256}")
            appendLine("#accepted=${snapshot.acceptance.accepted}")
            snapshot.acceptance.failures.forEach { failure ->
                append("#failure=")
                append(failure.code.name)
                failure.typeId?.let { append(":$it") }
                appendLine()
            }
            appendLine(
                "scope\ttypeId\ttruePositives\tfalsePositives\tfalseNegatives\tprecision\trecall\tf1" +
                    "\tstructuredCompletionRate\tinvalidFindingRate\tinvalidResultRate",
            )
            appendMetricRow(
                scope = "aggregate",
                typeId = "-",
                metrics = score.aggregate,
                structuredCompletionRate = score.structuredCompletionRate,
                invalidFindingRate = score.invalidFindingRate,
                invalidResultRate = score.invalidResultRate,
            )
            score.perType.toSortedMap().forEach { (typeId, metrics) ->
                appendMetricRow(
                    scope = "type",
                    typeId = typeId,
                    metrics = metrics,
                    structuredCompletionRate = null,
                    invalidFindingRate = null,
                    invalidResultRate = null,
                )
            }
        }
    }

    private fun StringBuilder.appendMetricRow(
        scope: String,
        typeId: String,
        metrics: ExactOccurrenceMetrics,
        structuredCompletionRate: Double?,
        invalidFindingRate: Double?,
        invalidResultRate: Double?,
    ) {
        append(scope)
        append('\t')
        append(typeId)
        append('\t')
        append(metrics.counts.truePositives)
        append('\t')
        append(metrics.counts.falsePositives)
        append('\t')
        append(metrics.counts.falseNegatives)
        append('\t')
        append(metrics.precision)
        append('\t')
        append(metrics.recall)
        append('\t')
        append(metrics.f1)
        append('\t')
        append(structuredCompletionRate ?: "-")
        append('\t')
        append(invalidFindingRate ?: "-")
        append('\t')
        append(invalidResultRate ?: "-")
        appendLine()
    }
}
