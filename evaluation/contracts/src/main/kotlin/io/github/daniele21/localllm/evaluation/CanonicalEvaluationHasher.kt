package io.github.daniele21.localllm.evaluation

import java.security.MessageDigest

object CanonicalEvaluationHasher {
    fun sampleSetDigest(orderedCaseIds: List<EvaluationCaseId>): SampleSetDigest {
        require(orderedCaseIds.isNotEmpty()) { "Cannot hash an empty sample set" }
        val canonical = CanonicalBuilder("sample-set-v1")
            .strings("caseIds", orderedCaseIds.map { it.value })
            .build()
        return SampleSetDigest(sha256(canonical))
    }

    fun evaluatorSetDigest(caseEvaluators: List<CaseEvaluatorIdentity>): EvaluatorSetDigest {
        require(caseEvaluators.isNotEmpty()) { "Cannot hash an empty evaluator set" }
        val canonical = CanonicalBuilder("evaluator-set-v1")
            .number("count", caseEvaluators.size.toLong())
            .apply {
                caseEvaluators.forEachIndexed { index, identity ->
                    text("case[$index].id", identity.caseId.value)
                    text("case[$index].type", identity.evaluator.type.name)
                    number("case[$index].version", identity.evaluator.version.value.toLong())
                    stringMap("case[$index].parameters", identity.evaluator.parameters)
                }
            }
            .build()
        return EvaluatorSetDigest(sha256(canonical))
    }

    fun caseExecutionSemanticsDigest(
        cases: List<CaseExecutionSemanticIdentity>,
    ): CaseExecutionSemanticsDigest {
        require(cases.isNotEmpty()) { "Cannot hash empty case execution semantics" }
        val canonical = CanonicalBuilder("case-execution-semantics-v1")
            .number("count", cases.size.toLong())
            .apply {
                cases.forEachIndexed { index, identity ->
                    text("case[$index].id", identity.caseId.value)
                    text("case[$index].outputConstraintDigest", identity.outputConstraintDigest)
                }
            }
            .build()
        return CaseExecutionSemanticsDigest(sha256(canonical))
    }

    fun semanticExecutionFingerprint(
        execution: EvaluationSemanticExecution,
    ): EvaluationSemanticExecutionFingerprint {
        val canonical = CanonicalBuilder("semantic-execution-v1")
            .number("semanticsVersion", execution.semanticsVersion.toLong())
            .text("profileId", execution.profile.id.value)
            .number("profileVersion", execution.profile.version.toLong())
            .text("backendRevision", execution.backendRevision)
            .number("contextSize", execution.contextSize.toLong())
            .nullableText("presetId", execution.preset?.id?.value)
            .nullableNumber("presetVersion", execution.preset?.version?.toLong())
            .text("thinkingMode", execution.thinkingMode.name)
            .number("temperatureBits", execution.temperature.toRawBits().toLong())
            .number("topPBits", execution.topP.toRawBits().toLong())
            .number("topK", execution.topK.toLong())
            .number("minPBits", execution.minP.toRawBits().toLong())
            .number("presencePenaltyBits", execution.presencePenalty.toRawBits().toLong())
            .number("repeatPenaltyBits", execution.repeatPenalty.toRawBits().toLong())
            .number("repeatLastN", execution.repeatLastN.toLong())
            .text("seedPolicy", execution.seedPolicy.name)
            .number("effectiveSeed", execution.effectiveSeed)
            .number("maxOutputTokens", execution.maxOutputTokens.toLong())
            .text("chatTemplateId", execution.chatTemplateId)
            .text("chatTemplateSource", execution.chatTemplateSource.name)
            .nullableText("systemPromptVersion", execution.systemPromptVersion)
            .text("caseExecutionSemanticsDigest", execution.caseExecutionSemanticsDigest.sha256)
            .build()
        return EvaluationSemanticExecutionFingerprint(sha256(canonical))
    }

