package io.github.daniele21.localllm.observability.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {
            TelemetryEntities.GenerationRunEntity.class,
            TelemetryEntities.StructuredLogEntity.class,
            TelemetryEntities.HealthCheckEntity.class
        },
        version = 1,
        exportSchema = false)
public abstract class TelemetryDatabase extends RoomDatabase {
    public abstract TelemetryDao telemetryDao();
}
