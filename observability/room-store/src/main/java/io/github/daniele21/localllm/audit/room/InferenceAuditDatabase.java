package io.github.daniele21.localllm.audit.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {InferenceAuditEntities.InferenceAuditEntity.class},
        version = 1,
        exportSchema = true)
public abstract class InferenceAuditDatabase extends RoomDatabase {
    public abstract InferenceAuditDao inferenceAuditDao();
}
