package io.github.daniele21.localllm.observability.room

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.InferenceSessionRecord
import io.github.daniele21.localllm.observability.SessionRunStatus
import io.github.daniele21.localllm.observability.SessionTelemetryRetentionPolicy
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.Executors

class RoomSessionTelemetryRepositoryTest {
    @Test
    fun `repository persists session updates and applies independent retention`() {
        val sessionDao = FakeSessionTelemetryDao()
        val repository = RoomTelemetryRepository(
            dao = unusedTelemetryDao(),
            retention = TelemetryRetentionPolicy(),
            executor = Executors.newSingleThreadExecutor(),
            sessionDao = sessionDao,
            sessionRetention = SessionTelemetryRetentionPolicy(maxSessions = 2),
        )

        repository.use {
            repository.recordSession(activeSession("s1", 1))
            repository.recordSession(activeSession("s2", 2))
            repository.recordSession(activeSession("s3", 3))

            assertEquals(listOf("s3", "s2"), repository.recentSessions().map { it.sessionId.value })
            assertNull(repository.findSession(SessionId("s1")))

            repository.recordSession(activeSession("s2", 2).copy(useCaseRevision = 5, bindingRevision = 9))
            assertEquals(5, repository.findSession(SessionId("s2"))?.useCaseRevision)
            assertEquals(9, repository.findSession(SessionId("s2"))?.bindingRevision)
        }
    }

    private fun activeSession(id: String, timestamp: Long): InferenceSessionRecord = InferenceSessionRecord(
        sessionId = SessionId(id),
        applicationId = ApplicationId("redactguard"),
        useCaseId = UseCaseId("document-pii-detection"),
        modelDigest = ModelDigest("a".repeat(64)),
        sessionKind = SessionKind.STATELESS,
        createdAtEpochMs = timestamp,
        closedAtEpochMs = null,
        status = SessionRunStatus.ACTIVE,
        closeReason = null,
        useCaseRevision = 4,
        bindingRevision = 7,
    )

    private fun unusedTelemetryDao(): TelemetryDao = Proxy.newProxyInstance(
        TelemetryDao::class.java.classLoader,
        arrayOf(TelemetryDao::class.java),
    ) { _, method, _ ->
        throw AssertionError("Unexpected telemetry DAO call: ${method.name}")
    } as TelemetryDao
}

private class FakeSessionTelemetryDao : SessionTelemetryDao {
    private val sessions = linkedMapOf<String, TelemetryEntities.InferenceSessionEntity>()

    override fun upsertSession(session: TelemetryEntities.InferenceSessionEntity) {
        sessions[session.sessionId] = session
    }

    override fun recentSessions(limit: Int): List<TelemetryEntities.InferenceSessionEntity> = sessions.values
        .sortedWith(
            compareByDescending<TelemetryEntities.InferenceSessionEntity> { it.createdAtEpochMs }
                .thenByDescending { it.sessionId },
        )
        .take(limit)

    override fun findSession(sessionId: String): TelemetryEntities.InferenceSessionEntity? = sessions[sessionId]

    override fun trimSessions(maxRows: Int) {
        val retained = recentSessions(maxRows).mapTo(mutableSetOf()) { it.sessionId }
        sessions.keys.removeAll { it !in retained }
    }
}
