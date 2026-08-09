from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}, got {text.count(old)}")
    p.write_text(text.replace(old, new))


telemetry = "observability/contracts/src/main/kotlin/io/github/daniele21/localllm/observability/Telemetry.kt"
old = '''data class BenchmarkKey(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val modelDigest: ModelDigest,
    val modelLoadKind: ModelLoadKind,
) {
    init {
        require(modelLoadKind != ModelLoadKind.UNKNOWN) { "Benchmark load kind must be explicit" }
    }

    val stableId: String
        get() = listOf(
            applicationId.value,
            useCaseId.value,
            modelDigest.sha256,
            modelLoadKind.name,
        ).joinToString("|")
}
'''
new = '''data class BenchmarkExecutionIdentity private constructor(val fingerprint: String) {
    init {
        require(FINGERPRINT_PATTERN.matches(fingerprint)) { "Benchmark execution fingerprint must be SHA-256" }
    }

    companion object {
        private val FINGERPRINT_PATTERN = Regex("[0-9a-f]{64}")

        fun fromFingerprint(fingerprint: String): BenchmarkExecutionIdentity =
            BenchmarkExecutionIdentity(fingerprint.lowercase())

        fun fromRun(run: GenerationRunRecord): BenchmarkExecutionIdentity {
            val canonical = listOf(
                value(run.contextSize),
                value(run.promptTokenCount),
                value(run.presetId?.value),
                value(run.presetVersion),
                value(run.thinkingMode?.name),
                floatValue(run.temperature),
                floatValue(run.topP),
                value(run.topK),
                floatValue(run.minP),
                floatValue(run.presencePenalty),
                floatValue(run.repeatPenalty),
                value(run.repeatLastN),
                value(run.seedPolicy?.name),
                value(run.effectiveSeed),
                value(run.maxOutputTokens),
                value(run.chatTemplateId),
                value(run.chatTemplateSource?.name),
                value(run.systemPromptVersion),
            ).joinToString("|")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return BenchmarkExecutionIdentity(digest)
        }

        private fun value(value: Any?): String = value?.toString() ?: "~"

        private fun floatValue(value: Float?): String = value?.toRawBits()?.toString() ?: "~"
    }
}

data class BenchmarkKey(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val modelDigest: ModelDigest,
    val modelLoadKind: ModelLoadKind,
    val executionIdentity: BenchmarkExecutionIdentity,
) {
    init {
        require(modelLoadKind != ModelLoadKind.UNKNOWN) { "Benchmark load kind must be explicit" }
    }

    val stableId: String
        get() = listOf(
            applicationId.value,
            useCaseId.value,
            modelDigest.sha256,
            modelLoadKind.name,
            executionIdentity.fingerprint,
        ).joinToString("|")

    fun matches(run: GenerationRunRecord): Boolean =
        run.applicationId == applicationId &&
            run.useCaseId == useCaseId &&
            run.modelDigest == modelDigest &&
            run.modelLoadKind == modelLoadKind &&
            BenchmarkExecutionIdentity.fromRun(run) == executionIdentity

    companion object {
        fun fromRun(run: GenerationRunRecord): BenchmarkKey = BenchmarkKey(
            applicationId = run.applicationId,
            useCaseId = run.useCaseId,
            modelDigest = run.modelDigest,
            modelLoadKind = run.modelLoadKind,
            executionIdentity = BenchmarkExecutionIdentity.fromRun(run),
        )
    }
}
'''
replace_once(telemetry, old, new)

engine = "observability/benchmark-engine/src/main/kotlin/io/github/daniele21/localllm/observability/benchmark/BenchmarkEngine.kt"
old = '''private fun matchingRuns(runs: List<GenerationRunRecord>, key: BenchmarkKey): List<GenerationRunRecord> = runs.asSequence()
    .filter { it.status == RunStatus.COMPLETED }
    .filter { it.applicationId == key.applicationId }
    .filter { it.useCaseId == key.useCaseId }
    .filter { it.modelDigest == key.modelDigest }
    .filter { it.modelLoadKind == key.modelLoadKind }
    .filter { it.completedAtEpochMs != null }
'''
new = '''private fun matchingRuns(runs: List<GenerationRunRecord>, key: BenchmarkKey): List<GenerationRunRecord> = runs.asSequence()
    .filter { it.status == RunStatus.COMPLETED }
    .filter(key::matches)
    .filter { it.completedAtEpochMs != null }
'''
replace_once(engine, old, new)

