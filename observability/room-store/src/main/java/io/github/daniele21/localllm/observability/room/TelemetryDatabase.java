package io.github.daniele21.localllm.observability.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {
            TelemetryEntities.GenerationRunEntity.class,
            TelemetryEntities.StructuredLogEntity.class,
            TelemetryEntities.HealthCheckEntity.class,
            TelemetryEntities.ResourceSnapshotEntity.class,
            TelemetryEntities.BenchmarkBaselineEntity.class,
            TelemetryEntities.BenchmarkBaselineHistoryEntity.class
        },
        version = 5,
        exportSchema = true)
public abstract class TelemetryDatabase extends RoomDatabase {
    public abstract TelemetryDao telemetryDao();
}
