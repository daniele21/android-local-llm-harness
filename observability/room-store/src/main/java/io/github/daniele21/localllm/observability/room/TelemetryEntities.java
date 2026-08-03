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
}
