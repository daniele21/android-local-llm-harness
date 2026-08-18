package io.github.daniele21.localllm.observability.room;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public interface SessionTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSession(TelemetryEntities.InferenceSessionEntity session);

    @Query(
            "SELECT * FROM inference_sessions "
                    + "ORDER BY created_at_epoch_ms DESC, session_id DESC LIMIT :limit")
    List<TelemetryEntities.InferenceSessionEntity> recentSessions(int limit);

    @Nullable
    @Query("SELECT * FROM inference_sessions WHERE session_id = :sessionId LIMIT 1")
    TelemetryEntities.InferenceSessionEntity findSession(String sessionId);

    @Query(
            "DELETE FROM inference_sessions WHERE session_id NOT IN ("
                    + "SELECT session_id FROM inference_sessions "
                    + "ORDER BY created_at_epoch_ms DESC, session_id DESC LIMIT :maxRows)")
    void trimSessions(int maxRows);

    @Transaction
    default void upsertSessionWithRetention(
            TelemetryEntities.InferenceSessionEntity session, int maxRows) {
        upsertSession(session);
        trimSessions(maxRows);
    }
}
