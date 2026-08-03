package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.observability.SanityExecutionResult
import io.github.daniele21.localllm.observability.SanityExecutor
import io.github.daniele21.localllm.observability.SanityFixture
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalLlmSanityExecutor(
    private val client: LocalLlmClient,
    private val requestIdFactory: () -> RequestId = { RequestId(UUID.randomUUID().toString()) },
    private val monotonicClock: () -> Long = System::nanoTime,
) : SanityExecutor {
    override fun execute(fixture: SanityFixture): SanityExecutionResult {
        val startedAt = monotonicClock()
        val prepared = runCatching {
            client.prepare(fixture.applicationId, fixture.useCaseId)
        }.getOrElse { error ->
            return failure(
                startedAt = startedAt,
                code = "SANITY_PREPARE_EXCEPTION",
                detail = error.message,
            )
        }
        if (!prepared.ready) {
            return failure(
                startedAt = startedAt,
                code = "SANITY_PREPARE_FAILED",
                detail = prepared.detail,
            )
        }

        val sessionId = runCatching {
            client.createSession(fixture.applicationId, fixture.useCaseId)
        }.getOrElse { error ->
            return failure(
                startedAt = startedAt,
                code = "SANITY_SESSION_FAILED",
                detail = error.message,
            )
        }

        return try {
            generate(fixture, sessionId, startedAt)
        } finally {
            runCatching { client.closeSession(sessionId) }
        }
    }

    private fun generate(fixture: SanityFixture, sessionId: SessionId, startedAt: Long): SanityExecutionResult {
        val terminal = AtomicReference<GenerationEvent?>()
        val completed = CountDownLatch(1)
        val request = GenerationRequest(
            requestId = requestIdFactory(),
            sessionId = sessionId,
            applicationId = fixture.applicationId,
            useCaseId = fixture.useCaseId,
            input = fixture.input,
            overrides = GenerationOverrides(
                maxOutputTokens = fixture.generation.maxOutputTokens,
                temperature = fixture.generation.temperature,
                seed = fixture.generation.seed,
            ),
        )
        val handle = runCatching {
            client.generate(
                request,
                GenerationListener { event ->
                    if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                        if (terminal.compareAndSet(null, event)) {
                            completed.countDown()
                        }
                    }
                },
            )
        }.getOrElse { error ->
            return failure(
                startedAt = startedAt,
                code = "SANITY_GENERATION_EXCEPTION",
                detail = error.message,
            )
        }

        if (!completed.await(fixture.timeoutMs, TimeUnit.MILLISECONDS)) {
            handle.cancel()
            return failure(
                startedAt = startedAt,
                code = "SANITY_TIMEOUT",
                detail = "Generation did not reach a terminal event within the fixture timeout.",
            )
        }

        return when (val event = terminal.get()) {
            is GenerationEvent.Completed -> SanityExecutionResult(
                output = event.output,
                outputTokens = event.metrics.outputTokens,
                durationMs = elapsedMillis(startedAt),
            )

            is GenerationEvent.Failed -> failure(
                startedAt = startedAt,
                code = event.error.code,
                detail = event.error.message,
            )

            else -> failure(
                startedAt = startedAt,
                code = "SANITY_MISSING_TERMINAL_EVENT",
                detail = "The sanity listener completed without a terminal event.",
            )
        }
    }

    private fun failure(startedAt: Long, code: String, detail: String?): SanityExecutionResult = SanityExecutionResult(
        output = null,
        outputTokens = null,
        durationMs = elapsedMillis(startedAt),
        errorCode = code,
        errorDetail = detail,
    )

    private fun elapsedMillis(startedAtNanos: Long): Long = (monotonicClock() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