# Health-check IDs must also distinguish incompatible execution configurations.
replace_once(
    engine,
    '''        key.modelDigest.sha256,\n        key.modelLoadKind.name,\n''',
    '''        key.modelDigest.sha256,\n        key.modelLoadKind.name,\n        key.executionIdentity.fingerprint,\n''',
)

entities = "observability/room-store/src/main/java/io/github/daniele21/localllm/observability/room/TelemetryEntities.java"
text = Path(entities).read_text()
needle = '''        @NonNull\n        @ColumnInfo(name = "model_load_kind")\n        public String modelLoadKind = "";\n'''
replacement = needle + '''\n        @NonNull\n        @ColumnInfo(name = "execution_identity")\n        public String executionIdentity = "";\n'''
if text.count(needle) != 2:
    raise SystemExit(f"expected two benchmark modelLoadKind fields, got {text.count(needle)}")
Path(entities).write_text(text.replace(needle, replacement))

mapper = "observability/room-store/src/main/kotlin/io/github/daniele21/localllm/observability/room/TelemetryEntityMapper.kt"
text = Path(mapper).read_text()
if "import io.github.daniele21.localllm.observability.BenchmarkExecutionIdentity\n" not in text:
    text = text.replace(
        "import io.github.daniele21.localllm.observability.BenchmarkBaseline\n",
        "import io.github.daniele21.localllm.observability.BenchmarkBaseline\nimport io.github.daniele21.localllm.observability.BenchmarkExecutionIdentity\n",
    )
text = text.replace(
    '''            modelLoadKind = baseline.key.modelLoadKind.name\n            capturedAtEpochMs = baseline.capturedAtEpochMs\n''',
    '''            modelLoadKind = baseline.key.modelLoadKind.name\n            executionIdentity = baseline.key.executionIdentity.fingerprint\n            capturedAtEpochMs = baseline.capturedAtEpochMs\n''',
)
text = text.replace(
    '''        modelLoadKind = entity.modelLoadKind,\n        capturedAtEpochMs = entity.capturedAtEpochMs,\n''',
    '''        modelLoadKind = entity.modelLoadKind,\n        executionIdentity = entity.executionIdentity,\n        capturedAtEpochMs = entity.capturedAtEpochMs,\n''',
)
text = text.replace(
    '''        modelLoadKind = baseline.key.modelLoadKind.name\n        capturedAtEpochMs = baseline.capturedAtEpochMs\n''',
    '''        modelLoadKind = baseline.key.modelLoadKind.name\n        executionIdentity = baseline.key.executionIdentity.fingerprint\n        capturedAtEpochMs = baseline.capturedAtEpochMs\n''',
)
text = text.replace(
    '''        modelLoadKind: String,\n        capturedAtEpochMs: Long,\n''',
    '''        modelLoadKind: String,\n        executionIdentity: String,\n        capturedAtEpochMs: Long,\n''',
)
text = text.replace(
    '''            modelLoadKind = ModelLoadKind.valueOf(modelLoadKind),\n        ),\n''',
    '''            modelLoadKind = ModelLoadKind.valueOf(modelLoadKind),\n            executionIdentity = BenchmarkExecutionIdentity.fromFingerprint(executionIdentity),\n        ),\n''',
)
Path(mapper).write_text(text)

# Replace obsolete benchmark baselines during 7->8: their execution identity cannot be reconstructed safely.
repo = "observability/room-store/src/main/kotlin/io/github/daniele21/localllm/observability/room/RoomTelemetryRepository.kt"
text = Path(repo).read_text()
marker = '''        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN min_p REAL")
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN presence_penalty REAL")
                database.execSQL("ALTER TABLE generation_runs ADD COLUMN thinking_mode TEXT")
            }
        }
'''
addition = marker + '''
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
'''
if text.count(marker) != 1:
    raise SystemExit("migration 6->7 marker mismatch")
text = text.replace(marker, addition)
text = text.replace(
    '''                MIGRATION_6_7,\n            ).build()''',
    '''                MIGRATION_6_7,\n                MIGRATION_7_8,\n            ).build()''',
)
Path(repo).write_text(text)

