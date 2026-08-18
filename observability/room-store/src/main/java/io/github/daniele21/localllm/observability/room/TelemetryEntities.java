package io.github.daniele21.localllm.observability.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

public final class TelemetryEntities {
    private TelemetryEntities() {}

    @Entity(
            tableName = "generation_runs",
            indices = {
                @Index(value = {"started_at_epoch_ms"}),
                @Index(value = {"application_id", "use_case_id"}),
                @Index(value = {"session_id"})
            })
    public static final class GenerationRunEntity {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "request_id")
        public String requestId = "";

        @NonNull
        @ColumnInfo(name = "application_id")
        public String applicationId = "";

        @NonNull
        @ColumnInfo(name = "use_case_id")
        public String useCaseId = "";

        @NonNull
        @ColumnInfo(name = "model_digest")
        public String modelDigest = "";

        @ColumnInfo(name = "started_at_epoch_ms")
        public long startedAtEpochMs;

        @Nullable
        @ColumnInfo(name = "completed_at_epoch_ms")
        public Long completedAtEpochMs;

        @NonNull
        public String status = "";

        @Nullable
        @ColumnInfo(name = "queue_ms")
        public Long queueMs;

        @Nullable
        @ColumnInfo(name = "model_load_ms")
        public Long modelLoadMs;

        @NonNull
        @ColumnInfo(name = "model_load_kind")
        public String modelLoadKind = "UNKNOWN";

        @Nullable
        @ColumnInfo(name = "time_to_first_token_ms")
        public Long timeToFirstTokenMs;

        @Nullable
        @ColumnInfo(name = "total_ms")
        public Long totalMs;

        @Nullable
        @ColumnInfo(name = "input_tokens")
        public Integer inputTokens;

        @Nullable
        @ColumnInfo(name = "output_tokens")
        public Integer outputTokens;

        @Nullable
        @ColumnInfo(name = "decode_tokens_per_second")
        public Double decodeTokensPerSecond;

        @Nullable
        @ColumnInfo(name = "error_code")
        public String errorCode;

        @Nullable
        @ColumnInfo(name = "prefill_ms")
        public Long prefillMs;

        @Nullable
        @ColumnInfo(name = "decode_ms")
        public Long decodeMs;

