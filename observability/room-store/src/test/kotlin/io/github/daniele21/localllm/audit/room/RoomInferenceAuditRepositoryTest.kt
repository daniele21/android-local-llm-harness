package io.github.daniele21.localllm.audit.room

import io.github.daniele21.localllm.audit.InferenceAuditAdmission
import io.github.daniele21.localllm.audit.InferenceAuditExecutionIdentity
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import io.github.daniele21.localllm.audit.InferenceAuditInput
import io.github.daniele21.localllm.audit.InferenceAuditMetrics
import io.github.daniele21.localllm.audit.InferenceAuditOrigin
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditPrepared
import io.github.daniele21.localllm.audit.InferenceAuditQuery
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditRetentionPolicy
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.InferenceAuditTerminal
import io.github.daniele21.localllm.audit.InferenceAuditTerminalContent
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class RoomInferenceAuditRepositoryTest {
    @Test
    fun `strict lifecycle round trips encrypted sensitive content`() {
        val dao = FakeInferenceAuditDao()
        repository(dao).use { repository ->
            val requestId = RequestId("audit-round-trip")
            assertSuccess(repository.admit(admission(requestId, 100L, "secret input")))
            assertSuccess(
                repository.markPrepared(
                    prepared(requestId, 110L, "system wrapper secret input"),
                ),
            )
            assertSuccess(repository.markRunning(requestId, 120L))
            assertSuccess(repository.recordTerminal(completed(requestId, 150L, "private answer")))

            val restored = successValue(repository.find(requestId))
            requireNotNull(restored)
            assertEquals(InferenceAuditStatus.COMPLETED, restored.status)
            assertEquals(InferenceAuditInput.Text("secret input"), restored.admission.input)
            assertEquals("system wrapper secret input", restored.prepared?.effectivePrompt)
            assertEquals("private answer", restored.terminal?.content?.answerOutput)
            assertEquals(50L, restored.terminal?.metrics?.totalMs)

            val persisted = requireNotNull(dao.find(requestId.value))
            assertFalse(persisted.encryptedContent.containsUtf8("secret input"))
            assertFalse(persisted.encryptedContent.containsUtf8("system wrapper secret input"))
            assertFalse(persisted.encryptedContent.containsUtf8("private answer"))
        }
    }

    @Test
    fun `metadata list queries do not decrypt sensitive content`() {
        val dao = FakeInferenceAuditDao()
        val cipher = CountingCipher(JvmAesGcmCipher())
        repository(dao, cipher = cipher).use { repository ->
            val requestId = RequestId("metadata-only")
            assertSuccess(repository.admit(admission(requestId, 100L, "secret")))
            val opensBeforeList = cipher.openCalls

            val summaries = successValue(
                repository.recent(
                    InferenceAuditQuery(applicationId = ApplicationId("consumer-app")),
                ),
            )

            assertEquals(listOf(requestId), summaries.map { it.requestId })
            assertEquals(opensBeforeList, cipher.openCalls)
        }
    }

    @Test
    fun `retention evicts oldest terminal records and protects active records`() {
        val dao = FakeInferenceAuditDao()
        repository(
            dao = dao,
            retention = InferenceAuditRetentionPolicy(
                maxRecords = 2,
                maxAgeMs = 10_000L,
                maxEncryptedContentBytes = 1_000_000L,
            ),
        ).use { repository ->
            complete(repository, RequestId("old"), 100L)
            val active = RequestId("active")
            assertSuccess(repository.admit(admission(active, 200L, "still running")))
            complete(repository, RequestId("new"), 300L)

            assertEquals(null, successValue(repository.find(RequestId("old"))))
            assertTrue(successValue(repository.find(active)) != null)
            assertTrue(successValue(repository.find(RequestId("new"))) != null)
        }
    }

    @Test
    fun `encryption failure fails admission before persistence`() {
        val dao = FakeInferenceAuditDao()
        repository(dao, cipher = FailingCipher()).use { repository ->
            val result = repository.admit(admission(RequestId("blocked"), 100L, "must not persist"))

            assertEquals(
                InferenceAuditResult.Failure(InferenceAuditFailureCode.ENCRYPTION_UNAVAILABLE),
                result,
            )
            assertEquals(0, dao.countRecords())
        }
    }

    private fun repository(
        dao: FakeInferenceAuditDao,
        retention: InferenceAuditRetentionPolicy = InferenceAuditRetentionPolicy(),
        cipher: InferenceAuditCipher = JvmAesGcmCipher(),
    ): RoomInferenceAuditRepository = RoomInferenceAuditRepository(
        dao = dao,
        retention = retention,
        cipher = cipher,
        executor = Executors.newSingleThreadExecutor(),
    )

    private fun admission(requestId: RequestId, timestamp: Long, input: String): InferenceAuditAdmission =
        InferenceAuditAdmission(
            requestId = requestId,
            origin = InferenceAuditOrigin(
                kind = InferenceAuditOriginKind.EXTERNAL_CONSUMER,
                applicationId = ApplicationId("consumer-app"),
                useCaseId = UseCaseId("assistant"),
                verifiedPackageName = "com.example.consumer",
            ),
            receivedAtEpochMs = timestamp,
            input = InferenceAuditInput.Text(input),
        )

    private fun prepared(requestId: RequestId, timestamp: Long, prompt: String): InferenceAuditPrepared =
        InferenceAuditPrepared(
            requestId = requestId,
            preparedAtEpochMs = timestamp,
            effectivePrompt = prompt,
            execution = InferenceAuditExecutionIdentity(
                modelDigest = ModelDigest("a".repeat(64)),
                modelLoadKind = ModelLoadKind.WARM,
                backendId = "llama.cpp",
                backendExecutionFingerprint = "b".repeat(64),
            ),
        )

    private fun completed(requestId: RequestId, timestamp: Long, answer: String): InferenceAuditTerminal =
        InferenceAuditTerminal(
            requestId = requestId,
            status = InferenceAuditStatus.COMPLETED,
            completedAtEpochMs = timestamp,
            content = InferenceAuditTerminalContent(answerOutput = answer),
            metrics = InferenceAuditMetrics(
                queueMs = 1L,
                modelLoadMs = 2L,
                timeToFirstTokenMs = 3L,
                totalMs = 50L,
                inputTokens = 4,
                outputTokens = 5,
                decodeTokensPerSecond = 6.0,
                modelLoadKind = ModelLoadKind.WARM,
                stopReason = StopReason.END_OF_GENERATION,
            ),
        )

    private fun complete(repository: RoomInferenceAuditRepository, requestId: RequestId, timestamp: Long) {
        assertSuccess(repository.admit(admission(requestId, timestamp, "input-$timestamp")))
        assertSuccess(repository.markPrepared(prepared(requestId, timestamp + 1, "prompt-$timestamp")))
        assertSuccess(repository.markRunning(requestId, timestamp + 2))
        assertSuccess(repository.recordTerminal(completed(requestId, timestamp + 3, "answer-$timestamp")))
    }

    private fun assertSuccess(result: InferenceAuditResult<Unit>) {
        assertTrue(result is InferenceAuditResult.Success)
    }

    private fun <T> successValue(result: InferenceAuditResult<T>): T {
        require(result is InferenceAuditResult.Success) { "Expected success but was $result" }
        return result.value
    }
}

