package io.github.daniele21.localllm.observability.room

import android.content.Context
import androidx.room.Room
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
class RoomTelemetryRepository internal constructor(
    private val dao: TelemetryDao,
    private val retention: TelemetryRetentionPolicy,
    private val executor: ExecutorService,
    private val closeDatabase: () -> Unit = {},
) : TelemetryRepository,
    AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun recordRun(run: GenerationRunRecord) {
        executeAsync {
            dao.upsertRunWithRetention(
                TelemetryEntityMapper.runEntity(run),
                retention.maxRuns,
            )
        }
    }

    override fun appendLog(log: StructuredLog) {
        executeAsync {
            dao.insertLogWithRetention(
                TelemetryEntityMapper.logEntity(log),
                retention.maxLogs,
            )
        }
    }

    override fun saveHealth(result: HealthCheckResult) {
        executeAsync {
            dao.upsertHealth(TelemetryEntityMapper.healthEntity(result))
        }
    }

    override fun recentRuns(limit: Int): List<GenerationRunRecord> = executeBlocking {
        dao.recentRuns(requirePositiveLimit(limit)).map(TelemetryEntityMapper::runRecord)
    }

    override fun findRun(requestId: RequestId): GenerationRunRecord? = executeBlocking {
        dao.findRun(requestId.value)?.let(TelemetryEntityMapper::runRecord)
    }

    override fun recentLogs(limit: Int, requestId: RequestId?): List<StructuredLog> = executeBlocking {
        val validatedLimit = requirePositiveLimit(limit)
        val entities = if (requestId == null) {
            dao.recentLogs(validatedLimit)
        } else {
            dao.recentLogsForRequest(requestId.value, validatedLimit)
        }
        entities.map(TelemetryEntityMapper::structuredLog)
    }

    override fun healthResults(): List<HealthCheckResult> = executeBlocking {
        dao.healthResults().map(TelemetryEntityMapper::healthResult)
    }

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = executeBlocking {
        DeveloperDashboardSnapshot(
            runtime = runtime,
            recentRuns = dao.recentRuns(retention.maxRuns).map(TelemetryEntityMapper::runRecord),
            recentLogs = dao.recentLogs(retention.maxLogs).map(TelemetryEntityMapper::structuredLog),
            health = dao.healthResults().map(TelemetryEntityMapper::healthResult),
            modelStoreBytes = 0L,
            modelCount = 0,
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdown()
        try {
            if (!executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (error: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            closeDatabase()
        }
    }

    private fun executeAsync(block: () -> Unit) {
        check(!closed.get()) { "Telemetry repository is closed" }
        executor.execute {
            runCatching(block)
        }
    }

    private fun <T> executeBlocking(block: () -> T): T {
        check(!closed.get()) { "Telemetry repository is closed" }
        return try {
            executor.submit<T> { block() }.get()
        } catch (error: InterruptedException) {
            interruptedFailure(error)
        } catch (error: ExecutionException) {
            throw executionFailure(error)
        }
    }

    private fun interruptedFailure(error: InterruptedException): Nothing {
        Thread.currentThread().interrupt()
        throw IllegalStateException("Telemetry operation was interrupted", error)
    }

    private fun executionFailure(error: ExecutionException): RuntimeException {
        val cause = error.cause ?: error
        return cause as? RuntimeException ?: IllegalStateException("Telemetry operation failed", cause)
    }

    private fun requirePositiveLimit(limit: Int): Int {
        require(limit > 0) { "Telemetry query limit must be positive" }
        return limit
    }

    companion object {
        const val DEFAULT_DATABASE_NAME: String = "local-llm-telemetry.db"
        private const val CLOSE_TIMEOUT_SECONDS = 5L

        fun open(
            context: Context,
            databaseName: String = DEFAULT_DATABASE_NAME,
            retention: TelemetryRetentionPolicy = TelemetryRetentionPolicy(),
        ): RoomTelemetryRepository {
            require(databaseName.isNotBlank()) { "Telemetry database name must not be blank" }
            val database = Room.databaseBuilder(
                context.applicationContext,
                TelemetryDatabase::class.java,
                databaseName,
            ).build()
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "local-llm-telemetry-store").apply { isDaemon = true }
            }
            return RoomTelemetryRepository(
                dao = database.telemetryDao(),
                retention = retention,
                executor = executor,
                closeDatabase = database::close,
            )
        }
    }
}
