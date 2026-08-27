package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerRuntimeIssue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class ConsumerRuntimeActivitySnapshot(
    val preparing: Boolean,
    val activeGenerations: Int,
    val lastIssue: ConsumerRuntimeIssue?,
)

/**
 * Tracks only connection-scoped transient activity needed to privacy-filter the shared runtime view.
 * Physical model/runtime state remains owned by Harness; this tracker never stores model identity.
 */
internal class ConsumerRuntimeActivityTracker {
    private val preparing = ConcurrentHashMap.newKeySet<HostClientToken>()
    private val generations = ConcurrentHashMap<HostClientToken, AtomicInteger>()
    private val issues = ConcurrentHashMap<HostClientToken, ConsumerRuntimeIssue>()

    fun beginPreparation(token: HostClientToken) {
        issues.remove(token)
        preparing.add(token)
    }

    fun finishPreparation(token: HostClientToken, issue: ConsumerRuntimeIssue?) {
        preparing.remove(token)
        if (issue == null) {
            issues.remove(token)
        } else {
            issues[token] = issue
        }
    }

    fun beginGeneration(token: HostClientToken) {
        issues.remove(token)
        generations.computeIfAbsent(token) { AtomicInteger() }.incrementAndGet()
    }

    fun finishGeneration(token: HostClientToken) {
        val counter = generations[token] ?: return
        if (counter.decrementAndGet() <= 0) {
            generations.remove(token, counter)
        }
    }

    fun snapshot(token: HostClientToken): ConsumerRuntimeActivitySnapshot = ConsumerRuntimeActivitySnapshot(
        preparing = token in preparing,
        activeGenerations = generations[token]?.get()?.coerceAtLeast(0) ?: 0,
        lastIssue = issues[token],
    )

    fun clear(token: HostClientToken) {
        preparing.remove(token)
        generations.remove(token)
        issues.remove(token)
    }

    fun clear() {
        preparing.clear()
        generations.clear()
        issues.clear()
    }
}