private class FakeInferenceAuditDao : InferenceAuditDao {
    private val records = linkedMapOf<String, InferenceAuditEntities.InferenceAuditEntity>()

    override fun upsert(record: InferenceAuditEntities.InferenceAuditEntity) {
        records[record.requestId] = record
    }

    override fun find(requestId: String): InferenceAuditEntities.InferenceAuditEntity? = records[requestId]

    override fun recent(
        limit: Int,
        applicationId: String?,
        useCaseId: String?,
        beforeReceivedAtEpochMs: Long?,
    ): List<InferenceAuditEntities.InferenceAuditEntity> = filtered(
        applicationId = applicationId,
        useCaseId = useCaseId,
        statuses = null,
        beforeReceivedAtEpochMs = beforeReceivedAtEpochMs,
    ).take(limit)

    override fun recentWithStatuses(
        limit: Int,
        applicationId: String?,
        useCaseId: String?,
        statuses: List<String>,
        beforeReceivedAtEpochMs: Long?,
    ): List<InferenceAuditEntities.InferenceAuditEntity> = filtered(
        applicationId = applicationId,
        useCaseId = useCaseId,
        statuses = statuses.toSet(),
        beforeReceivedAtEpochMs = beforeReceivedAtEpochMs,
    ).take(limit)

