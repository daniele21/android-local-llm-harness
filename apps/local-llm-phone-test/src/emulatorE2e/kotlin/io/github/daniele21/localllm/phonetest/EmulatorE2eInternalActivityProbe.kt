package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.RequestId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Emulator-only probe proving the real Harnex-internal audited runtime path and durable Room ledger. */
internal object EmulatorE2eInternalActivityProbe {
    fun run(context: Context): String = runCatching {
        EmulatorE2eGenerationGate.reset()
        EmulatorE2eBackendFailureGate.reset()
        execute(context)
    }.getOrElse { failure ->
        "available=false;error=${failure.javaClass.simpleName}"
    }

    private fun execute(context: Context): String {
        val graph = HarnessRuntimeGraph.from(context)
        val artifact = CuratedModelCatalog.releases.first().artifact
        val model =
            ImportedPhoneModel(
                digest = artifact.digest,
                fileName = artifact.fileName,
                sizeBytes = artifact.sizeBytes,
                architecture = artifact.architecture,
                quantization = artifact.quantization,
            )
        val harness = graph.harnessFor(model, HarnessRuntimePurpose.PLAYGROUND)
        val prepared = harness.client.prepare(harness.applicationId, harness.useCaseId)
        check(prepared.ready) { "Internal Activity probe prepare failed" }
        val session = harness.client.createSession(harness.applicationId, harness.useCaseId)
        val requestId = RequestId("lia-internal-${UUID.randomUUID()}")
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()

        try {
            harness.client.generate(
                GenerationRequest(
                    requestId = requestId,
                    sessionId = session,
                    applicationId = harness.applicationId,
                    useCaseId = harness.useCaseId,
                    input = INTERNAL_PROMPT,
                ),
                GenerationListener { event ->
                    if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                        terminalEvent.set(event)
                        terminal.countDown()
                    }
                },
            )
            check(terminal.await(TERMINAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Internal Activity probe timed out"
            }
            check(terminalEvent.get() is GenerationEvent.Completed) {
                "Internal Activity probe did not complete"
            }
        } finally {
            runCatching { harness.client.closeSession(session) }
        }

        val record =
            when (val result = graph.inferenceAuditRepository.find(requestId)) {
                is InferenceAuditResult.Success -> requireNotNull(result.value) { "Internal Activity record missing" }
                is InferenceAuditResult.Failure -> error("Internal Activity lookup failed: ${result.code.name}")
            }
        val terminalRecord = requireNotNull(record.terminal) { "Internal Activity terminal missing" }
        val preparedRecord = requireNotNull(record.prepared) { "Internal Activity prepared state missing" }
        check(record.status == InferenceAuditStatus.COMPLETED) { "Internal Activity status mismatch" }
        check(record.admission.origin.kind == InferenceAuditOriginKind.HARNEX_INTERNAL) {
            "Internal Activity origin mismatch"
        }
        check(record.admission.origin.verifiedPackageName == null) {
            "Internal Activity must not carry an external verified package"
        }

        return buildString {
            append("available=true")
            append(";request_id=${record.requestId.value}")
            append(";origin_kind=${record.admission.origin.kind.name}")
            append(";status=${record.status.name}")
            append(";application_id=${record.admission.origin.applicationId.value}")
            append(";use_case_id=${record.admission.origin.useCaseId.value}")
            append(";verified_package_present=${record.admission.origin.verifiedPackageName != null}")
            append(";input_present=${record.admission.input.characterCount > 0}")
            append(";effective_prompt_present=${!preparedRecord.effectivePrompt.isNullOrBlank()}")
            append(";answer_present=${!terminalRecord.content?.answerOutput.isNullOrBlank()}")
            append(";model_digest_present=${preparedRecord.execution.modelDigest.sha256.isNotBlank()}")
            append(";total_ms_present=${terminalRecord.metrics?.totalMs != null}")
            append(";output_tokens_present=${terminalRecord.metrics?.outputTokens != null}")
            append(";decode_tps_present=${terminalRecord.metrics?.decodeTokensPerSecond != null}")
            append(";sensitive_values_exported=false")
        }
    }

    private const val INTERNAL_PROMPT = "Harnex internal Activity persistence probe"
    private const val TERMINAL_TIMEOUT_SECONDS = 5L
}
