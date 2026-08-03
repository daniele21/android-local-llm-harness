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
                @Index(value = {"application_id", "use_case_id"})
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
}
