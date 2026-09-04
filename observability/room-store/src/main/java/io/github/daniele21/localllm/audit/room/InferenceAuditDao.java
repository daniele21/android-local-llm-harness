package io.github.daniele21.localllm.audit.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface InferenceAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(InferenceAuditEntities.InferenceAuditEntity record);

    @Nullable
    @Query("SELECT * FROM inference_audit_records WHERE request_id = :requestId LIMIT 1")
    InferenceAuditEntities.InferenceAuditEntity find(String requestId);

    @Query(
            "SELECT * FROM inference_audit_records "
                    + "WHERE (:applicationId IS NULL OR application_id = :applicationId) "
                    + "AND (:useCaseId IS NULL OR use_case_id = :useCaseId) "
                    + "AND (:beforeReceivedAtEpochMs IS NULL OR received_at_epoch_ms < :beforeReceivedAtEpochMs) "
                    + "ORDER BY received_at_epoch_ms DESC, request_id DESC LIMIT :limit")
    List<InferenceAuditEntities.InferenceAuditEntity> recent(
            int limit,
            @Nullable String applicationId,
            @Nullable String useCaseId,
            @Nullable Long beforeReceivedAtEpochMs);

    @Query(
            "SELECT * FROM inference_audit_records "
                    + "WHERE (:applicationId IS NULL OR application_id = :applicationId) "
                    + "AND (:useCaseId IS NULL OR use_case_id = :useCaseId) "
                    + "AND status IN (:statuses) "
                    + "AND (:beforeReceivedAtEpochMs IS NULL OR received_at_epoch_ms < :beforeReceivedAtEpochMs) "
                    + "ORDER BY received_at_epoch_ms DESC, request_id DESC LIMIT :limit")
    List<InferenceAuditEntities.InferenceAuditEntity> recentWithStatuses(
            int limit,
            @Nullable String applicationId,
            @Nullable String useCaseId,
            List<String> statuses,
            @Nullable Long beforeReceivedAtEpochMs);

    @Query(
            "SELECT * FROM inference_audit_records "
                    + "WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED') "
                    + "ORDER BY received_at_epoch_ms ASC, request_id ASC LIMIT :limit")
    List<InferenceAuditEntities.InferenceAuditEntity> nonTerminal(int limit);

    @Query("SELECT COUNT(*) FROM inference_audit_records")
    int countRecords();

    @Query("SELECT COALESCE(SUM(encrypted_content_bytes), 0) FROM inference_audit_records")
    long encryptedContentBytes();

    @Query(
            "SELECT request_id FROM inference_audit_records "
                    + "WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED') "
                    + "ORDER BY COALESCE(completed_at_epoch_ms, received_at_epoch_ms) ASC, request_id ASC LIMIT :limit")
    List<String> oldestTerminalRequestIds(int limit);

    @Query(
            "DELETE FROM inference_audit_records "
                    + "WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED') "
                    + "AND COALESCE(completed_at_epoch_ms, received_at_epoch_ms) < :cutoffEpochMs")
    int deleteTerminalOlderThan(long cutoffEpochMs);

    @Query("DELETE FROM inference_audit_records WHERE request_id = :requestId")
    int deleteByRequestId(String requestId);

    @Query(
            "DELETE FROM inference_audit_records "
                    + "WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED')")
    int clearTerminalHistory();
}
