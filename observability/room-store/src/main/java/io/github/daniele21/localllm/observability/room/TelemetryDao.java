package io.github.daniele21.localllm.observability.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRun(TelemetryEntities.GenerationRunEntity run);

    @Query(
            "SELECT * FROM generation_runs "
                    + "ORDER BY started_at_epoch_ms DESC, request_id DESC LIMIT :limit")
    List<TelemetryEntities.GenerationRunEntity> recentRuns(int limit);

    @Nullable
    @Query("SELECT * FROM generation_runs WHERE request_id = :requestId LIMIT 1")
    TelemetryEntities.GenerationRunEntity findRun(String requestId);

    @Query(
            "DELETE FROM generation_runs WHERE request_id NOT IN ("
                    + "SELECT request_id FROM generation_runs "
                    + "ORDER BY started_at_epoch_ms DESC, request_id DESC LIMIT :maxRows)")
    void trimRuns(int maxRows);

    @Insert
    long insertLog(TelemetryEntities.StructuredLogEntity log);

    @Query(
            "SELECT * FROM structured_logs "
                    + "ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :limit")
    List<TelemetryEntities.StructuredLogEntity> recentLogs(int limit);

    @Query(
            "SELECT * FROM structured_logs WHERE request_id = :requestId "
                    + "ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :limit")
    List<TelemetryEntities.StructuredLogEntity> recentLogsForRequest(String requestId, int limit);

    @Query(
            "DELETE FROM structured_logs WHERE id NOT IN ("
                    + "SELECT id FROM structured_logs "
                    + "ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :maxRows)")
    void trimLogs(int maxRows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHealth(TelemetryEntities.HealthCheckEntity result);

    @Query("SELECT * FROM health_results ORDER BY id ASC")
    List<TelemetryEntities.HealthCheckEntity> healthResults();
}