        @Nullable @ColumnInfo(name = "preset_id") public String presetId;
        @Nullable @ColumnInfo(name = "preset_version") public Integer presetVersion;
        @Nullable public Float temperature;
        @Nullable @ColumnInfo(name = "top_p") public Float topP;
        @Nullable @ColumnInfo(name = "top_k") public Integer topK;
        @Nullable @ColumnInfo(name = "min_p") public Float minP;
        @Nullable @ColumnInfo(name = "presence_penalty") public Float presencePenalty;
        @Nullable @ColumnInfo(name = "thinking_mode") public String thinkingMode;
        @Nullable @ColumnInfo(name = "repeat_penalty") public Float repeatPenalty;
        @Nullable @ColumnInfo(name = "repeat_last_n") public Integer repeatLastN;
        @Nullable @ColumnInfo(name = "seed_policy") public String seedPolicy;
        @Nullable @ColumnInfo(name = "effective_seed") public Long effectiveSeed;
        @Nullable @ColumnInfo(name = "max_output_tokens") public Integer maxOutputTokens;
        @Nullable @ColumnInfo(name = "context_size") public Integer contextSize;
        @Nullable @ColumnInfo(name = "prompt_token_count") public Integer promptTokenCount;
        @Nullable @ColumnInfo(name = "chat_template_id") public String chatTemplateId;
        @Nullable @ColumnInfo(name = "chat_template_source") public String chatTemplateSource;
        @Nullable @ColumnInfo(name = "system_prompt_version") public String systemPromptVersion;
        @Nullable @ColumnInfo(name = "stop_reason") public String stopReason;
        @Nullable @ColumnInfo(name = "prompt_planning_ms") public Long promptPlanningMs;
        @Nullable @ColumnInfo(name = "context_creation_ms") public Long contextCreationMs;
        @Nullable @ColumnInfo(name = "session_id") public String sessionId;
        @Nullable @ColumnInfo(name = "use_case_revision") public Integer useCaseRevision;
        @Nullable @ColumnInfo(name = "binding_revision") public Integer bindingRevision;
    }

    @Entity(
            tableName = "inference_sessions",
            indices = {
                @Index(value = {"created_at_epoch_ms"}),
                @Index(value = {"application_id", "use_case_id"}),
                @Index(value = {"model_digest"})
            })
    public static final class InferenceSessionEntity {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "session_id")
        public String sessionId = "";

        @NonNull
        @ColumnInfo(name = "application_id")
        public String applicationId = "";

        @NonNull
        @ColumnInfo(name = "use_case_id")
        public String useCaseId = "";

        @NonNull
        @ColumnInfo(name = "model_digest")
        public String modelDigest = "";

        @NonNull
        @ColumnInfo(name = "session_kind")
        public String sessionKind = "";

        @ColumnInfo(name = "created_at_epoch_ms")
        public long createdAtEpochMs;

        @Nullable
        @ColumnInfo(name = "closed_at_epoch_ms")
        public Long closedAtEpochMs;

        @NonNull
        public String status = "";

        @Nullable
        @ColumnInfo(name = "close_reason")
        public String closeReason;

        @Nullable
        @ColumnInfo(name = "preset_id")
        public String presetId;

        @Nullable
        @ColumnInfo(name = "preset_version")
        public Integer presetVersion;

        @Nullable
        @ColumnInfo(name = "use_case_revision")
        public Integer useCaseRevision;

        @Nullable
        @ColumnInfo(name = "binding_revision")
        public Integer bindingRevision;
    }

    @Entity(
            tableName = "structured_logs",
            indices = {
                @Index(value = {"timestamp_epoch_ms"}),
                @Index(value = {"request_id"})
            })
    public static final class StructuredLogEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;

        @ColumnInfo(name = "timestamp_epoch_ms")
        public long timestampEpochMs;

        @NonNull
        public String level = "";

        @NonNull
        public String component = "";

        @NonNull
        public String event = "";

        @Nullable
        @ColumnInfo(name = "request_id")
        public String requestId;

        @NonNull
        @ColumnInfo(name = "encoded_fields")
        public String encodedFields = "";
    }

    @Entity(tableName = "health_results")
    public static final class HealthCheckEntity {
        @PrimaryKey
        @NonNull
        public String id = "";

        @NonNull
        public String status = "";

        @NonNull
        public String detail = "";

        @ColumnInfo(name = "duration_ms")
        public long durationMs;
    }

    @Entity(
            tableName = "resource_snapshots",
            indices = {@Index(value = {"timestamp_epoch_ms"})})
    public static final class ResourceSnapshotEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;

        @ColumnInfo(name = "timestamp_epoch_ms")
        public long timestampEpochMs;

        @Nullable
        @ColumnInfo(name = "process_pss_bytes")
        public Long processPssBytes;

        @Nullable
        @ColumnInfo(name = "native_heap_bytes")
        public Long nativeHeapBytes;

        @Nullable
        @ColumnInfo(name = "java_heap_used_bytes")
        public Long javaHeapUsedBytes;

        @Nullable
        @ColumnInfo(name = "available_memory_bytes")
        public Long availableMemoryBytes;

        @Nullable
        @ColumnInfo(name = "low_memory")
        public Boolean lowMemory;

        @NonNull
        @ColumnInfo(name = "thermal_status")
        public String thermalStatus = "UNKNOWN";
    }

    @Entity(tableName = "benchmark_baselines")
    public static final class BenchmarkBaselineEntity {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "baseline_id")
        public String baselineId = "";

        @NonNull
        @ColumnInfo(name = "application_id")
        public String applicationId = "";

        @NonNull
        @ColumnInfo(name = "use_case_id")
        public String useCaseId = "";

        @NonNull
        @ColumnInfo(name = "model_digest")
        public String modelDigest = "";

        @NonNull
        @ColumnInfo(name = "model_load_kind")
        public String modelLoadKind = "";

        @NonNull
        @ColumnInfo(name = "execution_identity")
        public String executionIdentity = "";

        @ColumnInfo(name = "captured_at_epoch_ms")
        public long capturedAtEpochMs;

        @ColumnInfo(name = "sample_count")
        public int sampleCount;

        @Nullable
        @ColumnInfo(name = "median_time_to_first_token_ms")
        public Double medianTimeToFirstTokenMs;

        @Nullable
        @ColumnInfo(name = "p95_time_to_first_token_ms")
        public Double p95TimeToFirstTokenMs;

        @Nullable
        @ColumnInfo(name = "median_total_ms")
        public Double medianTotalMs;

        @Nullable
        @ColumnInfo(name = "p95_total_ms")
        public Double p95TotalMs;

        @Nullable
        @ColumnInfo(name = "median_decode_tokens_per_second")
        public Double medianDecodeTokensPerSecond;
    }

    @Entity(
            tableName = "benchmark_baseline_history",
            indices = {
                @Index(value = {"captured_at_epoch_ms"}),
                @Index(
                        value = {
                            "application_id",
                            "use_case_id",
                            "model_digest",
                            "model_load_kind"
                        })
            })
    public static final class BenchmarkBaselineHistoryEntity {
        @PrimaryKey(autoGenerate = true)
        public long id;

        @NonNull
        @ColumnInfo(name = "application_id")
        public String applicationId = "";

        @NonNull
        @ColumnInfo(name = "use_case_id")
        public String useCaseId = "";

        @NonNull
        @ColumnInfo(name = "model_digest")
        public String modelDigest = "";

        @NonNull
        @ColumnInfo(name = "model_load_kind")
        public String modelLoadKind = "";

        @NonNull
        @ColumnInfo(name = "execution_identity")
        public String executionIdentity = "";

        @ColumnInfo(name = "captured_at_epoch_ms")
        public long capturedAtEpochMs;

        @ColumnInfo(name = "sample_count")
        public int sampleCount;

        @Nullable
        @ColumnInfo(name = "median_time_to_first_token_ms")
        public Double medianTimeToFirstTokenMs;

        @Nullable
        @ColumnInfo(name = "p95_time_to_first_token_ms")
        public Double p95TimeToFirstTokenMs;

        @Nullable
        @ColumnInfo(name = "median_total_ms")
        public Double medianTotalMs;

        @Nullable
        @ColumnInfo(name = "p95_total_ms")
        public Double p95TotalMs;

        @Nullable
        @ColumnInfo(name = "median_decode_tokens_per_second")
        public Double medianDecodeTokensPerSecond;
    }
}
