package io.github.daniele21.localllm.observability.room

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.ResourceSnapshot
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

    override fun recordResourceSnapshot(snapshot: ResourceSnapshot) {
        executeAsync {
            dao.insertResourceSnapshotWithRetention(
                TelemetryEntityMapper.resourceEntity(snapshot),
                retention.maxResourceSnapshots,
            )
        }
    }

    override fun saveBenchmarkBaseline(baseline: BenchmarkBaseline) {
        executeAsync {
            dao.saveBenchmarkBaselineWithHistory(
                TelemetryEntityMapper.benchmarkEntity(baseline),
                TelemetryEntityMapper.benchmarkHistoryEntity(baseline),
                retention.maxBenchmarkBaselines,
            )
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

    override fun recentResourceSnapshots(limit: Int): List<ResourceSnapshot> = executeBlocking {
        dao.recentResourceSnapshots(requirePositiveLimit(limit)).map(TelemetryEntityMapper::resourceSnapshot)
    }

    override fun benchmarkBaselines(): List<BenchmarkBaseline> = executeBlocking {
        dao.benchmarkBaselines().map { TelemetryEntityMapper.benchmarkBaseline(it) }
    }

    override fun benchmarkBaselineHistory(limit: Int): List<BenchmarkBaseline> = executeBlocking {
        dao.benchmarkBaselineHistory(requirePositiveLimit(limit)).map { TelemetryEntityMapper.benchmarkBaseline(it) }
    }

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = executeBlocking {
        DeveloperDashboardSnapshot(
            runtime = runtime,
            recentRuns = dao.recentRuns(retention.maxRuns).map(TelemetryEntityMapper::runRecord),
            recentLogs = dao.recentLogs(retention.maxLogs).map(TelemetryEntityMapper::structuredLog),
            health = dao.healthResults().map(TelemetryEntityMapper::healthResult),
            resources = dao.recentResourceSnapshots(retention.maxResourceSnapshots)
                .map(TelemetryEntityMapper::resourceSnapshot),
            benchmarkBaselines = dao.benchmarkBaselines().map { TelemetryEntityMapper.benchmarkBaseline(it) },
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE generation_runs ADD COLUMN model_load_kind TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS resource_snapshots (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp_epoch_ms INTEGER NOT NULL, " +
                        "process_pss_bytes INTEGER, " +
                        "native_heap_bytes INTEGER, " +
                        "java_heap_used_bytes INTEGER, " +
                        "available_memory_bytes INTEGER, " +
                        "low_memory INTEGER, " +
                        "thermal_status TEXT NOT NULL)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_resource_snapshots_timestamp_epoch_ms " +
                        "ON resource_snapshots(timestamp_epoch_ms)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS benchmark_baselines (" +
                        "baseline_id TEXT NOT NULL PRIMARY KEY, " +
                        "application_id TEXT NOT NULL, " +
                        "use_case_id TEXT NOT NULL, " +
                        "model_digest TEXT NOT NULL, " +
                        "model_load_kind TEXT NOT NULL, " +
                        "captured_at_epoch_ms INTEGER NOT NULL, " +
                        "sample_count INTEGER NOT NULL, " +
                        "median_time_to_first_token_ms REAL, " +
                        "p95_time_to_first_token_ms REAL, " +
                        "median_total_ms REAL, " +
                        "p95_total_ms REAL, " +
                        "median_decode_tokens_per_second REAL)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS benchmark_baseline_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "application_id TEXT NOT NULL, " +
                        "use_case_id TEXT NOT NULL, " +
                        "model_digest TEXT NOT NULL, " +
                        "model_load_kind TEXT NOT NULL, " +
                        "captured_at_epoch_ms INTEGER NOT NULL, " +
                        "sample_count INTEGER NOT NULL, " +
                        "median_time_to_first_token_ms REAL, " +
                        "p95_time_to_first_token_ms REAL, " +
                        "median_total_ms REAL, " +
                        "p95_total_ms REAL, " +
                        "median_decode_tokens_per_second REAL)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_benchmark_baseline_history_captured_at_epoch_ms " +
                        "ON benchmark_baseline_history(captured_at_epoch_ms)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_benchmark_baseline_history_application_id_use_case_id_model_digest_model_load_kind " +
                        "ON benchmark_baseline_history(" +
                        "application_id, use_case_id, model_digest, model_load_kind)",
                )
                database.execSQL(
                    "INSERT INTO benchmark_baseline_history (" +
                        "application_id, use_case_id, model_digest, model_load_kind, " +
                        "captured_at_epoch_ms, sample_count, median_time_to_first_token_ms, " +
                        "p95_time_to_first_token_ms, median_total_ms, p95_total_ms, " +
                        "median_decode_tokens_per_second) " +
                        "SELECT application_id, use_case_id, model_digest, model_load_kind, " +
                        "captured_at_epoch_ms, sample_count, median_time_to_first_token_ms, " +
                        "p95_time_to_first_token_ms, median_total_ms, p95_total_ms, " +
                        "median_decode_tokens_per_second FROM benchmark_baselines",
                )
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val columns = listOf(
                    "preset_id TEXT", "preset_version INTEGER", "temperature REAL", "top_p REAL", "top_k INTEGER",
                    "seed_policy TEXT", "effective_seed INTEGER", "max_output_tokens INTEGER", "context_size INTEGER",
                    "prompt_token_count INTEGER", "chat_template_id TEXT", "chat_template_source TEXT",
                    "system_prompt_version TEXT", "stop_reason TEXT", "prompt_planning_ms INTEGER",
                    "context_creation_ms INTEGER",
                )
                columns.forEach { database.execSQL("ALTER TABLE generation_runs ADD COLUMN $it") }
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN repeat_penalty REAL")
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN repeat_last_n INTEGER")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN min_p REAL")
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN presence_penalty REAL")
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN thinking_mode TEXT")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS benchmark_baselines")
                database.execSQL("DROP TABLE IF EXISTS benchmark_baseline_history")
                database.execSQL(
                    "CREATE TABLE benchmark_baselines (" +
                        "baseline_id TEXT NOT NULL PRIMARY KEY, " +
                        "application_id TEXT NOT NULL, " +
                        "use_case_id TEXT NOT NULL, " +
                        "model_digest TEXT NOT NULL, " +
                        "model_load_kind TEXT NOT NULL, " +
                        "execution_identity TEXT NOT NULL, " +
                        "captured_at_epoch_ms INTEGER NOT NULL, " +
                        "sample_count INTEGER NOT NULL, " +
                        "median_time_to_first_token_ms REAL, " +
                        "p95_time_to_first_token_ms REAL, " +
                        "median_total_ms REAL, " +
                        "p95_total_ms REAL, " +
                        "median_decode_tokens_per_second REAL)",
                )
                database.execSQL(
                    "CREATE TABLE benchmark_baseline_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "application_id TEXT NOT NULL, " +
                        "use_case_id TEXT NOT NULL, " +
                        "model_digest TEXT NOT NULL, " +
                        "model_load_kind TEXT NOT NULL, " +
                        "execution_identity TEXT NOT NULL, " +
                        "captured_at_epoch_ms INTEGER NOT NULL, " +
                        "sample_count INTEGER NOT NULL, " +
                        "median_time_to_first_token_ms REAL, " +
                        "p95_time_to_first_token_ms REAL, " +
                        "median_total_ms REAL, " +
                        "p95_total_ms REAL, " +
                        "median_decode_tokens_per_second REAL)",
                )
                database.execSQL(
                    "CREATE INDEX index_benchmark_baseline_history_captured_at_epoch_ms " +
                        "ON benchmark_baseline_history(captured_at_epoch_ms)",
                )
                database.execSQL(
                    "CREATE INDEX index_benchmark_baseline_history_application_id_use_case_id_model_digest_model_load_kind " +
                        "ON benchmark_baseline_history(application_id, use_case_id, model_digest, model_load_kind)",
                )
            }
        }

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
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
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
