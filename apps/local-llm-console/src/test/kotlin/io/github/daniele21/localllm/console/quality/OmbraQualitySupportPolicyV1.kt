package io.github.daniele21.localllm.console.quality

/**
 * Release-support acceptance policy registered before any supported Qwen3.5 corpus run.
 *
 * The corpus has five positive exact occurrences for each required category. With that
 * discrete support, the recall thresholds below require all five occurrences for every
 * category and all 35 occurrences in aggregate. False positives remain slightly more
 * tolerant because OMBRA requires explicit human review before redaction/export; false
 * negatives are not reviewable because they never enter the review surface.
 */
internal object OmbraQualitySupportPolicyV1 {
    val policy =
        QualityAcceptancePolicy(
            policyVersion = 1,
            corpusIdentity =
            QualityCorpusIdentity(
                schemaVersion = 1,
                corpusVersion = "ombra-pii-synthetic-v2",
                sha256 = "a04f79dec42ee4208e4db27512664cc20f66cc863fd80ae4fcdc1019a2f37a5f",
            ),
            requiredTypeIds =
            setOf(
                "full-name",
                "email",
                "telephone",
                "postal-address",
                "italian-tax-code",
                "iban",
                "custom-1",
            ),
            thresholds =
            QualityThresholds(
                minAggregatePrecision = 0.90,
                minAggregateRecall = 0.98,
                minAggregateF1 = 0.94,
                minPerTypePrecision = 0.80,
                minPerTypeRecall = 0.90,
                minPerTypeF1 = 0.85,
                minStructuredCompletionRate = 0.98,
                maxInvalidFindingRate = 0.02,
                maxInvalidResultRate = 0.0,
            ),
        )
}
