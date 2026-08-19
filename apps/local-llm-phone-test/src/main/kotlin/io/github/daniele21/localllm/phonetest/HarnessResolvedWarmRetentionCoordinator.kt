package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest

internal class HarnessResolvedWarmRetentionCoordinator(
    private val clock: WarmIdleEpochClock,
    private val scheduler: WarmIdleDeadlineScheduler,
    private val loadedModel: () -> ModelDigest?,
    private val unloadIdleResources: () -> Boolean,
) : AutoCloseable {
    private val lock = Any()
    private var generation = 0L
    private var scheduledDigest: ModelDigest? = null

    fun cancel() {
        synchronized(lock) {
            generation += 1
            scheduledDigest = null
            scheduler.cancel()
        }
    }

    fun schedule(modelDigest: ModelDigest, retainModelWarmMs: Long) {
        require(retainModelWarmMs >= 0) { "Resolved warm-retention duration must not be negative" }
        synchronized(lock) {
            generation += 1
            val expectedGeneration = generation
            scheduledDigest = modelDigest
            scheduler.cancel()
            if (loadedModel() != modelDigest) {
                scheduledDigest = null
                return
            }
            if (retainModelWarmMs == 0L) {
                releaseIfIdleLocked(modelDigest, expectedGeneration)
            } else {
                scheduler.scheduleAt(clock.nowEpochMs() + retainModelWarmMs) {
                    onDeadline(modelDigest, expectedGeneration)
                }
            }
        }
    }

    override fun close() {
        cancel()
        scheduler.close()
    }

    private fun onDeadline(modelDigest: ModelDigest, expectedGeneration: Long) {
        synchronized(lock) {
            if (generation != expectedGeneration || scheduledDigest != modelDigest) return
            releaseIfIdleLocked(modelDigest, expectedGeneration)
        }
    }

    private fun releaseIfIdleLocked(modelDigest: ModelDigest, expectedGeneration: Long) {
        if (loadedModel() != modelDigest) {
            scheduledDigest = null
            return
        }
        if (unloadIdleResources()) {
            scheduledDigest = null
            return
        }
        scheduler.scheduleAt(clock.nowEpochMs() + RETRY_DELAY_MS) {
            onDeadline(modelDigest, expectedGeneration)
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 1_000L
    }
}
