package io.github.daniele21.localllm.audit.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

public final class InferenceAuditEntities {
    private InferenceAuditEntities() {}

    @Entity(
            tableName = "inference_audit_records",
            indices = {
                @Index(value = {"received_at_epoch_ms"}),
                @Index(value = {"application_id", "use_case_id"}),
                @Index(value = {"status"})
            })
    public static final class InferenceAuditEntity {
        @PrimaryKey
        @NonNull
        @ColumnInfo(name = "request_id")
        public String requestId = "";

        @NonNull
        @ColumnInfo(name = "origin_kind")
        public String originKind = "";

        @NonNull
        @ColumnInfo(name = "application_id")
        public String applicationId = "";

        @NonNull
        @ColumnInfo(name = "use_case_id")
        public String useCaseId = "";

        @Nullable
        @ColumnInfo(name = "verified_package_name")
        public String verifiedPackageName;

        @ColumnInfo(name = "received_at_epoch_ms")
        public long receivedAtEpochMs;

        @NonNull public String status = "";

        @Nullable
        @ColumnInfo(name = "prepared_at_epoch_ms")
        public Long preparedAtEpochMs;

        @Nullable
        @ColumnInfo(name = "running_at_epoch_ms")
        public Long runningAtEpochMs;

        @Nullable
        @ColumnInfo(name = "completed_at_epoch_ms")
        public Long completedAtEpochMs;

        @Nullable
        @ColumnInfo(name = "model_digest")
        public String modelDigest;

        @Nullable
        @ColumnInfo(name = "model_load_kind")
        public String modelLoadKind;

        @Nullable
        @ColumnInfo(name = "preset_id")
        public String presetId;

        @Nullable
        @ColumnInfo(name = "preset_version")
        public Integer presetVersion;

        @Nullable
        @ColumnInfo(name = "backend_id")
        public String backendId;

        @Nullable
        @ColumnInfo(name = "backend_revision")
        public String backendRevision;

        @Nullable
        @ColumnInfo(name = "backend_execution_fingerprint")
        public String backendExecutionFingerprint;

        @Nullable
        @ColumnInfo(name = "effective_placement")
        public String effectivePlacement;

        @Nullable
        @ColumnInfo(name = "use_case_revision")
        public Integer useCaseRevision;

        @Nullable
        @ColumnInfo(name = "binding_revision")
        public Integer bindingRevision;

        @Nullable
        @ColumnInfo(name = "terminal_code")
        public String terminalCode;

        @ColumnInfo(name = "terminal_has_metrics")
        public boolean terminalHasMetrics;

        @Nullable
        @ColumnInfo(name = "metric_model_load_kind")
        public String metricModelLoadKind;

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
        @ColumnInfo(name = "prefill_ms")
        public Long prefillMs;

        @Nullable
        @ColumnInfo(name = "decode_ms")
        public Long decodeMs;

        @Nullable
        @ColumnInfo(name = "stop_reason")
        public String stopReason;

        @Nullable
        @ColumnInfo(name = "prompt_planning_ms")
        public Long promptPlanningMs;

        @Nullable
        @ColumnInfo(name = "context_creation_ms")
        public Long contextCreationMs;

        @Nullable
        @ColumnInfo(name = "time_to_first_answer_ms")
        public Long timeToFirstAnswerMs;

        @Nullable
        @ColumnInfo(name = "reasoning_tokens")
        public Integer reasoningTokens;

        @Nullable
        @ColumnInfo(name = "answer_tokens")
        public Integer answerTokens;

        @NonNull
        @ColumnInfo(name = "encrypted_content")
        public byte[] encryptedContent = new byte[0];

        @ColumnInfo(name = "encrypted_content_bytes")
        public long encryptedContentBytes;
    }
}
