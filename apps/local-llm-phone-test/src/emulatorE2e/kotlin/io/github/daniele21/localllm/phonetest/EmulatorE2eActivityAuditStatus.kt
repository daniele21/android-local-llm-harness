package io.github.daniele21.localllm.phonetest

/**
 * Emulator-only privacy-safe projection of the durable Activity ledger.
 *
 * Sensitive inference values are inspected in-process only to derive presence flags and are never
 * returned through the broadcast result.
 */
internal object EmulatorE2eActivityAuditStatus {
    fun query(context: android.content.Context, verifiedPackageName: String): String {
        require(verifiedPackageName.isNotBlank()) { "Verified package name must not be blank" }
        val snapshot = HarnessRuntimeGraph.from(context).inferenceActivitySource.snapshot()
        snapshot.errorCode?.let { errorCode ->
            return "available=false;count=0;error=${errorCode.name}"
        }

        val matching = snapshot.items.filter { item -> item.verifiedPackageName == verifiedPackageName }
        val latest = matching.maxByOrNull(InferenceActivityListItem::receivedAtEpochMs)
            ?: return "available=false;count=0;error=none"
        val detail =
            when (val result = HarnessRuntimeGraph.from(context).inferenceActivitySource.detail(latest.requestId)) {
                is InferenceActivityDetailResult.Available -> result.detail

                is InferenceActivityDetailResult.Unavailable -> {
                    return "available=false;count=${matching.size};error=${result.errorCode?.name ?: "not_found"}"
                }
            }

        return buildString {
            append("available=true")
            append(";count=${matching.size}")
            append(";request_id=${detail.requestId}")
            append(";status=${detail.status.name}")
            append(";application_id=${detail.applicationId}")
            append(";use_case_id=${detail.useCaseId}")
            append(";verified_package=${detail.verifiedPackageName.orEmpty()}")
            append(";input_present=${detail.input.isNotBlank()}")
            append(";effective_prompt_present=${!detail.effectivePrompt.isNullOrBlank()}")
            append(";answer_present=${!detail.answerOutput.isNullOrBlank()}")
            append(";reasoning_present=${!detail.reasoningOutput.isNullOrBlank()}")
        }
    }
}