    internal fun runFingerprint(identity: EvaluationRunIdentityUnchecked): EvaluationRunFingerprint {
        val environment = identity.runtimeEnvironment
        val canonical = CanonicalBuilder("evaluation-run-v1")
            .text("modelDigest", identity.model.artifactDigest.sha256)
            .text("modelProfileId", identity.model.modelProfileId)
            .nullableText("modelTier", identity.model.tier)
            .nullableText("modelQuantization", identity.model.quantization)
            .text("datasetId", identity.dataset.id.value)
            .text("datasetVersion", identity.dataset.version.value)
            .text("datasetDigest", identity.dataset.digest.sha256)
            .text("sampleSetDigest", identity.sampleSetDigest.sha256)
            .text("samplingPolicyId", identity.samplingPolicy.id.value)
            .number("samplingPolicyVersion", identity.samplingPolicy.version.toLong())
            .number("samplingSeed", identity.samplingSeed)
            .text("evaluatorSetDigest", identity.evaluatorSetDigest.sha256)
            .text("semanticExecutionFingerprint", identity.semanticExecution.fingerprint.sha256)
            .text("deviceClass", environment.deviceClass)
            .number("androidApiLevel", environment.androidApiLevel.toLong())
            .text("abi", environment.abi)
            .text("backendRevision", environment.backendRevision)
            .text("harnessBuildIdentity", environment.harnessBuildIdentity)
            .text("runtimeTuningProfileId", environment.runtimeTuningProfileId)
            .number("runtimeTuningProfileVersion", environment.runtimeTuningProfileVersion.toLong())
            .text("loadPolicy", environment.loadPolicy.name)
            .text("warmupPolicy", environment.warmupPolicy.name)
            .build()
        return EvaluationRunFingerprint(sha256(canonical))
    }

    fun runFingerprint(identity: EvaluationRunIdentity): EvaluationRunFingerprint = runFingerprint(
        EvaluationRunIdentityUnchecked(
            model = identity.model,
            dataset = identity.dataset,
            sampleSetDigest = identity.sampleSetDigest,
            samplingPolicy = identity.samplingPolicy,
            samplingSeed = identity.samplingSeed,
            evaluatorSetDigest = identity.evaluatorSetDigest,
            semanticExecution = identity.semanticExecution,
            runtimeEnvironment = identity.runtimeEnvironment,
        ),
    )

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }

    private class CanonicalBuilder(private val schema: String) {
        private val value = StringBuilder().apply { appendEncoded(schema) }

        fun text(name: String, content: String): CanonicalBuilder = apply {
            value.appendEncoded(name)
            value.append('S')
            value.appendEncoded(content)
        }

        fun nullableText(name: String, content: String?): CanonicalBuilder = apply {
            value.appendEncoded(name)
            if (content == null) {
                value.append('N')
            } else {
                value.append('S')
                value.appendEncoded(content)
            }
        }

        fun number(name: String, content: Long): CanonicalBuilder = text(name, content.toString())

        fun nullableNumber(name: String, content: Long?): CanonicalBuilder = nullableText(name, content?.toString())

        fun strings(name: String, contents: List<String>): CanonicalBuilder = apply {
            number("$name.count", contents.size.toLong())
            contents.forEachIndexed { index, content -> text("$name[$index]", content) }
        }

        fun stringMap(name: String, contents: Map<String, String>): CanonicalBuilder = apply {
            val sorted = contents.toSortedMap()
            number("$name.count", sorted.size.toLong())
            sorted.entries.forEachIndexed { index, entry ->
                text("$name[$index].key", entry.key)
                text("$name[$index].value", entry.value)
            }
        }

        fun build(): String = value.toString()
    }

    private fun StringBuilder.appendEncoded(content: String) {
        append(content.length)
        append(':')
        append(content)
    }

    private const val HEX = "0123456789abcdef"
}