    override fun nonTerminal(limit: Int): List<InferenceAuditEntities.InferenceAuditEntity> = records.values
        .filter { it.status !in TERMINAL_STATUSES }
        .sortedWith(compareBy<InferenceAuditEntities.InferenceAuditEntity> { it.receivedAtEpochMs }.thenBy { it.requestId })
        .take(limit)

    override fun countRecords(): Int = records.size

    override fun encryptedContentBytes(): Long = records.values.sumOf { it.encryptedContentBytes }

    override fun oldestTerminalRequestIds(limit: Int): List<String> = records.values
        .filter { it.status in TERMINAL_STATUSES }
        .sortedWith(
            compareBy<InferenceAuditEntities.InferenceAuditEntity> { it.completedAtEpochMs ?: it.receivedAtEpochMs }
                .thenBy { it.requestId },
        ).take(limit)
        .map { it.requestId }

    override fun deleteTerminalOlderThan(cutoffEpochMs: Long): Int {
        val ids = records.values
            .filter { it.status in TERMINAL_STATUSES }
            .filter { (it.completedAtEpochMs ?: it.receivedAtEpochMs) < cutoffEpochMs }
            .map { it.requestId }
        ids.forEach(records::remove)
        return ids.size
    }

    override fun deleteByRequestId(requestId: String): Int = if (records.remove(requestId) != null) 1 else 0

    override fun clearTerminalHistory(): Int {
        val ids = records.values.filter { it.status in TERMINAL_STATUSES }.map { it.requestId }
        ids.forEach(records::remove)
        return ids.size
    }

    private fun filtered(
        applicationId: String?,
        useCaseId: String?,
        statuses: Set<String>?,
        beforeReceivedAtEpochMs: Long?,
    ): List<InferenceAuditEntities.InferenceAuditEntity> = records.values
        .asSequence()
        .filter { applicationId == null || it.applicationId == applicationId }
        .filter { useCaseId == null || it.useCaseId == useCaseId }
        .filter { statuses == null || it.status in statuses }
        .filter { beforeReceivedAtEpochMs == null || it.receivedAtEpochMs < beforeReceivedAtEpochMs }
        .sortedWith(
            compareByDescending<InferenceAuditEntities.InferenceAuditEntity> { it.receivedAtEpochMs }
                .thenByDescending { it.requestId },
        ).toList()

    private companion object {
        val TERMINAL_STATUSES = setOf("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED")
    }
}

private class JvmAesGcmCipher : InferenceAuditCipher {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private val random = SecureRandom()

    override fun seal(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun open(ciphertext: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, 12)
        val encrypted = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }
}

private class CountingCipher(private val delegate: InferenceAuditCipher) : InferenceAuditCipher {
    var openCalls: Int = 0
        private set

    override fun seal(plaintext: ByteArray): ByteArray = delegate.seal(plaintext)

    override fun open(ciphertext: ByteArray): ByteArray {
        openCalls += 1
        return delegate.open(ciphertext)
    }
}

private class FailingCipher : InferenceAuditCipher {
    override fun seal(plaintext: ByteArray): ByteArray =
        throw InferenceAuditCipherException(InferenceAuditFailureCode.ENCRYPTION_UNAVAILABLE)

    override fun open(ciphertext: ByteArray): ByteArray = error("open must not be called")
}

private fun ByteArray.containsUtf8(value: String): Boolean {
    val needle = value.toByteArray(Charsets.UTF_8)
    if (needle.isEmpty() || needle.size > size) return false
    return indices.any { start ->
        start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
    }
}
