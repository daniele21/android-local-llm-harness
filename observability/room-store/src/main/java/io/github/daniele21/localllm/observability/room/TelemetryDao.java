package io.github.daniele21.localllm.observability.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
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

    @Transaction
    default void upsertRunWithRetention(
            TelemetryEntities.GenerationRunEntity run, int maxRows) {
        upsertRun(run);
        trimRuns(maxRows);
    }

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

    @Transaction
    default void insertLogWithRetention(
            TelemetryEntities.StructuredLogEntity log, int maxRows) {
        insertLog(log);
        trimLogs(maxRows);
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHealth(TelemetryEntities.HealthCheckEntity result);

    @Query("SELECT * FROM health_results ORDER BY id ASC")
    List<TelemetryEntities.HealthCheckEntity> healthResults();

    @Insert
    long insertResourceSnapshot(TelemetryEntities.ResourceSnapshotEntity snapshot);

    @Query(
            "SELECT * FROM resource_snapshots "
                    + "ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :limit")
    List<TelemetryEntities.ResourceSnapshotEntity> recentResourceSnapshots(int limit);

    @Query(
            "DELETE FROM resource_snapshots WHERE id NOT IN ("
                    + "SELECT id FROM resource_snapshots "
                    + "ORDER BY timestamp_epoch_ms DESC, id DESC LIMIT :maxRows)")
    void trimResourceSnapshots(int maxRows);

    @Transaction
    default void insertResourceSnapshotWithRetention(
            TelemetryEntities.ResourceSnapshotEntity snapshot, int maxRows) {
        insertResourceSnapshot(snapshot);
        trimResourceSnapshots(maxRows);
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertBenchmarkBaseline(TelemetryEntities.BenchmarkBaselineEntity baseline);

    @Query("SELECT * FROM benchmark_baselines ORDER BY baseline_id ASC")
    List<TelemetryEntities.BenchmarkBaselineEntity> benchmarkBaselines();

    @Insert
    long insertBenchmarkBaselineHistory(
            TelemetryEntities.BenchmarkBaselineHistoryEntity baseline);

    @Query(
            "SELECT * FROM benchmark_baseline_history "
                    + "ORDER BY captured_at_epoch_ms DESC, id DESC LIMIT :limit")
    List<TelemetryEntities.BenchmarkBaselineHistoryEntity> benchmarkBaselineHistory(int limit);

    @Query(
            "DELETE FROM benchmark_baseline_history WHERE id NOT IN ("
                    + "SELECT id FROM benchmark_baseline_history "
                    + "ORDER BY captured_at_epoch_ms DESC, id DESC LIMIT :maxRows)")
    void trimBenchmarkBaselineHistory(int maxRows);

    @Transaction
    default void saveBenchmarkBaselineWithHistory(
            TelemetryEntities.BenchmarkBaselineEntity activeBaseline,
            TelemetryEntities.BenchmarkBaselineHistoryEntity historicalBaseline,
            int maxRows) {
        upsertBenchmarkBaseline(activeBaseline);
        insertBenchmarkBaselineHistory(historicalBaseline);
        trimBenchmarkBaselineHistory(maxRows);
    }
}