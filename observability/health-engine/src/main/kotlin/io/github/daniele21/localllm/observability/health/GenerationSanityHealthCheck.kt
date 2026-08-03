package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.HealthStatus
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class SanityOutputMatch {
    EXACT,
    CONTAINS,
}

data class GenerationSanitySpec(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val prompt: String,
    val expectedOutput: String,
    val outputMatch: SanityOutputMatch = SanityOutputMatch.CONTAINS,
    val caseSensitive: Boolean = false,
    val maxOutputTokens: Int = 16,
    val temperature: Float = 0f,
    val seed: Long = 0L,
    val timeoutMs: Long = 30_000L,
) {
    init {
        require(prompt.isNotBlank()) { "Sanity prompt must not be blank" }
        require(expectedOutput.isNotBlank()) { "Expected sanity output must not be blank" }
        require(maxOutputTokens > 0) { "Sanity max output tokens must be positive" }
        require(temperature.isFinite() && temperature >= 0f) { "Sanity temperature must be finite and non-negative" }
        require(timeoutMs > 0) { "Sanity timeout must be positive" }
    }
}

fun interface SanityRequestIdFactory {
    fun create(): RequestId
}

class GenerationSanityHealthCheck(
    private val client: LocalLlmClient,
    private val spec: GenerationSanitySpec,
    private val requestIdFactory: SanityRequestIdFactory = SanityRequestIdFactory {
        RequestId(UUID.randomUUID().toString())
    },
) : HealthCheck {
    override val id: String = "generation-sanity:${spec.applicationId.value}:${spec.useCaseId.value}"

    override fun evaluate(): HealthAssessment {
        val prepared = runCatching {
            client.prepare(spec.applicationId, spec.useCaseId)
        }.getOrElse {
            return failed("Generation sanity model preparation failed unexpectedly")
        }
        if (!prepared.ready) {
            return failed("Generation sanity model could not be prepared")
        }

        val sessionId = runCatching {
            client.createSession(spec.applicationId, spec.useCaseId)
        }.getOrElse {
            return failed("Generation sanity session could not be created")
        }
        val assessment = runCatching {
            generate(sessionId)
        }.getOrElse {
            failed("Generation sanity failed unexpectedly")
        }
        val closed = runCatching {
            client.closeSession(sessionId)
        }.isSuccess
        return if (closed) {
            assessment
        } else {
            failed("Generation sanity session cleanup failed")
        }
    }

    private fun generate(sessionId: SessionId): HealthAssessment {
        val terminalEvent = AtomicReference<GenerationEvent?>()
        val terminalLatch = CountDownLatch(1)
        val requestId = requestIdFactory.create()
        val request = GenerationRequest(
            requestId = requestId,
            sessionId = sessionId,
            applicationId = spec.applicationId,
            useCaseId = spec.useCaseId,
            input = spec.prompt,
            overrides = GenerationOverrides(
                maxOutputTokens = spec.maxOutputTokens,
                temperature = spec.temperature,
                seed = spec.seed,
            ),
        )
        val handle = client.generate(
            request,
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    if (terminalEvent.compareAndSet(null, event)) {
                        terminalLatch.countDown()
                    }
                }
            },
        )

        if (!terminalLatch.await(spec.timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching(handle::cancel)
            return failed("Generation sanity timed out")
        }

        return when (val event = terminalEvent.get()) {
            is GenerationEvent.Completed -> completed(event.output)
            is GenerationEvent.Failed -> failed("Generation sanity failed with ${event.error.code}")
            else -> failed("Generation sanity ended without a terminal result")
        }
    }

    private fun completed(output: String): HealthAssessment = if (matches(output)) {
        HealthAssessment(
            status = HealthStatus.PASS,
            detail = "Generation sanity completed and matched the expected output",
        )
    } else {
        failed("Generation sanity completed but output did not match the expected result")
    }

    private fun matches(output: String): Boolean {
        val actual = output.trim()
        val expected = spec.expectedOutput.trim()
        return when (spec.outputMatch) {
            SanityOutputMatch.EXACT -> actual.equals(expected, ignoreCase = !spec.caseSensitive)
            SanityOutputMatch.CONTAINS -> actual.contains(expected, ignoreCase = !spec.caseSensitive)
        }
    }

    private fun failed(detail: String): HealthAssessment = HealthAssessment(
        status = HealthStatus.FAIL,
        detail = detail,
    )
}