# Room schema version.
db = "observability/room-store/src/main/java/io/github/daniele21/localllm/observability/room/TelemetryDatabase.java"
replace_once(db, "        version = 7,\n", "        version = 8,\n")

# Update benchmark tests to build the key from an exact run configuration and prove mismatch rejection.
test = "observability/benchmark-engine/src/test/kotlin/io/github/daniele21/localllm/observability/benchmark/BenchmarkEngineTest.kt"
text = Path(test).read_text()
text = text.replace(
    '''import io.github.daniele21.localllm.contracts.ModelLoadKind\n''',
    '''import io.github.daniele21.localllm.contracts.InferencePresetId\nimport io.github.daniele21.localllm.contracts.ModelLoadKind\nimport io.github.daniele21.localllm.contracts.SeedPolicyType\nimport io.github.daniele21.localllm.contracts.ThinkingMode\n''',
)
text = text.replace(
    '''    private val warmKey = BenchmarkKey(applicationId, useCaseId, digest, ModelLoadKind.WARM)\n''',
    '''    private val warmKey by lazy { BenchmarkKey.fromRun(run("identity", 10, 100, 50.0, 1L)) }\n''',
)
insert_after = '''    fun `does not mix cold runs into a warm baseline`() {
        val repository = InMemoryTelemetryRepository()
        repeat(4) { index -> repository.recordRun(run("warm-$index", 10, 100, 50.0, index.toLong() + 1)) }
        repository.recordRun(
            run("cold", 999, 999, 1.0, 5L, loadKind = ModelLoadKind.COLD),
        )

        val result = BenchmarkBaselineRecorder(repository, policy).capture(warmKey)

        assertEquals(BenchmarkCaptureResult.InsufficientSamples(4, 5), result)
    }
'''
new_test = insert_after + '''
    @Test
    fun `does not mix different execution identities`() {
        val repository = InMemoryTelemetryRepository()
        repeat(4) { index -> repository.recordRun(run("matching-$index", 10, 100, 50.0, index.toLong() + 1)) }
        repository.recordRun(
            run("other-context", 10, 100, 50.0, 5L, contextSize = 4_096),
        )
        repository.recordRun(
            run("other-thinking", 10, 100, 50.0, 6L, thinkingMode = ThinkingMode.ENABLED),
        )

        val result = BenchmarkBaselineRecorder(repository, policy).capture(warmKey)

        assertEquals(BenchmarkCaptureResult.InsufficientSamples(4, 5), result)
        assertFalse(warmKey.matches(run("mismatch", 10, 100, 50.0, 7L, contextSize = 4_096)))
        assertTrue(warmKey.stableId.endsWith(warmKey.executionIdentity.fingerprint))
    }
'''
if text.count(insert_after) != 1:
    raise SystemExit("benchmark test insertion marker mismatch")
text = text.replace(insert_after, new_test)
text = text.replace(
    '''        loadKind: ModelLoadKind = ModelLoadKind.WARM,\n    ): GenerationRunRecord = GenerationRunRecord(''',
    '''        loadKind: ModelLoadKind = ModelLoadKind.WARM,\n        contextSize: Int = 2_048,\n        thinkingMode: ThinkingMode = ThinkingMode.DISABLED,\n    ): GenerationRunRecord = GenerationRunRecord(''',
)
text = text.replace(
    '''        errorCode = null,\n        modelLoadKind = loadKind,\n    )\n}''',
    '''        errorCode = null,\n        modelLoadKind = loadKind,\n        presetId = InferencePresetId("qwen35-text-quality"),\n        presetVersion = 1,\n        temperature = 1.0f,\n        topP = 1.0f,\n        topK = 20,\n        minP = 0.0f,\n        presencePenalty = 2.0f,\n        thinkingMode = thinkingMode,\n        repeatPenalty = 1.0f,\n        repeatLastN = 64,\n        seedPolicy = SeedPolicyType.FIXED,\n        effectiveSeed = 20260307L,\n        maxOutputTokens = 64,\n        contextSize = contextSize,\n        promptTokenCount = 10,\n        chatTemplateId = "qwen35",\n        systemPromptVersion = "benchmark-v1",\n    )\n}''',
)
Path(test).write_text(text)
