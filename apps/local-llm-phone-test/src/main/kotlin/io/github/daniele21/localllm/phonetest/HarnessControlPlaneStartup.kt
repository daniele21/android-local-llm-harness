package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class HarnessControlPlaneStartupConflictException(val code: HarnessControlPlaneConflictCode, val identity: String) :
    IllegalStateException("Built-in control-plane conflict: $code ($identity)")

/** Applies the pure built-in reconciliation as one store transaction before readers are exposed. */
internal class HarnessControlPlaneStartup(
    private val store: HostControlPlaneStore,
    private val reconciler: HarnessControlPlaneReconciler,
    private val epochClock: () -> Long = System::currentTimeMillis,
    private val executorFactory: () -> ExecutorService = ::newControlPlaneStartupExecutor,
) {
    /**
     * This is intentionally a synchronous barrier for callers, but the Room transaction executes on a
     * dedicated worker because Android Activity and Service composition roots are created on the main thread.
     */
    fun reconcile(): HostControlPlaneState = HarnessControlPlaneStartupRunner(
        task = ::reconcileTransaction,
        executorFactory = executorFactory,
    ).run()

    private fun reconcileTransaction(): HostControlPlaneState = store.transact { current ->
        when (val result = reconciler.reconcile(current, epochClock())) {
            is HarnessControlPlaneReconciliationResult.Success -> result.state

            is HarnessControlPlaneReconciliationResult.Conflict ->
                throw HarnessControlPlaneStartupConflictException(result.code, result.identity)
        }
    }
}

/** Runs the mandatory startup transaction away from Android component main threads and propagates its real failure. */
internal class HarnessControlPlaneStartupRunner(
    private val task: () -> HostControlPlaneState,
    private val executorFactory: () -> ExecutorService = ::newControlPlaneStartupExecutor,
) {
    fun run(): HostControlPlaneState {
        val executor = executorFactory()
        val future = executor.submit<HostControlPlaneState> { task() }
        return try {
            future.get()
        } catch (failure: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw IllegalStateException("Control-plane startup reconciliation was interrupted", failure)
        } catch (failure: ExecutionException) {
            throw failure.cause.asStartupFailure(failure)
        } finally {
            executor.shutdownNow()
        }
    }
}

private fun newControlPlaneStartupExecutor(): ExecutorService = Executors.newSingleThreadExecutor { task ->
    Thread(task, "harness-control-plane-startup").apply { isDaemon = true }
}

private fun Throwable?.asStartupFailure(wrapper: ExecutionException): RuntimeException = when (this) {
    is RuntimeException -> this
    is Error -> throw this
    null -> IllegalStateException("Control-plane startup reconciliation failed", wrapper)
    else -> IllegalStateException("Control-plane startup reconciliation failed", this)
}
